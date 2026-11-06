package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.YearMonth

/**
 * Textos de las **notificaciones**. Las palabras están en `res/values/copy_notifications.xml`;
 * aquí se decide qué frase toca (tramo, objetivo, aviso tardío…) y con qué huecos. Los nombres y
 * descripciones de los dos primeros canales están en `res/values/strings.xml`
 * (`notification_channel_*`), porque los usa el sistema.
 */
object NotificationCopy {

    /**
     * Aviso diario (a la hora elegida). Título con la cifra; texto según el tramo, el objetivo y
     * si no ha habido vídeos.
     *
     * @param yesterday el aviso llega tarde, ya pasada la medianoche (móvil apagado, ahorro de
     *   batería): resume el día anterior y lo dice («212 vídeos ayer»), sin hablar de «mañana».
     */
    fun daily(count: Int, totalMs: Long, seed: Long, goal: Int? = null, yesterday: Boolean = false): TitledText {
        val t = Words.duration(totalMs)
        val tCap = Format.capitalize(t)
        val n = Format.integer(count)

        if (yesterday) {
            val title = str(R.string.notif_daily_title_late, Words.videos(count))
            if (count <= 0) return TitledText(title, pick(seed, "notif.daily.late.zero", R.array.notif_daily_late_zero))
            return TitledText(title, pick(seed, "notif.daily.late", R.array.notif_daily_late, t, tCap))
        }

        val title = str(R.string.notif_daily_title, Words.videos(count))
        if (count <= 0) return TitledText(title, pick(seed, "notif.daily.zero", R.array.notif_daily_zero))

        if (goal != null && goal > 0) {
            val g = Format.integer(goal)
            val text = if (count > goal) {
                pick(seed, "notif.daily.over", R.array.notif_daily_over, g, n, t, Words.videos(count - goal))
            } else {
                pick(seed, "notif.daily.under", R.array.notif_daily_under, n, g, t)
            }
            return TitledText(title, text)
        }

        val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
        val text = when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> pick(seed, "notif.daily.first", R.array.notif_daily_first, t, tCap)

            Band.NORMAL -> pick(
                seed, "notif.daily.normal",
                if (Words.isSingularDuration(totalMs)) R.array.notif_daily_normal_one else R.array.notif_daily_normal,
                t, tCap,
            )

            Band.HIGH -> if (eq != null) {
                pick(seed, "notif.daily.high", R.array.notif_daily_high, tCap, eq)
            } else {
                pick(seed, "notif.daily.high", R.array.notif_daily_high_noeq, tCap)
            }

            Band.EXTREME -> if (eq != null) {
                pick(seed, "notif.daily.extreme", R.array.notif_daily_extreme, tCap, eq)
            } else {
                pick(seed, "notif.daily.extreme", R.array.notif_daily_extreme_noeq, tCap)
            }
        }
        return TitledText(title, text)
    }

    /** Resumen mensual (el día 1): invita a abrir el Wrapped (`story:MONTH:-1`). */
    fun monthly(month: YearMonth, count: Long, totalMs: Long, seed: Long): TitledText {
        val m = Format.monthStandalone(month.monthValue)
        val mCap = Format.capitalize(m)
        val title = pick(seed, "notif.monthly.title", R.array.notif_monthly_title, m, mCap)
        if (count <= 0) return TitledText(title, pick(seed, "notif.monthly.zero", R.array.notif_monthly_zero, m, mCap))
        return TitledText(
            title,
            pick(seed, "notif.monthly", R.array.notif_monthly, Words.videos(count), Words.duration(totalMs)),
        )
    }

    /** Versión pública del diario (pantalla de bloqueo con «ocultar contenido sensible»): sin cifras. */
    val dailyPublic: TitledText
        get() = TitledText(str(R.string.notif_daily_public_title), str(R.string.notif_daily_public_text))

    /** Versión pública del mensual. */
    val monthlyPublic: TitledText
        get() = TitledText(str(R.string.notif_monthly_public_title), str(R.string.notif_monthly_public_text))

    /** Si Android desconecta el servicio (opcional y sin implementar: le faltaría su canal). */
    val serviceLost: TitledText
        get() = TitledText(str(R.string.notif_service_lost_title), str(R.string.notif_service_lost_text))

    // ------------------------------------------------------------------ aviso en directo

    /**
     * Aviso en directo, una vez al día, en el momento en que el recuento supera el objetivo:
     * «Llevas 151. Tu objetivo era 150.». Sin culpa: informa y deja la decisión en tus manos.
     */
    fun goalPassed(count: Int, goal: Int, seed: Long): TitledText = TitledText(
        GOAL_PASSED_TITLE,
        pick(seed, "notif.goal.live", R.array.notif_goal_live, Format.integer(count), Format.integer(goal)),
    )

    val GOAL_PASSED_TITLE: String get() = str(R.string.notif_goal_title)

    /** Versión pública (pantalla de bloqueo con «ocultar contenido sensible»): sin cifras. */
    val goalPassedPublic: TitledText
        get() = TitledText(GOAL_PASSED_TITLE, str(R.string.notif_goal_public_text))

    // ------------------------------------------------------------------ nivel nuevo del Pasaporte

    /** «Nivel nuevo: PLATA». El único aviso del modo pasaporte; abre la pestaña Pasaporte. */
    fun levelUp(level: TravelerLevel, seed: Long): TitledText = TitledText(
        str(R.string.notif_level_title, PassportCopy.levelName(level)),
        pick(seed, "notif.level", R.array.notif_level),
    )

    /** Versión pública del aviso de nivel. */
    val levelUpPublic: TitledText
        get() = TitledText(str(R.string.notif_level_public_title), str(R.string.notif_level_public_text))

    /*
     * Canales nuevos, en el mismo grupo «Resúmenes» que el diario y el mensual (cada uno se puede
     * silenciar por separado en los ajustes del sistema). Sus textos están en
     * `copy_notifications.xml` (`notif_channel_*`).
     */
    val CHANNEL_GOAL_NAME: String get() = str(R.string.notif_channel_goal_name)
    val CHANNEL_GOAL_DESCRIPTION: String get() = str(R.string.notif_channel_goal_description)
    val CHANNEL_LEVEL_NAME: String get() = str(R.string.notif_channel_level_name)
    val CHANNEL_LEVEL_DESCRIPTION: String get() = str(R.string.notif_channel_level_description)
}
