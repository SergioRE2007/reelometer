package com.reelometro.app.copy

import com.reelometro.app.data.Category
import com.reelometro.app.game.ChallengeDay
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.ChallengeStatus
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapState
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampKind
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.game.WeeklyChallenge
import com.reelometro.app.stats.Equivalences
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate

/**
 * Segunda foto de los textos en español: lo que no cubre la de [CopyCatalogTest] (Pasaporte,
 * imagen compartible y todas las formas de las equivalencias). Se sacó del código de antes de
 * pasar los textos a recursos y vive en `src/test/resources/i18n/copy-golden-extra.txt`.
 * Solo usa la API pública de `PassportCopy`, `ShareCopy` y `Equivalences`, que no cambió.
 * Si cambia un texto a propósito: `REELOMETRO_GOLDEN_UPDATE=1 ./gradlew testDebugUnitTest --tests '*CopyGoldenExtraTest*'`.
 */
class CopyGoldenExtraTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val seeds = 0L..11L
    private val min = 60_000L
    private val hour = 3_600_000L

    private fun constants(cls: Class<*>): List<Pair<String, String>> {
        val fields = cls.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { f -> f.isAccessible = true; f.name to (f.get(null) as String) }
        val instance = cls.declaredFields.firstOrNull { it.name == "INSTANCE" }?.get(null)
        val getters = cls.declaredMethods
            .filter { it.name.startsWith("get") && it.name.length > 3 && it.parameterCount == 0 && it.returnType == String::class.java && Modifier.isPublic(it.modifiers) }
            .map { m -> m.name.removePrefix("get") to (m.invoke(if (Modifier.isStatic(m.modifiers)) null else instance) as String) }
        return (fields + getters).distinctBy { it.first }.map { (n, v) -> "${cls.simpleName}.$n" to v }
    }

    private fun lines(): List<String> {
        val out = mutableListOf<String>()
        fun put(source: String, text: Any?) {
            out += "$source → $text"
        }
        constants(PassportCopy::class.java).forEach { (n, v) -> put(n, v) }
        constants(ShareCopy::class.java).forEach { (n, v) -> put(n, v) }

        // ---------------------------------------------------------------- Pasaporte
        val P = PassportCopy
        listOf(0, 1, 2, 7, 1_234).forEach { put("onBoard($it)", P.onBoard(it)) }
        for (mood in FlapMood.entries) {
            put("moodLabel($mood)", P.moodLabel(mood))
            seeds.forEach { put("moodPhrase($mood, $it)", P.moodPhrase(mood, it)) }
        }
        seeds.forEach {
            put("warmingPhrase($it)", P.warmingPhrase(it))
            put("recoveredPhrase($it)", P.recoveredPhrase(it))
            put("warmupText($it)", P.warmupText(it))
            put("ticketFooter($it)", P.ticketFooter(it))
            put("levelUpNotificationText($it)", P.levelUpNotificationText(it))
        }
        val flaps = buildList {
            for (mood in FlapMood.entries) for (prev in listOf(null, FlapMood.ZOMBI, FlapMood.EN_FORMA))
                for (ref in listOf<Pair<Int?, Boolean>>(null to false, 150 to true, 120 to false))
                    for ((under, tracked) in listOf(0 to 0, 1 to 1, 0 to 3, 5 to 7, 7 to 7))
                        add(FlapState(mood, prev, under, tracked, ref.first, ref.second))
        }
        for (f in flaps) {
            put("record($f)", P.record(f))
            put("referenceLine($f)", P.referenceLine(f))
            put("flapDescription($f)", P.flapDescription(f, "Frase."))
            seeds.forEach { put("flapPhrase($f, $it)", P.flapPhrase(f, it)) }
        }
        for (level in TravelerLevel.entries) {
            put("levelName($level)", P.levelName(level))
            put("levelCode($level)", P.levelCode(level))
            put("levelTagline($level)", P.levelTagline(level))
            put("levelIndex($level)", P.levelIndex(level))
            put("levelSpoken($level)", P.levelSpoken(level))
            put("ticketShareText($level)", P.ticketShareText(level))
            put("ticketFileName($level)", P.ticketFileName(level))
            put("levelUpNotificationTitle($level)", P.levelUpNotificationTitle(level))
            seeds.forEach { put("levelCaption($level, $it)", P.levelCaption(level, it)) }
            listOf(0, 1, 2, 250).forEach { put("milesToNext($it, $level)", P.milesToNext(it, level)) }
            for (xp in listOf(0, 1, 450)) for (remaining in listOf(null, 0, 1, 120)) for (streak in listOf(0, 1, 5)) {
                put("levelDescription($level, $xp, $remaining, $streak)", P.levelDescription(level, xp, remaining, streak))
            }
        }
        listOf(0, 1, 2, 150, 1_234).forEach {
            put("miles($it)", P.miles(it))
            put("milesValue($it)", P.milesValue(it))
        }
        listOf(0, 1, 7).forEach { put("streakValue($it)", P.streakValue(it)) }
        listOf(true, false).forEach { put("lineDays($it)", P.lineDays(it)) }
        for (id in StampId.entries) {
            put("stampName($id)", P.stampName(id))
            put("stampHint($id)", P.stampHint(id))
            put("stampXp($id)", P.stampXp(id))
            put("stampSpoken($id)", P.stampSpoken(id))
            seeds.forEach { put("stampCaption($id, $it)", P.stampCaption(id, it)) }
            listOf(StampState(id), StampState(id, earnedOn = today), StampState(id, progress = 0.4f), StampState(id, progress = 0f))
                .forEach { put("stampDescription($it)", P.stampDescription(it)) }
        }
        StampKind.entries.forEach { put("stampKindLabel($it)", P.stampKindLabel(it)) }
        put("stampDate", P.stampDate(today))
        put("stampEarnedOn", P.stampEarnedOn(today))
        listOf(0f, 0.345f, 1f).forEach { put("stampProgress($it)", P.stampProgress(it)) }
        put("stampsCount", P.stampsCount(3, 23))
        listOf(10, 20).forEach { put("challengeTitle($it)", P.challengeTitle(it)) }
        ChallengeStatus.entries.forEach { put("challengeStatus($it)", P.challengeStatus(it)) }
        listOf(0, 1, 1_234).forEach { put("challengeCaption($it)", P.challengeCaption(it)) }
        val week = today.minusDays(1)
        val days = (0..6).map { ChallengeDay(week.plusDays(it.toLong()), if (it < 2) 40 else null, 50) }
        for (status in ChallengeStatus.entries) for (total in listOf(0, 1, 50, 270, 300)) for (ghost in listOf(0, 1, 50, 135)) {
            val c = WeeklyChallenge(week, 10, 300, 270, total, ghost, days, status)
            put("challengeDescription($c)", P.challengeDescription(c, "Frase."))
            seeds.forEach { put("challengePhrase($status, $total, $ghost, $it)", P.challengePhrase(c, it)) }
        }
        for (count in listOf(null, 0, 1, 40)) for (ghost in listOf(null, 0, 1, 50)) {
            put("ghostDayDescription($count, $ghost)", P.ghostDayDescription(today, count, ghost))
        }
        for (total in listOf(0, 1, 50)) for (ghost in listOf(0, 1, 50)) put("chartDescription($total, $ghost)", P.chartDescription(total, ghost))
        put("pastLabel", P.pastLabel(week))
        listOf(true, false).forEach { put("pastValue($it)", P.pastValue(it)) }
        for (won in listOf(true, false)) for (total in listOf(0, 1, 240)) {
            val r = ChallengeResult(week, 270, total, won)
            put("pastDescription($r)", P.pastDescription(r))
            put("challengeWonTitle($r)", P.challengeWonTitle(r))
            seeds.forEach { put("challengeWonCaption($r, $it)", P.challengeWonCaption(r, it)) }
        }

        // ---------------------------------------------------------------- Imagen compartible
        val S = ShareCopy
        val periods = listOf(
            Triple(CopyPeriod.WEEK, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)),
            Triple(CopyPeriod.WEEK, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)),
            Triple(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            Triple(CopyPeriod.MONTH, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
            Triple(CopyPeriod.YEAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            Triple(CopyPeriod.ALL, LocalDate.of(2026, 3, 3), today),
        )
        for ((period, start, end) in periods) {
            put("periodLine($period, $start)", S.periodLine(period, start, end))
            put("fileName($period, $start)", S.fileName(period, start))
            for (count in listOf(0L, 1L, 4_812L)) for (ms in listOf(0L, 47 * min, 29 * hour)) {
                put("shareText($period, $start, $count, $ms)", S.shareText(period, start, count, ms))
                put("imageDescription($period, $start, $count, $ms)", S.imageDescription(period, start, end, count, ms))
            }
        }
        for (count in listOf(0L, 1L, 4_812L)) for (ms in listOf(0L, 47 * min, 29 * hour)) for (d in listOf(1, 31)) {
            put("lines($count, $ms, $d)", S.lines(count, ms, d))
        }
        put("lines(completo)", S.lines(4_812, 29 * hour, 31, LocalDate.of(2026, 10, 14), 612, 23, "pepito", Category.HUMOR, 0.34))
        put("lines(mañana)", S.lines(10, 60_000, 2, LocalDate.of(2026, 10, 14), 1, 9, "@ana", Category.OTROS, 0.6, includeTotals = false))
        for (ms in listOf(47 * min, 29 * hour, 500 * hour)) {
            put("totalLine($ms)", S.totalLine(ms))
            seeds.forEach { put("equivalenceLines($ms, $it)", S.equivalenceLines(Equivalences.pick(ms, 3, it))) }
        }
        seeds.forEach { put("footer($it)", S.footer(it)) }

        // ---------------------------------------------------------------- Equivalencias, todas las formas
        val fractions = listOf(
            0.0001, 0.0008, 0.004, 0.02, 0.1, 0.2, 0.24, 0.26, 0.33, 0.4, 0.5, 0.6, 0.74, 0.76, 0.9, 0.97, 1.0, 1.04,
            1.3, 1.5, 1.6, 1.97, 2.0, 2.5, 3.2, 12.0, 99.6, 1_234.0,
        )
        for (eq in Equivalences.ALL) {
            put("eq.${eq.id}.label", eq.label)
            put("eq.${eq.id}.basis", eq.basis)
            for (f in fractions) {
                val v = eq.value(f * eq.hoursEach)
                val k = "eq.${eq.id}($f)"
                put("$k.text", v.text)
                put("$k.amountText", v.amountText)
                put("$k.infinitive", v.infinitive)
                put("$k.ticketValue", v.ticketValue)
                put("$k.reachesOne", v.reachesOne)
                (0L..2L).forEach { put("$k.sentence($it)", v.sentence(it)) }
            }
        }
        for (ms in listOf(0L, 15_000L, 47 * min, 3 * hour, 29 * hour, 500 * hour)) {
            seeds.forEach { put("headline($ms, $it)", Equivalences.headline(ms, it)) }
        }
        return out.map { it.replace("\n", "⏎") }.distinct().sorted()
    }

    @Test
    fun `los textos del pasaporte, compartir y equivalencias no cambian (foto)`() {
        val lines = lines()
        val root = listOf(File("src/test"), File("app/src/test")).first { it.exists() }
        val golden = File(root, "resources/i18n/copy-golden-extra.txt")
        val actual = lines.joinToString("\n", postfix = "\n")
        if (System.getenv("REELOMETRO_GOLDEN_UPDATE") == "1" || !golden.exists()) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            return
        }
        val expected = golden.readLines().filter { it.isNotEmpty() }
        val missing = expected - lines.toSet()
        val extra = lines - expected.toSet()
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            File(root.parentFile.parentFile, "build/copy-golden-extra-actual.txt").apply { parentFile.mkdirs() }.writeText(actual)
            fail(
                "Los textos han cambiado (${missing.size} antes, ${extra.size} ahora; todo en build/copy-golden-extra-actual.txt):\n" +
                    missing.take(15).joinToString("\n") { "− $it" } + "\n" + extra.take(15).joinToString("\n") { "+ $it" },
            )
        }
    }
}
