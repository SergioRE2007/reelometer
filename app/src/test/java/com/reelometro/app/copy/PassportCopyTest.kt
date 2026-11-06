package com.reelometro.app.copy

import com.reelometro.app.copy.CopyRules.plain
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.LocalDate

/** Textos del Pasaporte: reglas de estilo (CopyRules), variantes deterministas y casos límite. */
class PassportCopyTest {

    private val seeds = -3L..80L
    private val today = LocalDate.of(2026, 11, 4)
    private val numbers = listOf(0, 1, 2, 7, 12, 999, 1_000, 1_234, 99_999, 1_234_567)

    /** Palabras de culpa que el modo Pasaporte no usa nunca (además de las de CopyRules). */
    private val guilt = listOf("culpa", "fracas", "castigo", "vergüenza", "fallaste", "has fallado", "perdedor")

    private fun check(text: String, where: String, checkNumbers: Boolean = true) {
        val v = CopyRules.violations(text, checkNumbers = checkNumbers)
        assertTrue("«$text» ($where): $v", v.isEmpty())
        val lower = text.lowercase(com.reelometro.app.stats.Format.locale)
        guilt.forEach { assertFalse("«$text» ($where) suena a culpa: «$it»", it in lower) }
    }

    private fun flap(
        mood: FlapMood = FlapMood.NORMAL,
        previous: FlapMood? = null,
        under: Int = 5,
        tracked: Int = 7,
        reference: Int? = 150,
        isGoal: Boolean = true,
    ) = FlapState(mood, previous, under, tracked, reference, isGoal)

    private fun challenge(
        status: ChallengeStatus = ChallengeStatus.EN_CURSO,
        total: Int = 405,
        ghostSoFar: Int = 490,
        target: Int = 1_098,
        reduction: Int = 10,
    ): WeeklyChallenge {
        val monday = LocalDate.of(2026, 11, 2)
        return WeeklyChallenge(
            weekStart = monday,
            reductionPercent = reduction,
            ghostTotal = 1_225,
            target = target,
            total = total,
            ghostSoFar = ghostSoFar,
            days = (0 until 7).map { ChallengeDay(monday.plusDays(it.toLong()), count = if (it < 3) 135 else null, ghost = 175) },
            status = status,
        )
    }

    // ------------------------------------------------------------------ todo pasa las reglas

