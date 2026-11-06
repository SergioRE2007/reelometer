package com.reelometro.app.game

import java.time.LocalDate

/* Utilidades de los tests del motor del pasaporte (puras: sin Android). */

/** Lunes 2 de noviembre de 2026. */
internal val LUNES: LocalDate = LocalDate.of(2026, 11, 2)

/** Un día cualquiera a partir de [LUNES]. */
internal fun dia(n: Int): LocalDate = LUNES.plusDays(n.toLong())

/** [videos] por día, seguidos desde [from]. */
internal fun dias(from: LocalDate, vararg videos: Int): Map<LocalDate, Int> =
    videos.withIndex().associate { (i, v) -> from.plusDays(i.toLong()) to v }

/** [n] días seguidos desde [from] con [videos] cada uno. */
internal fun iguales(from: LocalDate, n: Int, videos: Int): Map<LocalDate, Int> =
    (0 until n).associate { from.plusDays(it.toLong()) to videos }

internal fun calcular(
    today: LocalDate,
    goal: Int?,
    counts: Map<LocalDate, Int>,
    memory: PassportMemory = PassportMemory(),
    history: GameHistory = GameHistory(),
): GameResult = PassportEngine.compute(GameInput(today, goal, history.copy(counts = counts), memory))

internal fun PassportState.sello(id: StampId): StampState = stamps.first { it.id == id }

internal fun PassportState.fecha(id: StampId): LocalDate? = sello(id).earnedOn

/** Memoria ya estrenada (no es el primer cálculo: nada se da por celebrado en silencio). */
internal fun estrenada(since: LocalDate = LUNES.minusYears(1)): PassportMemory = PassportMemory(since = since)
