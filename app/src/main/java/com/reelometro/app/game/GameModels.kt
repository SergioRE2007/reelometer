package com.reelometro.app.game

import java.time.LocalDate

/*
 * Modo «Pasaporte»: modelos PUROS (sin Android) que comparten la lógica (game/), los datos
 * (data/game/) y la pantalla (ui/passport/). Para no romper a nadie, mejor añadir campos con valor
 * por defecto que cambiar nombres o firmas.
 *
 * Principio que no se negocia: el juego premia VER MENOS. La experiencia sale de días bajo la
 * referencia, rachas, retos y sellos; nunca de vídeos vistos. Nada se pierde con castigo.
 */

/**
 * Estado de Flap según los últimos 7 días frente a la referencia (objetivo o, sin objetivo, la
 * media de las 4 semanas anteriores). Del mejor al peor. **Nunca muere**: en el peor estado está
 * «zombi del scroll» y se recupera en cuanto vuelves a cumplir.
 */
enum class FlapMood { EN_FORMA, NORMAL, CANSADO, ZOMBI }

/** Lo que la pantalla necesita para pintar y animar a Flap. */
data class FlapState(
    val mood: FlapMood,
    /**
     * Estado que el usuario vio la última vez, si es distinto de [mood] (para animar el cambio);
     * `null` si no ha cambiado o es la primera vez.
     */
    val previousMood: FlapMood? = null,
    /** Días de la ventana (los últimos 7) que quedaron en la referencia o por debajo. */
    val daysUnder: Int = 0,
    /** Días de la ventana con registro (0–7). Con pocos días, Flap está «calentando». */
    val daysTracked: Int = 0,
    /** Referencia diaria usada (vídeos); `null` si todavía no hay ni objetivo ni historial suficiente. */
    val reference: Int? = null,
    /** `true` si [reference] es el objetivo diario; `false` si es la media de las 4 semanas anteriores. */
    val referenceIsGoal: Boolean = false,
) {
    /** Sin base para juzgar todavía (ni objetivo ni historial): Flap espera en NORMAL. */
    val warmingUp: Boolean get() = reference == null
}

/**
 * Niveles de viajero frecuente, de menos a más. [minXp] es la experiencia mínima de cada uno.
 * Los nombres que se ven salen de `copy/PassportCopy`.
 */
enum class TravelerLevel(val minXp: Int) {
    PASAJERO(0),
    FRECUENTE(150),
    PLATA(400),
    ORO(900),
    PLATINO(1_800),
    ;

    val next: TravelerLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forXp(xp: Int): TravelerLevel = entries.last { xp >= it.minXp }
    }
}

/** De dónde sale la experiencia (se enseña en la tarjeta de embarque, con transparencia). */
data class XpBreakdown(
    /** Por días cerrados en la referencia o por debajo. */
    val days: Int = 0,
    /** Por rachas (bonificación al llegar a 3, 7, 14, 30… días seguidos). */
    val streaks: Int = 0,
    /** Por retos semanales cumplidos. */
    val challenges: Int = 0,
    /** Por sellos (los irónicos no dan nada). */
    val stamps: Int = 0,
) {
    val total: Int get() = days + streaks + challenges + stamps
}

/** Nivel actual y progreso hacia el siguiente. */
data class LevelProgress(
    val level: TravelerLevel,
    val xp: Int,
) {
    val next: TravelerLevel? get() = level.next

    /** Experiencia ganada dentro del nivel actual. */
    val xpIntoLevel: Int get() = xp - level.minXp

    /** Experiencia que separa el nivel actual del siguiente; `null` en el último. */
    val xpForNext: Int? get() = next?.let { it.minXp - level.minXp }

    /** 0–1 hacia el siguiente nivel (1 en el último). */
    val fraction: Float
        get() = xpForNext?.let { (xpIntoLevel.toFloat() / it).coerceIn(0f, 1f) } ?: 1f

    companion object {
        fun of(xp: Int): LevelProgress = LevelProgress(TravelerLevel.forXp(xp.coerceAtLeast(0)), xp.coerceAtLeast(0))
    }
}

