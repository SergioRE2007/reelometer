package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diario del servicio: cuándo se conectó y se desconectó, y si el que lo apagó fue el usuario o el sistema. */
class ServiceJournalRulesTest {

    private val empty = ServiceJournalState()
    private val hour = 3_600_000L

    private fun ServiceJournalState.connect(at: Long, kind: ConnectionKind = ConnectionKind.WARM) =
        ServiceJournalRules.connected(this, at, kind)

    private fun ServiceJournalState.disconnect(at: Long, kind: DisconnectionKind) =
        ServiceJournalRules.disconnected(this, at, kind)

    @Test
    fun `la conexion dice si el proceso estaba vivo, lo arranco Android o venia de reiniciar el movil`() {
        val boot = ServiceHealth.BOOT_WINDOW_MS
        assertEquals(ConnectionKind.WARM, ServiceJournalRules.connectionKind(processAgeMs = 10 * 60_000L, uptimeAtProcessStartMs = hour))
        assertEquals("aunque el móvil esté recién encendido", ConnectionKind.WARM, ServiceJournalRules.connectionKind(60_000, 30_000))
        assertEquals(ConnectionKind.COLD, ServiceJournalRules.connectionKind(processAgeMs = 800, uptimeAtProcessStartMs = hour))
        assertEquals(ConnectionKind.COLD, ServiceJournalRules.connectionKind(ServiceJournalRules.COLD_START_MS, boot))
        assertEquals(ConnectionKind.BOOT, ServiceJournalRules.connectionKind(processAgeMs = 800, uptimeAtProcessStartMs = boot - 1))
    }

    @Test
    fun `guarda lo mas reciente primero y solo lo ultimo`() {
        var state = empty
        repeat(ServiceJournalRules.MAX + 3) { state = state.connect(it * hour).disconnect(it * hour + 1, DisconnectionKind.TURNED_OFF) }
        assertEquals(ServiceJournalRules.MAX, state.connections.size)
        assertEquals(ServiceJournalRules.MAX, state.disconnections.size)
        assertEquals((ServiceJournalRules.MAX + 2) * hour, state.lastConnection!!.at)
        assertEquals(state.connections.map { it.at }.sortedDescending(), state.connections.map { it.at })
        assertEquals("lo más viejo se cae", 3 * hour, state.connections.last().at)
    }

    @Test
    fun `una conexion apuntada tarde queda en su sitio por la hora`() {
        // La conexión se escribe fuera del hilo principal: puede llegar al disco después de la desconexión.
        val state = empty.disconnect(2_000, DisconnectionKind.TURNED_OFF).connect(1_000).connect(500)
        assertEquals(listOf(1_000L, 500L), state.connections.map { it.at })
    }

    @Test
    fun `se guarda como texto y vuelve igual`() {
        val state = empty
            .connect(1_000, ConnectionKind.BOOT)
            .connect(2_000, ConnectionKind.COLD)
            .connect(3_000, ConnectionKind.WARM)
            .disconnect(2_500, DisconnectionKind.TURNED_OFF)
            .disconnect(3_500, DisconnectionKind.UNBOUND)
        val text = ServiceJournalRules.encode(state)
        assertEquals("C:3000:W;C:2000:C;C:1000:B;D:3500:S;D:2500:U", text)
        assertEquals(state, ServiceJournalRules.decode(text))
    }

    @Test
    fun `lo que no se entiende se salta sin fallar`() {
        assertEquals(empty, ServiceJournalRules.decode(null))
        assertEquals(empty, ServiceJournalRules.decode(""))
        assertEquals(empty, ServiceJournalRules.decode("basura;C:no:W;C:1:Z;X:1:W;C:1;D:5:WW"))
        val mixed = ServiceJournalRules.decode("C:1000:W;;basura;D:900:U")
        assertEquals(listOf(ServiceConnection(1_000, ConnectionKind.WARM)), mixed.connections)
        assertEquals(listOf(ServiceDisconnection(900, DisconnectionKind.TURNED_OFF)), mixed.disconnections)
    }

    @Test
    fun `sin haber funcionado nunca no hay nada que perder`() {
        assertFalse(ServiceJournalRules.lostBySystem(enabled = false, empty))
        assertNull(empty.lastConnection)
    }

    @Test
    fun `activado no esta perdido`() {
        assertFalse(ServiceJournalRules.lostBySystem(enabled = true, empty.connect(1_000)))
    }

    @Test
    fun `apagado sin que nadie lo apagara lo quito el sistema`() {
        // Forzar la detención mata el proceso y saca el servicio de los activados: no queda ninguna desconexión.
        assertTrue(ServiceJournalRules.lostBySystem(enabled = false, empty.connect(1_000)))
    }

    @Test
    fun `apagado en Ajustes con el servicio en marcha lo apago el usuario`() {
        val state = empty.connect(1_000).disconnect(5_000, DisconnectionKind.TURNED_OFF)
        assertFalse(ServiceJournalRules.lostBySystem(enabled = false, state))
    }

    @Test
    fun `un apagado anterior a la ultima conexion no cuenta`() {
        // Lo apagó, lo volvió a encender y después lo cerró el sistema.
        val state = empty.connect(1_000).disconnect(5_000, DisconnectionKind.TURNED_OFF).connect(9_000)
        assertTrue(ServiceJournalRules.lostBySystem(enabled = false, state))
    }

    @Test
    fun `desenlazado por Android y luego apagado tambien es del sistema`() {
        val state = empty.connect(1_000).disconnect(5_000, DisconnectionKind.UNBOUND)
        assertTrue(ServiceJournalRules.lostBySystem(enabled = false, state))
    }
}
