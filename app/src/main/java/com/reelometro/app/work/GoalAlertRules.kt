package com.reelometro.app.work

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
