package com.reelometro.app.copy

import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.YearMonth

/**
 * Textos de las **notificaciones**. Los nombres y descripciones de los canales están en
 * `res/values/strings.xml` (`notification_channel_*`), porque los usa el sistema.
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
            val title = "${Words.videos(count)} ayer"
            if (count <= 0) {
                return TitledText(
                    title,
                    pick(
                        seed, "notif.daily.late.zero",
                        "Ayer, ni uno. Que conste en acta.",
                        "Ayer el marcador se quedó a cero.",
                        "Cero vídeos ayer. El resumen llega tarde, pero llega.",
                    ),
                )
            }
            return TitledText(
                title,
                pick(
                    seed, "notif.daily.late",
                    "$tCap de vídeos. El resumen llega tarde, pero llega.",
                    "Con retraso: $t en total.",
                    "$tCap en total. Hoy el marcador ya ha vuelto a cero.",
                ),
            )
        }

        val title = "${Words.videos(count)} hoy"
        if (count <= 0) {
            return TitledText(
                title,
                pick(
                    seed, "notif.daily.zero",
                    "Día limpio. Que conste en acta.",
                    "Ni uno. El marcador no tiene nada que añadir.",
                    "Hoy el algoritmo se ha quedado con las ganas.",
                ),
            )
        }

        if (goal != null && goal > 0) {
            val g = Format.integer(goal)
            return if (count > goal) {
                val over = Words.videos(count - goal)
                TitledText(
                    title,
                    pick(
                        seed, "notif.daily.over",
                        "Objetivo: $g. Hoy: $n. En total, $t.",
                        "$over por encima del objetivo, en $t.",
                        "Te has pasado del objetivo en $over. Mañana, borrón y cuenta nueva.",
                    ),
                )
            } else {
                TitledText(
                    title,
                    pick(
                        seed, "notif.daily.under",
                        "Dentro del objetivo: $n de $g.",
                        "$n de $g, con margen. En total, $t.",
                        "Por debajo de $g. Así, sin más.",
                    ),
                )
            }
        }

        val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
        val text = when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> pick(
                seed, "notif.daily.first",
                "$tCap. Casi ni cuenta. Pero cuenta.",
                "Poca cosa: $t. Mañana, a ver.",
                "$tCap en total. Día tranquilo.",
            )

            Band.NORMAL -> pick(
                seed, "notif.daily.normal",
                "$tCap de vídeos. Mañana, el marcador vuelve a cero.",
                "$tCap. Un día normal, que ya es decir.",
                "Eso ${if (Words.isSingularDuration(totalMs)) "es" else "son"} $t que nadie te va a devolver.",
            )

            Band.HIGH -> pick(
                seed, "notif.daily.high",
                if (eq != null) "$tCap. Con eso te daba para $eq." else "$tCap. Un buen rato.",
                "$tCap de pantalla. El algoritmo está encantado.",
                "$tCap. Un buen día para el algoritmo.",
            )

            Band.EXTREME -> pick(
                seed, "notif.daily.extreme",
                "$tCap. Esto ya es un turno de trabajo.",
                if (eq != null) "$tCap de vídeos. Te daba para $eq." else "$tCap de vídeos. Y subiendo.",
                "$tCap. Mañana, borrón y cuenta nueva.",
            )
        }
        return TitledText(title, text)
    }

    /** Resumen mensual (el día 1): invita a abrir el Wrapped (`story:MONTH:-1`). */
    fun monthly(month: YearMonth, count: Long, totalMs: Long, seed: Long): TitledText {
        val m = Format.monthName(month.monthValue)
        val title = pick(
            seed, "notif.monthly.title",
            "Tu $m, contado.",
            "${Format.capitalize(m)}, en cifras.",
            "${Format.capitalize(m)} ya tiene resumen.",
        )
        if (count <= 0) {
            return TitledText(
                title,
                pick(
                    seed, "notif.monthly.zero",
                    "${Format.capitalize(m)}, a cero. No hay mucho que resumir, y quizá ese es el resumen.",
                    "Ni un vídeo en $m. El resumen más corto de la historia.",
                    "Cero vídeos en $m. Que conste en acta.",
                ),
            )
        }
        val v = Words.videos(count)
        val t = Words.duration(totalMs)
        return TitledText(
            title,
            pick(
                seed, "notif.monthly",
                "$v y $t. Toca para verlo entero.",
                "$v, $t y un creador favorito. Toca para ver el resumen.",
                "El resumen está listo: $v en $t.",
            ),
        )
    }

    /** Versión pública del diario (pantalla de bloqueo con «ocultar contenido sensible»): sin cifras. */
    val dailyPublic: TitledText = TitledText("Resumen diario", "Tu cuenta del día está lista.")

    /** Versión pública del mensual. */
    val monthlyPublic: TitledText = TitledText("Resumen del mes", "El resumen del mes está listo.")

    /** Si Android desconecta el servicio (opcional y sin implementar: le faltaría su canal). */
    val serviceLost: TitledText = TitledText(
        "Reelómetro ha dejado de contar",
        "Android ha desconectado el servicio de accesibilidad. Toca para volver a conectarlo.",
    )

    // ------------------------------------------------------------------ aviso en directo

    /**
     * Aviso en directo, una vez al día, en el momento en que el recuento supera el objetivo:
     * «Llevas 151. Tu objetivo era 150.». Sin culpa: informa y deja la decisión en tus manos.
     */
    fun goalPassed(count: Int, goal: Int, seed: Long): TitledText {
        val n = Format.integer(count)
        val g = Format.integer(goal)
        return TitledText(
            GOAL_PASSED_TITLE,
            pick(
                seed, "notif.goal.live",
                "Llevas $n. Tu objetivo era $g.",
                "Llevas $n. Tu objetivo era $g. Lo que queda de hoy lo decides tú.",
                "Llevas $n. Tu objetivo era $g. El marcador sigue contando, sin rencor.",
            ),
        )
    }

    const val GOAL_PASSED_TITLE = "Por encima del objetivo"

    /** Versión pública (pantalla de bloqueo con «ocultar contenido sensible»): sin cifras. */
    val goalPassedPublic: TitledText = TitledText(GOAL_PASSED_TITLE, "Has pasado tu objetivo de hoy.")

    // ------------------------------------------------------------------ nivel nuevo del Pasaporte

    /** «Nivel nuevo: PLATA». El único aviso del modo pasaporte; abre la pestaña Pasaporte. */
    fun levelUp(level: TravelerLevel, seed: Long): TitledText = TitledText(
        "Nivel nuevo: ${PassportCopy.levelName(level)}",
        pick(
            seed, "notif.level",
            "Tu pasaporte sube de categoría. Se ha ganado viendo menos, que conste.",
            "Flap te espera en el Pasaporte con la tarjeta nueva.",
            "Más experiencia por menos vídeos. La tarjeta nueva está en el Pasaporte.",
        ),
    )

    /** Versión pública del aviso de nivel. */
    val levelUpPublic: TitledText = TitledText("Pasaporte", "Tienes un nivel nuevo en el pasaporte.")

    /*
     * Canales nuevos, en el mismo grupo «Resúmenes» que el diario y el mensual (cada uno se puede
     * silenciar por separado en los ajustes del sistema). Van aquí y no en `strings.xml`; se
     * pueden mover allí sin cambiar nada más.
     */
    const val CHANNEL_GOAL_NAME = "Aviso al pasar el objetivo"
    const val CHANNEL_GOAL_DESCRIPTION = "Un solo aviso al día, en el momento en que pasas tu objetivo de vídeos."
    const val CHANNEL_LEVEL_NAME = "Nivel del pasaporte"
    const val CHANNEL_LEVEL_DESCRIPTION = "Aviso cuando subes de nivel en el modo pasaporte."
}
