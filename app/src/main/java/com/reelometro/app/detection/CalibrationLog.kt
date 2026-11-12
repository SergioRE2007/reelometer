package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tipo de entrada del registro de calibración. */
enum class CalibrationEventType {
    /** Un `TYPE_VIEW_SCROLLED` de una de las apps, con la decisión de [FeedRules]. */
    SCROLL,

    /** Un `TYPE_WINDOW_STATE_CHANGED` (actividad, diálogo…). */
    WINDOW,

    /** Un `TYPE_VIEW_CLICKED` evaluado por [ShareDetector] (sin textos: solo id, clase y regla). */
    CLICK,
    VIDEO_START,
    VIDEO_END,
    VIDEO_DISCARD,

    /** Autor/categoría leídos de la pantalla. */
    INFO,
    SHARE,

    /** El usuario se ha ido: otra app, pantalla apagada, otra pantalla de la app, plataforma desactivada. */
    LEAVE,
}

/**
 * Una entrada del registro. [decision] es la de [FeedRules]/[ShareDetector] (✓ true, ✗ false, null si
 * no aplica) y [rule] la regla que decidió.
 */
data class CalibrationEvent(
    val time: Long,
    val type: CalibrationEventType,
    val packageName: String? = null,
    val platform: Platform? = null,
    val viewId: String? = null,
    val className: String? = null,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
    val scrollDeltaY: Int? = null,
    val scrollDeltaX: Int? = null,
    val nodeWidth: Int = 0,
    val nodeHeight: Int = 0,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val decision: Boolean? = null,
    val rule: String? = null,
    val note: String? = null,
    val videoId: Long? = null,
    val durationMs: Long? = null,
) {
    /** «✓», «✗» o «» (no aplica). */
    val decisionMark: String
        get() = when (decision) {
            true -> "✓"
            false -> "✗"
            null -> ""
        }

    /** Una línea de texto plano con todos los datos (para la lista y para exportar). */
    fun toLine(zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append(TIME_FORMAT.format(Instant.ofEpochMilli(time).atZone(zone)))
        append(' ').append(type.name)
        when {
            platform != null -> append(' ').append(platform.shortName)
            packageName != null -> append(' ').append(packageName)
        }
        if (decision != null) append(' ').append(decisionMark)
        rule?.let { append(" [").append(it).append(']') }
        viewId?.let { append(" id=").append(it.substringAfter(":id/")) }
        className?.let { append(" clase=").append(simpleClassName(it)) }
        if (fromIndex >= 0 || toIndex >= 0) append(" idx=").append(fromIndex).append('→').append(toIndex)
        if (itemCount >= 0) append(" de ").append(itemCount)
        scrollDeltaY?.let { append(" dy=").append(it) }
        scrollDeltaX?.takeIf { it != 0 }?.let { append(" dx=").append(it) }
        if (nodeWidth > 0 || nodeHeight > 0) append(" nodo=").append(nodeWidth).append('×').append(nodeHeight)
        if (screenWidth > 0 || screenHeight > 0) append(" pantalla=").append(screenWidth).append('×').append(screenHeight)
        videoId?.let { append(" vídeo=").append(it) }
        durationMs?.let { append(" duración=").append(it).append(" ms") }
        note?.let { append(" · ").append(it) }
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

/**
 * Registro en memoria (buffer circular de [CAPACITY] eventos) para la pantalla de Calibración.
 * Nada se escribe a disco y no cuesta nada mientras [enabled] es false: [log] ni siquiera construye
 * el evento.
 *
 * Uso desde la pantalla: `enabled.value = true` al abrirla (o con el interruptor CAPTURAR), observar
 * [events] (más recientes primero), [clear] y [exportText] para copiar/compartir.
 */
object CalibrationLog {
    const val CAPACITY = 500

    /** La pantalla de Calibración lo activa al abrirse. */
    val enabled = MutableStateFlow(false)

    private val buffer = ArrayDeque<CalibrationEvent>(CAPACITY)
    private val _events = MutableStateFlow<List<CalibrationEvent>>(emptyList())

    /** Eventos capturados, el más reciente primero. */
    val events: StateFlow<List<CalibrationEvent>> = _events.asStateFlow()

    /**
     * Copia de cada evento, ya en texto, a otro sitio (solo QA: el gancho `CALIBRATION` de la build debug lo manda a
     * logcat para leer recorridos largos por cable, sin el tope de [CAPACITY]). `null` = nada.
     */
    @Volatile
    var mirror: ((String) -> Unit)? = null

    /** Cabecera de la exportación (móvil, Android, versiones de las apps). La rellena el servicio. */
    @Volatile
    var environment: String? = null

    /** Registra un evento si la captura está activa. Si no lo está, [block] no se ejecuta. */
    inline fun log(block: () -> CalibrationEvent) {
        if (enabled.value) add(block())
    }

    @PublishedApi
    internal fun add(event: CalibrationEvent) {
        synchronized(buffer) {
            buffer.addFirst(event)
            while (buffer.size > CAPACITY) buffer.removeLast()
            _events.value = buffer.toList()
        }
        mirror?.invoke(event.toLine())
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _events.value = emptyList()
        }
    }

    /**
     * Texto plano con una línea por evento, del más antiguo al más reciente.
     *
     * @param fallbackEnvironment móvil y versiones si el servicio no ha llegado a ponerlos (no se ha conectado).
     * @param service estado del servicio ([ServiceHealth]) para la cabecera: con 0 eventos es lo primero que mirar.
     * @param performance líneas de rendimiento del servicio ([ServiceMetrics]), ya en texto: van en la cabecera.
     * @param exits últimos cierres de la app ([ProcessExits.recent]), ya en texto, con su título si se han podido leer:
     *   van detrás.
     * @param system estado del sistema, conexiones del servicio y fallos contenidos ([SystemState], [ServiceJournal],
     *   [ServiceFailures]), ya en texto: va detrás de los cierres.
     * @param privateNames nombres de contactos y grupos del usuario: se cambian por `redactToken(n)` en la nota y la
     *   regla de cada evento ([CalibrationRedactor]) para que no salgan del móvil. La cabecera no se toca.
     *
     * Las frases fijas de la cabecera («registro de calibración», «Servicio:», «eventos…») van siempre en español
     * (deuda conocida: es un registro para desarrollo); quien llama pasa [performance] también en español.
     */
    fun exportText(
        fallbackEnvironment: String? = null,
        service: ServiceHealth? = null,
        performance: List<String> = emptyList(),
        exits: List<String> = emptyList(),
        system: List<String> = emptyList(),
        privateNames: Collection<String> = emptyList(),
        redactToken: (Int) -> String = { "[#$it]" },
    ): String {
        val list = events.value
        val redactor = CalibrationRedactor.compile(privateNames, redactToken)
        val text = buildString {
            appendLine("Reelometer · registro de calibración")
            (environment ?: fallbackEnvironment)?.let { appendLine(it) }
            service?.let { appendLine("Servicio: ${it.name}") }
            ServiceStatus.startError.value?.let { appendLine("Fallo al arrancar el servicio: $it") }
            ServiceStatus.startIssues.value.forEach { appendLine("Aviso al arrancar el servicio: $it") }
            if (performance.isNotEmpty()) {
                appendLine()
                performance.forEach { appendLine(it) }
                appendLine()
            }
            if (exits.isNotEmpty()) {
                if (performance.isEmpty()) appendLine()
                // La descripción viene del sistema: ya va limpia ([ExitReasons.safeDescription]), y por si acaso se
                // le quitan también los nombres.
                exits.forEach { appendLine(redactor?.invoke(it) ?: it) }
                appendLine()
            }
            if (system.isNotEmpty()) {
                if (performance.isEmpty() && exits.isEmpty()) appendLine()
                // Solo horas, cifras y nombres de clases; por si acaso, también sin nombres.
                system.forEach { appendLine(redactor?.invoke(it) ?: it) }
                appendLine()
            }
            appendLine("${list.size} eventos (del más antiguo al más reciente)")
            appendLine()
            for (event in list.asReversed()) appendLine(redactor?.let { event.redacted(it) }?.toLine() ?: event.toLine())
        }
        return text
    }

    /** Solo los campos libres (nota y regla) pueden llevar un nombre: las claves fijas y la cabecera no se tocan. */
    private fun CalibrationEvent.redacted(redact: (String) -> String): CalibrationEvent =
        copy(note = note?.let(redact), rule = rule?.let(redact))
}