/** Familia de un sello (cambia su forma: redondo, rectangular, octogonal…). */
enum class StampKind { OBJETIVO, RACHA, RETO, HITO, IRONICO }

/**
 * Los sellos del pasaporte, en el orden en que se enseñan. [xp] es lo que dan al conseguirlos
 * (0 en los irónicos). Nombres y pistas en `copy/PassportCopy`; reglas en `game/`.
 */
enum class StampId(val kind: StampKind, val xp: Int) {
    /** Primer día cerrado en el objetivo o por debajo. */
    PRIMER_DIA(StampKind.OBJETIVO, 20),

    /** Un día por debajo de la mitad de la referencia. */
    MEDIO_DIA(StampKind.OBJETIVO, 30),

    /** Un día entero sin vídeos cortos (con historial antes y después). */
    DIA_EN_BLANCO(StampKind.OBJETIVO, 40),

    /** Sábado y domingo de la misma semana bajo la referencia. */
    FIN_DE_SEMANA(StampKind.OBJETIVO, 30),

    /** Rachas de días bajo la referencia. */
    RACHA_3(StampKind.RACHA, 30),
    RACHA_7(StampKind.RACHA, 60),
    RACHA_14(StampKind.RACHA, 100),
    RACHA_30(StampKind.RACHA, 200),

    /** Una semana un 20 % por debajo de la anterior. */
    SEMANA_MENOS_20(StampKind.RETO, 60),

    /** Un mes natural con menos vídeos que el anterior. */
    MES_A_LA_BAJA(StampKind.RETO, 80),

    /** Primer reto semanal cumplido. */
    PRIMER_RETO(StampKind.RETO, 40),

    /** Cuatro retos semanales cumplidos (no hace falta que sean seguidos). */
    CUATRO_RETOS(StampKind.RETO, 100),

    /** Flap vuelve de ZOMBI a NORMAL o mejor. */
    REGRESO(StampKind.RETO, 50),

    /** Una semana completa con registro (todos los días con el servicio contando). */
    PRIMERA_SEMANA(StampKind.HITO, 10),

    /** Primer resumen (Wrapped) compartido. */
    WRAPPED_COMPARTIDO(StampKind.HITO, 10),

    /** Primer ticket guardado en el móvil. */
    TICKET_GUARDADO(StampKind.HITO, 10),

    /** Primer nivel nuevo (FRECUENTE). */
    PRIMER_ASCENSO(StampKind.HITO, 0),

    /** «Madrugador del scroll»: vídeos entre las 3:00 y las 4:59. Irónico, sin experiencia. */
    MADRUGADOR(StampKind.IRONICO, 0),

    /** 1.000 vídeos en total. Irónico. */
    MIL_VIDEOS(StampKind.IRONICO, 0),

    /** 10.000 vídeos en total. Irónico. */
    DIEZ_MIL_VIDEOS(StampKind.IRONICO, 0),

    /** Las 3 plataformas en un mismo día. Irónico. */
    TRES_PLATAFORMAS(StampKind.IRONICO, 0),

    /** 5 categorías distintas en una semana. Irónico. */
    CINCO_CATEGORIAS(StampKind.IRONICO, 0),

    /** Más de 100 vídeos en una sola hora. Irónico. */
    MARATON(StampKind.IRONICO, 0),
    ;

    val ironic: Boolean get() = kind == StampKind.IRONICO
}

/** Un sello del pasaporte: conseguido (con fecha) o pendiente (en negativo, con pista). */
data class StampState(
    val id: StampId,
    /** Día en que se consiguió; `null` si falta. */
    val earnedOn: LocalDate? = null,
    /** Ya se celebró (animación de estampado vista). */
    val celebrated: Boolean = false,
    /** Progreso hacia el sello pendiente, 0–1, si tiene sentido (rachas, totales); `null` si no. */
    val progress: Float? = null,
) {
    val earned: Boolean get() = earnedOn != null
}

