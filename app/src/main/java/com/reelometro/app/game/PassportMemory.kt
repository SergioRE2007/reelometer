package com.reelometro.app.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Lo único del pasaporte que se guarda (en el móvil; `data/game/PassportStore`). Todo lo demás se
 * recalcula del historial de vídeos cada vez. Se guarda solo lo que no se puede recalcular o no
 * debe cambiar si cambian los datos o el objetivo:
 *
 * - [stamps]: el día en que se consiguió cada sello (un sello conseguido no se pierde nunca);
 * - [celebrated]: claves de [Celebration] ya vistas;
 * - [highestLevel]: el nivel más alto alcanzado (no baja aunque cambie el objetivo);
 * - [lastSeenMood]: el último estado de Flap que vio el usuario (para animar el cambio);
 * - [challenges]: retos semanales cerrados (el objetivo y el historial pueden cambiar después);
 * - [events]: la primera vez que pasó cada [GameEvent] (Wrapped compartido, ticket guardado…).
 *
 * Todo crece de forma monótona salvo con [cleared] (BORRAR TODO o el gancho de QA GAME_RESET).
 */
data class PassportMemory(
    /** Sube en cada borrado: un cálculo empezado antes de borrar no puede resucitar nada al guardarse. */
    val generation: Int = 0,
    /**
     * Día del primer cálculo del pasaporte (o del primero tras borrarlo); `null` si aún no se ha
     * calculado. En ese primer cálculo, lo conseguido antes de ayer se da por celebrado.
     */
    val since: LocalDate? = null,
    val stamps: Map<StampId, LocalDate> = emptyMap(),
    val celebrated: Set<String> = emptySet(),
    val highestLevel: TravelerLevel = TravelerLevel.PASAJERO,
    val lastSeenMood: FlapMood? = null,
    /** Ordenados por [ChallengeResult.weekStart], el más antiguo primero; uno por semana. */
    val challenges: List<ChallengeResult> = emptyList(),
    val events: Map<GameEvent, LocalDate> = emptyMap(),
) {
    /**
     * Suma lo que trae [update] (lo que acaba de calcular el motor) sin perder nada de lo que ya hay:
     * las fechas y retos ya guardados mandan, las celebraciones se unen, el nivel se queda con el más
     * alto, los eventos con su primera fecha y el último estado visto de Flap no se toca.
     */
    fun absorb(update: PassportMemory): PassportMemory {
        val known = challenges.mapTo(HashSet()) { it.weekStart }
        val mergedEvents = HashMap(events)
        update.events.forEach { (event, date) ->
            val current = mergedEvents[event]
            if (current == null || date.isBefore(current)) mergedEvents[event] = date
        }
        return copy(
            since = since ?: update.since,
            stamps = update.stamps + stamps,
            celebrated = celebrated + update.celebrated,
            highestLevel = maxOf(highestLevel, update.highestLevel),
            challenges = (challenges + update.challenges.filter { it.weekStart !in known }).sortedBy { it.weekStart },
            events = mergedEvents,
        )
    }

    /** Registra [event] en [on] si es la primera vez (o si es anterior a la fecha guardada). */
    fun withEvent(event: GameEvent, on: LocalDate): PassportMemory {
        val known = events[event]
        return if (known != null && !on.isBefore(known)) this else copy(events = events + (event to on))
    }

    /** Marca como vistas estas claves de [Celebration]. */
    fun celebrate(keys: Collection<String>): PassportMemory =
        if (celebrated.containsAll(keys)) this else copy(celebrated = celebrated + keys)

    /** El usuario ha visto a Flap en [mood]. */
    fun seeing(mood: FlapMood): PassportMemory = if (lastSeenMood == mood) this else copy(lastSeenMood = mood)

    /** Pasaporte vacío (BORRAR TODO), con la generación siguiente. */
    fun cleared(): PassportMemory = PassportMemory(generation = generation + 1)

    /**
     * Al volver a encender el modo pasaporte: se conserva todo, pero el siguiente cálculo vuelve a ser
     * «el primero» (lo de antes de ayer se da por celebrado), para que no llegue de golpe lo conseguido
     * con el modo oculto. Sube la generación para que un cálculo en marcha no devuelva el [since] viejo.
     */
    fun rebased(): PassportMemory = copy(since = null, generation = generation + 1)

    /**
     * Solo QA (`GAME_CELEBRATE`): deja pendiente una celebración de prueba.
     * - [ForcedCelebration.STAMP]: [stamp] conseguido hoy y sin celebrar.
     * - [ForcedCelebration.LEVEL]: el nivel más alto (FRECUENTE si aún era PASAJERO) sin celebrar.
     * - [ForcedCelebration.CHALLENGE]: el reto de la semana pasada, cumplido y sin celebrar.
     */
    fun forcing(kind: ForcedCelebration, stamp: StampId, today: LocalDate): PassportMemory = when (kind) {
        ForcedCelebration.STAMP -> copy(
            since = since ?: today,
            stamps = stamps + (stamp to today),
            celebrated = celebrated - Celebration.StampEarned(stamp, today).key,
        )
        ForcedCelebration.LEVEL -> {
            val level = maxOf(highestLevel, TravelerLevel.FRECUENTE)
            copy(since = since ?: today, highestLevel = level, celebrated = celebrated - Celebration.LevelUp(level).key)
        }
        ForcedCelebration.CHALLENGE -> {
            val weekStart = PassportRules.weekStart(today).minusWeeks(1)
            val result = ChallengeResult(weekStart, target = FORCED_TARGET, total = FORCED_TOTAL, won = true)
            copy(
                since = since ?: today,
                challenges = (challenges.filterNot { it.weekStart == weekStart } + result).sortedBy { it.weekStart },
                celebrated = celebrated - Celebration.ChallengeWon(result).key,
            )
        }
    }

    private companion object {
        const val FORCED_TARGET = 100
        const val FORCED_TOTAL = 80
    }
}

