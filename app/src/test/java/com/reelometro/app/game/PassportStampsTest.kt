package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Cada regla de sello, con sus bordes. [LUNES] es lunes: dia(5) sábado, dia(6) domingo. */
class PassportStampsTest {

    @Test
    fun `primer día en el objetivo o por debajo, y hoy no cuenta hasta que acaba`() {
        assertEquals(dia(1), calcular(dia(3), 100, dias(LUNES, 150, 100, 50)).state.fecha(StampId.PRIMER_DIA))
        assertNull(calcular(dia(1), 100, dias(LUNES, 150, 10)).state.fecha(StampId.PRIMER_DIA))
        assertNull(calcular(dia(3), 100, dias(LUNES, 101, 150, 200)).state.fecha(StampId.PRIMER_DIA))
    }

    @Test
    fun `medio día por debajo de la mitad de la referencia`() {
        val s = calcular(dia(2), 100, dias(LUNES, 50, 49)).state
        assertEquals(dia(1), s.fecha(StampId.MEDIO_DIA))
        assertNull(calcular(dia(2), 100, dias(LUNES, 50, 50)).state.fecha(StampId.MEDIO_DIA))
        // Sin objetivo ni media todavía, no se puede juzgar.
        assertNull(calcular(dia(2), null, dias(LUNES, 50, 1)).state.fecha(StampId.MEDIO_DIA))
    }

    @Test
    fun `un día a 0 solo vale si hay vídeos antes y después`() {
        val trailing = calcular(dia(5), 100, mapOf(LUNES to 60)).state
        assertNull(trailing.fecha(StampId.DIA_EN_BLANCO))
        assertNull(trailing.fecha(StampId.MEDIO_DIA))
        val between = calcular(dia(3), 100, mapOf(LUNES to 60, dia(2) to 60)).state
        assertEquals(dia(1), between.fecha(StampId.DIA_EN_BLANCO))
        assertEquals(dia(1), between.fecha(StampId.MEDIO_DIA))
        // Los vídeos de hoy también confirman el día en blanco de ayer.
        val today = calcular(dia(2), 100, mapOf(LUNES to 60, dia(2) to 5)).state
        assertEquals(dia(1), today.fecha(StampId.DIA_EN_BLANCO))
        // El día en blanco no necesita objetivo ni media.
        assertEquals(dia(1), calcular(dia(3), null, mapOf(LUNES to 60, dia(2) to 60)).state.fecha(StampId.DIA_EN_BLANCO))
    }

    @Test
    fun `fin de semana con sábado y domingo bajo la referencia`() {
        assertEquals(dia(6), calcular(dia(7), 100, iguales(LUNES, 7, 60)).state.fecha(StampId.FIN_DE_SEMANA))
        assertNull(calcular(dia(7), 100, dias(LUNES, 60, 60, 60, 60, 60, 150, 60)).state.fecha(StampId.FIN_DE_SEMANA))
        assertNull(calcular(dia(7), 100, dias(LUNES, 60, 60, 60, 60, 60, 60, 150)).state.fecha(StampId.FIN_DE_SEMANA))
        // El domingo de hoy aún no está cerrado.
        assertNull(calcular(dia(6), 100, iguales(LUNES, 7, 60)).state.fecha(StampId.FIN_DE_SEMANA))
        // Empezar a contar en domingo: el sábado no tiene registro.
        assertNull(calcular(dia(8), 100, iguales(dia(6), 2, 60)).state.fecha(StampId.FIN_DE_SEMANA))
        assertEquals(dia(13), calcular(dia(14), 100, iguales(dia(6), 8, 60)).state.fecha(StampId.FIN_DE_SEMANA))
    }

    @Test
    fun `rachas con su fecha y su progreso`() {
        val s = calcular(dia(7), 100, iguales(LUNES, 7, 60)).state
        assertEquals(dia(2), s.fecha(StampId.RACHA_3))
        assertEquals(dia(6), s.fecha(StampId.RACHA_7))
        assertNull(s.fecha(StampId.RACHA_14))
        assertEquals(0.5f, s.sello(StampId.RACHA_14).progress ?: -1f, 0f)
        assertEquals(7f / 30, s.sello(StampId.RACHA_30).progress ?: -1f, 0.0001f)
        // Racha de 2: el de 3 aún no.
        val two = calcular(dia(3), 100, dias(LUNES, 150, 60, 60)).state
        assertNull(two.fecha(StampId.RACHA_3))
        assertEquals(2f / 3, two.sello(StampId.RACHA_3).progress ?: -1f, 0.0001f)
        // Si hoy ya se ha pasado, la racha actual (y su progreso) es 0.
        val over = calcular(dia(3), 100, dias(LUNES, 150, 60, 60, 101)).state
        assertEquals(0, over.streak)
        assertEquals(0f, over.sello(StampId.RACHA_3).progress ?: -1f, 0f)
    }