enum class ChallengeStatus { EN_CURSO, CUMPLIDO, FALLADO }

/** Un día del reto: lo de esta semana frente a lo del mismo día de la semana anterior («fantasma»). */
data class ChallengeDay(
    val date: LocalDate,
    /** Vídeos de ese día; `null` si es un día futuro. */
    val count: Int?,
    /** Vídeos del mismo día de la semana anterior; `null` si ese día no hubo registro. */
    val ghost: Int?,
)

/**
 * Reto semanal contra tu «yo fantasma»: cada lunes, «esta semana, menos que la anterior» (o un
 * −10 / −20 % según el historial). Se cumple si el total de la semana queda en [target] o por debajo.
 */
data class WeeklyChallenge(
    /** Lunes de la semana del reto. */
    val weekStart: LocalDate,
    /** 0 = «menos que la anterior»; 10 o 20 = ese porcentaje por debajo. */
    val reductionPercent: Int,
    /** Total de la semana anterior entera. */
    val ghostTotal: Int,
    /** Como mucho estos vídeos en la semana. */
    val target: Int,
    /** Lo que llevas esta semana. */
    val total: Int,
    /** Lo que llevaba el fantasma hasta el mismo día de la semana. */
    val ghostSoFar: Int,
    /** Siete días, de lunes a domingo. */
    val days: List<ChallengeDay>,
    val status: ChallengeStatus,
) {
    /** Lo que queda hasta el tope (0 si ya se pasó). */
    val remaining: Int get() = (target - total).coerceAtLeast(0)
}

/** Un reto ya cerrado (se guarda: el objetivo puede cambiar después). */
data class ChallengeResult(
    val weekStart: LocalDate,
    val target: Int,
    val total: Int,
    val won: Boolean,
)

/** Algo que celebrar la próxima vez que se abra la app (una sola vez cada cosa). */
sealed interface Celebration {
    /** Clave estable para marcarla como vista ([PassportSource.markCelebrated]). */
    val key: String

    data class StampEarned(val stamp: StampId, val on: LocalDate) : Celebration {
        override val key: String get() = "stamp:${stamp.name}"
    }

    data class LevelUp(val level: TravelerLevel) : Celebration {
        override val key: String get() = "level:${level.name}"
    }

    data class ChallengeWon(val result: ChallengeResult) : Celebration {
        override val key: String get() = "challenge:${result.weekStart}"
    }
}

/** Cosas que pasan fuera de los datos de uso y dan sellos (se guardan con su fecha). */
enum class GameEvent { WRAPPED_SHARED, TICKET_SAVED, LEVEL_TICKET_SHARED }

/** Todo lo que pinta la pestaña PASAPORTE. */
data class PassportState(
    val today: LocalDate,
    /** Objetivo diario de Ajustes; `null` sin objetivo (se usa la media como referencia). */
    val goal: Int?,
    val flap: FlapState,
    val level: LevelProgress,
    val xp: XpBreakdown,
    /** Racha actual de días cerrados en la referencia o por debajo. */
    val streak: Int,
    /** Todos los sellos, en el orden de [StampId]. */
    val stamps: List<StampState>,
    /** Reto de esta semana; `null` si aún no hay una semana anterior con la que comparar. */
    val challenge: WeeklyChallenge?,
    /** Retos ya cerrados, el más reciente primero. */
    val pastChallenges: List<ChallengeResult>,
    /** Celebraciones pendientes, en el orden en que deben salir. */
    val pending: List<Celebration>,
    /** Días con registro en todo el historial. */
    val trackedDays: Int,
) {
    val earnedStamps: Int get() = stamps.count { it.earned }
}
