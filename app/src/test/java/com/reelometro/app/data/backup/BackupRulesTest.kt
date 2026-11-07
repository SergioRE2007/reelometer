package com.reelometro.app.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate

class BackupRulesTest {

    @Test
    fun `el nombre lleva la fecha ISO y se lee de vuelta`() {
        val date = LocalDate.of(2026, 11, 7)
        assertEquals("reelometer-auto-2026-11-07.csv", BackupRules.fileName(date))
        assertEquals(date, BackupRules.dateOf(BackupRules.fileName(date)))
    }

    @Test
    fun `las copias de antes del cambio de nombre se siguen reconociendo`() {
        assertEquals(LocalDate.of(2026, 11, 7), BackupRules.dateOf("reelometro-auto-2026-11-07.csv"))
    }

    @Test
    fun `solo cuentan como copias los nombres exactos de la app`() {
        for (name in listOf("reelometer", "reelometro")) {
            assertNull(BackupRules.dateOf("$name-2026-11-07.csv")) // exportado a mano
            assertNull(BackupRules.dateOf("$name-auto-2026-11-07 (1).csv")) // duplicado del proveedor
            assertNull(BackupRules.dateOf("$name-auto-2026-11-07.csv.bak"))
            assertNull(BackupRules.dateOf("$name-auto-2026-13-40.csv")) // fecha imposible
            assertNull(BackupRules.dateOf("x-$name-auto-2026-11-07.csv"))
        }
        assertNull(BackupRules.dateOf("Reelometer-auto-2026-11-07.csv"))
        assertNull(BackupRules.dateOf("Reelometro-auto-2026-11-07.csv"))
        assertNull(BackupRules.dateOf("reelometra-auto-2026-11-07.csv"))
    }

    @Test
    fun `las copias viejas y las nuevas comparten el tope de 4`() {
        val oldNames = (0L until 3L).map { "reelometro-auto-${LocalDate.of(2026, 9, 1).plusWeeks(it)}.csv" }
        val newNames = (0L until 3L).map { BackupRules.fileName(LocalDate.of(2026, 10, 1).plusWeeks(it)) }
        val deleted = BackupRules.toDelete(oldNames + newNames + "reelometro-2026-09-01.csv")
        assertEquals(setOf(oldNames[0], oldNames[1]), deleted.toSet())
    }

    @Test
    fun `al rotar se quedan las 4 mas recientes y nunca se toca otro fichero`() {
        val copies = (0L until 6L).map { BackupRules.fileName(LocalDate.of(2026, 10, 1).plusWeeks(it)) }
        val others = listOf("reelometer-2026-10-01.csv", "reelometro-2026-10-01.csv", "fotos.zip", "reelometro-auto-2020-01-01 (1).csv")
        val deleted = BackupRules.toDelete((copies + others).shuffled(java.util.Random(7)))
        assertEquals(setOf(copies[0], copies[1]), deleted.toSet())
        assertTrue(BackupRules.toDelete(copies.take(4)).isEmpty())
        assertTrue(BackupRules.toDelete(others).isEmpty())
        assertEquals(copies.take(5).toSet(), BackupRules.toDelete(copies, keep = 1).toSet())
    }

    @Test
    fun `sin permiso o sin carpeta es NO_ACCESS y el resto FAILED`() {
        assertEquals(BackupError.NO_ACCESS, BackupRules.classify(SecurityException("revocado")))
        assertEquals(BackupError.NO_ACCESS, BackupRules.classify(FileNotFoundException("borrada")))
        assertEquals(BackupError.NO_ACCESS, BackupRules.classify(IllegalArgumentException("no existe")))
        assertEquals(BackupError.FAILED, BackupRules.classify(IOException("sin espacio")))
        assertEquals(BackupError.FAILED, BackupRules.classify(RuntimeException("otra cosa")))
    }
}

/** Carpeta en memoria: nombre → contenido. */
private class FakeFolder(names: List<String> = emptyList()) : BackupFolder {
    val files = names.associateWith { "viejo" }.toMutableMap()
    var writeError: Exception? = null
    var listError: Exception? = null

    override fun list(): List<String> {
        listError?.let { throw it }
        return files.keys.toList()
    }

    override fun openForWrite(name: String): OutputStream {
        writeError?.let { throw it }
        return object : ByteArrayOutputStream() {
            override fun close() {
                files[name] = toString(Charsets.UTF_8)
            }
        }
    }

    override fun delete(name: String): Boolean = files.remove(name) != null
}

class BackupRunnerTest {

    private val today = LocalDate.of(2026, 11, 7)

    private suspend fun run(folder: BackupFolder, hasData: Boolean = true, export: (OutputStream) -> Int = { it.write("csv".toByteArray()); 12 }) =
        BackupRunner.run(folder, today, hasData = { hasData }, export = { export(it) })

    @Test
    fun `escribe la copia de hoy y borra las viejas que sobran`() = runTest {
        val old = (1L..4L).map { BackupRules.fileName(today.minusWeeks(it)) }
        val folder = FakeFolder(old + "notas.txt" + "reelometro-2026-01-01.csv")
        val outcome = run(folder)
        assertEquals(BackupOutcome.Done("reelometer-auto-2026-11-07.csv", 12, listOf(old[3])), outcome)
        assertEquals("csv", folder.files["reelometer-auto-2026-11-07.csv"])
        assertEquals(setOf(old[0], old[1], old[2], "notas.txt", "reelometro-2026-01-01.csv", BackupRules.fileName(today)), folder.files.keys)
    }

    @Test
    fun `dos copias el mismo dia sobrescriben la de hoy`() = runTest {
        val folder = FakeFolder(listOf(BackupRules.fileName(today)))
        run(folder)
        assertEquals(mapOf(BackupRules.fileName(today) to "csv"), folder.files)
    }

    @Test
    fun `sin videos no escribe ni borra nada`() = runTest {
        val old = (1L..6L).map { BackupRules.fileName(today.minusWeeks(it)) }
        val folder = FakeFolder(old)
        assertEquals(BackupOutcome.Empty, run(folder, hasData = false))
        assertEquals(old.toSet(), folder.files.keys)
    }

    @Test
    fun `sin permiso devuelve NO_ACCESS sin reventar y no borra nada`() = runTest {
        val old = (1L..6L).map { BackupRules.fileName(today.minusWeeks(it)) }
        val folder = FakeFolder(old).apply { writeError = SecurityException("permiso retirado") }
        assertEquals(BackupOutcome.Failed(BackupError.NO_ACCESS), run(folder))
        assertEquals(old.toSet(), folder.files.keys)
    }

    @Test
    fun `un fallo al exportar devuelve FAILED`() = runTest {
        val outcome = run(FakeFolder()) { throw IOException("disco lleno") }
        assertEquals(BackupOutcome.Failed(BackupError.FAILED), outcome)
    }

    @Test
    fun `si no se puede listar la carpeta, la copia vale igual y no se borra nada`() = runTest {
        val old = (1L..6L).map { BackupRules.fileName(today.minusWeeks(it)) }
        val folder = FakeFolder(old).apply { listError = IOException("lento") }
        val outcome = run(folder)
        assertEquals(BackupOutcome.Done(BackupRules.fileName(today), 12, emptyList()), outcome)
        assertEquals(7, folder.files.size)
    }
}