    @Test
    fun `semana un 20 por ciento por debajo de la anterior`() {
        val first = iguales(LUNES, 7, 100)
        assertEquals(dia(13), calcular(dia(14), null, first + iguales(dia(7), 7, 80)).state.fecha(StampId.SEMANA_MENOS_20))
        val oneMore = first + iguales(dia(7), 7, 80) + (dia(13) to 81)
        assertNull(calcular(dia(14), null, oneMore).state.fecha(StampId.SEMANA_MENOS_20))
        // La semana aún no está cerrada.
        assertNull(calcular(dia(13), null, first + iguales(dia(7), 7, 80)).state.fecha(StampId.SEMANA_MENOS_20))
        // La semana anterior tiene que tener registro los 7 días.
        val partial = iguales(dia(1), 6, 100) + iguales(dia(7), 7, 10)
        assertNull(calcular(dia(14), null, partial).state.fecha(StampId.SEMANA_MENOS_20))
        val third = partial + iguales(dia(14), 7, 8)
        assertEquals(dia(20), calcular(dia(21), null, third).state.fecha(StampId.SEMANA_MENOS_20))
    }

    @Test
    fun `mes a la baja con menos vídeos por día que el mes anterior`() {
        val october = iguales(LocalDate.of(2026, 10, 1), 31, 10)
        val fewer = october + iguales(LocalDate.of(2026, 11, 1), 30, 9)
        val dec1 = LocalDate.of(2026, 12, 1)
        assertEquals(LocalDate.of(2026, 11, 30), calcular(dec1, null, fewer).state.fecha(StampId.MES_A_LA_BAJA))
        // Noviembre sin cerrar todavía.
        assertNull(calcular(LocalDate.of(2026, 11, 30), null, fewer).state.fecha(StampId.MES_A_LA_BAJA))
        // Igual por día: no.
        val same = october + iguales(LocalDate.of(2026, 11, 1), 30, 10)
        assertNull(calcular(dec1, null, same).state.fecha(StampId.MES_A_LA_BAJA))
        // El mes anterior necesita 14 días con registro.
        val short = iguales(LocalDate.of(2026, 10, 20), 12, 10) + iguales(LocalDate.of(2026, 11, 1), 30, 1)
        assertNull(calcular(dec1, null, short).state.fecha(StampId.MES_A_LA_BAJA))
    }

    @Test
    fun `mes a la baja al cambiar de año y en febrero`() {
        val december = iguales(LocalDate.of(2026, 12, 1), 31, 10)
        val january = iguales(LocalDate.of(2027, 1, 1), 31, 9)
        assertEquals(
            LocalDate.of(2027, 1, 31),
            calcular(LocalDate.of(2027, 2, 1), null, december + january).state.fecha(StampId.MES_A_LA_BAJA),
        )
        // Febrero con menos vídeos en total pero más por día que enero: no.
        val jan10 = iguales(LocalDate.of(2027, 1, 1), 31, 10)
        val feb11 = iguales(LocalDate.of(2027, 2, 1), 28, 11)
        assertNull(calcular(LocalDate.of(2027, 3, 1), null, jan10 + feb11).state.fecha(StampId.MES_A_LA_BAJA))
    }

    @Test
    fun `primer reto y cuatro retos cumplidos`() {
        val results = listOf(true, false, true, true, true).mapIndexed { i, won ->
            ChallengeResult(dia(-35 + 7 * i), target = 100, total = if (won) 90 else 120, won = won)
        }
        val s = calcular(LUNES, null, emptyMap(), estrenada().copy(challenges = results)).state
        assertEquals(dia(-29), s.fecha(StampId.PRIMER_RETO))
        assertEquals(dia(-1), s.fecha(StampId.CUATRO_RETOS))
        assertEquals(200, s.xp.challenges)
        val three = calcular(LUNES, null, emptyMap(), estrenada().copy(challenges = results.dropLast(1))).state
        assertNull(three.fecha(StampId.CUATRO_RETOS))
        assertEquals(0.75f, three.sello(StampId.CUATRO_RETOS).progress ?: -1f, 0f)
    }

