package com.reelometro.app.game

import java.time.LocalDate

/**
 * Lo que el motor del pasaporte necesita del historial de vídeos (lo lee `data/game/` con SQL).
 * Todo en días locales.
 *
 * Los sellos irónicos que ya están guardados en la memoria no hace falta volver a consultarlos: el
 * repositorio deja su campo a `null` y el motor usa la fecha guardada.
 */
data class GameHistory(
    /**
     * Vídeos por día, de **todo** el historial y hasta hoy incluido (solo hacen falta los días con
     * vídeos: los que faltan valen 0). El primer día con vídeos es el «primer registro».
     */
    val counts: Map<LocalDate, Int> = emptyMap(),
    /** Primer día con vídeos de las 3 plataformas (TRES_PLATAFORMAS). */
    val threePlatformsOn: LocalDate? = null,
    /** Primer día en que una semana llegó a 5 categorías distintas (CINCO_CATEGORIAS); ver [PassportRules.varietyDay]. */
    val fiveCategoriesOn: LocalDate? = null,
    /** Primer día con algún vídeo entre las 3:00 y las 4:59 (MADRUGADOR). */
    val earlyBirdOn: LocalDate? = null,
    /** Primer día con más de 100 vídeos en una misma hora del reloj (MARATON). */
    val marathonOn: LocalDate? = null,
)

/** Todo lo que entra en un cálculo del pasaporte. */
data class GameInput(
    val today: LocalDate,
    /** Objetivo diario de Ajustes; `null` (o ≤ 0) = sin objetivo, se usa la media como referencia. */
    val goal: Int?,
    val history: GameHistory,
    val memory: PassportMemory = PassportMemory(),
)

/**
 * Resultado de un cálculo: lo que se pinta y la memoria actualizada (sellos nuevos con su fecha,
 * retos cerrados, nivel más alto, celebraciones que se dan por vistas). Si [memory] es igual a la de
 * entrada, no hay nada que guardar.
 */
data class GameResult(
    val state: PassportState,
    val memory: PassportMemory,
)
