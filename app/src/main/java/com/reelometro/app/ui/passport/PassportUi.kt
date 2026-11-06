package com.reelometro.app.ui.passport

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.copy.Variants
import com.reelometro.app.game.ChallengeStatus
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampKind
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.game.WeeklyChallenge
import com.reelometro.app.stats.Format
import java.time.LocalDate

/*
 * Estado de la pestaña PASAPORTE, ya formateado (cifras con Format, textos de PassportCopy).
 * Lógica PURA (sin Android): se prueba con tests JVM. La pantalla solo pinta.
 */

sealed interface PassportUiState {
    data object Loading : PassportUiState

    data class Error(val message: String) : PassportUiState

    data class Content(
        /** «63 DÍAS A BORDO». */
        val kicker: String,
        val flap: FlapUi,
        val level: LevelUi,
        val challenge: ChallengeUi,
        val past: List<PastChallengeUi>,
        val stamps: List<StampUi>,
        /** «9 DE 23». */
        val stampsCount: String,
        /** Sin objetivo, las pistas aclaran que la referencia es la media. */
        val referenceNote: String?,
    ) : PassportUiState
}

/**
 * Flap y su estado. [previousMood] ≠ `null` → la pantalla anima el cambio. Tras enseñarlo (con o sin
 * animación) siempre avisa con `onMoodSeen`, para que el siguiente cambio tenga un «antes».
 */
data class FlapUi(
    val mood: FlapMood,
    val previousMood: FlapMood?,
    /** «EN FORMA», «CALENTANDO». */
    val label: String,
    /** «5 DE 7 DÍAS BAJO TU OBJETIVO». */
    val record: String,
    /** «TU OBJETIVO: 150 AL DÍA»; `null` mientras calienta. */
    val reference: String?,
    val phrase: String,
    val warmingUp: Boolean,
    val description: String,
)

/** Tarjeta de embarque del nivel. */
data class LevelUi(
    val level: TravelerLevel,
    /** «PLA». */
    val code: String,
    /** «PLATA». */
    val name: String,
    val tagline: String,
    /** Código y nombre del siguiente; `null` en el último nivel. */
    val nextCode: String?,
    val nextName: String?,
    /** «NIVEL 3 DE 5». */
    val index: String,
    /** 0–1 hacia el siguiente nivel. */
    val fraction: Float,
    /** «520 MILLAS». */
    val miles: String,
    /** «FALTAN 380 MILLAS PARA ORO» o el texto de nivel máximo. */
    val toNext: String,
    val breakdown: List<TicketLine>,
    val total: TicketLine,
    val streak: TicketLine,
    val note: String,
    val description: String,
)

/** Reto semanal: en curso/cerrado, o «calentando» si aún no hay semana anterior. */
sealed interface ChallengeUi {
    data class Warmup(val label: String, val text: String) : ChallengeUi

    data class Active(
        /** «ESTA SEMANA, UN 10 % MENOS». */
        val title: String,
        val status: ChallengeStatus,
        /** «EN CURSO», «CUMPLIDO», «NO ESTA VEZ». */
        val statusLabel: String,
        /** Cifra grande: lo que llevas. */
        val total: String,
        /** «DE UN TOPE DE 1.098». */
        val caption: String,
        /** Lo que llevas frente al tope (0–1, recortado). */
        val fraction: Float,
        /** Ya has pasado el tope (la barra se queda llena y la cifra en acento). */
        val over: Boolean,
        val lines: List<TicketLine>,
        val phrase: String,
        val days: List<GhostDayUi>,
        val todayIndex: Int?,
        val chartDescription: String,
        val legend: String,
        val description: String,
    ) : ChallengeUi
}

/** Un día de la gráfica fantasma (la pantalla lo convierte en `GhostBar`). */
data class GhostDayUi(val label: String, val count: Int?, val ghost: Int?, val description: String)

/** Un reto ya cerrado. */
data class PastChallengeUi(val key: String, val label: String, val value: String, val won: Boolean, val description: String)

