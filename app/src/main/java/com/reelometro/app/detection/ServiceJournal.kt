package com.reelometro.app.detection

import android.content.Context
import androidx.core.content.edit

/** Cómo llegó una conexión del servicio (dice si el proceso venía de morir, de un reinicio o estaba vivo). */
enum class ConnectionKind(val code: Char) {
    /** El proceso arrancó en los primeros minutos de encendido del móvil. */
    BOOT('B'),

    /** Android arrancó el proceso para enlazar el servicio (la app no estaba en marcha: venía de un cierre). */
    COLD('C'),

    /** Con la app ya en marcha (al activarlo en Ajustes, o al apagarlo y encenderlo). */
    WARM('W'),
}

/** Cómo acabó una conexión de la que el servicio se enteró (si el proceso muere, no hay nada que apuntar). */
enum class DisconnectionKind(val code: Char) {
    /** Ya no estaba entre los servicios activados: lo apagó alguien en Ajustes › Accesibilidad. */
    TURNED_OFF('U'),

    /** Android lo desenlazó con el interruptor todavía encendido. */
    UNBOUND('S'),
}

data class ServiceConnection(val at: Long, val kind: ConnectionKind)

data class ServiceDisconnection(val at: Long, val kind: DisconnectionKind)

/** Lo último que el servicio ha apuntado de su vida, de lo más reciente a lo más antiguo. */
data class ServiceJournalState(
    val connections: List<ServiceConnection> = emptyList(),
    val disconnections: List<ServiceDisconnection> = emptyList(),
) {
    val lastConnection: ServiceConnection? get() = connections.firstOrNull()
}

/** Reglas puras del diario del servicio (sin Android). */
object ServiceJournalRules {
    /** Cuántas conexiones y desconexiones se guardan. */
    const val MAX = 6

    /** Un proceso más joven que esto al conectarse el servicio lo arrancó Android para enlazarlo. */
    const val COLD_START_MS = 15_000L

    fun connectionKind(processAgeMs: Long, uptimeAtProcessStartMs: Long): ConnectionKind = when {
        processAgeMs > COLD_START_MS -> ConnectionKind.WARM
        uptimeAtProcessStartMs < ServiceHealth.BOOT_WINDOW_MS -> ConnectionKind.BOOT
        else -> ConnectionKind.COLD
    }

    fun connected(state: ServiceJournalState, at: Long, kind: ConnectionKind): ServiceJournalState =
        state.copy(connections = (listOf(ServiceConnection(at, kind)) + state.connections).sortedByDescending { it.at }.take(MAX))

    fun disconnected(state: ServiceJournalState, at: Long, kind: DisconnectionKind): ServiceJournalState =
        state.copy(disconnections = (listOf(ServiceDisconnection(at, kind)) + state.disconnections).sortedByDescending { it.at }.take(MAX))

    /**
     * El servicio funcionó y ahora está **apagado** sin que nadie lo apagara en Ajustes mientras estaba en marcha: lo
     * ha quitado el sistema. Es lo que hace Android al «forzar la detención» de la app (lo haga el usuario, el ahorro
     * de batería de la marca o la limpieza de apps): mata el proceso, que no llega a apuntar nada, y saca el servicio
     * de los activados.
     *
     * Límite: si el proceso ya estaba muerto cuando el usuario lo apagó a mano, también sale `true` (nadie pudo
     * apuntarlo). Aun así el origen es el mismo: antes lo cerró el sistema.
     *
     * @param enabled activado ahora en Ajustes › Accesibilidad.
     */
    fun lostBySystem(enabled: Boolean, state: ServiceJournalState): Boolean {
        if (enabled) return false
        val last = state.lastConnection ?: return false
        return state.disconnections.none { it.kind == DisconnectionKind.TURNED_OFF && it.at >= last.at }
    }

    /** `C:<ms>:<tipo>` y `D:<ms>:<tipo>` separados por `;`. Lo que no se entiende se salta. */
    fun encode(state: ServiceJournalState): String =
        (state.connections.map { "C:${it.at}:${it.kind.code}" } + state.disconnections.map { "D:${it.at}:${it.kind.code}" })
            .joinToString(";")

    fun decode(text: String?): ServiceJournalState {
        val connections = ArrayList<ServiceConnection>()
        val disconnections = ArrayList<ServiceDisconnection>()
        for (entry in text.orEmpty().split(';')) {
            val parts = entry.split(':')
            if (parts.size != 3 || parts[2].length != 1) continue
            val at = parts[1].toLongOrNull() ?: continue
            val code = parts[2][0]
            when (parts[0]) {
                "C" -> ConnectionKind.entries.firstOrNull { it.code == code }?.let { connections += ServiceConnection(at, it) }
                "D" -> DisconnectionKind.entries.firstOrNull { it.code == code }?.let { disconnections += ServiceDisconnection(at, it) }
            }
        }
        return ServiceJournalState(
            connections.sortedByDescending { it.at }.take(MAX),
            disconnections.sortedByDescending { it.at }.take(MAX),
        )
    }
}

/**
 * Diario del servicio en las preferencias del aviso (`service_health`): cuándo se conectó y cuándo se desconectó. Lo
 * escribe el servicio y lo leen Hoy, la pantalla de conectar, el aviso y la cabecera del registro de Calibración. Solo
 * horas y un tipo: nada del usuario. Nunca lanza.
 *
 * Si el proceso muere (lo cierra el sistema) no hay desconexión que apuntar: eso es justo lo que distingue «lo apagó
 * el usuario» de «lo quitó el sistema» ([ServiceJournalRules.lostBySystem]). La hora de la muerte la da
 * `ApplicationExitInfo` ([ProcessExits]).
 */
class ServiceJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): ServiceJournalState = runCatching { ServiceJournalRules.decode(prefs.getString(KEY, null)) }
        .getOrDefault(ServiceJournalState())

    fun onConnected(at: Long, kind: ConnectionKind) = update { ServiceJournalRules.connected(it, at, kind) }

    fun onDisconnected(at: Long, kind: DisconnectionKind) = update { ServiceJournalRules.disconnected(it, at, kind) }

    /** La pérdida (la conexión que se perdió) de la que ya se ha avisado: no se avisa dos veces de la misma. */
    var notifiedLossOf: Long?
        get() = runCatching { prefs.getLong(KEY_NOTIFIED, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } }.getOrNull()
        set(value) {
            runCatching { prefs.edit { if (value == null) remove(KEY_NOTIFIED) else putLong(KEY_NOTIFIED, value) } }
        }

    private fun update(change: (ServiceJournalState) -> ServiceJournalState) {
        // El servicio escribe desde dos hilos (conexión, fuera del principal; desconexión, en el principal).
        synchronized(LOCK) {
            runCatching { prefs.edit { putString(KEY, ServiceJournalRules.encode(change(read()))) } }
        }
    }

    private companion object {
        const val PREFS = "service_health"
        const val KEY = "journal"
        const val KEY_NOTIFIED = "loss_notified"
        val LOCK = Any()
    }
}
