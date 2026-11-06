package com.reelometro.app.work

import com.reelometro.app.game.TravelerLevel
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Reglas puras del **aviso en directo al pasar el objetivo** (sin Android: se prueban con
 * tests JVM). Una sola notificación al día, en el momento en que el recuento de hoy
 * SUPERA el objetivo («Llevas 151. Tu objetivo era 150.»): llegar justo al objetivo no avisa.
 */
object GoalAlertRules {

    /** Por qué no se avisa. */
    enum class Skip {
        /** El usuario no lo ha encendido en Ajustes (está apagado por defecto). */
        DISABLED,

        /** Sin objetivo diario no hay nada que pasar. */
        NO_GOAL,

        /** Ya se avisó hoy. Al cambiar de día, vuelve a poder avisar. */
        ALREADY_SENT,

        /** Aún no lo ha superado (igualarlo no cuenta). */
        NOT_OVER,
    }

    /**
     * Lo barato, antes de contar nada (el servicio lo llama en cada vídeo): encendido, con objetivo
     * y sin aviso hoy. [force] (QA) salta el ajuste y el «ya enviado», nunca la falta de objetivo.
     */
    fun precheck(enabled: Boolean, goal: Int?, today: LocalDate, lastSent: LocalDate?, force: Boolean = false): Skip? = when {
        goal == null || goal <= 0 -> Skip.NO_GOAL
        force -> null
        !enabled -> Skip.DISABLED
        lastSent == today -> Skip.ALREADY_SENT
        else -> null
    }

    /** Decisión completa con el recuento de hoy: `null` = avisar ya. */
    fun decide(
        enabled: Boolean,
        goal: Int?,
        count: Int,
        today: LocalDate,
        lastSent: LocalDate?,
        force: Boolean = false,
    ): Skip? {
        precheck(enabled, goal, today, lastSent, force)?.let { return it }
        return if (goal == null || count <= goal) Skip.NOT_OVER else null
    }

    /** El aviso habla de «hoy»: desaparece solo a medianoche. */
    fun expiry(now: ZonedDateTime): ZonedDateTime = SummaryRules.dailyExpiry(now)
}

/**
 * Reglas puras del **aviso de nivel nuevo** del Pasaporte (el único aviso del modo pasaporte).
 * Nunca con el modo pasaporte apagado. La primera vez que se mira, el nivel actual se toma como
 * punto de partida sin avisar (no se anuncia lo que ya se tenía al actualizar la app); después,
 * avisa cuando el nivel supera al último avisado. Si el nivel baja (BORRAR TODO), la base baja con él.
 */
object LevelAlertRules {

    /** Qué hacer con el nivel actual. */
    sealed interface Outcome {
        /** Avisar de [level] y guardarlo como el último avisado. */
        data class Notify(val level: TravelerLevel) : Outcome

        /** Guardar [level] como base, sin avisar (primera vez o el nivel ha bajado). */
        data class Baseline(val level: TravelerLevel) : Outcome

        /** Nada que hacer (modo pasaporte apagado o mismo nivel). */
        data object Keep : Outcome
    }

    fun decide(passportEnabled: Boolean, current: TravelerLevel, lastNotified: TravelerLevel?): Outcome = when {
        !passportEnabled -> Outcome.Keep
        lastNotified == null -> Outcome.Baseline(current)
        current > lastNotified -> Outcome.Notify(current)
        current < lastNotified -> Outcome.Baseline(current)
        else -> Outcome.Keep
    }

    /**
     * ¿Toca mirar el nivel con este vídeo? Solo con el primero de cada día (el nivel cambia al cerrarse
     * los días, a medianoche): así el servicio no recalcula el pasaporte en cada vídeo.
     */
    fun shouldCheckOnVideo(passportEnabled: Boolean, today: LocalDate, lastChecked: LocalDate?): Boolean =
        passportEnabled && lastChecked != today

    /** Lee el nivel guardado por su nombre; `null` si no hay o ya no existe. */
    fun parse(name: String?): TravelerLevel? = TravelerLevel.entries.firstOrNull { it.name == name }

    /** El aviso de nivel no habla de «hoy»: se queda dos días. */
    val LIFETIME: Duration = Duration.ofDays(2)

    fun expiry(now: ZonedDateTime): ZonedDateTime = now.plus(LIFETIME)
}
