package com.reelometro.app.copy

import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Expectativas concretas de cada pantalla (cifras, concordancias, casos límite). */
class ScreenCopyTest {

    private val min = 60_000L
    private val hour = 3_600_000L
    private val seeds = 0L until 60L
    private val today = LocalDate.of(2026, 11, 3)

    private fun all(f: (Long) -> String): Set<String> = seeds.map { plain(f(it)) }.toSet()

    // ------------------------------------------------------------------ Hoy

    @Test
    fun `la frase de referencia sale tal cual`() {
        val variants = all { TodayCopy.headline(212, 47 * min, it) }
        assertTrue(variants.toString(), "Hoy llevas 212. Eso son 47 minutos que nadie te va a devolver." in variants)
    }

    @Test
    fun `con cero vídeos, ganas tú`() {
        val variants = all { TodayCopy.headline(0, 0, it) }
        assertTrue("De momento, ganas tú." in variants)
        assertTrue(variants.none { it.contains("0 vídeo") })
        val firstDay = all { TodayCopy.headline(0, 0, it, firstDay = true) }
        assertTrue(firstDay.intersect(variants).isEmpty())
    }

    @Test
    fun `el objetivo cambia la frase`() {
        val over = all { TodayCopy.headline(212, 47 * min, it, goal = 150) }
        assertTrue(over.any { "62 vídeos por encima" in it })
        assertTrue(over.any { "Objetivo: 150. Realidad: 212." == it })
        val near = all { TodayCopy.headline(149, 40 * min, it, goal = 150) }
        assertTrue(near.toString(), near.any { "Te queda 1 vídeo para el objetivo" in it })
        assertTrue(near.none { "quedan 1 " in it })
        val at = all { TodayCopy.headline(150, 40 * min, it, goal = 150) }
        assertTrue(at.any { "150 de 150" in it })
    }

    @Test
    fun `concordancia con duraciones en singular`() {
        val oneMinute = all { TodayCopy.headline(50, 60_000, it) }
        assertTrue(oneMinute.toString(), oneMinute.any { it == "Eso es un minuto que nadie te va a devolver." })
        assertTrue(oneMinute.none { "son un minuto" in it })
        val oneHour = all { TodayCopy.headline(212, hour, it) }
        assertTrue(oneHour.any { it == "Hoy llevas 212. Eso es una hora que nadie te va a devolver." })
        assertTrue(all { DayCopy.quote(212, hour, it) }.none { "fueron una hora" in it })
        assertTrue(all { TodayCopy.headline(212, 100 * min, it) }.any { it == "Hoy llevas 212. Eso es una hora y 40 minutos que nadie te va a devolver." })
        assertTrue(all { TodayCopy.headline(212, 100 * min, it) }.any { it.startsWith("Una hora y 40 minutos.") })
    }

    @Test
    fun `primer día sin historial`() {
        val first = all { TodayCopy.headline(34, 9 * min, it, firstDay = true) }
        assertTrue(first.any { "Primer día contado: 34 vídeos y 9 minutos." in it })
        val justStarted = all { TodayCopy.headline(1, 0, it, firstDay = true) }
        assertTrue(justStarted.none { "segundos" in it })
    }

    @Test
    fun `cifras grandes con punto de millar`() {
        val variants = all { TodayCopy.headline(12_345, 30 * hour, it) }
        assertTrue(variants.any { "12.345" in it })
        assertTrue(variants.none { "12345" in it })
    }