    @Test
    fun `regreso de zombi a normal o mejor`() {
        // Una semana pasándose y luego días bajo el objetivo: zombi el día 6, normal el 11.
        val history = iguales(LUNES, 7, 200) + iguales(dia(7), 7, 50)
        assertEquals(dia(11), calcular(dia(13), 100, history).state.fecha(StampId.REGRESO))
        // Detectado hoy mismo con el estado actual.
        assertEquals(dia(11), calcular(dia(11), 100, history).state.fecha(StampId.REGRESO))
        // Todavía cansado: aún no.
        assertNull(calcular(dia(10), 100, history).state.fecha(StampId.REGRESO))
        // Sin pasar por zombi no hay regreso.
        assertNull(calcular(dia(20), 100, iguales(LUNES, 20, 50)).state.fecha(StampId.REGRESO))
    }

    @Test
    fun `primera semana con siete días con registro ya cerrados`() {
        val six = calcular(dia(6), 100, iguales(LUNES, 7, 500)).state
        assertNull(six.fecha(StampId.PRIMERA_SEMANA))
        assertEquals(6f / 7, six.sello(StampId.PRIMERA_SEMANA).progress ?: -1f, 0.0001f)
        assertEquals(dia(6), calcular(dia(7), 100, iguales(LUNES, 7, 500)).state.fecha(StampId.PRIMERA_SEMANA))
    }

    @Test
    fun `los eventos dan sus sellos con su fecha`() {
        val memory = estrenada().copy(
            events = mapOf(
                GameEvent.WRAPPED_SHARED to dia(-3),
                GameEvent.TICKET_SAVED to dia(-2),
                GameEvent.LEVEL_TICKET_SHARED to dia(-1),
            ),
        )
        val s = calcular(LUNES, null, emptyMap(), memory).state
        assertEquals(dia(-3), s.fecha(StampId.WRAPPED_COMPARTIDO))
        assertEquals(dia(-2), s.fecha(StampId.TICKET_GUARDADO))
        assertEquals(StampId.WRAPPED_COMPARTIDO.xp + StampId.TICKET_GUARDADO.xp, s.xp.stamps)
    }

    @Test
    fun `sellos irónicos del historial, totales y fechas futuras`() {
        val history = GameHistory(earlyBirdOn = dia(1), threePlatformsOn = dia(2), fiveCategoriesOn = dia(3), marathonOn = dia(9))
        // Acumulado: 400, 800, 1.100 (MIL), 9.100 y 10.000 (DIEZ MIL) hoy.
        val s = calcular(dia(4), null, dias(LUNES, 400, 400, 300, 8_000, 900), history = history).state
        assertEquals(dia(1), s.fecha(StampId.MADRUGADOR))
        assertEquals(dia(2), s.fecha(StampId.TRES_PLATAFORMAS))
        assertEquals(dia(3), s.fecha(StampId.CINCO_CATEGORIAS))
        assertNull("una fecha futura no vale", s.fecha(StampId.MARATON))
        assertEquals(dia(2), s.fecha(StampId.MIL_VIDEOS))
        assertEquals(dia(4), s.fecha(StampId.DIEZ_MIL_VIDEOS))
        assertNull(calcular(dia(4), null, dias(LUNES, 999)).state.fecha(StampId.MIL_VIDEOS))
        assertEquals(LUNES, calcular(dia(4), null, dias(LUNES, 1_000)).state.fecha(StampId.MIL_VIDEOS))
    }

    @Test
    fun `una fecha guardada manda y no desaparece aunque cambien los datos`() {
        val memory = estrenada().copy(
            stamps = mapOf(StampId.PRIMER_DIA to dia(-100), StampId.MADRUGADOR to dia(-50), StampId.RACHA_3 to dia(-40)),
        )
        val history = GameHistory(earlyBirdOn = dia(1))
        val s = calcular(dia(3), 100, dias(LUNES, 60, 60, 60), memory, history).state
        assertEquals(dia(-100), s.fecha(StampId.PRIMER_DIA))
        assertEquals(dia(-50), s.fecha(StampId.MADRUGADOR))
        assertEquals(dia(-40), s.fecha(StampId.RACHA_3))
        val empty = calcular(dia(3), null, emptyMap(), memory).state
        assertEquals(3, empty.earnedStamps)
    }
}
