package com.reelometro.app.detection

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Que un fallo del servicio se apunte en vez de cerrar la app (corrutinas, hilo principal y `Error`). */
class ServiceGuardTest {

    private val failures = ServiceFailures(clock = { 1_234L })

    /** Un ámbito como los del servicio: sin la red, el fallo llegaría al hilo y tumbaría el proceso (aquí, el test). */
    private fun scope(onFailure: (kotlin.coroutines.CoroutineContext, Throwable) -> Unit = { _, _ -> }) =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + ServiceGuard.handler("lectura de pantalla", failures, onFailure))

    @Test
    fun `un fallo sin capturar en un launch se apunta y no sale del ámbito`() {
        val uncaught = ArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            scope().launch { throw IllegalStateException("boom") }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertTrue("no llega al hilo: ${uncaught.firstOrNull()}", uncaught.isEmpty())
        val s = failures.snapshot()
        assertEquals(1, s.count)
        assertEquals(1_234L, s.lastAt)
        assertEquals("lectura de pantalla", s.lastWhat)
        assertEquals("IllegalStateException", s.lastError)
    }

    @Test
    fun `un fallo no para a las demas corrutinas del ámbito`() {
        val scope = scope()
        scope.launch { throw IllegalStateException() }
        var ran = false
        scope.launch { ran = true }
        assertTrue("el ámbito sigue vivo (SupervisorJob)", ran)
    }

    @Test
    fun `la corrutina con nombre se apunta con el suyo y avisa a quien lo pidio`() {
        val name = CoroutineName("paso del envío rápido")
        var told: CoroutineName? = null
        scope { context, _ -> told = context[CoroutineName] }.launch(name) { throw IllegalArgumentException() }
        assertEquals("paso del envío rápido", failures.snapshot().lastWhat)
        assertEquals("quien crea la red puede soltar lo que la corrutina dejó a medias", name, told)
    }

    @Test
    fun `si el aviso tambien falla, no pasa nada`() {
        scope { _, _ -> error("otro fallo") }.launch { throw IllegalStateException() }
        assertEquals(1, failures.snapshot().count)
    }

    @Test
    fun `run contiene tambien los Error y dice si acabo bien`() {
        assertTrue(ServiceGuard.run("evento 4096", failures) { })
        assertEquals(0, failures.snapshot().count)

        var told: Throwable? = null
        val ok = ServiceGuard.run("evento 4096", failures, onFailure = { told = it }) { throw NoSuchMethodError("getSource") }
        assertFalse(ok)
        assertTrue(told is NoSuchMethodError)
        assertFalse(ServiceGuard.run("captura", failures) { throw OutOfMemoryError() })
        assertFalse(ServiceGuard.run("lectura", failures) { throw StackOverflowError() })
        val s = failures.snapshot()
        assertEquals(3, s.count)
        assertEquals("lectura", s.lastWhat)
        assertEquals("StackOverflowError", s.lastError)
    }

    @Test
    fun `del fallo se guarda la clase y el sitio, nunca el mensaje`() {
        ServiceGuard.run("lectura de pantalla", failures) { throw IllegalStateException("nombre leído de la pantalla: Marta") }
        val s = failures.snapshot()
        assertEquals("IllegalStateException", s.lastError)
        assertTrue("dónde saltó, en el código de la app: ${s.lastWhere}", s.lastWhere!!.startsWith("ServiceGuardTest.kt:"))
        assertFalse(s.toString(), "Marta" in s.toString())
    }

    @Test
    fun `un fallo sin marcos de la app no tiene sitio`() {
        val foreign = RuntimeException().apply { stackTrace = arrayOf(StackTraceElement("android.os.Parcel", "readException", "Parcel.java", 10)) }
        assertNull(ServiceFailures.whereOf(foreign))
        failures.record("evento 1", foreign)
        assertNull(failures.snapshot().lastWhere)
    }

    @Test
    fun `reset lo deja a cero`() {
        failures.record("evento 1", IllegalStateException())
        failures.reset()
        assertEquals(ServiceFailures.Snapshot(), failures.snapshot())
    }
}
