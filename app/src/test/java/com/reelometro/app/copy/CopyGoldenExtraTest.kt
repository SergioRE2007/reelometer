package com.reelometro.app.copy

import android.view.accessibility.AccessibilityEvent
import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.ConnectionKind
import com.reelometro.app.detection.DisconnectionKind
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.ExitReasons
import com.reelometro.app.detection.ExitRecord
import com.reelometro.app.detection.ServiceConnection
import com.reelometro.app.detection.ServiceDisconnection
import com.reelometro.app.detection.ServiceFailures
import com.reelometro.app.detection.ServiceJournalState
import com.reelometro.app.detection.ServiceMetrics
import com.reelometro.app.detection.ServiceStatus
import com.reelometro.app.detection.SystemState
import com.reelometro.app.game.FlapDay
import com.reelometro.app.game.FlapFakes
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.stats.Equivalences
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Segunda foto de los textos en español: lo que no cubre la de [CopyCatalogTest] (pestaña Flap,
 * imagen compartible, todas las formas de las equivalencias y las líneas de Calibración:
 * RENDIMIENTO y CIERRES, con el aviso de servicio parado según el motivo del cierre). Se sacó del
 * código de antes de pasar los textos a recursos y vive en `src/test/resources/i18n/copy-golden-extra.txt`.
 * Usa la API pública de `FlapCopy`, `ShareCopy`, `Equivalences`, `CalibrationMetricsCopy` y
 * `ExitCopy` (más `TodayCopy.serviceStalled` y `NotificationCopy.serviceStalled`); las líneas de
 * Flap llevan delante «FlapCopy.» para no mezclarse con las demás.
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

    /** El bloque «Estado del sistema»: sin nada que contar (ni Android da los datos) y con todo. */
    private fun systemSamples(at: Long): List<Pair<String, SystemStateCopy.Input>> = listOf(
        "vacío" to SystemStateCopy.Input(
            system = SystemState(),
            journal = ServiceJournalState(),
            failures = ServiceFailures.Snapshot(),
            memory = ServiceStatus.MemoryWarnings(),
            lastVideoAt = null,
        ),
        "lleno" to SystemStateCopy.Input(
            system = SystemState(
                standbyBucket = 40,
                batteryUnrestricted = false,
                backgroundRestricted = true,
                powerSave = false,
                hibernationExempt = true,
                notificationsAllowed = true,
                processAgeMs = 3 * hour + 12 * min,
            ),
            journal = ServiceJournalState(
                connections = listOf(
                    ServiceConnection(at, ConnectionKind.COLD),
                    ServiceConnection(at - 5 * hour, ConnectionKind.WARM),
                    ServiceConnection(at - 30 * hour, ConnectionKind.BOOT),
                ),
                disconnections = listOf(
                    ServiceDisconnection(at - 6 * hour, DisconnectionKind.TURNED_OFF),
                    ServiceDisconnection(at - 40 * hour, DisconnectionKind.UNBOUND),
                ),
            ),
            failures = ServiceFailures.Snapshot(3, at - hour, "lectura de pantalla", "IllegalStateException", "RecipientDetector.kt:412"),
            memory = ServiceStatus.MemoryWarnings(2, 40, at - 2 * hour),
            lastVideoAt = at - 10 * min,
        ),
    )

    /** Fotos fijas de [ServiceMetrics]: vacía, recién arrancada (sin ritmo) y tras un buen rato con las tres apps. */
    private fun metricsSamples(): List<Pair<String, ServiceMetrics.Snapshot>> {
        val empty = ServiceMetrics(clockMs = { 0 }).snapshot()
        var now = 0L
        val busy = ServiceMetrics(clockMs = { now })
        repeat(1_234) { busy.onEvent(Platform.TIKTOK, AccessibilityEvent.TYPE_VIEW_SCROLLED, 250_000) }
        repeat(56) { busy.onEvent(Platform.TIKTOK, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 2_500_000) }
        repeat(7) { busy.onEvent(Platform.INSTAGRAM, AccessibilityEvent.TYPE_VIEW_CLICKED, 21_000_000, capturing = true) }
        repeat(2) { busy.onEvent(Platform.INSTAGRAM, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, 40_000, forQuickSend = true) }
        busy.onEvent(Platform.INSTAGRAM, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, 40_000)
        busy.onEvent(Platform.YOUTUBE, AccessibilityEvent.TYPE_VIEW_SCROLLED, 900_000)
        busy.onTreeRead(Platform.YOUTUBE, 300, 14_000_000, found = false)
        busy.onTreeRead(Platform.YOUTUBE, 280, 9_500_000, found = false)
        busy.onTreeRead(Platform.TIKTOK, 141, 3_200_000, found = true)
        repeat(4) { busy.onTreeUnreadable(Platform.TIKTOK) }
        repeat(45) { busy.onPresenceCheck(1_700_000) }
        busy.quickStarted()
        busy.onChildFetch(321)
        busy.quickEnded(45_000_000)
        busy.onChildFetch(12_024)
        repeat(67) { busy.onParentFetch() }
        repeat(8) { busy.onFinderQuery() }
        busy.start()
        now = 3_000
        val fresh = busy.snapshot()
        // Un evento cada 30 s hasta los 7 min y medio: todo ese rato cuenta como actividad.
        while (now < 7 * 60_000 + 30_000) {
            now += 30_000
            busy.onEvent(Platform.YOUTUBE, AccessibilityEvent.TYPE_VIEW_SCROLLED, 900_000)
        }
        return listOf("vacía" to empty, "reciente" to fresh, "7 min" to busy.snapshot())
    }

    private fun lines(): List<String> {
        val out = mutableListOf<String>()
        fun put(source: String, text: Any?) {
            out += "$source → $text"
        }
        constants(FlapCopy::class.java).forEach { (n, v) -> put(n, v) }
        constants(ShareCopy::class.java).forEach { (n, v) -> put(n, v) }

        // ---------------------------------------------------------------- Rendimiento del servicio (Calibración)
        constants(CalibrationMetricsCopy::class.java).forEach { (n, v) -> put(n, v) }
        for ((name, snapshot) in metricsSamples()) {
            put("CalibrationMetricsCopy.summary($name)", CalibrationMetricsCopy.summary(snapshot))
            CalibrationMetricsCopy.exportLines(snapshot).forEachIndexed { i, line -> put("CalibrationMetricsCopy.exportLines($name)[$i]", line) }
        }
        listOf(1, 2, 1_234).forEach { put("CalibrationMetricsCopy.redacted($it)", CalibrationMetricsCopy.redacted(it)) }

        // ---------------------------------------------------------------- Motivo del cierre (diagnóstico y Calibración)
        constants(ExitCopy::class.java).forEach { (n, v) -> put(n, v) }
        val exitAt = LocalDate.of(2026, 11, 10).atTime(14, 32).toInstant(ZoneOffset.UTC).toEpochMilli()
        for (category in ExitCategory.entries) {
            put("ExitCopy.diagnosis($category)", ExitCopy.diagnosis(category))
            put("ExitCopy.categoryLabel($category)", ExitCopy.categoryLabel(category))
            put("TodayCopy.serviceStalled(0, $category)", TodayCopy.serviceStalled(0L, category))
            put("NotificationCopy.serviceStalled($category)", NotificationCopy.serviceStalled(ExitCopy.diagnosis(category)).text)
        }
        listOf(
            ExitRecord(13, 0, "SwipeUpClean", 125, exitAt),
            ExitRecord(10, 21, "stop com.reelometro.app due to clear data", 400, exitAt),
            ExitRecord(3, 0, null, 400, exitAt),
        ).forEach { r -> put("ExitCopy.logLine(${r.reason}/${r.subreason})", ExitCopy.logLine(ExitReasons.classify(r), ZoneOffset.UTC)) }
        // El paquete de otra app se tapa en la descripción (el de Reelometer no) y la categoría sale del original.
        val otherApp = ExitRecord(13, 0, "kill by com.miui.powerkeeper for com.reelometro.app", 125, exitAt)
        put("ExitCopy.logLine(otra app)", ExitCopy.logLine(ExitReasons.classify(otherApp), ZoneOffset.UTC))
        listOf(1, 2).forEach { put("ExitCopy.otherApp($it)", ExitCopy.otherApp(it)) }
        // Con la memoria del proceso al morir (kB): va detrás, en MB.
        val withMemory = ExitRecord(3, 0, null, 125, exitAt, pssKb = 86_500, rssKb = 131_072)
        put("ExitCopy.logLine(con memoria)", ExitCopy.logLine(ExitReasons.classify(withMemory), ZoneOffset.UTC))

        // ---------------------------------------------------------------- El sistema ha apagado el servicio
        put("OnboardingCopy.LOST_TITLE", OnboardingCopy.LOST_TITLE)
        put("OnboardingCopy.lostBody(null)", OnboardingCopy.lostBody(null))
        put("OnboardingCopy.lostBody(USER_STOPPED)", OnboardingCopy.lostBody(ExitCategory.USER_STOPPED))
        (0L..2L).forEach { put("TodayCopy.serviceLost($it)", TodayCopy.serviceLost(it)) }
        put("TodayCopy.serviceLost(0, RESTRICTED)", TodayCopy.serviceLost(0L, ExitCategory.RESTRICTED))

        // ---------------------------------------------------------------- Estado del sistema (Calibración)
        for ((name, input) in systemSamples(exitAt)) {
            SystemStateCopy.lines(input, ZoneOffset.UTC).forEachIndexed { i, line -> put("SystemStateCopy.lines($name)[$i]", line) }
        }

        // ---------------------------------------------------------------- Flap
        val F = FlapCopy
        put("FlapCopy.kicker", F.kicker(today))
        for (mood in FlapMood.entries) {
            put("FlapCopy.moodLabel($mood)", F.moodLabel(mood))
            put("FlapCopy.moodSpoken($mood)", F.moodSpoken(mood))
            seeds.forEach { put("FlapCopy.moodPhrase($mood, $it)", F.moodPhrase(mood, it)) }
            listOf(0, 1, 32, 100).forEach { put("FlapCopy.description($mood, $it)", F.description(mood, it)) }
            listOf(1, 2, 9, 1_234).forEach { put("FlapCopy.toWorse($mood, $it)", F.toWorse(mood, it)) }
        }
        seeds.forEach {
            put("FlapCopy.freshPhrase($it)", F.freshPhrase(it))
            put("FlapCopy.recoveredPhrase($it)", F.recoveredPhrase(it))
        }
        listOf(0, 1, 62, 100).forEach {
            put("FlapCopy.lifeValue($it)", F.lifeValue(it))
            put("FlapCopy.lifeDescription($it)", F.lifeDescription(it))
        }
        for (goal in listOf<Int?>(null, 1, 40, 1_500)) {
            val limit = FlapRules.limitFor(goal)
            put("FlapCopy.howLimit($limit, ${goal != null})", F.howLimit(limit, goal != null))
            put("FlapCopy.noGoal($limit)", F.noGoal(limit))
            for (count in listOf(0, 1, 2, limit / 2, limit, limit + 1, limit * 2 - 1, limit * 2, 12_345).distinct()) {
                put("FlapCopy.todayLine($count, $limit)", F.todayLine(count, limit))
                put("FlapCopy.nextLine($count, $limit)", F.nextLine(FlapFakes.state(today, goal, count)))
            }
        }
        FlapFakes.state(today).week.forEach { put("FlapCopy.dayDescription(${it.date}, ${it.count})", F.dayDescription(it)) }
        put("FlapCopy.dayDescription(sin registro)", F.dayDescription(FlapDay(today.minusDays(1), null, null)))
        listOf(0, 1, 2, 7, 1_234).forEach { put("FlapCopy.streak($it)", F.streak(it)) }

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
    fun `los textos de Flap, compartir, equivalencias y Calibración no cambian (foto)`() {
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
