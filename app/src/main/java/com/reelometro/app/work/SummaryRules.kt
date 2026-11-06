package com.reelometro.app.work

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Reglas puras de los avisos (sin Android): cuándo toca el siguiente, qué resume una ejecución que
 * llega tarde o antes de hora, si hay que avisar y cuándo caduca el aviso.
 *
 * Las horas se calculan en hora local con `LocalDate.atTime(...).atZone(zona)` y los retrasos como
 * `Duration` entre instantes, así que los cambios de hora salen bien: en España el último domingo de
 * marzo dura 23 horas y el de octubre 25; una hora que no existe (las 02:30 de marzo) pasa a la
 * siguiente válida (03:30) y una que se repite (las 02:30 de octubre) solo cuenta una vez.
 */
object SummaryRules {

    /** Hora del resumen mensual (el día 1). No depende de la del diario. */
    val MONTHLY_TIME: LocalTime = LocalTime.of(10, 0)

    /** Hasta cuánto tarde se manda aún el diario; si ya es otro día, el texto dice «ayer». */
    val DAILY_LATE_LIMIT: Duration = Duration.ofHours(12)

    /** Si se ejecuta antes de hora (han cambiado el reloj o la zona), no avisa: se reprograma. */
    val EARLY_TOLERANCE: Duration = Duration.ofMinutes(2)

    /** Qué hacer con una ejecución del aviso. */
    sealed interface Run {
        /**
         * Avisar. [date] es lo que se resume: el día (diario) o el día 1 del mes (mensual).
         * [late]: ya es otro día (el diario dice «ayer»).
         */
        data class Notify(val date: LocalDate, val late: Boolean) : Run

        /** Llega antes de hora: no avisa y se reprograma para la hora buena. */
        data object Early : Run

        /** Llega demasiado tarde: no avisa (se reprograma el siguiente). */
        data object Stale : Run
    }

    /** Por qué no se avisa. */
    enum class Skip { NO_PERMISSION, DISABLED, SERVICE_OFF, NO_DATA }

    /**
     * Siguiente aviso diario a las [time] locales: estrictamente después de [now] y, si se da
     * [after], en un día posterior a ese (el worker lo usa para no avisar dos veces el mismo día
     * aunque el reloj vaya justo o la hora se repita en el cambio de octubre).
     */
    fun nextDaily(now: ZonedDateTime, time: LocalTime, after: LocalDate? = null): ZonedDateTime {
        var date = now.toLocalDate()
        if (after != null && !date.isAfter(after)) date = after.plusDays(1)
        while (true) {
            val at = date.atTime(time).atZone(now.zone)
            if (at.isAfter(now)) return at
            date = date.plusDays(1)
        }
    }

    /** Siguiente resumen mensual: el día 1 a las [MONTHLY_TIME], después de [now] y de [after]. */
    fun nextMonthly(now: ZonedDateTime, after: LocalDate? = null): ZonedDateTime {
        var month = YearMonth.from(now)
        if (after != null && !month.atDay(1).isAfter(after)) month = YearMonth.from(after).plusMonths(1)
        while (true) {
            val at = month.atDay(1).atTime(MONTHLY_TIME).atZone(now.zone)
            if (at.isAfter(now)) return at
            month = month.plusMonths(1)
        }
    }

    /**
     * Ejecución del aviso diario programado para el día [scheduled] a las [time]. Si llega tarde,
     * después de medianoche (móvil apagado, Doze), resume [scheduled], no el día nuevo.
     */
    fun dailyRun(scheduled: LocalDate, time: LocalTime, now: ZonedDateTime): Run {
        val due = scheduled.atTime(time).atZone(now.zone)
        return when {
            now.isBefore(due.minus(EARLY_TOLERANCE)) -> Run.Early
            now.toLocalDate() == scheduled -> Run.Notify(scheduled, late = false)
            Duration.between(due, now) <= DAILY_LATE_LIMIT -> Run.Notify(scheduled, late = true)
            else -> Run.Stale
        }
    }

    /**
     * Ejecución del resumen mensual programado para el día 1 [scheduled]: resume el mes anterior.
     * Si llega tarde vale mientras siga siendo ese mes (después, «el mes pasado» sería otro).
     */
    fun monthlyRun(scheduled: LocalDate, now: ZonedDateTime): Run {
        val due = scheduled.atTime(MONTHLY_TIME).atZone(now.zone)
        return when {
            now.isBefore(due.minus(EARLY_TOLERANCE)) -> Run.Early
            YearMonth.from(now) == YearMonth.from(scheduled) ->
                Run.Notify(YearMonth.from(scheduled).minusMonths(1).atDay(1), late = now.toLocalDate() != scheduled)
            else -> Run.Stale
        }
    }

    /**
     * Por qué no se avisa del día (null = se avisa). Sin permiso nunca se notifica; con el servicio
     * desactivado no se está contando y un «0 vídeos» engañaría. [force] (QA) solo salta los ajustes.
     */
    fun dailySkip(enabled: Boolean, canNotify: Boolean, serviceOn: Boolean, force: Boolean = false): Skip? = when {
        !canNotify -> Skip.NO_PERMISSION
        force -> null
        !enabled -> Skip.DISABLED
        !serviceOn -> Skip.SERVICE_OFF
        else -> null
    }

    /**
     * Por qué no se avisa del mes (null = se avisa). Se omite si la app no contaba ese mes
     * ([trackedDays] = 0) y si el mes sale a cero con el servicio desactivado.
     */
    fun monthlySkip(
        enabled: Boolean,
        canNotify: Boolean,
        trackedDays: Int,
        count: Long,
        serviceOn: Boolean,
        force: Boolean = false,
    ): Skip? = when {
        !canNotify -> Skip.NO_PERMISSION
        force -> null
        !enabled -> Skip.DISABLED
        trackedDays <= 0 -> Skip.NO_DATA
        count <= 0 && !serviceOn -> Skip.SERVICE_OFF
        else -> null
    }

    /** El diario dice «hoy» (o «ayer»): deja de valer a la medianoche siguiente. */
    fun dailyExpiry(now: ZonedDateTime): ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)

    /** El mensual abre `story:MONTH:-1`, relativo al mes en curso: caduca cuando empieza el mes siguiente. */
    fun monthlyExpiry(now: ZonedDateTime): ZonedDateTime =
        YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay(now.zone)

    /** Configuración que decide la programación del diario (va como etiqueta del trabajo). */
    fun dailyKey(time: LocalTime, zone: ZoneId): String =
        String.format(Locale.ROOT, "daily@%02d:%02d@%s", time.hour, time.minute, zone.id)

    /** Ídem para el mensual (la hora es fija; cuenta la zona). */
    fun monthlyKey(zone: ZoneId): String =
        String.format(Locale.ROOT, "monthly@%02d:%02d@%s", MONTHLY_TIME.hour, MONTHLY_TIME.minute, zone.id)
}