/** Un sello del pasaporte, listo para pintar. */
data class StampUi(
    val id: StampId,
    val kind: StampKind,
    /** «RACHA DE 7». */
    val name: String,
    /** «CONEXIÓN», «SIN MILLAS». */
    val kindLabel: String,
    val earned: Boolean,
    /** «12 OCT 2026» si está conseguido. */
    val date: String?,
    /** El conseguido más reciente (va en acento). */
    val latest: Boolean,
    val ironic: Boolean,
    /** Progreso del pendiente (0–1) y su texto («50 % HECHO»). */
    val progress: Float?,
    val progressText: String?,
    val hint: String,
    /** «+60 MILLAS» / «SIN MILLAS». */
    val xp: String,
    /** «CONSEGUIDO EL 12 OCT 2026» o «PENDIENTE». */
    val status: String,
    /** Giro del sello en grados (estable por sello, entre −5 y 5). */
    val tilt: Float,
    val description: String,
)

/** De [PassportState] a [PassportUiState]: todo el formato y la elección de frases. */
object PassportMapper {

    /** Retos pasados que se enseñan (los más recientes): una lista breve. */
    const val MAX_PAST = 6

    fun loading(): PassportUiState = PassportUiState.Loading

    fun error(today: LocalDate): PassportUiState = PassportUiState.Error(StateCopy.error(Variants.daySeed(today)))

    fun map(state: PassportState): PassportUiState.Content {
        val seed = Variants.daySeed(state.today)
        val goalSet = state.goal != null
        return PassportUiState.Content(
            kicker = PassportCopy.onBoard(state.trackedDays),
            flap = flap(state, seed),
            level = level(state),
            challenge = state.challenge?.let { challenge(it, state.today, seed) } ?: ChallengeUi.Warmup(
                label = PassportCopy.WARMING_UP,
                text = PassportCopy.warmupText(seed),
            ),
            past = state.pastChallenges.take(MAX_PAST).map { r ->
                PastChallengeUi(
                    key = r.weekStart.toString(),
                    label = PassportCopy.pastLabel(r.weekStart),
                    value = PassportCopy.pastValue(r.won),
                    won = r.won,
                    description = PassportCopy.pastDescription(r),
                )
            },
            stamps = stamps(state.stamps),
            stampsCount = PassportCopy.stampsCount(state.earnedStamps, state.stamps.size),
            referenceNote = if (goalSet) null else PassportCopy.REFERENCE_NOTE,
        )
    }

    private fun flap(state: PassportState, seed: Long): FlapUi {
        val f = state.flap
        val phrase = PassportCopy.flapPhrase(f, seed)
        return FlapUi(
            mood = f.mood,
            previousMood = f.previousMood?.takeIf { it != f.mood },
            label = if (f.warmingUp) PassportCopy.WARMING_UP else PassportCopy.moodLabel(f.mood),
            record = PassportCopy.record(f),
            reference = PassportCopy.referenceLine(f),
            phrase = phrase,
            warmingUp = f.warmingUp,
            description = PassportCopy.flapDescription(f, phrase),
        )
    }

    private fun level(state: PassportState): LevelUi {
        val progress = state.level
        val level = progress.level
        val next = progress.next
        val xpForNext = progress.xpForNext
        val remaining = if (xpForNext != null) (xpForNext - progress.xpIntoLevel).coerceAtLeast(0) else null
        val xp = state.xp
        return LevelUi(
            level = level,
            code = PassportCopy.levelCode(level),
            name = PassportCopy.levelName(level),
            tagline = PassportCopy.levelTagline(level),
            nextCode = next?.let(PassportCopy::levelCode),
            nextName = next?.let(PassportCopy::levelName),
            index = PassportCopy.levelIndex(level),
            fraction = progress.fraction.coerceIn(0f, 1f),
            miles = PassportCopy.miles(progress.xp),
            toNext = if (next != null && remaining != null) PassportCopy.milesToNext(remaining, next) else PassportCopy.TOP_LEVEL,
            breakdown = listOf(
                TicketLine(PassportCopy.lineDays(state.goal != null), PassportCopy.milesValue(xp.days)),
                TicketLine(PassportCopy.LINE_STREAKS, PassportCopy.milesValue(xp.streaks)),
                TicketLine(PassportCopy.LINE_CHALLENGES, PassportCopy.milesValue(xp.challenges)),
                TicketLine(PassportCopy.LINE_STAMPS, PassportCopy.milesValue(xp.stamps)),
            ),
            total = TicketLine(PassportCopy.LINE_TOTAL, PassportCopy.miles(progress.xp)),
            streak = TicketLine(PassportCopy.LINE_STREAK, PassportCopy.streakValue(state.streak)),
            note = PassportCopy.NO_MILES_FOR_VIDEOS,
            description = PassportCopy.levelDescription(level, progress.xp, remaining, state.streak),
        )
    }