    @Test
    fun `comparación con tu media`() {
        val up = TodayCopy.comparison(134.0, 100.0, DayOfWeek.TUESDAY)
        assertEquals("+34 % QUE TU MARTES MEDIO A ESTA HORA", plain(up.text))
        assertEquals("Un 34 % más que tu martes medio a esta hora.", plain(up.description))
        assertEquals(Direction.UP, up.direction)

        val down = TodayCopy.comparison(88.0, 100.0, DayOfWeek.TUESDAY)
        assertEquals("−12 % QUE TU MARTES MEDIO A ESTA HORA", plain(down.text))
        assertEquals(Direction.DOWN, down.direction)

        val same = TodayCopy.comparison(102.0, 100.0, DayOfWeek.TUESDAY)
        assertEquals("COMO TU MARTES MEDIO A ESTA HORA", same.text)
        assertEquals(Direction.SAME, same.direction)

        assertEquals("EL DOBLE QUE TU MARTES MEDIO A ESTA HORA", TodayCopy.comparison(200.0, 100.0, DayOfWeek.TUESDAY).text)
        assertEquals("EL TRIPLE QUE TU MARTES MEDIO A ESTA HORA", TodayCopy.comparison(300.0, 100.0, DayOfWeek.TUESDAY).text)
        assertEquals("2,4 VECES TU MARTES MEDIO A ESTA HORA", TodayCopy.comparison(240.0, 100.0, DayOfWeek.TUESDAY).text)
        assertEquals("LA MITAD QUE TU MARTES MEDIO A ESTA HORA", TodayCopy.comparison(50.0, 100.0, DayOfWeek.TUESDAY).text)

        val none = TodayCopy.comparison(10.0, null, DayOfWeek.SATURDAY)
        assertEquals("AÚN NO HAY SÁBADOS CON LOS QUE COMPARAR", none.text)
        assertEquals(Direction.NONE, none.direction)
        assertEquals("AÚN NO HAY LUNES CON LOS QUE COMPARAR", TodayCopy.comparison(1.0, null, DayOfWeek.MONDAY).text)

        assertEquals("TUS MARTES, A ESTA HORA, SUELEN IR A CERO", TodayCopy.comparison(5.0, 0.0, DayOfWeek.TUESDAY).text)
        assertEquals("COMO TU MARTES MEDIO A ESTA HORA", TodayCopy.comparison(0.0, 0.0, DayOfWeek.TUESDAY).text)
        assertEquals("+34 % QUE TU MARTES MEDIO", plain(DayCopy.comparison(134, 100.0, DayOfWeek.TUESDAY).text))
    }

    @Test
    fun `etiquetas de hoy`() {
        assertEquals("HOY · MARTES 3 NOV", TodayCopy.header(today))
        assertEquals("OBJETIVO 150", TodayCopy.goalLabel(150))
        assertEquals("+62 PASADO", plain(TodayCopy.goalStatus(212, 150)))
        assertEquals("QUEDAN 38", TodayCopy.goalStatus(112, 150))
        assertEquals("QUEDA 1", TodayCopy.goalStatus(149, 150))
        assertEquals("EN EL LÍMITE", TodayCopy.goalStatus(150, 150))
        assertEquals("47 MIN DE PANTALLA · 4 COMPARTIDOS", plain(TodayCopy.screenTime(47 * min, 4)))
        assertEquals("47 MIN DE PANTALLA · 1 COMPARTIDO", plain(TodayCopy.screenTime(47 * min, 1)))
        assertEquals("47 MIN DE PANTALLA", plain(TodayCopy.screenTime(47 * min)))
        assertEquals("HORA PUNTA 23:00 · 41 VÍDEOS", plain(TodayCopy.peakHour(23, 41)))
        assertEquals("120 · 26 MIN", plain(TodayCopy.platformValue(120, 26 * min)))
        assertEquals("VÍDEO", TodayCopy.counterLabel(1))
        assertEquals("212 vídeos", plain(TodayCopy.counterDescription(212)))
    }

