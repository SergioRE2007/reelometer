package com.reelometro.app.copy

import com.reelometro.app.copy.CopyRules.plain
import org.junit.Assert.assertEquals
import org.junit.Test

class WordsTest {

    private val min = 60_000L
    private val hour = 3_600_000L

    @Test
    fun `singular y plural con cifras formateadas`() {
        assertEquals("0 vídeos", plain(Words.videos(0)))
        assertEquals("1 vídeo", plain(Words.videos(1)))
        assertEquals("2 vídeos", plain(Words.videos(2)))
        assertEquals("10.000 vídeos", plain(Words.videos(10_000)))
        assertEquals("1.234.567 vídeos", plain(Words.videos(1_234_567L)))
        assertEquals("1 vez", plain(Words.times(1)))
        assertEquals("3 veces", plain(Words.times(3)))
        assertEquals("1 día", plain(Words.days(1)))
        assertEquals("12 días", plain(Words.days(12)))
    }

    @Test
    fun `la cifra va pegada a su palabra con espacio duro`() {
        assertEquals("212${Words.NBSP}vídeos", Words.videos(212))
    }

    @Test
    fun `duraciones con palabras`() {
        assertEquals("0 segundos", plain(Words.duration(0)))
        assertEquals("un segundo", plain(Words.duration(1_000)))
        assertEquals("45 segundos", plain(Words.duration(45_000)))
        assertEquals("un minuto", plain(Words.duration(min)))
        assertEquals("47 minutos", plain(Words.duration(47 * min)))
        assertEquals("una hora", plain(Words.duration(60 * min)))
        assertEquals("una hora y un minuto", plain(Words.duration(61 * min)))
        assertEquals("una hora y 12 minutos", plain(Words.duration(72 * min)))
        assertEquals("3 horas", plain(Words.duration(3 * hour)))
        assertEquals("10 horas", plain(Words.duration(10 * hour + 20 * min)))
        assertEquals("61 horas", plain(Words.duration(61 * hour)))
        assertEquals("5.000 horas", plain(Words.duration(5_000 * hour)))
        assertEquals("0 segundos", plain(Words.duration(-5)))
    }

    @Test
    fun `la frase y la etiqueta redondean igual`() {
        // 46 min 40 s: «47 MIN» en la etiqueta y «47 minutos» en la frase
        val ms = 46 * min + 40_000
        assertEquals("47 MIN", plain(Words.durationLabel(ms)))
        assertEquals("47 minutos", plain(Words.duration(ms)))
        assertEquals("3,2 H", plain(Words.durationLabel((3.2 * hour).toLong())))
    }

    @Test
    fun `listas con y y con e`() {
        assertEquals("", Words.join(emptyList()))
        assertEquals("TikTok", Words.join(listOf("TikTok")))
        assertEquals("TikTok y Reels", Words.join(listOf("TikTok", "Reels")))
        assertEquals("TikTok, Reels y Shorts", Words.join(listOf("TikTok", "Reels", "Shorts")))
        assertEquals("Reels e Instagram", Words.join(listOf("Reels", "Instagram")))
        assertEquals("agua e hilo", Words.join(listOf("agua", "hilo")))
        assertEquals("agua y hielo", Words.join(listOf("agua", "hielo")))
    }

    @Test
    fun `horas con su artículo`() {
        assertEquals("a la 01:00", Words.atHour(1))
        assertEquals("a las 00:00", Words.atHour(0))
        assertEquals("a las 23:00", Words.atHour(23))
        assertEquals("a las 21:30", Words.atTime(21, 30))
        assertEquals("a la 01:15", Words.atTime(1, 15))
    }

    @Test
    fun `usuarios y frases`() {
        assertEquals("@pepito", Words.handle("pepito"))
        assertEquals("@pepito", Words.handle(" @pepito "))
        assertEquals("@Pepa.Real", Words.handle("Pepa.Real"))
        assertEquals("Leer 14 libros.", plain(Words.sentence("leer 14 libros")))
        assertEquals("¿Qué?", Words.sentence("¿Qué?"))
    }
}
