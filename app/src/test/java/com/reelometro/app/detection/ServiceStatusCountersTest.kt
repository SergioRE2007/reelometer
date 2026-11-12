package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Test

/** Contadores de la prueba de un minuto (ServiceStatus es global: se miran diferencias). */
class ServiceStatusCountersTest {

    @Test
    fun `cuenta todos los eventos aunque lastEventAt solo se publique una vez por segundo`() {
        val before = ServiceStatus.eventCount()
        repeat(5) { ServiceStatus.onEvent(1_000_000L) }
        assertEquals(before + 5, ServiceStatus.eventCount())
    }

    @Test
    fun `cuenta los vídeos nuevos`() {
        val before = ServiceStatus.videoCount.value
        ServiceStatus.onVideo(1_000_000L)
        ServiceStatus.onVideo(1_000_500L)
        assertEquals(before + 2, ServiceStatus.videoCount.value)
    }
}