    @Test
    fun `banda desde que usas Reelometer`() {
        val first = LocalDate.of(2026, 3, 3)
        assertEquals("DESDE EL 3 MAR: 12.408 VÍDEOS · 61 H", plain(TodayCopy.sinceBand(first, today, 12_408, 61 * hour)))
        assertEquals("DESDE HOY: 1 VÍDEO · 0 MIN", plain(TodayCopy.sinceBand(today, today, 1, 15_000)))
        assertEquals("DESDE AYER: 34 VÍDEOS · 9 MIN", plain(TodayCopy.sinceBand(today.minusDays(1), today, 34, 9 * min)))
        assertEquals("DESDE EL 3 MAR 2025: 1 VÍDEO · 1 MIN", plain(TodayCopy.sinceBand(LocalDate.of(2025, 3, 3), today, 1, min)))
        assertEquals(
            "Desde el 3 de marzo: 12.408 vídeos y 61 horas.",
            plain(TodayCopy.sinceBandDescription(first, today, 12_408, 61 * hour)),
        )
    }

    @Test
    fun `servicio desactivado con ironía y sin dramas`() {
        val off = all { TodayCopy.serviceOff(it) }
        assertTrue(off.contains("Sin el permiso de accesibilidad no veo nada. Que, por otra parte, es lo que esperas de una app así."))
        assertEquals("SIN SEÑAL", TodayCopy.SERVICE_OFF_LABEL)
    }

    // ------------------------------------------------------------------ Estadísticas

    @Test
    fun `tendencia frente al periodo anterior`() {
        assertEquals("+12 % QUE LA SEMANA ANTERIOR", plain(StatsCopy.trend(112.0, 100.0, CopyPeriod.WEEK)!!.text))
        assertEquals("−8 % QUE EL MES ANTERIOR", plain(StatsCopy.trend(92.0, 100.0, CopyPeriod.MONTH)!!.text))
        assertEquals("COMO EL AÑO ANTERIOR", StatsCopy.trend(100.0, 100.0, CopyPeriod.YEAR)!!.text)
        assertEquals("SIN DATOS DEL MES ANTERIOR", StatsCopy.trend(5.0, null, CopyPeriod.MONTH)!!.text)
        assertEquals("SIN DATOS DE LA SEMANA ANTERIOR", StatsCopy.trend(5.0, null, CopyPeriod.WEEK)!!.text)
        assertEquals("LA SEMANA ANTERIOR FUE A CERO", StatsCopy.trend(5.0, 0.0, CopyPeriod.WEEK)!!.text)
        assertNull(StatsCopy.trend(5.0, 3.0, CopyPeriod.ALL))
        assertNull(StatsCopy.trendQuote(5.0, 3.0, CopyPeriod.ALL, 0))
        val month = all { StatsCopy.trendQuote(5.0, null, CopyPeriod.MONTH, it)!! }
        assertTrue(month.toString(), month.any { "Es tu primer mes contado. El próximo" in it })
        val week = all { StatsCopy.trendQuote(5.0, null, CopyPeriod.WEEK, it)!! }
        assertTrue(week.any { "Es tu primera semana contada. La próxima" in it })
    }

    @Test
    fun `rachas con y sin objetivo`() {
        assertEquals("1 DÍA", plain(StatsCopy.streakValue(1)))
        assertEquals("4 DÍAS", plain(StatsCopy.streakValue(4)))
        assertEquals("0 DÍAS", plain(StatsCopy.streakValue(-3)))
        assertTrue("Ponte un objetivo y empezamos a contar." in all { StatsCopy.noGoalStreak(it) })
        val ongoing = all { StatsCopy.streakQuote(11, 12, 150, it) }
        assertTrue(ongoing.toString(), ongoing.any { "Falta 1 día" in it })
        assertTrue(all { StatsCopy.streakQuote(4, 12, 150, it) }.any { "Faltan 8 días" in it })
        assertTrue(all { StatsCopy.streakQuote(1, 1, 150, it) }.any { it.startsWith("Un día sin pasar de 150") })
    }

