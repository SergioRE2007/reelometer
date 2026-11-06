package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format

/**
 * Tramos de un día según vídeos y minutos (manda el más alto de los dos):
 *
 * | Tramo   | Vídeos   | Minutos   |
 * |---------|----------|-----------|
 * | ZERO    | 0        | —         |
 * | FIRST   | 1–9      | < 10      |
 * | NORMAL  | 10–99    | 10–59     |
 * | HIGH    | 100–299  | 60–179    |
 * | EXTREME | ≥ 300    | ≥ 180     |
 */
internal enum class Band { ZERO, FIRST, NORMAL, HIGH, EXTREME }

internal object Bands {
    fun of(count: Int, totalMs: Long): Band {
        if (count <= 0) return Band.ZERO
        val minutes = totalMs.coerceAtLeast(0) / 60_000
        val byCount = when {
            count < 10 -> 1
            count < 100 -> 2
            count < 300 -> 3
            else -> 4
        }
        val byTime = when {
            minutes < 10 -> 1
            minutes < 60 -> 2
            minutes < 180 -> 3
            else -> 4
        }
        return Band.entries[maxOf(byCount, byTime)]
    }

    /** Tramo de un periodo según su media diaria. */
    fun ofDailyAverage(count: Long, totalMs: Long, days: Int): Band {
        if (count <= 0) return Band.ZERO
        val d = days.coerceAtLeast(1)
        return of(Math.max(1L, Math.round(count.toDouble() / d)).toInt(), totalMs / d)
    }
}

/** Situación respecto al objetivo diario («como mucho N al día»). */
internal enum class GoalState { NONE, UNDER, NEAR, AT, OVER }

internal fun goalState(count: Int, goal: Int?): GoalState = when {
    goal == null || goal <= 0 -> GoalState.NONE
    count > goal -> GoalState.OVER
    count == goal -> GoalState.AT
    count >= goal * 0.8 -> GoalState.NEAR
    else -> GoalState.UNDER
}

/** Comparación de un valor con una base (media, periodo anterior). */
internal sealed interface Delta {
    /** No hay con qué comparar. */
    data object NoBase : Delta

    /** Base y valor a cero. */
    data object BothZero : Delta

    /** Base a cero y valor por encima. */
    data object FromZero : Delta

    data class Ratio(val ratio: Double) : Delta
}

internal object Deltas {
    /** Por debajo de ±5 % se considera «igual». */
    const val SAME_BAND = 0.05

    fun of(value: Double, base: Double?): Delta = when {
        base == null || base.isNaN() || base < 0 -> Delta.NoBase
        base == 0.0 && value <= 0.0 -> Delta.BothZero
        base == 0.0 -> Delta.FromZero
        else -> Delta.Ratio(value.coerceAtLeast(0.0) / base)
    }

    /**
     * Línea para una proporción [ratio] (valor / base) frente a [ref] en minúsculas
     * («tu martes medio a esta hora», «la semana anterior»). Las frases están en
     * `res/values/copy_today.xml` (`today_delta_*`).
     */
    fun ratioLine(ratio: Double, ref: String): DeltaLine {
        val refUpper = Format.upper(ref)
        return when {
            ratio >= 1.95 -> when (val times = Format.decimal(ratio)) {
                Format.integer(2) -> DeltaLine(
                    str(R.string.today_delta_double, refUpper),
                    str(R.string.today_delta_double_description, ref),
                    Direction.UP,
                )
                Format.integer(3) -> DeltaLine(
                    str(R.string.today_delta_triple, refUpper),
                    str(R.string.today_delta_triple_description, ref),
                    Direction.UP,
                )
                else -> DeltaLine(
                    str(R.string.today_delta_times, times, refUpper),
                    str(R.string.today_delta_times_description, times, ref),
                    Direction.UP,
                )
            }

            ratio > 1 + SAME_BAND -> DeltaLine(
                str(R.string.today_delta_more, Format.signedPercent(ratio - 1), refUpper),
                str(R.string.today_delta_more_description, Format.percent(ratio - 1), ref),
                Direction.UP,
            )

            ratio >= 1 - SAME_BAND -> DeltaLine(
                str(R.string.today_delta_same, refUpper),
                str(R.string.today_delta_same_description, ref),
                Direction.SAME,
            )

            ratio >= 0.45 && ratio < 0.55 -> DeltaLine(
                str(R.string.today_delta_half, refUpper),
                str(R.string.today_delta_half_description, ref),
                Direction.DOWN,
            )

            else -> DeltaLine(
                str(R.string.today_delta_less, Format.signedPercent(ratio - 1), refUpper),
                str(R.string.today_delta_less_description, Format.percent(1 - ratio), ref),
                Direction.DOWN,
            )
        }
    }
}