/** Celebraciones de prueba del gancho de QA `GAME_CELEBRATE`. */
enum class ForcedCelebration {
    STAMP, LEVEL, CHALLENGE;

    companion object {
        /** `stamp`, `level` o `challenge` (sin distinguir mayúsculas); `null` o vacío = [STAMP]. */
        fun parse(name: String?): ForcedCelebration? {
            val clean = name?.trim()?.uppercase()
            if (clean.isNullOrEmpty()) return STAMP
            return entries.firstOrNull { it.name == clean }
        }
    }
}

/**
 * JSON de [PassportMemory] (kotlinx.serialization). Tolerante: los nombres o fechas que no se
 * entienden (p. ej. un sello retirado) se ignoran uno a uno, y un texto ilegible da un pasaporte vacío.
 */
object PassportMemoryCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(memory: PassportMemory): String = json.encodeToString(MemoryDto.serializer(), memory.toDto())

    fun decode(text: String?): PassportMemory {
        if (text.isNullOrBlank()) return PassportMemory()
        return runCatching { json.decodeFromString(MemoryDto.serializer(), text).toMemory() }
            .getOrDefault(PassportMemory())
    }

    private fun PassportMemory.toDto(): MemoryDto = MemoryDto(
        v = VERSION,
        generation = generation,
        since = since?.toString(),
        stamps = stamps.entries.sortedBy { it.key.ordinal }.associate { (id, date) -> id.name to date.toString() },
        celebrated = celebrated.sorted(),
        highestLevel = highestLevel.name,
        lastSeenMood = lastSeenMood?.name,
        challenges = challenges.map { ChallengeDto(it.weekStart.toString(), it.target, it.total, it.won) },
        events = events.entries.sortedBy { it.key.ordinal }.associate { (event, date) -> event.name to date.toString() },
    )

    private fun MemoryDto.toMemory(): PassportMemory = PassportMemory(
        generation = generation,
        since = date(since),
        stamps = stamps.entries.mapNotNull { (name, value) ->
            val id = StampId.entries.firstOrNull { it.name == name }
            val date = date(value)
            if (id == null || date == null) null else id to date
        }.toMap(),
        celebrated = celebrated.toSet(),
        highestLevel = TravelerLevel.entries.firstOrNull { it.name == highestLevel } ?: TravelerLevel.PASAJERO,
        lastSeenMood = FlapMood.entries.firstOrNull { it.name == lastSeenMood },
        challenges = challenges.mapNotNull { dto ->
            date(dto.weekStart)?.let { ChallengeResult(it, dto.target, dto.total, dto.won) }
        }.distinctBy { it.weekStart }.sortedBy { it.weekStart },
        events = events.entries.mapNotNull { (name, value) ->
            val event = GameEvent.entries.firstOrNull { it.name == name }
            val date = date(value)
            if (event == null || date == null) null else event to date
        }.toMap(),
    )

    private fun date(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@Serializable
internal data class MemoryDto(
    val v: Int = PassportMemoryCodec.VERSION,
    val generation: Int = 0,
    val since: String? = null,
    val stamps: Map<String, String> = emptyMap(),
    val celebrated: List<String> = emptyList(),
    val highestLevel: String? = null,
    val lastSeenMood: String? = null,
    val challenges: List<ChallengeDto> = emptyList(),
    val events: Map<String, String> = emptyMap(),
)

@Serializable
internal data class ChallengeDto(
    val weekStart: String,
    val target: Int,
    val total: Int,
    val won: Boolean,
)
