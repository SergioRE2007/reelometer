package com.reelometro.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Reloj del sistema que sigue la zona horaria **actual** (a diferencia de `Clock.systemDefaultZone()`,
 * que la fija al crearse): si el usuario viaja o cambia la zona, «hoy» cambia con él.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}

/** Día local (epochDay) y minuto del día (0–1439) de un instante en una zona: las claves de agregación. */
data class LocalStamp(val day: Long, val minuteOfDay: Int) {
    companion object {
        fun of(epochMs: Long, zone: ZoneId): LocalStamp {
            val t = Instant.ofEpochMilli(epochMs).atZone(zone)
            return LocalStamp(t.toLocalDate().toEpochDay(), t.hour * 60 + t.minute)
        }
    }
}

/**
 * Avisa de que el tiempo ha pasado lo suficiente como para recalcular una vista aunque no cambien
 * los datos (cambio de día, comparación «a esta hora»).
 */
fun interface Ticker {
    /**
     * Emite al suscribirse y después en cada medianoche local; si [everyMinutes] no es `null`,
     * también cada [everyMinutes] minutos alineados al reloj (…, 14:00, 14:05, …).
     */
    fun ticks(everyMinutes: Int?): Flow<Unit>
}

/** [Ticker] basado en un [Clock] y `delay` (solo consume mientras alguien observa). */
class ClockTicker(private val clock: Clock) : Ticker {

    override fun ticks(everyMinutes: Int?): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(millisUntilNextTick(clock.millis(), clock.zone, everyMinutes))
        }
    }

    companion object {
        /** Margen tras la frontera para que el reloj ya marque el día/minuto nuevo al recalcular. */
        const val SLACK_MS = 250L

        /** Milisegundos hasta el próximo aviso: la próxima medianoche local o el próximo múltiplo de minutos. */
        fun millisUntilNextTick(nowMs: Long, zone: ZoneId, everyMinutes: Int?): Long {
            val now = Instant.ofEpochMilli(nowMs).atZone(zone)
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            var next = midnight
            if (everyMinutes != null && everyMinutes > 0) {
                // Alineado a la época: coincide con la hora local en todas las zonas actuales
                // (sus desfases son múltiplos de 15 min) para periodos que dividen 15.
                val period = everyMinutes * 60_000L
                val aligned = (Math.floorDiv(nowMs, period) + 1) * period
                if (aligned < next) next = aligned
            }
            return (next - nowMs).coerceAtLeast(0) + SLACK_MS
        }
    }
}
