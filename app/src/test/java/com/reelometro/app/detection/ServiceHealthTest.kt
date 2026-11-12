package com.reelometro.app.detection

import com.reelometro.app.detection.ServiceHealth.Companion.GRACE_MS
import com.reelometro.app.work.ServiceHealthRules
import com.reelometro.app.work.ServiceHealthRules.Action
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Servicio activado pero sin funcionar (interruptor encendido, 0 eventos: pasa en algunos Xiaomi). */
class ServiceHealthTest {

    @Test
    fun `apagado en Ajustes es OFF aunque algo diga conectado`() {
        assertEquals(ServiceHealth.OFF, ServiceHealth.of(enabled = false, connected = false, startFailed = false, processAgeMs = 0))
        assertEquals(ServiceHealth.OFF, ServiceHealth.of(enabled = false, connected = true, startFailed = false, processAgeMs = 0))
    }

    @Test
    fun `conectado es OK`() {
        assertEquals(ServiceHealth.OK, ServiceHealth.of(enabled = true, connected = true, startFailed = false, processAgeMs = 999_999))
    }

    @Test
    fun `activado y sin conectar da un margen al arrancar el proceso y luego es STALLED`() {
        assertEquals(ServiceHealth.STARTING, ServiceHealth.of(true, connected = false, startFailed = false, processAgeMs = 0))
        assertEquals(ServiceHealth.STARTING, ServiceHealth.of(true, connected = false, startFailed = false, processAgeMs = GRACE_MS - 1))
        assertEquals(ServiceHealth.STALLED, ServiceHealth.of(true, connected = false, startFailed = false, processAgeMs = GRACE_MS))
    }

    @Test
    fun `si el arranque ha fallado es STALLED sin esperar`() {
        assertEquals(ServiceHealth.STALLED, ServiceHealth.of(true, connected = false, startFailed = true, processAgeMs = 0))
    }

    @Test
    fun `tras reiniciar el móvil el margen es más largo`() {
        assertEquals(ServiceHealth.BOOT_GRACE_MS, ServiceHealth.graceFor(10_000))
        assertEquals(GRACE_MS, ServiceHealth.graceFor(10 * 60_000L))
        assertEquals(
            ServiceHealth.STARTING,
            ServiceHealth.of(true, connected = false, startFailed = false, processAgeMs = 60_000, graceMs = ServiceHealth.BOOT_GRACE_MS),
        )
        assertEquals(ServiceHealth.STALLED, ServiceHealth.of(true, connected = false, startFailed = false, processAgeMs = 60_000))
        assertEquals(
            "un fallo de arranque no espera ni tras reiniciar",
            ServiceHealth.STALLED,
            ServiceHealth.of(true, connected = false, startFailed = true, processAgeMs = 0, graceMs = ServiceHealth.BOOT_GRACE_MS),
        )
    }

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `avisa una vez al día si está STALLED`() {
        assertEquals(Action.NOTIFY, ServiceHealthRules.action(ServiceHealth.STALLED, true, true, lastNotified = null, today = today))
        assertEquals(Action.NOTIFY, ServiceHealthRules.action(ServiceHealth.STALLED, true, true, today.minusDays(1), today))
        assertEquals(Action.NOTHING, ServiceHealthRules.action(ServiceHealth.STALLED, true, true, today, today))
    }

    @Test
    fun `no avisa sin divulgación aceptada, sin permiso ni mientras arranca`() {
        assertEquals(Action.NOTHING, ServiceHealthRules.action(ServiceHealth.STALLED, disclosureAccepted = false, canNotify = true, null, today))
        assertEquals(Action.NOTHING, ServiceHealthRules.action(ServiceHealth.STALLED, disclosureAccepted = true, canNotify = false, null, today))
        assertEquals(Action.NOTHING, ServiceHealthRules.action(ServiceHealth.STARTING, true, true, null, today))
    }

    @Test
    fun `apagado por el sistema avisa una vez por cada perdida`() {
        fun off(lost: Boolean, notified: Boolean = false, visible: Boolean = false, accepted: Boolean = true, can: Boolean = true) =
            ServiceHealthRules.action(
                ServiceHealth.OFF, accepted, can, lastNotified = today, today = today,
                lost = lost, lossNotified = notified, appVisible = visible,
            )
        assertEquals("aunque hoy ya hubiera otro aviso: es otra cosa", Action.NOTIFY_LOST, off(lost = true))
        assertEquals("de esa pérdida ya se avisó: ni se repite ni se quita", Action.NOTHING, off(lost = true, notified = true))
        assertEquals("con la app en pantalla lo dice Hoy", Action.NOTHING, off(lost = true, visible = true))
        assertEquals(Action.NOTHING, off(lost = true, accepted = false))
        assertEquals(Action.NOTHING, off(lost = true, can = false))
        assertEquals("lo apagó el usuario: fuera el aviso", Action.CANCEL, off(lost = false))
    }

    @Test
    fun `perdido solo cuenta con el servicio apagado`() {
        assertEquals(Action.CANCEL, ServiceHealthRules.action(ServiceHealth.OK, true, true, null, today, lost = true))
        assertEquals(Action.NOTIFY, ServiceHealthRules.action(ServiceHealth.STALLED, true, true, null, today, lost = true))
    }

    @Test
    fun `funcionando o apagado quita el aviso`() {
        assertEquals(Action.CANCEL, ServiceHealthRules.action(ServiceHealth.OK, true, true, today, today))
        assertEquals(Action.CANCEL, ServiceHealthRules.action(ServiceHealth.OFF, true, true, today, today))
    }

    @Test
    fun `la ventana de reinicio cubre un arranque con PIN`() {
        assertEquals("proceso nacido a los 3 min de encendido", ServiceHealth.BOOT_GRACE_MS, ServiceHealth.graceFor(3 * 60_000L))
        assertEquals(GRACE_MS, ServiceHealth.graceFor(ServiceHealth.BOOT_WINDOW_MS))
    }

    @Test
    fun `tras reiniciar y seguir sin conectar, la primera revisión aplaza y la segunda avisa`() {
        assertEquals(
            Action.RETRY,
            ServiceHealthRules.action(ServiceHealth.STALLED, true, true, null, today, bootRetryPending = true),
        )
        assertEquals(
            Action.NOTIFY,
            ServiceHealthRules.action(ServiceHealth.STALLED, true, true, null, today, bootRetryPending = false),
        )
        assertEquals(
            "sin divulgación o sin permiso no hay nada que aplazar",
            Action.NOTHING,
            ServiceHealthRules.action(ServiceHealth.STALLED, false, true, null, today, bootRetryPending = true),
        )
        assertEquals(
            "si conectó mientras tanto, se quita el aviso",
            Action.CANCEL,
            ServiceHealthRules.action(ServiceHealth.OK, true, true, null, today, bootRetryPending = true),
        )
    }
}
