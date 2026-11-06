package com.reelometro.app.debug

import android.content.Context
import androidx.room.withTransaction
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ShareEvent
import com.reelometro.app.data.ShareRecipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Herramientas de desarrollo accesibles desde la UI (Ajustes > sección debug) y desde [DebugReceiver].
 * Existe una versión vacía en `src/release` con `AVAILABLE = false`: el código de `main` puede llamarla
 * sin `if (BuildConfig.DEBUG)` y R8 elimina todo en release.
 */
object DebugTools {
    const val AVAILABLE = true

    /** Semilla por defecto: los mismos datos en cada ejecución (para un mismo «ahora»). */
    const val DEFAULT_SEED = SampleDataGenerator.DEFAULT_SEED

    private const val INSERT_CHUNK = 2_000

    /**
     * **Sustituye** los datos de uso por un historial de ejemplo realista: `weeks` semanas hasta
     * ahora mismo (hoy a medias, nada en el futuro). Todo en una transacción, así que las pantallas
     * abiertas se recalculan una sola vez. Devuelve el número de vídeos generados.
     */
    suspend fun seedSampleData(context: Context, weeks: Int = 8, seed: Long = DEFAULT_SEED): Int {
        val db = ReelometroApp.from(context).database
        val data = withContext(Dispatchers.Default) {
            SampleDataGenerator(seed, ZoneId.systemDefault()).generate(weeks, Instant.now())
        }
        db.withTransaction {
            val dao = db.reelDao()
            dao.deleteAll()
            data.views.chunked(INSERT_CHUNK).forEach { dao.insertAll(it) }
            if (data.shares.isNotEmpty()) dao.insertShares(data.shares)
            val recipients = sampleRecipients(data.shares, seed)
            if (recipients.isNotEmpty()) dao.insertRecipients(recipients)
        }
        return data.views.size
    }

    /** «A quién envías» de ejemplo: cada compartido de Instagram va a una o dos personas o grupos. */
    private fun sampleRecipients(shares: List<ShareEvent>, seed: Long): List<ShareRecipient> {
        val random = Random(seed)
        return shares.filter { it.platform == Platform.INSTAGRAM.name }.flatMap { share ->
            val count = if (random.nextInt(4) == 0) 2 else 1
            SAMPLE_RECIPIENTS.shuffled(random).take(count).map {
                ShareRecipient(
                    platform = share.platform, at = share.at, day = share.day, minuteOfDay = share.minuteOfDay,
                    recipient = it, reelId = share.reelId,
                )
            }
        }
    }

    /** Con pesos: los primeros salen más (repetidos en la lista). */
    private val SAMPLE_RECIPIENTS = listOf(
        "Los del piso", "Los del piso", "Los del piso", "Marta", "Marta", "Primos", "Dani", "Lucía",
    )

    /** Borra todos los datos de uso (no los ajustes). */
    suspend fun clearData(context: Context) {
        ReelometroApp.from(context).repository.clearAll()
    }
}
