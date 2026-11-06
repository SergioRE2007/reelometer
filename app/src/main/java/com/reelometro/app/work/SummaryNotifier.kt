package com.reelometro.app.work

import android.content.Context
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.ui.nav.DeepLinks
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * Monta y publica los avisos: datos de `ReelRepository`, textos de [NotificationCopy] y reglas de
 * [SummaryRules]. Lo usan los workers y los ganchos de QA (`DebugReceiver`, con `force`).
 * Devuelve una línea legible con lo que ha hecho (para Logcat).
 */
object SummaryNotifier {

    /** Ruta que abre el diario del día en curso. */
    private const val ROUTE_TODAY = "today"

    /**
     * Aviso diario de [date]. [late]: ya es otro día (dice «ayer» y abre el detalle de ese día).
     * [force] (QA) salta los ajustes y el estado del servicio, nunca el permiso.
     */
    suspend fun daily(
        context: Context,
        date: LocalDate,
        late: Boolean,
        now: ZonedDateTime = ZonedDateTime.now(),
        force: Boolean = false,
    ): String {
        val app = ReelometroApp.from(context)
        val settings = app.settings.current()
        SummaryRules.dailySkip(
            enabled = settings.notifyDaily,
            canNotify = Notifications.canNotify(context),
            serviceOn = ReelAccessibilityService.isEnabled(context),
            force = force,
        )?.let { return "omitido ($it)" }

        val day = app.repository.day(date)
        val copy = NotificationCopy.daily(
            count = day.totals.count,
            totalMs = day.totals.totalMs,
            seed = Variants.daySeed(date),
            goal = day.goal?.goal,
            yesterday = late,
        )
        val route = if (late) DeepLinks.forDay(date.toEpochDay()) else ROUTE_TODAY
        val shown = Notifications.show(
            context, Notifications.Kind.DAILY, copy, NotificationCopy.dailyPublic, route, now, SummaryRules.dailyExpiry(now),
        )
        return if (shown) "publicado «${copy.title}» · ${copy.text} → $route" else "omitido (${SummaryRules.Skip.NO_PERMISSION})"
    }

    /**
     * Resumen de [month] (normalmente el mes pasado), que abre su Wrapped. Se omite si ese mes la
     * app no contaba o si sale a cero con el servicio desactivado (salvo [force], para QA).
     */
    suspend fun monthly(
        context: Context,
        month: YearMonth,
        now: ZonedDateTime = ZonedDateTime.now(),
        force: Boolean = false,
    ): String {
        val app = ReelometroApp.from(context)
        val settings = app.settings.current()
        val current = YearMonth.from(now)
        val offset = ((month.year - current.year) * 12 + (month.monthValue - current.monthValue)).coerceAtMost(0)
        val stats = app.repository.period(PeriodRef(PeriodKind.MONTH, offset))
        SummaryRules.monthlySkip(
            enabled = settings.notifyMonthly,
            canNotify = Notifications.canNotify(context),
            trackedDays = stats.trackedDays,
            count = stats.totals.count.toLong(),
            serviceOn = ReelAccessibilityService.isEnabled(context),
            force = force,
        )?.let { return "omitido ($it)" }

        val copy = NotificationCopy.monthly(
            month = month,
            count = stats.totals.count.toLong(),
            totalMs = stats.totals.totalMs,
            seed = month.year * 12L + month.monthValue,
        )
        val route = DeepLinks.forStory(PeriodKind.MONTH.name, offset)
        val shown = Notifications.show(
            context, Notifications.Kind.MONTHLY, copy, NotificationCopy.monthlyPublic, route, now, SummaryRules.monthlyExpiry(now),
        )
        return if (shown) "publicado «${copy.title}» · ${copy.text} → $route" else "omitido (${SummaryRules.Skip.NO_PERMISSION})"
    }
}
