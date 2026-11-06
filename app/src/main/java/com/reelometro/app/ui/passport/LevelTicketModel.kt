package com.reelometro.app.ui.passport

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.ShareCopy
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.copy.Variants
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.stats.Format

/**
 * Lo que lleva el ticket de nivel (imagen 1080 × 1920, `LevelTicketRenderer`): tarjeta de embarque
 * con el nivel enorme, la ruta ORIGEN → DESTINO, millas y sellos, código de barras con un trazo
 * por sello (los conseguidos, gruesos) y el sello «EMBARQUE CONFIRMADO». Lógica PURA.
 */
data class LevelTicketData(
    val brand: String,
    val title: String,
    val dateLine: String,
    val levelLabel: String,
    /** «PLATA», enorme. */
    val levelName: String,
    /** Ruta: código y nombre del nivel y del siguiente (o fin del trayecto). */
    val originLabel: String,
    val originCode: String,
    val destinationLabel: String,
    val destinationCode: String,
    val lines: List<TicketLine>,
    val total: TicketLine,
    /** Sellos conseguidos, el más reciente primero (como mucho [MAX_STAMPS]). */
    val stamps: List<String>,
    val stampsLabel: String,
    val footer: String,
    val stamp: String,
    val stampDate: String,
    val barcode: List<Float>,
    val barcodeCaption: String,
    val brandFooter: String,
    val fileName: String,
    val shareText: String,
)

object LevelTicketModel {

    const val MAX_STAMPS = 6

    fun build(state: PassportState, level: TravelerLevel = state.level.level): LevelTicketData {
        val seed = Variants.daySeed(state.today)
        val next = level.next
        val earned = state.stamps.filter { it.earnedOn != null }.sortedByDescending { it.earnedOn }
        val won = state.pastChallenges.count { it.won }
        val xp = state.level.xp.coerceAtLeast(level.minXp)
        return LevelTicketData(
            brand = ShareCopy.BRAND,
            title = PassportCopy.TICKET_TITLE,
            dateLine = PassportCopy.stampDate(state.today),
            levelLabel = PassportCopy.TICKET_LEVEL,
            levelName = PassportCopy.levelName(level),
            originLabel = PassportCopy.LABEL_ORIGIN,
            originCode = PassportCopy.levelCode(level),
            destinationLabel = if (next != null) PassportCopy.LABEL_DESTINATION else PassportCopy.LABEL_FINAL,
            destinationCode = PassportCopy.levelCode(next ?: level),
            lines = listOf(
                TicketLine(PassportCopy.TICKET_STAMPS, PassportCopy.stampsCount(earned.size, state.stamps.size)),
                TicketLine(PassportCopy.TICKET_STREAK, PassportCopy.streakValue(state.streak)),
                TicketLine(PassportCopy.TICKET_CHALLENGES, Format.integer(won)),
                TicketLine(PassportCopy.TICKET_DAYS, Format.integer(state.trackedDays.coerceAtLeast(0))),
            ),
            total = TicketLine(PassportCopy.TICKET_MILES, Format.integer(xp)),
            stamps = earned.take(MAX_STAMPS).map { PassportCopy.stampName(it.id) },
            stampsLabel = PassportCopy.SECTION_STAMPS,
            footer = PassportCopy.ticketFooter(seed),
            stamp = PassportCopy.TICKET_STAMP,
            stampDate = PassportCopy.stampDate(state.today),
            barcode = state.stamps.map { s -> if (s.earnedOn != null) 0.4f + 0.6f * (s.id.xp.coerceAtMost(200) / 200f) else 0f },
            barcodeCaption = barcodeCaption(xp, earned.size, state.today.year),
            brandFooter = ShareCopy.BRAND_FOOTER,
            fileName = PassportCopy.ticketFileName(level),
            shareText = PassportCopy.ticketShareText(level),
        )
    }

    /** Pie del código, como un EAN: «0000520 · 009 · 2026» (millas, sellos, año). Es un código, no una cifra. */
    fun barcodeCaption(miles: Int, stamps: Int, year: Int): String {
        val m = miles.coerceIn(0, 9_999_999)
        val s = stamps.coerceIn(0, 999)
        return "${m.toString().padStart(7, '0')} · ${s.toString().padStart(3, '0')} · $year"
    }
}
