package com.reelometro.app.game

import java.time.LocalDate

/*
 * Pestaña FLAP (antes «Pasaporte»): modelos PUROS (sin Android) que comparten la lógica (game/),
 * los datos (data/game/), la pantalla (ui/passport/), el widget y los textos.
 *
 * La idea, en una frase: **cada vídeo le quita vida a Flap**. Cada día empieza a tope (100 de vida,
 * EN_FORMA); con tu objetivo de vídeos llega a la noche a media vida y, si lo doblas, cae KO. A
 * medianoche revive. Las reglas y los números están en [FlapRules]; nada más (sin niveles, sin
 * experiencia, sin sellos, sin retos).
 */

/**
 * Cómo está Flap, del mejor al peor. Depende **solo de los vídeos de hoy** frente a su aguante
 * ([FlapRules.limitFor]); los umbrales, en [FlapRules.moodFor]. Dentro del aguante nunca enferma.
 */
enum class FlapMood {
    /** Menos de la mitad del aguante: a tope. */
    EN_FORMA,

    /** De la mitad al 80 % del aguante. */
    NORMAL,

    /** Del 80 % al aguante (incluido): al límite. */
    CANSADO,

    /** Por encima del aguante, sin llegar al doble. */
    ENFERMO,

    /** El doble del aguante o más: fuera de combate hasta mañana. */
    KO,
}

/** Un día de la tira de la semana: cómo acabó Flap ese día. */
data class FlapDay(
    val date: LocalDate,
    /** Vídeos de ese día; `null` si es anterior al primer día con vídeos (sin registro). */
    val count: Int?,
    /** Estado con el que acabó el día (con el aguante de ahora); `null` sin registro. */
    val mood: FlapMood?,
)

/** Todo lo que pinta la pestaña FLAP. El widget no lo usa: solo pinta el [FlapMood] de hoy con [FlapRules.moodFor] (WidgetData.flapFor). */
data class FlapState(
    val today: LocalDate,
    /** Objetivo diario de Ajustes; `null` sin objetivo (entonces el aguante es [FlapRules.DEFAULT_LIMIT]). */
    val goal: Int?,
    /** Aguante de hoy: [goal] o, sin objetivo, [FlapRules.DEFAULT_LIMIT]. Siempre ≥ 1. */
    val limit: Int,
    /** Vídeos de hoy. */
    val count: Int,
    /** Estado de ahora ([FlapRules.moodFor] de [count] y [limit]). */
    val mood: FlapMood,
    /** Vida de ahora, 0–100 ([FlapRules.life]); 0 solo en KO. */
    val life: Int,
    /** Vídeos que faltan para pasar a un estado peor ([FlapRules.videosToWorse]); `null` en KO. */
    val videosToWorse: Int?,
    /**
     * Último estado que el usuario vio en la pestaña ([FlapSource.markMoodSeen]) si es distinto de
     * [mood], para animar el cambio; `null` si es el mismo o si nunca lo ha visto. Si es **peor**
     * que [mood], Flap ha revivido (día nuevo) o se ha subido el objetivo.
     */
    val previousMood: FlapMood? = null,
    /**
     * Los 7 días cerrados anteriores a hoy, del más antiguo (hace 7 días) a ayer. Siempre 7
     * elementos; los anteriores al primer registro, con `count`/`mood` a `null`.
     */
    val week: List<FlapDay> = emptyList(),
    /**
     * Días cerrados seguidos, hasta ayer, en que Flap acabó sin enfermar (vídeos ≤ aguante de
     * ahora). Un día sin vídeos después del primer registro cuenta (0 vídeos). 0 si ayer enfermó.
     */
    val healthyStreak: Int = 0,
    /** Días con registro en todo el historial (del primer día con vídeos a hoy). 0 = recién instalada. */
    val trackedDays: Int = 0,
) {
    val hasGoal: Boolean get() = goal != null

    /** Ha revivido o mejorado desde la última vez que lo viste (día nuevo u objetivo más alto). */
    val recovered: Boolean get() = previousMood != null && previousMood.ordinal > mood.ordinal

    /** Ha empeorado desde la última vez que lo viste. */
    val worsened: Boolean get() = previousMood != null && previousMood.ordinal < mood.ordinal
}
