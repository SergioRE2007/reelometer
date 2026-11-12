package com.reelometro.app.ui.passport

import com.reelometro.app.copy.FlapCopy
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.game.FlapDay
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.game.FlapState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.withIndex
import java.time.LocalDate

/*
 * Estado de la pestaña FLAP, ya formateado (cifras con Format, textos de FlapCopy).
 * Lógica PURA (sin Android): se prueba con tests JVM. La pantalla solo pinta.
 */

sealed interface FlapUiState {
    data object Loading : FlapUiState

    data class Error(val message: String) : FlapUiState

    data class Content(
        /** «HOY · MARTES 3 NOV». */
        val kicker: String,
        val mood: FlapMood,
        /**
         * Estado que el usuario vio la última vez, si es distinto de [mood]: la pantalla voltea de
         * él al nuevo. Tras enseñarlo (con o sin animación) avisa con `onMoodSeen`.
         */
        val previousMood: FlapMood?,
        /** Ha revivido o mejorado desde la última vez (salto alegre). */
        val recovered: Boolean,
        /** «ENFERMO». */
        val label: String,
        /** Frase corta del estado (variante del día). */
        val phrase: String,
        /** Frase de «ha revivido» si [recovered]; la pantalla la deja puesta mientras no cambie el estado. */
        val recoveredPhrase: String?,
        /** Vida 0–100. */
        val life: Int,
        /** [life] de 0 a 1, para la barra. */
        val lifeFraction: Float,
        /** «62». */
        val lifeValue: String,
        /** Vida en peligro (ENFERMO o KO): barra y cifra en acento. */
        val danger: Boolean,
        /** KO: el nombre del estado va en acento. */
        val ko: Boolean,
        /** TalkBack de la barra: «Vida: 62 de 100». */
        val lifeDescription: String,
        /** «HOY 31 VÍDEOS · AGUANTA 40». */
        val today: String,
        /** «Con 9 vídeos más se pondrá enfermo.» o, en KO, «KO hasta mañana. A medianoche revive.». */
        val next: String,
        /** Las tres líneas de «Cómo funciona». */
        val how: List<String>,
        /** Sin objetivo: «Sin objetivo, Flap aguanta 150 vídeos al día…» (con el botón PONER OBJETIVO); `null` con objetivo. */
        val noGoal: String?,
        /** Los 7 días anteriores; vacía en una instalación nueva (no se enseña la semana). */
        val week: List<FlapDayUi>,
        /** «5 días seguidos sin que enferme»; `null` si la racha es 0 o en una instalación nueva. */
        val streak: String?,
        /** TalkBack de Flap: «Flap: enfermo, vida 32 de 100.» (la frase se lee aparte). */
        val description: String,
    ) : FlapUiState
}

/** Un día de la tira de la semana, listo para pintar. Sin registro, [mood] y [count] van a `null` (hueco). */
data class FlapDayUi(
    /** Clave estable para la lista (la fecha). */
    val key: String,
    /** «L». */
    val initial: String,
    val mood: FlapMood?,
    /** «34». */
    val count: String?,
    val description: String,
)

/** De [FlapState] a [FlapUiState]: todo el formato y la elección de frases. */
object FlapMapper {

    fun loading(): FlapUiState = FlapUiState.Loading

    fun error(today: LocalDate): FlapUiState = FlapUiState.Error(StateCopy.error(Variants.daySeed(today)))

    fun map(state: FlapState): FlapUiState.Content {
        val seed = Variants.daySeed(state.today)
        val fresh = state.trackedDays <= 0
        val life = state.life.coerceIn(0, FlapRules.MAX_LIFE)
        val phrase = FlapCopy.phrase(state, seed)
        val previous = state.previousMood?.takeIf { it != state.mood }
        val recovered = previous != null && previous.ordinal > state.mood.ordinal
        return FlapUiState.Content(
            kicker = FlapCopy.kicker(state.today),
            mood = state.mood,
            previousMood = previous,
            recovered = recovered,
            label = FlapCopy.moodLabel(state.mood),
            phrase = phrase,
            recoveredPhrase = if (recovered) FlapCopy.recoveredPhrase(seed) else null,
            life = life,
            lifeFraction = life / FlapRules.MAX_LIFE.toFloat(),
            lifeValue = FlapCopy.lifeValue(life),
            danger = state.mood.ordinal >= FlapMood.ENFERMO.ordinal,
            ko = state.mood == FlapMood.KO,
            lifeDescription = FlapCopy.lifeDescription(life),
            today = FlapCopy.todayLine(state.count, state.limit),
            next = FlapCopy.nextLine(state),
            how = FlapCopy.howLines(state.limit, state.hasGoal),
            noGoal = if (state.hasGoal) null else FlapCopy.noGoal(state.limit),
            week = if (fresh) emptyList() else state.week.map { day(it) },
            streak = if (fresh) null else FlapCopy.streak(state.healthyStreak),
            description = FlapCopy.description(state.mood, life),
        )
    }

    private fun day(day: FlapDay): FlapDayUi {
        val known = day.count != null && day.mood != null
        return FlapDayUi(
            key = day.date.toString(),
            initial = FlapCopy.dayInitial(day.date),
            mood = if (known) day.mood else null,
            count = if (known) FlapCopy.dayCount(day.count) else null,
            description = FlapCopy.dayDescription(day),
        )
    }

    /**
     * Flujo de estados de la pestaña (lo que `FlapViewModel` pone en un `stateIn`). Cada valor de
     * [attempts] (0, 1, 2… al pulsar REINTENTAR) vuelve a suscribirse a [source]. Un fallo del
     * origen (o del formato) da [FlapUiState.Error]; al reintentar (dentro de una misma
     * suscripción) se pasa por [FlapUiState.Loading] hasta el siguiente dato. Lo que cuenta es el
     * orden del intento en esta suscripción, no su valor: si el `stateIn` vuelve a arrancar (la
     * pestaña vuelve de segundo plano) con [attempts] ya en 1, no se repite el Loading.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun states(
        source: () -> Flow<FlapState>,
        attempts: Flow<Int>,
        clock: () -> LocalDate,
    ): Flow<FlapUiState> = attempts.withIndex().flatMapLatest { (index, _) ->
        source()
            .map<FlapState, FlapUiState> { FlapMapper.map(it) }
            .onStart { if (index > 0) emit(FlapUiState.Loading) }
            .catch { emit(FlapMapper.error(clock())) }
    }
}

/**
 * Recuerda qué estado de Flap ya se ha marcado como visto, para no repetir la llamada a
 * `FlapSource.markMoodSeen` si la pantalla se recompone o vuelve antes de que llegue el estado nuevo.
 */
class MoodSeenGate {
    private var last: FlapMood? = null

    /** `true` si hay que avisar de que se ha visto [mood]. */
    fun shouldMark(mood: FlapMood): Boolean {
        if (last == mood) return false
        last = mood
        return true
    }

    /** Olvida lo marcado (al salir de la pestaña: lo visto puede haberse borrado entretanto). */
    fun reset() {
        last = null
    }
}