    @Test
    fun `todas las constantes cumplen las reglas`() {
        // Constantes (`const val`) y propiedades con getter que leen los recursos.
        val fields = PassportCopy::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.name to it.get(null) as String }
        val getters = PassportCopy::class.java.declaredMethods
            .filter {
                it.name.startsWith("get") && it.name.length > 3 && it.parameterCount == 0 &&
                    it.returnType == String::class.java && Modifier.isPublic(it.modifiers)
            }
            .map { it.name.removePrefix("get") to it.invoke(PassportCopy) as String }
        val constants = (fields + getters).distinctBy { it.first }
        assertTrue("se esperaban muchas constantes, hay ${constants.size}", constants.size >= 30)
        constants.forEach { (name, value) -> check(value, name) }
    }

    @Test
    fun `Flap, en todos los estados y situaciones, cumple las reglas`() {
        for (mood in FlapMood.entries) {
            check(PassportCopy.moodLabel(mood), "moodLabel $mood")
            for (seed in seeds) check(PassportCopy.moodPhrase(mood, seed), "moodPhrase $mood $seed")
        }
        for (seed in seeds) {
            check(PassportCopy.warmingPhrase(seed), "warming $seed")
            check(PassportCopy.recoveredPhrase(seed), "recovered $seed")
        }
        for (mood in FlapMood.entries) for (previous in FlapMood.entries + listOf(null))
            for (tracked in 0..7) for (under in listOf(0, 1, tracked)) for (isGoal in listOf(true, false))
                for (reference in listOf(null, 0, 1, 150, 12_345)) {
                    val f = flap(mood, previous, under, tracked, reference, isGoal)
                    val phrase = PassportCopy.flapPhrase(f, tracked.toLong())
                    check(phrase, "flapPhrase $f")
                    check(PassportCopy.record(f), "record $f")
                    PassportCopy.referenceLine(f)?.let { check(it, "referenceLine $f") }
                    check(PassportCopy.flapDescription(f, phrase), "flapDescription $f")
                }
        numbers.forEach { check(PassportCopy.onBoard(it), "onBoard $it") }
        check(PassportCopy.onBoard(-4), "onBoard negativo")
    }

    @Test
    fun `niveles y millas cumplen las reglas`() {
        for (level in TravelerLevel.entries) {
            check(PassportCopy.levelName(level), "levelName")
            check(PassportCopy.levelCode(level), "levelCode")
            check(PassportCopy.levelTagline(level), "levelTagline")
            check(PassportCopy.levelIndex(level), "levelIndex")
            check(PassportCopy.levelSpoken(level), "levelSpoken")
            check(PassportCopy.ticketShareText(level), "ticketShareText")
            check(PassportCopy.ticketFileName(level), "ticketFileName")
            check(PassportCopy.levelUpNotificationTitle(level), "notificationTitle")
            for (seed in seeds) {
                check(PassportCopy.levelCaption(level, seed), "levelCaption $level $seed")
            }
            for (n in numbers + listOf(-5)) {
                level.next?.let { check(PassportCopy.milesToNext(n, it), "milesToNext $n") }
                check(PassportCopy.levelDescription(level, n, if (level.next != null) n else null, n), "levelDescription $level $n")
            }
        }
        for (n in numbers + listOf(-5)) {
            check(PassportCopy.miles(n), "miles $n")
            check(PassportCopy.milesValue(n), "milesValue $n")
            check(PassportCopy.streakValue(n), "streakValue $n")
        }
        check(PassportCopy.lineDays(true), "lineDays")
        check(PassportCopy.lineDays(false), "lineDays")
        for (seed in seeds) {
            check(PassportCopy.levelUpNotificationText(seed), "notificationText")
            check(PassportCopy.ticketFooter(seed), "ticketFooter")
        }
    }

    @Test
    fun `sellos cumplen las reglas en todos los estados`() {
        for (id in StampId.entries) {
            check(PassportCopy.stampName(id), "stampName $id")
            check(PassportCopy.stampHint(id), "stampHint $id")
            check(PassportCopy.stampXp(id), "stampXp $id")
            check(PassportCopy.stampSpoken(id), "stampSpoken $id")
            check(PassportCopy.stampDescription(StampState(id, earnedOn = today)), "descripción conseguido $id")
            check(PassportCopy.stampDescription(StampState(id)), "descripción pendiente $id")
            for (p in listOf(-1f, 0f, 0.004f, 0.5f, 0.999f, 1f, 3f)) {
                check(PassportCopy.stampDescription(StampState(id, progress = p)), "descripción progreso $id $p")
                check(PassportCopy.stampProgress(p), "stampProgress $p")
            }
            for (seed in seeds) check(PassportCopy.stampCaption(id, seed), "stampCaption $id $seed")
        }
        StampKind.entries.forEach { check(PassportCopy.stampKindLabel(it), "kindLabel $it") }
        check(PassportCopy.stampDate(today), "stampDate")
        check(PassportCopy.stampEarnedOn(LocalDate.of(2027, 1, 31)), "stampEarnedOn")
        for (a in numbers) check(PassportCopy.stampsCount(a, 23), "stampsCount $a")
    }

    @Test
    fun `el reto cumple las reglas por delante, por detrás, empatado, pasado, cumplido y no`() {
        for (status in ChallengeStatus.entries) {
            check(PassportCopy.challengeStatus(status), "status $status")
            for (total in numbers) for (ghost in listOf(0, 1, total, total + 1, 12_345)) for (target in listOf(0, 1, 1_098, 99_999))
                for (seed in seeds step 7) {
                    val c = challenge(status, total, ghost, target)
                    val phrase = PassportCopy.challengePhrase(c, seed)
                    check(phrase, "challengePhrase $status $total $ghost $target $seed")
                    check(PassportCopy.challengeDescription(c, phrase), "challengeDescription")
                }
        }
        for (r in listOf(0, 10, 15, 20)) check(PassportCopy.challengeTitle(r), "challengeTitle $r")
        for (n in numbers) {
            check(PassportCopy.challengeCaption(n), "challengeCaption $n")
            check(PassportCopy.chartDescription(n, n + 1), "chartDescription $n")
            check(PassportCopy.ghostDayDescription(today, n, n), "ghostDay $n")
        }
        check(PassportCopy.ghostDayDescription(today, null, null), "ghostDay futuro sin fantasma")
        for (seed in seeds) check(PassportCopy.warmupText(seed), "warmup $seed")
        for (won in listOf(true, false)) for (n in numbers) {
            val r = ChallengeResult(LocalDate.of(2026, 10, 26), target = n, total = n + 1, won = won)
            check(PassportCopy.pastLabel(r.weekStart), "pastLabel")
            check(PassportCopy.pastValue(won), "pastValue")
            check(PassportCopy.pastDescription(r), "pastDescription")
            check(PassportCopy.challengeWonTitle(r), "challengeWonTitle")
            for (seed in seeds step 5) check(PassportCopy.challengeWonCaption(r, seed), "challengeWonCaption")
        }
    }

    // ------------------------------------------------------------------ expectativas concretas

    @Test
    fun `el registro de Flap concuerda y dice la referencia`() {
        assertEquals("5 DE 7 DÍAS BAJO TU OBJETIVO", plain(PassportCopy.record(flap())))
        assertEquals("5 DE 7 DÍAS BAJO TU MEDIA", plain(PassportCopy.record(flap(isGoal = false, reference = 140))))
        assertEquals("0 DE 1 DÍA BAJO TU OBJETIVO", plain(PassportCopy.record(flap(under = 0, tracked = 1))))
        assertEquals(PassportCopy.WARMING_UP, PassportCopy.record(flap(reference = null)))
        assertEquals("TU OBJETIVO: 150 AL DÍA", plain(PassportCopy.referenceLine(flap())!!))
        assertEquals("TU MEDIA: 1.234 AL DÍA", plain(PassportCopy.referenceLine(flap(reference = 1_234, isGoal = false))!!))
        assertNull(PassportCopy.referenceLine(flap(reference = null)))
        // Nunca más días bajo la referencia que días contados.
        assertEquals("3 DE 3 DÍAS BAJO TU OBJETIVO", plain(PassportCopy.record(flap(under = 9, tracked = 3))))
    }

    @Test
    fun `al volver de zombi la frase lo celebra y calentando no juzga`() {
        val recovered = seeds.map { PassportCopy.flapPhrase(flap(FlapMood.NORMAL, FlapMood.ZOMBI), it) }.toSet()
        assertEquals(seeds.map { PassportCopy.recoveredPhrase(it) }.toSet(), recovered)
        val warming = seeds.map { PassportCopy.flapPhrase(flap(reference = null), it) }.toSet()
        assertEquals(seeds.map { PassportCopy.warmingPhrase(it) }.toSet(), warming)
        val zombi = seeds.map { PassportCopy.flapPhrase(flap(FlapMood.ZOMBI, FlapMood.NORMAL), it) }.toSet()
        assertEquals(seeds.map { PassportCopy.moodPhrase(FlapMood.ZOMBI, it) }.toSet(), zombi)
    }

    @Test
    fun `las variantes son deterministas, de 3 a 6, y no se repiten dos días seguidos`() {
        val families: Map<String, (Long) -> String> = buildMap {
            FlapMood.entries.forEach { m -> put("mood $m") { s: Long -> PassportCopy.moodPhrase(m, s) } }
            put("warming") { s: Long -> PassportCopy.warmingPhrase(s) }
            put("recovered") { s: Long -> PassportCopy.recoveredPhrase(s) }
            put("warmup") { s: Long -> PassportCopy.warmupText(s) }
            put("footer") { s: Long -> PassportCopy.ticketFooter(s) }
            put("level") { s: Long -> PassportCopy.levelCaption(TravelerLevel.PLATA, s) }
            put("ironic") { s: Long -> PassportCopy.stampCaption(StampId.MADRUGADOR, s) }
            put("stamp") { s: Long -> PassportCopy.stampCaption(StampId.RACHA_7, s) }
            put("ahead") { s: Long -> PassportCopy.challengePhrase(challenge(total = 300, ghostSoFar = 400), s) }
            put("behind") { s: Long -> PassportCopy.challengePhrase(challenge(total = 450, ghostSoFar = 400), s) }
            put("won") { s: Long -> PassportCopy.challengePhrase(challenge(ChallengeStatus.CUMPLIDO), s) }
            put("lost") { s: Long -> PassportCopy.challengePhrase(challenge(ChallengeStatus.FALLADO), s) }
        }
        families.forEach { (name, f) ->
            val all = seeds.map(f)
            assertEquals("$name: misma semilla, misma frase", all, seeds.map(f))
            assertTrue("$name: ${all.toSet().size} variantes", all.toSet().size in 3..6)
            all.zipWithNext().forEach { (a, b) -> assertNotEquals("$name: repite dos días seguidos «$a»", a, b) }
        }
    }

    @Test
    fun `el reto distingue por delante, por detrás y pasado sin culpa`() {
        val ahead = seeds.map { plain(PassportCopy.challengePhrase(challenge(total = 300, ghostSoFar = 385), it)) }.toSet()
        assertTrue(ahead.toString(), ahead.all { "85 vídeos" in it })
        val one = seeds.map { plain(PassportCopy.challengePhrase(challenge(total = 300, ghostSoFar = 301), it)) }.toSet()
        assertTrue(one.toString(), one.all { "1 vídeo" in it && "1 vídeos" !in it })
        val behind = seeds.map { plain(PassportCopy.challengePhrase(challenge(total = 440, ghostSoFar = 400), it)) }.toSet()
        assertTrue(behind.toString(), behind.all { "40 vídeos" in it })
        assertTrue(ahead.intersect(behind).isEmpty())
        val over = seeds.map { PassportCopy.challengePhrase(challenge(total = 1_200, ghostSoFar = 1_000), it) }.toSet()
        assertTrue(over.intersect(behind).isEmpty())
        val won = seeds.map { plain(PassportCopy.challengePhrase(challenge(ChallengeStatus.CUMPLIDO, total = 980), it)) }.toSet()
        assertTrue(won.toString(), won.all { "980" in it && "1.098" in it })
    }

    @Test
    fun `títulos del reto y millas en singular y plural`() {
        assertEquals("ESTA SEMANA, MENOS QUE LA ANTERIOR", PassportCopy.challengeTitle(0))
        assertEquals("ESTA SEMANA, UN 10 % MENOS", plain(PassportCopy.challengeTitle(10)))
        assertEquals("ESTA SEMANA, UN 20 % MENOS", plain(PassportCopy.challengeTitle(20)))
        assertEquals("1 MILLA", plain(PassportCopy.miles(1)))
        assertEquals("1.234 MILLAS", plain(PassportCopy.miles(1_234)))
        assertEquals("FALTA 1 MILLA PARA ORO", plain(PassportCopy.milesToNext(1, TravelerLevel.ORO)))
        assertEquals("FALTAN 380 MILLAS PARA ORO", plain(PassportCopy.milesToNext(380, TravelerLevel.ORO)))
        assertEquals("1 DÍA A BORDO", plain(PassportCopy.onBoard(1)))
        assertEquals("63 DÍAS A BORDO", plain(PassportCopy.onBoard(63)))
        assertEquals("NIVEL 3 DE 5", PassportCopy.levelIndex(TravelerLevel.PLATA))
    }

    @Test
    fun `los nombres de los sellos caben y son únicos`() {
        val names = StampId.entries.map { PassportCopy.stampName(it) }
        assertEquals(names.size, names.toSet().size)
        names.forEach { name ->
            assertEquals("en mayúsculas: $name", name.uppercase(com.reelometro.app.stats.Format.locale), name)
            assertTrue("demasiado largo para un sello: $name", name.length <= 18)
            name.split(' ', ' ').forEach { word -> assertTrue("palabra larga «$word» en $name", word.length <= 10) }
        }
        assertEquals("SEMANA −20 %", plain(PassportCopy.stampName(StampId.SEMANA_MENOS_20)))
        assertEquals("1.000 VÍDEOS", PassportCopy.stampName(StampId.MIL_VIDEOS))
    }

    @Test
    fun `los irónicos dicen que no suman y los demás dicen cuánto`() {
        StampId.entries.forEach { id ->
            val xp = plain(PassportCopy.stampXp(id))
            if (id.xp <= 0) assertEquals("SIN MILLAS", xp) else assertEquals("+${id.xp} MILLAS", xp.replace(".", ""))
        }
        assertEquals("SIN MILLAS", PassportCopy.stampKindLabel(StampKind.IRONICO))
        val ironic = seeds.map { PassportCopy.stampCaption(StampId.MARATON, it) }.toSet()
        val normal = seeds.map { PassportCopy.stampCaption(StampId.RACHA_7, it) }.toSet()
        assertTrue(ironic.intersect(normal).isEmpty())
        assertTrue(normal.toString(), normal.all { "60" in it && "millas" in it })
    }

    @Test
    fun `los códigos de nivel caben en el panel split-flap`() {
        val codes = TravelerLevel.entries.map { PassportCopy.levelCode(it) }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { code ->
            assertEquals(3, code.length)
            assertTrue(code, code.all { it in "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ" })
        }
    }

    @Test
    fun `descripciones de TalkBack completas`() {
        val d = PassportCopy.flapDescription(flap(FlapMood.EN_FORMA, under = 7), "Flap va sobrado esta semana.")
        assertTrue(d, d.startsWith("Flap"))
        assertTrue(d, "en forma" in d && "7 de 7 días" in plain(d) && d.endsWith("Flap va sobrado esta semana."))
        val level = plain(PassportCopy.levelDescription(TravelerLevel.PLATA, 520, 380, 4))
        assertEquals("Tarjeta de embarque: nivel Plata, 3 de 5. 520 millas. Faltan 380 millas para Oro. Racha actual: 4 días.", level)
        val top = plain(PassportCopy.levelDescription(TravelerLevel.PLATINO, 2_000, null, 1))
        assertTrue(top, "Nivel máximo" in top && "1 día." in top)
        val stamp = plain(PassportCopy.stampDescription(StampState(StampId.RACHA_7, earnedOn = LocalDate.of(2026, 10, 12))))
        assertEquals("Sello Racha de 7, conseguido el 12 de octubre de 2026. Da 60 millas.", stamp)
        val pending = plain(PassportCopy.stampDescription(StampState(StampId.RACHA_14, progress = 0.5f)))
        assertTrue(pending, pending.startsWith("Sello Racha de 14, pendiente. 50 % hecho. Pista: "))
    }
}