    @Test
    fun `cabeceras y cifras de estadísticas`() {
        assertEquals("27 OCT – 2 NOV 2026", StatsCopy.weekLabel(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2)))
        assertEquals("29 DIC 2025 – 4 ENE 2026", StatsCopy.weekLabel(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)))
        assertEquals("OCTUBRE 2026", StatsCopy.monthLabel(YearMonth.of(2026, 10)))
        assertEquals("2026", StatsCopy.yearLabel(2026))
        assertEquals("DESDE EL 3 MAR 2026", StatsCopy.allLabel(LocalDate.of(2026, 3, 3)))
        assertEquals(
            "VÍDEOS · 9 H 12 MIN · 172/DÍA · 27 S POR VÍDEO",
            plain(StatsCopy.summaryLine(1_204, 9 * hour + 12 * min, 172.0, 27_000)),
        )
        assertEquals("DOMINGO · 240 DE MEDIA", plain(StatsCopy.weakDayValue(DayOfWeek.SUNDAY, 240.0)))
        assertEquals("23:00 · 41 VÍDEOS", plain(StatsCopy.peakHourValue(23, 41)))
        assertEquals("1 MIN", plain(StatsCopy.perVideoValue(65_000)))
        assertEquals("Martes 3 de noviembre: 212 vídeos, el día con más.", plain(StatsCopy.barDescription(today, 212, true)))
    }

    @Test
    fun `la hora punta habla de su franja`() {
        assertTrue(all { StatsCopy.peakHourQuote(23, it) }.contains("23:00. El clásico «uno más y me duermo»."))
        assertTrue(all { StatsCopy.peakHourQuote(1, it) }.any { "a la 01:00" in it })
        assertTrue(all { StatsCopy.peakHourQuote(15, it) }.any { "sobremesa" in it })
    }

    // ------------------------------------------------------------------ Detalle del día

    @Test
    fun `detalle del día`() {
        assertEquals("Hoy, martes 3 de noviembre", DayCopy.title(today, today))
        assertEquals("Ayer, lunes 2 de noviembre", DayCopy.title(today.minusDays(1), today))
        assertEquals("Miércoles 31 de diciembre de 2025", DayCopy.title(LocalDate.of(2025, 12, 31), today))
        assertEquals("AYER · LUNES 2 NOV", DayCopy.label(today.minusDays(1), today))
        assertEquals("TU RÉCORD: EL DÍA CON MÁS VÍDEOS", DayCopy.rankLine(1, 64))
        assertEquals("N.º 3 DE 64 DÍAS CONTADOS", DayCopy.rankLine(3, 64))
        assertNull(DayCopy.rankLine(1, 1))
        assertTrue(all { DayCopy.quote(1, 20_000, it) }.any { it == "Ese día fue 1 vídeo. Casi nada." })
        assertTrue(all { DayCopy.quote(212, 47 * min, it, goal = 150) }.any { "62 vídeos" in it })
    }

    // ------------------------------------------------------------------ Wrapped e imagen compartible

    @Test
    fun `portada del wrapped con concordancia`() {
        val oct = LocalDate.of(2026, 10, 1)
        assertTrue("Tu octubre, contado." in all { WrappedCopy.coverTitle(CopyPeriod.MONTH, oct, it) })
        assertTrue("Lo que octubre se llevó." in all { WrappedCopy.coverTitle(CopyPeriod.MONTH, oct, it) })
        assertTrue("Tu semana, contada." in all { WrappedCopy.coverTitle(CopyPeriod.WEEK, oct, it) })
        assertTrue("Tu 2026, contado." in all { WrappedCopy.coverTitle(CopyPeriod.YEAR, LocalDate.of(2026, 1, 1), it) })
        assertEquals("REELOMETER · OCTUBRE 2026", WrappedCopy.coverLabel(CopyPeriod.MONTH, oct, LocalDate.of(2026, 10, 31)))
        assertTrue("Tu octubre, contado." in all { WrappedCopy.hubHeadline(YearMonth.of(2026, 10), it) })
    }

    @Test
    fun `diapositivas del wrapped`() {
        assertTrue(all { WrappedCopy.total(4_812, 31, it) }.any { "Deslizaste hacia arriba 4.812 veces." == it })
        assertTrue(all { WrappedCopy.time(29 * hour, it) }.any { "Más de un día entero" in it })
        assertTrue(all { WrappedCopy.time(29 * hour, it) }.any { "casi 4 jornadas laborales" in it })
        assertTrue(all { WrappedCopy.time(50 * hour, it) }.any { "Más de 2 días enteros" in it })
        assertTrue(all { WrappedCopy.creator("pepito", 312, it) }.any { "@pepito" in it && "312 vídeos" in it })
        assertTrue(all { WrappedCopy.category(Category.HUMOR, 0.34, it) }.any { "34 %" in it })
        assertTrue(all { WrappedCopy.category(Category.HUMOR, 0.34, it) }.any { "nariz" in it })
        assertTrue(all { WrappedCopy.worstDay(LocalDate.of(2026, 10, 14), 612, it) }.any { "el miércoles 14 de octubre" in it })
        val two = all { WrappedCopy.platforms(listOf(Platform.TIKTOK to 0.7, Platform.INSTAGRAM to 0.3), it) }
        assertTrue(two.toString(), two.any { "Reels se quedó con el resto." in it })
        val three = all { WrappedCopy.platforms(listOf(Platform.TIKTOK to 0.6, Platform.INSTAGRAM to 0.3, Platform.YOUTUBE to 0.1), it) }
        assertTrue(three.any { "Reels y Shorts se repartieron el resto." in it })
        assertTrue(all { WrappedCopy.platforms(listOf(Platform.YOUTUBE to 1.0), it) }.any { "Fidelidad absoluta" in it })
        val closing = all { WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), true, it) }
        assertTrue(closing.any { "Noviembre ya ha empezado a contar." in it })
        val open = all { WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 11, 1), false, it) }
        assertTrue(open.none { "Esto fue" in it })
    }

    @Test
    fun `ticket compartible`() {
        val lines = ShareCopy.lines(4_812, 29 * hour, 31, LocalDate.of(2026, 10, 14), 612, 23, "pepito", Category.HUMOR, 0.34)
        val byLabel = lines.associate { it.label to plain(it.value) }
        assertEquals("4.812", byLabel["📱 VÍDEOS"])
        assertEquals("29 H", byLabel["⏱️ TIEMPO"])
        assertEquals("155", byLabel["📅 AL DÍA"])
        assertEquals("14 OCT · 612", byLabel["🔥 PEOR DÍA"])
        assertEquals("23:00", byLabel["🌙 HORA PUNTA"])
        assertEquals("@pepito", byLabel["👑 CREADOR N.º 1"])
        assertEquals("34 %", byLabel["😂 HUMOR"])
        assertEquals("PAGADO CON TU TIEMPO", ShareCopy.STAMP)
        assertEquals("REELOMETER · CUENTA LO QUE NO CUENTAS", ShareCopy.BRAND_FOOTER)
        assertEquals(
            "Mi octubre en Reelometer: 4.812 vídeos y 29 horas. No se admiten devoluciones. 🧾",
            plain(ShareCopy.shareText(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), 4_812, 29 * hour)),
        )
        assertEquals("reelometer-octubre-2026.png", ShareCopy.fileName(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1)))
        assertEquals("reelometer-semana-2026-10-26.png", ShareCopy.fileName(CopyPeriod.WEEK, LocalDate.of(2026, 10, 26)))
        // Sin opcionales: solo vídeos y tiempo
        assertEquals(2, ShareCopy.lines(1, 60_000, 1).size)
    }

    // ------------------------------------------------------------------ Onboarding, ajustes, avisos

    @Test
    fun `la guía usa los nombres reales de Android`() {
        val guide = OnboardingCopy.GUIDE_STEPS.joinToString(" ") { it.text }
        listOf("Aplicaciones descargadas", "Usar Reelometer", "pueda controlar totalmente tu dispositivo", "Permitir").forEach {
            assertTrue("La guía debe citar «$it»", it in guide)
        }
        val restricted = OnboardingCopy.RESTRICTED_TITLE + OnboardingCopy.RESTRICTED_STEPS.joinToString(" ") { it.text } +
            OnboardingCopy.RESTRICTED_NOTE
        listOf("Ajuste restringido", "Información de la aplicación", "⋮", "Permitir ajustes restringidos").forEach {
            assertTrue("La ayuda de ajustes restringidos debe citar «$it»", it in restricted)
        }
        assertEquals(listOf(1, 2, 3, 4, 5), OnboardingCopy.GUIDE_STEPS.map { it.number })
        assertEquals("A las 21:30, tu cuenta del día.", OnboardingCopy.notifyDaily(21, 30))
        assertEquals("Entre 5 y 2.000 vídeos al día.", plain(OnboardingCopy.goalRange(5, 2_000)))
        assertEquals("Como mucho, 150 al día.", OnboardingCopy.goalChosen(150))
        assertEquals("Cuenta lo que no cuentas.", OnboardingCopy.WHAT_TITLE)
    }

    @Test
    fun `la divulgación cubre lo que exige Google Play`() {
        val text = DisclosureCopy.fullText.joinToString(" ")
        // Qué datos, en qué apps, para qué, que no salen ni se comparten, que se puede desactivar y aceptación explícita
        listOf(
            "servicio de accesibilidad", "TikTok", "Instagram", "YouTube", "creador", "descripción", "solo en este móvil",
            "No se envía", "ni se comparte", "desactivar", "borrar", "ACEPTAR",
        ).forEach { assertTrue("La divulgación debe mencionar «$it»", it in text) }
        assertEquals(5, DisclosureCopy.SECTIONS.size)
        assertFalse(DisclosureCopy.ACCEPT == DisclosureCopy.DECLINE)
    }

    @Test
    fun `ajustes`() {
        assertEquals(8, SettingsCopy.SECTIONS.size)
        assertEquals((1..8).map { "%02d".format(it) }, SettingsCopy.SECTIONS.map { it.index })
        assertEquals("150 AL DÍA", plain(SettingsCopy.goalValue(150)))
        assertEquals("SIN OBJETIVO", SettingsCopy.goalValue(null))
        assertEquals("A 3 toques de la calibración.", SettingsCopy.calibrationTapsLeft(3))
        assertEquals("A un toque de la calibración.", SettingsCopy.calibrationTapsLeft(1))
        assertEquals(SettingsCopy.CALIBRATION_UNLOCKED, SettingsCopy.calibrationTapsLeft(0))
        assertEquals("Exportado: 1 vídeo.", plain(SettingsCopy.exportDone(1)))
        assertEquals("Exportado: 12.408 vídeos.", plain(SettingsCopy.exportDone(12_408)))
        assertTrue(plain(SettingsCopy.deleteDialogBody(12_408, 61 * hour)).startsWith("Se borrarán 12.408 vídeos y 61 horas"))
        assertEquals("Contando en TikTok, Reels y Shorts.", SettingsCopy.serviceConnected(Platform.entries.toList()))
        assertEquals("VERSIÓN 1.0.0", SettingsCopy.versionLabel("1.0.0"))
    }

    @Test
    fun `notificaciones`() {
        assertEquals("0 vídeos hoy", plain(NotificationCopy.daily(0, 0, 0).title))
        assertEquals("1 vídeo hoy", plain(NotificationCopy.daily(1, 20_000, 0).title))
        assertEquals("212 vídeos hoy", plain(NotificationCopy.daily(212, 47 * min, 0).title))
        assertTrue(all { NotificationCopy.daily(0, 0, it).text }.contains("Día limpio. Que conste en acta."))
        assertTrue(all { NotificationCopy.daily(212, 47 * min, it, goal = 150).text }.any { "62 vídeos" in it })
        val monthly = (0L until 60L).map { NotificationCopy.monthly(YearMonth.of(2026, 10), 4_812, 29 * hour, it) }
        assertTrue(monthly.any { plain(it.title) == "Tu octubre, contado." })
        assertTrue(monthly.any { "Toca para" in it.text })
        assertTrue(monthly.all { "4.812 vídeos" in plain(it.text) })
    }
}
