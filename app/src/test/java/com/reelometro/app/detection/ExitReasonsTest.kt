package com.reelometro.app.detection

import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.detection.ExitCategory.APP_CRASH
import com.reelometro.app.detection.ExitCategory.IGNORED
import com.reelometro.app.detection.ExitCategory.RECENTS_CLEAN
import com.reelometro.app.detection.ExitCategory.RESTRICTED
import com.reelometro.app.detection.ExitCategory.SYSTEM_LOW_MEMORY
import com.reelometro.app.detection.ExitCategory.SYSTEM_OTHER
import com.reelometro.app.detection.ExitCategory.USER_STOPPED
import com.reelometro.app.detection.ExitReasons.category
import com.reelometro.app.ui.today.TODAY
import com.reelometro.app.ui.today.TodayMapper
import com.reelometro.app.ui.today.TodayUiState
import com.reelometro.app.ui.today.allTime
import com.reelometro.app.ui.today.dayStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Motivos de cierre de la app (`ApplicationExitInfo`), con lo visto en un Redmi con HyperOS 3 (10 nov 2026). */
class ExitReasonsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val NOW = at(10, 13, 0)

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun rec(reason: Int, description: String?, importance: Int = 400, timestamp: Long = at(10, 12, 0), subreason: Int = 0) =
        ExitRecord(reason, subreason, description, importance, timestamp)

    @After
    fun clearLog() = CalibrationLog.clear()

    // ------------------------------------------------------------------ Los casos del Redmi

    @Test
    fun `SwipeUpClean con importancia 125 es limpiar recientes con el servicio enlazado`() {
        val info = ExitReasons.classify(rec(13, "SwipeUpClean", importance = 125))
        assertEquals(RECENTS_CLEAN, info.category)
        assertTrue(info.serviceBound)
    }

    @Test
    fun `OneKeyClean en cache es limpiar recientes sin enlazar`() {
        val info = ExitReasons.classify(rec(13, "OneKeyClean", importance = 400))
        assertEquals(RECENTS_CLEAN, info.category)
        assertFalse(info.serviceBound)
    }

    @Test
    fun `LOW_MEMORY en cache es falta de memoria sin enlazar`() {
        val info = ExitReasons.classify(rec(3, null, importance = 400))
        assertEquals(SYSTEM_LOW_MEMORY, info.category)
        assertFalse(info.serviceBound)
    }

    @Test
    fun `forzar detencion por borrar datos es del usuario aunque diga clear`() {
        assertEquals(USER_STOPPED, category(10, 21, "stop com.reelometro.app due to clear data"))
        assertEquals(USER_STOPPED, category(10, 21, null))
    }

    @Test
    fun `sin descripcion se clasifica por el motivo`() {
        assertEquals(SYSTEM_OTHER, category(13, 0, null))
        assertEquals(SYSTEM_OTHER, category(13, 0, ""))
        assertEquals(SYSTEM_OTHER, category(ExitReasons.REASON_SIGNALED, 0, null))
        assertEquals(SYSTEM_OTHER, category(ExitReasons.REASON_UNKNOWN, 0, null))
        assertEquals(SYSTEM_OTHER, category(99, 0, null)) // Un motivo de una versión futura.
    }

    // ------------------------------------------------------------------ Resto de categorías

    @Test
    fun `limpiadores de otras marcas por patrones`() {
        listOf("swipe_up_clean", "one key clean", "RemoveTask", "remove task", "clear all", "kill all background", "recents")
            .forEach { assertEquals(it, RECENTS_CLEAN, category(13, 0, it)) }
        assertEquals(RECENTS_CLEAN, category(10, ExitReasons.SUBREASON_REMOVE_TASK, null))
    }

    @Test
    fun `fallos de la app`() {
        listOf(4, 5, 6, 7).forEach { assertEquals(APP_CRASH, category(it, 0, "SwipeUpClean")) }
    }

    @Test
    fun `ahorro de bateria y restricciones`() {
        assertEquals(RESTRICTED, category(ExitReasons.REASON_EXCESSIVE_RESOURCE_USAGE, 0, null))
        assertEquals(RESTRICTED, category(ExitReasons.REASON_FREEZER, 0, null))
        assertEquals(RESTRICTED, category(13, ExitReasons.SUBREASON_CACHED_IDLE_FORCED_APP_STANDBY, null))
        assertEquals(RESTRICTED, category(13, 0, "PowerKeeper"))
        assertEquals(RESTRICTED, category(13, 0, "greezer"))
    }

    @Test
    fun `usuario y paquete`() {
        assertEquals(USER_STOPPED, category(ExitReasons.REASON_USER_REQUESTED, 0, null))
        assertEquals(USER_STOPPED, category(ExitReasons.REASON_USER_STOPPED, 0, null))
        assertEquals(USER_STOPPED, category(ExitReasons.REASON_PACKAGE_STATE_CHANGE, 0, null))
        assertEquals(USER_STOPPED, category(ExitReasons.REASON_PERMISSION_CHANGE, 0, null))
        assertEquals(IGNORED, category(ExitReasons.REASON_PACKAGE_UPDATED, 0, null))
        assertEquals(IGNORED, category(ExitReasons.REASON_EXIT_SELF, 0, null))
    }

    @Test
    fun `memoria por subrazon o descripcion`() {
        assertEquals(SYSTEM_LOW_MEMORY, category(13, ExitReasons.SUBREASON_TOO_MANY_CACHED, null))
        assertEquals(SYSTEM_LOW_MEMORY, category(13, 0, "lmk"))
        assertEquals(SYSTEM_OTHER, category(13, 0, "room"))
    }

    @Test
    fun `enlazado es importancia perceptible o mas`() {
        assertTrue(ExitReasons.classify(rec(13, null, importance = 100)).serviceBound)
        assertTrue(ExitReasons.classify(rec(13, null, importance = 230)).serviceBound)
        assertFalse(ExitReasons.classify(rec(13, null, importance = 300)).serviceBound)
        assertFalse(ExitReasons.classify(rec(13, null, importance = 0)).serviceBound)
    }

    // ------------------------------------------------------------------ El cierre relevante y la marca de visto

    private val redmi = listOf(
        rec(3, null, importance = 400, timestamp = at(10, 9, 0)),
        rec(13, "SwipeUpClean", importance = 125, timestamp = at(10, 10, 0)),
        rec(3, null, importance = 400, timestamp = at(10, 11, 0)),
        rec(1, null, importance = 400, timestamp = at(10, 12, 0)),
    )

    @Test
    fun `el relevante es el ultimo con el servicio enlazado`() {
        val r = ExitReasons.relevant(redmi, seenUpTo = null, now = NOW)!!
        assertEquals(RECENTS_CLEAN, r.category)
        assertEquals(at(10, 10, 0), r.record.timestamp)
    }

    @Test
    fun `sin ninguno enlazado el relevante es el ultimo que importa`() {
        val r = ExitReasons.relevant(redmi, seenUpTo = at(10, 10, 0), now = NOW)!!
        assertEquals(SYSTEM_LOW_MEMORY, r.category)
        assertEquals(at(10, 11, 0), r.record.timestamp)
    }

    @Test
    fun `sin marca un cierre de hace semanas no cuenta`() {
        val viejo = listOf(rec(4, null, importance = 100, timestamp = at(1, 9, 0)), rec(13, "SwipeUpClean", importance = 125, timestamp = at(2, 9, 0)))
        assertNull(ExitReasons.relevant(viejo, seenUpTo = null, now = NOW))
        // Con marca antigua tampoco explica nada el que es anterior a la marca.
        assertNull(ExitReasons.relevant(viejo, seenUpTo = at(3, 0, 0), now = NOW))
    }

    @Test
    fun `un cierre sin enlazar antiguo no explica el fallo`() {
        val noEnlazado = listOf(rec(3, null, importance = 400, timestamp = at(9, 9, 0)))
        assertNull(ExitReasons.relevant(noEnlazado, seenUpTo = at(8, 0, 0), now = NOW))
        assertEquals(SYSTEM_LOW_MEMORY, ExitReasons.relevant(noEnlazado, seenUpTo = at(8, 0, 0), now = at(9, 20, 0))!!.category)
        // El enlazado vale aunque sea más viejo que la ventana corta.
        val enlazado = listOf(rec(13, "SwipeUpClean", importance = 125, timestamp = at(9, 9, 0)))
        assertEquals(RECENTS_CLEAN, ExitReasons.relevant(enlazado, seenUpTo = at(8, 0, 0), now = NOW)!!.category)
    }

    @Test
    fun `lo ya visto no cuenta`() {
        assertNull(ExitReasons.relevant(redmi, seenUpTo = at(10, 12, 0), now = NOW))
        assertNull(ExitReasons.relevant(emptyList(), seenUpTo = null, now = NOW))
        assertNull(ExitReasons.relevant(listOf(rec(1, null)), seenUpTo = null, now = NOW)) // EXIT_SELF no explica nada.
    }

    @Test
    fun `la marca de visto es el cierre mas reciente y nunca retrocede`() {
        assertEquals(at(10, 12, 0), ExitReasons.seenMark(redmi, previous = null))
        assertEquals(at(10, 12, 0), ExitReasons.seenMark(redmi, previous = at(10, 9, 0)))
        assertEquals(at(11, 0, 0), ExitReasons.seenMark(redmi, previous = at(11, 0, 0)))
        assertEquals(5L, ExitReasons.seenMark(emptyList(), previous = 5L))
        assertNull(ExitReasons.seenMark(emptyList(), previous = null))
    }

    @Test
    fun `los ultimos cinco del mas reciente al mas antiguo`() {
        val many = (1..8).map { rec(3, null, timestamp = at(9, it, 0)) }.shuffled()
        val recent = ExitReasons.recent(many)
        assertEquals(5, recent.size)
        assertEquals((8 downTo 4).map { at(9, it, 0) }, recent.map { it.record.timestamp })
    }

    // ------------------------------------------------------------------ Adaptador y descripción

    @Test
    fun `subrazon leida del texto de ApplicationExitInfo`() {
        assertEquals(21, ExitReasons.parseSubreason("ApplicationExitInfo(timestamp=1 reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) status=0"))
        assertEquals(0, ExitReasons.parseSubreason("ApplicationExitInfo(reason=13)"))
        assertEquals(0, ExitReasons.parseSubreason(null))
    }

    @Test
    fun `descripcion limpia y recortada`() {
        assertNull(ExitReasons.safeDescription(null))
        assertNull(ExitReasons.safeDescription("   "))
        assertNull(ExitReasons.safeDescription("ñññ"))
        assertEquals("SwipeUpClean", ExitReasons.safeDescription("SwipeUpClean"))
        assertEquals("stop com.reelometro.app due to clear data", ExitReasons.safeDescription("stop com.reelometro.app due to clear data"))
        assertEquals("a b", ExitReasons.safeDescription("a\n\tb"))
        val long = ExitReasons.safeDescription("x".repeat(200))!!
        assertEquals(ExitReasons.DESCRIPTION_MAX + 1, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `los paquetes de otras apps se tapan y los de Reelometer no`() {
        assertEquals(
            "kill [#1] by [#2] for [#1]",
            ExitReasons.safeDescription("kill com.miui.home by com.android.systemui for com.miui.home"),
        )
        assertEquals("stop com.reelometro.app.qa due to clear data", ExitReasons.safeDescription("stop com.reelometro.app.qa due to clear data"))
        assertEquals("com.reelometro.app.debug:remote", ExitReasons.safeDescription("com.reelometro.app.debug:remote"))
        assertEquals("[#1]:remote", ExitReasons.safeDescription("com.miui.securitycenter:remote"))
        assertEquals("killed by [#1].", ExitReasons.safeDescription("killed by com.miui.securitycenter."))
        assertEquals("[#1]/.launcher.Launcher", ExitReasons.safeDescription("com.miui.home/.launcher.Launcher"))
        // La clase relativa se deja entera, tenga los trozos que tenga (no es otra app); un «/.» sin paquete delante no
        // protege nada, y detrás de unas cifras con punto el paquete también se tapa.
        assertEquals("[#1]/.launcher.recents.RecentsView", ExitReasons.safeDescription("com.miui.home/.launcher.recents.RecentsView"))
        assertEquals("com.reelometro.app/.ui.main.MainActivity", ExitReasons.safeDescription("com.reelometro.app/.ui.main.MainActivity"))
        assertEquals("x/.[#1]", ExitReasons.safeDescription("x/.com.secret.app"))
        assertEquals("x/.[#1]", ExitReasons.safeDescription("x/.com.whatsapp"))
        assertEquals("10.[#1]", ExitReasons.safeDescription("10.com.miui.home"))
        // Detrás de un punto suelto (descripción cortada con puntos suspensivos) también se tapa.
        assertEquals("...[#1]", ExitReasons.safeDescription("...com.miui.securitycenter"))
        assertEquals("kill..[#1]", ExitReasons.safeDescription("kill..com.miui.home"))
        // Empieza igual que el de Reelometer, pero es otro.
        assertEquals("[#1]", ExitReasons.safeDescription("com.reelometro.application"))
        // Sin punto entre letras no es un paquete.
        assertEquals("SwipeUpClean v1.2 adj 900.5", ExitReasons.safeDescription("SwipeUpClean v1.2 adj 900.5"))
        assertEquals("<otra 1>", ExitReasons.safeDescription("com.miui.home") { "<otra $it>" })
    }

    @Test
    fun `el paquete se tapa antes de recortar y el marcador cuenta en el largo`() {
        val cut = ExitReasons.safeDescription("a".repeat(58) + " com.secret.app")!!
        assertFalse(cut, cut.contains("secret"))
        assertEquals(ExitReasons.DESCRIPTION_MAX + 1, cut.length)
        assertTrue(cut.endsWith("…"))
        assertEquals("a".repeat(50) + " [#1]", ExitReasons.safeDescription("a".repeat(50) + " com.secret.app"))
    }

    @Test
    fun `la categoria sale de la descripcion original aunque el registro tape el paquete`() {
        val power = ExitReasons.classify(rec(13, "kill by com.miui.powerkeeper", importance = 125, timestamp = at(10, 14, 32)))
        assertEquals(RESTRICTED, power.category)
        assertEquals("10 nov 14:32 · batería o restricción · enlazado · motivo 13/0 · kill by [otra app 1]", ExitCopy.logLine(power, zone))
        // «recents» solo está en el nombre del paquete: cuenta para la categoría, pero no sale en el registro.
        val recents = ExitReasons.classify(rec(13, "com.android.systemui.recents", importance = 125, timestamp = at(10, 14, 32)))
        assertEquals(RECENTS_CLEAN, recents.category)
        assertEquals("10 nov 14:32 · limpiar recientes · enlazado · motivo 13/0 · [otra app 1]", ExitCopy.logLine(recents, zone))
    }

    @Test
    fun `en el registro la misma otra app lleva el mismo numero en todas las lineas`() {
        val exits = listOf("kill by com.miui.powerkeeper", "kill by com.miui.securitycenter", "kill by com.miui.powerkeeper")
            .map { ExitReasons.classify(rec(13, it, importance = 125, timestamp = at(10, 14, 32))) }
        assertEquals(
            listOf("kill by [otra app 1]", "kill by [otra app 2]", "kill by [otra app 1]"),
            ExitCopy.logLines(exits, zone).map { it.substringAfterLast(" · ") },
        )
        // Una línea suelta sigue empezando en 1.
        assertEquals("kill by [otra app 1]", ExitCopy.logLine(exits[1], zone).substringAfterLast(" · "))
    }

    // ------------------------------------------------------------------ Textos

    @Test
    fun `diagnostico concreto al limpiar recientes y sin culpar al movil si es un fallo`() {
        val recents = ExitCopy.diagnosis(RECENTS_CLEAN)!!
        assertTrue(recents, recents.contains("limpiar las apps recientes"))
        assertTrue(recents, recents.contains("candado"))
        val crash = ExitCopy.diagnosis(APP_CRASH)!!
        assertTrue(crash, crash.contains("fallo de la propia app"))
        assertNull(ExitCopy.diagnosis(IGNORED))
        ExitCategory.entries.filter { it != IGNORED }.forEach { assertTrue(ExitCopy.diagnosis(it)!!.isNotBlank()) }
    }

    @Test
    fun `la linea del registro con fecha, categoria, enlace y descripcion`() {
        val info = ExitReasons.classify(rec(13, "SwipeUpClean", importance = 125, timestamp = at(10, 14, 32)))
        assertEquals("10 nov 14:32 · limpiar recientes · enlazado · motivo 13/0 · SwipeUpClean", ExitCopy.logLine(info, zone))
        val none = ExitReasons.classify(rec(3, null, timestamp = at(10, 9, 5)))
        assertEquals("10 nov 09:05 · falta de memoria · sin enlazar · motivo 3/0 · sin descripción", ExitCopy.logLine(none, zone))
        assertEquals(listOf(ExitCopy.LOG_NONE), ExitCopy.logLines(emptyList(), zone))
    }

    @Test
    fun `Calibracion distingue ninguno, Android sin registro y error al leer`() {
        assertEquals(listOf(ExitCopy.LOG_NONE), ExitCopy.logLines(RecentExits(ExitAccess.READ), zone))
        assertEquals(listOf(ExitCopy.LOG_UNAVAILABLE), ExitCopy.logLines(RecentExits(ExitAccess.UNAVAILABLE), zone))
        assertEquals(listOf(ExitCopy.LOG_FAILED), ExitCopy.logLines(RecentExits(ExitAccess.FAILED), zone))
        assertEquals(3, setOf(ExitCopy.LOG_NONE, ExitCopy.LOG_UNAVAILABLE, ExitCopy.LOG_FAILED).size)
        assertTrue(ExitCopy.LOG_UNAVAILABLE, ExitCopy.LOG_UNAVAILABLE.contains("Android 11"))
        // Leídos: las líneas de siempre, del más reciente al más antiguo.
        val read = ExitReasons.recent(ExitHistory(ExitAccess.READ, redmi))
        assertEquals(ExitAccess.READ, read.access)
        assertEquals(listOf(at(10, 12, 0), at(10, 11, 0), at(10, 10, 0), at(10, 9, 0)), read.exits.map { it.record.timestamp })
        assertEquals(ExitCopy.logLines(read.exits, zone), ExitCopy.logLines(read, zone))
        // Sin leer no hay cierres que enseñar, solo el porqué.
        assertEquals(RecentExits(ExitAccess.FAILED), ExitReasons.recent(ExitHistory(ExitAccess.FAILED)))
        assertEquals(RecentExits(ExitAccess.UNAVAILABLE), ExitReasons.recent(ExitHistory(ExitAccess.UNAVAILABLE)))
    }

    @Test
    fun `Hoy sin senal lleva el motivo detras del aviso`() {
        val settings = AppSettings(onboardingDone = true, disclosureAccepted = true)
        val seed = Variants.daySeed(TODAY)
        val plain = TodayMapper.map(dayStats(), allTime(), settings, serviceOn = true, serviceStalled = true) as TodayUiState.Empty
        assertEquals(TodayCopy.serviceStalled(seed), plain.top.notice!!.text)
        val withReason = TodayMapper.map(
            dayStats(), allTime(), settings, serviceOn = true, serviceStalled = true, stallReason = RECENTS_CLEAN,
        ) as TodayUiState.Empty
        assertEquals("${TodayCopy.serviceStalled(seed)} ${ExitCopy.diagnosis(RECENTS_CLEAN)}", withReason.top.notice!!.text)
        // Un cierre que no explica nada deja el aviso de siempre.
        val ignored = TodayMapper.map(
            dayStats(), allTime(), settings, serviceOn = true, serviceStalled = true, stallReason = IGNORED,
        ) as TodayUiState.Empty
        assertEquals(TodayCopy.serviceStalled(seed), ignored.top.notice!!.text)
        // Fallo de la app: solo esa frase, sin las variantes que culpan al sistema.
        val crash = TodayMapper.map(
            dayStats(), allTime(), settings, serviceOn = true, serviceStalled = true, stallReason = APP_CRASH,
        ) as TodayUiState.Empty
        assertEquals(ExitCopy.diagnosis(APP_CRASH), crash.top.notice!!.text)
    }

    @Test
    fun `el aviso del servicio lleva el motivo si lo hay`() {
        assertEquals(NotificationCopy.serviceStalled, NotificationCopy.serviceStalled(null))
        val text = NotificationCopy.serviceStalled(ExitCopy.diagnosis(SYSTEM_LOW_MEMORY)).text
        assertTrue(text, text.startsWith(ExitCopy.diagnosis(SYSTEM_LOW_MEMORY)!!))
    }

    @Test
    fun `los cierres entran en el registro compartido sin nombres`() {
        val text = CalibrationLog.exportText(
            exits = listOf(ExitCopy.LOG_TITLE, "10 nov 14:32 · limpiar recientes · enlazado · motivo 13/0 · Lucia"),
            privateNames = listOf("Lucia"),
        )
        assertTrue(text, text.contains(ExitCopy.LOG_TITLE))
        assertTrue(text, text.contains("motivo 13/0 · [#1]"))
        assertFalse(text, text.contains("Lucia"))
    }
}
