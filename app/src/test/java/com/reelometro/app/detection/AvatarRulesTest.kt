package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRulesTest {

    @Test
    fun `iniciales del nombre`() {
        assertEquals("AG", AvatarRules.initials("Ana García"))
        assertEquals("M", AvatarRules.initials("marta"))
        assertEquals("LP", AvatarRules.initials("Los del piso"))
        assertEquals("ÁF", AvatarRules.initials("álvaro ruiz de la fuente"))
        assertEquals("AG", AvatarRules.initials("@ana.g_92"))
        assertEquals("P", AvatarRules.initials("Primos 2024"))
        assertEquals("?", AvatarRules.initials("  ·  "))
    }

    @Test
    fun `el fichero es un hash del nombre y no lo lleva`() {
        val a = AvatarRules.fileName("Los del piso")
        assertTrue(a.matches(Regex("^[0-9a-f]{32}\\.png$")))
        assertEquals(a, AvatarRules.fileName("los del  PISO"))
        assertNotEquals(a, AvatarRules.fileName("Primos"))
        assertFalse(a.contains("piso"))
    }

    @Test
    fun `se renueva como mucho una vez al dia`() {
        val day = AvatarRules.REFRESH_MS
        assertTrue(AvatarRules.needsRefresh(null, 1_000))
        assertTrue(AvatarRules.needsRefresh(0, 1_000))
        assertFalse(AvatarRules.needsRefresh(10_000, 10_000 + day - 1))
        assertTrue(AvatarRules.needsRefresh(10_000, 10_000 + day))
        assertTrue(AvatarRules.needsRefresh(10_000, 5_000)) // el reloj ha ido hacia atrás
        // Con una foto elegida de la galería, nunca.
        assertFalse(AvatarRules.needsRefresh(null, 1_000, chosen = true))
        assertFalse(AvatarRules.needsRefresh(10_000, 10_000 + day, chosen = true))
    }

    @Test
    fun `la foto elegida no se recorta nunca en el envio rapido`() {
        val day = AvatarRules.REFRESH_MS
        assertTrue(AvatarRules.captureInQuickSend(savedAt = null, failedAt = null, now = 5 * day))
        assertFalse(AvatarRules.captureInQuickSend(savedAt = null, failedAt = null, now = 5 * day, chosen = true))
        assertFalse(AvatarRules.captureInQuickSend(savedAt = null, failedAt = 4 * day, now = 5 * day, chosen = true))
    }

    @Test
    fun `cada fijado tiene su foto elegida aparte de la recortada`() {
        val legacy = AvatarRules.fileName("Los del piso")
        val cropped = AvatarRules.croppedFileName("Los del piso")
        val chosen = AvatarRules.chosenFileName("los del  PISO")
        val hash = legacy.removeSuffix(".png")
        // La clave de siempre no cambia (la usa QuickSendMemory).
        assertTrue(legacy.matches(Regex("^[0-9a-f]{32}\\.png$")))
        assertEquals("$hash.c${AvatarRules.CROP_VERSION}.png", cropped)
        assertEquals("$hash.mine.png", chosen)
        assertEquals(listOf(chosen, cropped, legacy), AvatarRules.fileNames("Los del piso"))
        assertEquals(3, AvatarRules.fileNames("Los del piso").toSet().size)
        assertTrue(AvatarRules.fileNames("Primos").none { it.startsWith(hash) })
    }

    @Test
    fun `que foto se pinta`() {
        assertEquals(AvatarRules.Source.CHOSEN, AvatarRules.pick(chosen = true, cropped = true, legacy = true))
        assertEquals(AvatarRules.Source.CHOSEN, AvatarRules.pick(chosen = true, cropped = false, legacy = false))
        assertEquals(AvatarRules.Source.CROPPED, AvatarRules.pick(chosen = false, cropped = true, legacy = true))
        // La de antes de 1.4.3 (quizá con la etiqueta de actividad) solo si no hay otra.
        assertEquals(AvatarRules.Source.LEGACY, AvatarRules.pick(chosen = false, cropped = false, legacy = true))
        assertNull(AvatarRules.pick(chosen = false, cropped = false, legacy = false))
    }

    @Test
    fun `el recorte deja fuera la etiqueta de actividad de abajo a la derecha`() {
        // Datos reales (Instagram 454, Pixel 10a): el ImageView del avatar de una celda de la cuadrícula, 194 px.
        val photo = box(443, 1110, 637, 1304)
        val crop = AvatarRules.cropBox(photo, 1080, 2424)!!
        assertEquals(box(459, 1126, 594, 1261), crop)
        // La etiqueta ocupa el borde de abajo hacia el centro (visto en el móvil): el recorte acaba antes del 79 % del alto.
        assertTrue((crop.bottom - photo.top).toDouble() / photo.height <= 0.79)
        val r = photo.width / 2.0
        val cx = (photo.left + photo.right) / 2.0
        val cy = (photo.top + photo.bottom) / 2.0
        // El círculo que pinta la burbuja (el inscrito en el recorte) cae entero dentro del círculo de la foto…
        val cr = crop.width / 2.0
        val ccx = (crop.left + crop.right) / 2.0
        val ccy = (crop.top + crop.bottom) / 2.0
        val far = Math.hypot(ccx - cx, ccy - cy) + cr
        assertTrue("llega a $far de $r", far <= 0.92 * r)
        // …y hacia la esquina de abajo a la derecha (donde va «47 m» o el punto verde) no pasa de dos tercios del radio.
        val diagonal = Math.hypot(ccx + cr / Math.sqrt(2.0) - cx, ccy + cr / Math.sqrt(2.0) - cy)
        assertTrue("llega a $diagonal de $r", diagonal <= 0.65 * r)
    }

    @Test
    fun `en un grupo el recorte sigue cogiendo los dos circulos`() {
        // Datos reales: dos ImageView que se pisan; photoOf devuelve el rectángulo que los envuelve.
        val back = box(75, 1824, 222, 1971)
        val front = box(139, 1888, 286, 2035)
        val photo = AvatarRules.clusters(listOf(back, front)).single()
        val crop = AvatarRules.cropBox(photo, 1080, 2424, group = true)!!
        assertEquals(box(83, 1832, 278, 2027), crop) // margen pequeño y simétrico: la cara de delante va abajo a la derecha
        val cr = crop.width / 2.0
        val ccx = (crop.left + crop.right) / 2.0
        val ccy = (crop.top + crop.bottom) / 2.0
        // El centro de cada círculo queda dentro del círculo de la burbuja, con margen.
        for (c in listOf(back, front)) {
            val d = Math.hypot((c.left + c.right) / 2.0 - ccx, (c.top + c.bottom) / 2.0 - ccy)
            assertTrue("centro a $d de $cr", d <= 0.8 * cr)
        }
    }

    @Test
    fun `foto de la galeria - cuadrado centrado`() {
        assertEquals(box(1000, 0, 4000, 3000), AvatarRules.centerSquare(5000, 3000)) // apaisada
        assertEquals(box(0, 500, 3000, 3500), AvatarRules.centerSquare(3000, 4000)) // vertical
        assertEquals(box(0, 0, 144, 144), AvatarRules.centerSquare(144, 144))
        assertEquals(box(0, 4, 1, 5), AvatarRules.centerSquare(1, 9))
        assertNull(AvatarRules.centerSquare(0, 100))
        assertNull(AvatarRules.centerSquare(100, -1))
    }

    @Test
    fun `foto de la galeria - se decodifica con muestreo`() {
        // 50 MP (8160 × 6120): el lado menor se queda en 191 px (≥ 144), sin cargar la foto entera.
        val big = AvatarRules.sampleSize(8160, 6120)
        assertEquals(32, big)
        assertTrue(6120 / big >= AvatarRules.SIZE_PX)
        assertTrue(6120 / (big * 2) < AvatarRules.SIZE_PX)
        assertEquals(1, AvatarRules.sampleSize(200, 200))
        assertEquals(1, AvatarRules.sampleSize(100, 50)) // más pequeña que la burbuja: tal cual
        assertEquals(2, AvatarRules.sampleSize(288, 400))
        // Una panorámica enorme: aunque el lado menor baje de 144, no pasa del tope de píxeles.
        val pano = AvatarRules.sampleSize(100_000, 300)
        assertTrue((100_000L / pano) * (300L / pano) <= AvatarRules.MAX_DECODE_PIXELS)
        assertEquals(1, AvatarRules.sampleSize(0, 0))
    }

    @Test
    fun `foto de la galeria - orientacion exif`() {
        assertEquals(listOf(0, 0, 180, 180, 90, 90, 270, 270), (1..8).map { AvatarRules.exifRotation(it) })
        assertEquals(listOf(false, true, false, true, true, false, true, false), (1..8).map { AvatarRules.exifMirror(it) })
        assertEquals(0, AvatarRules.exifRotation(0)) // sin dato
        assertFalse(AvatarRules.exifMirror(0))
    }

    @Test
    fun `recorte cuadrado dentro de la pantalla`() {
        // 160 px: 13 px fuera arriba y a la izquierda (8 %), 35 px abajo y a la derecha (22 %): queda un cuadrado de 112.
        assertEquals(box(113, 213, 225, 325), AvatarRules.cropBox(box(100, 200, 260, 360), SCREEN_W, SCREEN_H))
        // Un poco más ancha que alta: el cuadrado del lado menor (140), centrado, con sus márgenes (11 y 31).
        assertEquals(box(121, 211, 219, 309), AvatarRules.cropBox(box(100, 200, 260, 340), SCREEN_W, SCREEN_H))
        assertNull(AvatarRules.cropBox(box(0, 0, 400, 100), SCREEN_W, SCREEN_H)) // no es una foto
        assertNull(AvatarRules.cropBox(box(10, 10, 20, 20), SCREEN_W, SCREEN_H)) // muy pequeña
        assertNull(AvatarRules.cropBox(box(1000, 100, 1160, 260), SCREEN_W, SCREEN_H)) // se sale
    }

    @Test
    fun `la foto de la celda del fijado`() {
        val photo = desc("Foto del perfil de Los del piso", box(340, 1000, 500, 1160))
        val name = text("Los del piso", box(300, 1180, 540, 1230))
        FakeNode(bounds = box(300, 1000, 540, 1300), children = listOf(photo, name))
        assertEquals(photo.bounds, AvatarRules.photoOf(name, "los del piso", SCREEN_H))
        // Si se encontró por la descripción, es el propio nodo.
        assertEquals(photo.bounds, AvatarRules.photoOf(photo, "Los del piso", SCREEN_H))

        // Sin descripción: la imagen cuadrada de encima del nombre.
        val image = FakeNode(className = "android.widget.ImageView", bounds = box(40, 1000, 200, 1160))
        val wide = FakeNode(className = "android.widget.ImageView", bounds = box(0, 1000, 240, 1040))
        val plain = text("Marta", box(0, 1180, 240, 1230))
        FakeNode(bounds = box(0, 1000, 240, 1300), children = listOf(wide, image, plain))
        assertEquals(image.bounds, AvatarRules.photoOf(plain, "Marta", SCREEN_H))

        // La foto de otra persona no vale.
        val other = desc("Foto del perfil de Ana", box(340, 1000, 500, 1160), cls = "android.view.View")
        val primos = text("Primos", box(300, 1180, 540, 1230))
        FakeNode(bounds = box(300, 1000, 540, 1300), children = listOf(other, primos))
        assertNull(AvatarRules.photoOf(primos, "Primos", SCREEN_H))
    }

    @Test
    fun `la foto compuesta de un grupo es el rectangulo que envuelve sus circulos`() {
        // Dos círculos que se pisan (sin «Foto del perfil de…»), encima del nombre del grupo.
        val back = FakeNode(className = "android.widget.ImageView", bounds = box(80, 1000, 190, 1110))
        val front = FakeNode(className = "android.widget.ImageView", bounds = box(130, 1050, 240, 1160))
        val avatar = FakeNode(className = "android.widget.FrameLayout", bounds = box(80, 1000, 240, 1160), children = listOf(back, front))
        val name = text("Los del piso", box(40, 1180, 280, 1230))
        FakeNode(contentDescription = "Los del piso, grupo", bounds = box(40, 1000, 280, 1300), children = listOf(avatar, name))
        val photo = AvatarRules.photoOf(name, "Los del piso", SCREEN_H)
        assertEquals(box(80, 1000, 240, 1160), photo)
        // Es la de un grupo: margen pequeño y simétrico (6 px de 160 por lado).
        assertTrue(AvatarRules.photo(name, "Los del piso", SCREEN_H)!!.group)
        assertEquals(box(86, 1006, 234, 1154), AvatarRules.cropBox(photo!!, SCREEN_W, SCREEN_H, group = true))

        // Dos fotos separadas no se juntan: la más grande.
        assertEquals(
            listOf(box(0, 0, 100, 100), box(200, 0, 260, 60)),
            AvatarRules.clusters(listOf(box(0, 0, 100, 100), box(200, 0, 260, 60))),
        )
        // Tres que se encadenan, sí.
        assertEquals(listOf(box(0, 0, 160, 160)), AvatarRules.clusters(listOf(box(0, 0, 80, 80), box(100, 100, 160, 160), box(60, 60, 120, 120))))
    }
}
