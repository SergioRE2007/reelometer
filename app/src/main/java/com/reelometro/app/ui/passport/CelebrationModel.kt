package com.reelometro.app.ui.passport

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.game.Celebration
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.stats.Format

/** Qué se celebra (decide el arte y el gesto del componente común `Celebration`). */
enum class CelebrationType { STAMP, LEVEL, CHALLENGE }

/** Una celebración lista para pintar (lógica PURA: la prueba jvm-check). */
data class CelebrationUi(
    val key: String,
    val type: CelebrationType,
    /** «SELLO NUEVO», «NIVEL NUEVO», «RETO CUMPLIDO». */
    val label: String,
    val title: String,
    val caption: String,
    /** El sello que se estampa (solo [CelebrationType.STAMP]). */
    val stamp: StampUi? = null,
    /** El nivel nuevo (solo [CelebrationType.LEVEL]): su código en el panel y el ticket compartible. */
    val level: TravelerLevel? = null,
    val levelCode: String? = null,
    /** Cifra y pie del reto cumplido (solo [CelebrationType.CHALLENGE]). */
    val figure: String? = null,
    val figureCaption: String? = null,
    /** Botón principal: COMPARTIR TICKET (nivel) o VER EL PASAPORTE (sello y reto). */
    val actionLabel: String,
    val actionDescription: String? = null,
)

object CelebrationModel {

    /** La siguiente celebración pendiente que no se ha cerrado ya en esta sesión. */
    fun next(pending: List<Celebration>, dismissed: Set<String>): Celebration? =
        pending.firstOrNull { it.key !in dismissed }

    fun of(celebration: Celebration, state: PassportState): CelebrationUi {
        val day = Variants.daySeed(state.today)
        return when (celebration) {
            is Celebration.StampEarned -> {
                val id = celebration.stamp
                val stamp = PassportMapper.stamps(listOf(StampState(id, earnedOn = celebration.on))).first()
                CelebrationUi(
                    key = celebration.key,
                    type = CelebrationType.STAMP,
                    label = PassportCopy.CELEBRATE_STAMP,
                    title = PassportCopy.stampSpoken(id),
                    caption = PassportCopy.stampCaption(id, day + id.ordinal),
                    stamp = stamp,
                    actionLabel = PassportCopy.SEE_PASSPORT,
                )
            }
            is Celebration.LevelUp -> {
                val level = celebration.level
                CelebrationUi(
                    key = celebration.key,
                    type = CelebrationType.LEVEL,
                    label = PassportCopy.CELEBRATE_LEVEL,
                    title = PassportCopy.levelName(level),
                    caption = PassportCopy.levelCaption(level, day),
                    level = level,
                    levelCode = PassportCopy.levelCode(level),
                    actionLabel = PassportCopy.SHARE_TICKET,
                    actionDescription = PassportCopy.SHARE_TICKET_DESCRIPTION,
                )
            }
            is Celebration.ChallengeWon -> {
                val r = celebration.result
                CelebrationUi(
                    key = celebration.key,
                    type = CelebrationType.CHALLENGE,
                    label = PassportCopy.CELEBRATE_CHALLENGE,
                    title = PassportCopy.challengeWonTitle(r),
                    caption = PassportCopy.challengeWonCaption(r, day),
                    figure = Format.integer(r.total.coerceAtLeast(0)),
                    figureCaption = PassportCopy.challengeCaption(r.target),
                    actionLabel = PassportCopy.SEE_PASSPORT,
                )
            }
        }
    }
}
