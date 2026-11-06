package com.reelometro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un vídeo corto visto.
 *
 * Índices (los planes de consulta se comprueban en `QueryPlanTest`):
 * - `startedAt`: lista de recientes (Calibración) y exportación en orden cronológico.
 * - `day`: estrecho; primer día, vídeos por día de todo el historial (rachas) y búsquedas por día.
 * - `day, platform, category, minuteOfDay, durationMs, creator`: índice **cubriente** de todas las
 *   agregaciones por rango de días (por día, hora, plataforma, categoría, creador, comparaciones).
 *   Empieza por `day, platform`, así que también sirve de índice `(day, platform)`. Las filas llevan
 *   la descripción (texto largo), así que leer solo el índice evita tocar la tabla.
 *   No hay índice propio de `creator`: los rankings siempre van acotados por días y los resuelve
 *   este índice sin leer la tabla.
 */
@Entity(
    tableName = ReelView.TABLE,
    indices = [
        Index("startedAt"),
        Index("day"),
        Index("day", "platform", "category", "minuteOfDay", "durationMs", "creator"),
    ],
)
data class ReelView(
    /** Lo genera el detector y es único. */
    @PrimaryKey val id: Long,
    /** Nombre del enum [Platform]. */
    val platform: String,
    /** Instante de inicio (epoch ms). */
    val startedAt: Long,
    val durationMs: Long = 0,
    val creator: String? = null,
    val caption: String? = null,
    /** Nombre del enum [Category]. */
    val category: String = Category.OTROS.name,
    /** Día local (epochDay) en que empezó, con la zona del sistema al registrarlo. */
    val day: Long,
    /** Minuto local del día (0–1439) en que empezó. */
    val minuteOfDay: Int,
) {
    companion object {
        const val TABLE = "reel_views"
    }
}

/**
 * Una pulsación de «compartir» en un vídeo corto: que ocurrió y en qué vídeo, si se sabe. A quién se
 * envía va aparte, en [ShareRecipient] (solo Instagram y solo con el ajuste encendido). Sin clave
 * foránea: puede llegar antes que la fila del vídeo.
 */
@Entity(
    tableName = ShareEvent.TABLE,
    indices = [Index("day"), Index("reelId")],
)
data class ShareEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre del enum [Platform]. */
    val platform: String,
    /** Instante (epoch ms). */
    val at: Long,
    /** Día local (epochDay). */
    val day: Long,
    /** Minuto local del día (0–1439). */
    val minuteOfDay: Int,
    /** Vídeo en el que se compartió ([ReelView.id]), si se conoce. */
    val reelId: Long?,
) {
    companion object {
        const val TABLE = "share_events"
    }
}

/**
 * Un envío a una persona o grupo desde la hoja de compartir de Instagram (v3). Solo se guarda con el
 * ajuste «A quién envías» encendido ([com.reelometro.app.data.settings.AppSettings.recordRecipients]).
 * [recipient] es el nombre tal y como sale en la hoja; nunca se guarda el mensaje ni nada del chat.
 *
 * Índices: `day, recipient, platform` es cubriente para el ranking por rango de días; `reelId` para borrar los
 * envíos de un vídeo descartado.
 */
@Entity(
    tableName = ShareRecipient.TABLE,
    indices = [Index("day", "recipient", "platform"), Index("reelId")],
)
data class ShareRecipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre del enum [Platform] (hoy siempre `INSTAGRAM`). */
    val platform: String,
    /** Instante (epoch ms). */
    val at: Long,
    /** Día local (epochDay). */
    val day: Long,
    /** Minuto local del día (0–1439). */
    val minuteOfDay: Int,
    /** Nombre de la persona o del grupo, recortado a [ReelRepository.RECIPIENT_MAX] caracteres. */
    val recipient: String,
    /** Vídeo que se envió ([ReelView.id]), si se conoce. */
    val reelId: Long?,
) {
    companion object {
        const val TABLE = "share_recipients"
    }
}