    private fun challenge(c: WeeklyChallenge, today: LocalDate, seed: Long): ChallengeUi.Active {
        val phrase = PassportCopy.challengePhrase(c, seed)
        val total = c.total.coerceAtLeast(0)
        val target = c.target.coerceAtLeast(0)
        val lines = buildList {
            add(TicketLine(PassportCopy.LINE_TARGET, Format.integer(target)))
            add(TicketLine(PassportCopy.LINE_GHOST_SO_FAR, Format.integer(c.ghostSoFar.coerceAtLeast(0))))
            if (c.status == ChallengeStatus.EN_CURSO) add(TicketLine(PassportCopy.LINE_REMAINING, Format.integer(c.remaining)))
            add(TicketLine(PassportCopy.LINE_GHOST_TOTAL, Format.integer(c.ghostTotal.coerceAtLeast(0))))
        }
        val todayIndex = c.days.indexOfFirst { it.date == today }.takeIf { it >= 0 }
        return ChallengeUi.Active(
            title = PassportCopy.challengeTitle(c.reductionPercent),
            status = c.status,
            statusLabel = PassportCopy.challengeStatus(c.status),
            total = Format.integer(total),
            caption = PassportCopy.challengeCaption(target),
            fraction = if (target <= 0) (if (total > 0) 1f else 0f) else (total.toFloat() / target).coerceIn(0f, 1f),
            over = total > target,
            lines = lines,
            phrase = phrase,
            days = c.days.map { d ->
                GhostDayUi(
                    label = Format.weekdayInitial(d.date.dayOfWeek),
                    count = d.count?.coerceAtLeast(0),
                    ghost = d.ghost?.coerceAtLeast(0),
                    description = PassportCopy.ghostDayDescription(d.date, d.count, d.ghost),
                )
            },
            todayIndex = todayIndex,
            chartDescription = PassportCopy.chartDescription(total, c.ghostSoFar),
            legend = PassportCopy.CHART_LEGEND,
            description = PassportCopy.challengeDescription(c, phrase),
        )
    }

    internal fun stamps(stamps: List<StampState>): List<StampUi> {
        val latest = stamps.filter { it.earnedOn != null }.maxByOrNull { it.earnedOn!! }?.id
        return stamps.map { s ->
            val earnedOn = s.earnedOn
            val progress = if (earnedOn == null) s.progress?.coerceIn(0f, 1f) else null
            StampUi(
                id = s.id,
                kind = s.id.kind,
                name = PassportCopy.stampName(s.id),
                kindLabel = PassportCopy.stampKindLabel(s.id.kind),
                earned = earnedOn != null,
                date = earnedOn?.let(PassportCopy::stampDate),
                latest = earnedOn != null && s.id == latest,
                ironic = s.id.ironic,
                progress = progress,
                progressText = progress?.let(PassportCopy::stampProgress),
                hint = PassportCopy.stampHint(s.id),
                xp = PassportCopy.stampXp(s.id),
                status = earnedOn?.let(PassportCopy::stampEarnedOn) ?: PassportCopy.STAMP_PENDING,
                tilt = tilt(s.id),
                description = PassportCopy.stampDescription(s),
            )
        }
    }

    /** Giro estable por sello (entre −5 y 5 grados, nunca 0): parecen estampados a mano. */
    internal fun tilt(id: StampId): Float {
        val step = Math.floorMod(id.ordinal * 7 + 3, 11) - 5
        return (if (step == 0) 2 else step).toFloat()
    }
}
