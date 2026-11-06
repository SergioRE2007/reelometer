package com.reelometro.app.ui.wrapped

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Reloj de la historia como lógica pura: en qué diapositiva estamos y cuánto llevamos en ella.
 * La pausa (mantener pulsado, app en segundo plano, «reducir animaciones») no vive aquí: quien
 * llama simplemente deja de llamar a [tick].
 */
internal object StoryTimeline {

    /** Tiempo por diapositiva con avance automático. */
    const val SLIDE_MS = 6_000L

    /**
     * Un fotograma como mucho cuenta esto: un tirón (recolector, vuelta de segundo plano) no se
     * salta diapositivas.
     */
    const val MAX_FRAME_MS = 100L

    data class Position(val index: Int, val elapsedMs: Long) {
        companion object {
            val START = Position(0, 0)
        }
    }

    /**
     * Avanza [deltaMs]. Al completar una diapositiva pasa a la siguiente desde 0; en la última se
     * queda llena (la historia no se cierra sola: ahí están COMPARTIR y GUARDAR).
     */
    fun tick(pos: Position, deltaMs: Long, count: Int, slideMs: Long = SLIDE_MS): Position {
        if (count <= 0) return pos
        val p = clamp(pos, count)
        val elapsed = p.elapsedMs + deltaMs.coerceIn(0, MAX_FRAME_MS)
        return when {
            elapsed < slideMs -> p.copy(elapsedMs = elapsed)
            p.index < count - 1 -> Position(p.index + 1, 0)
            else -> p.copy(elapsedMs = slideMs)
        }
    }

    /** Siguiente diapositiva; en la última no hace nada. */
    fun next(pos: Position, count: Int): Position {
        val p = clamp(pos, count)
        return if (p.index < count - 1) Position(p.index + 1, 0) else p
    }

    /** Diapositiva anterior; en la primera vuelve a empezarla. */
    fun previous(pos: Position, count: Int): Position = Position((clamp(pos, count).index - 1).coerceAtLeast(0), 0)

    /** Relleno (0–1) del segmento [segment] de la barra de progreso. */
    fun segmentProgress(pos: Position, segment: Int, slideMs: Long = SLIDE_MS): Float = when {
        segment < pos.index -> 1f
        segment > pos.index -> 0f
        slideMs <= 0 -> 1f
        else -> (pos.elapsedMs.toFloat() / slideMs).coerceIn(0f, 1f)
    }

    /** La última diapositiva ya se ha completado: no hace falta seguir contando. */
    fun isFinished(pos: Position, count: Int, slideMs: Long = SLIDE_MS): Boolean =
        count <= 0 || (pos.index >= count - 1 && pos.elapsedMs >= slideMs)

    /** Toque en la pantalla: el tercio izquierdo vuelve atrás; el resto avanza. */
    fun isBackTap(x: Float, width: Float): Boolean = width > 0f && x < width / 3f

    private fun clamp(pos: Position, count: Int): Position =
        if (pos.index in 0 until count) pos else Position(pos.index.coerceIn(0, (count - 1).coerceAtLeast(0)), 0)
}

/**
 * Estado observable de la historia para Compose: [index] provoca recomposición (cambia la
 * diapositiva); [elapsedMs] solo se lee en la fase de dibujo (barra de progreso).
 */
@Stable
internal class StoryPlayer(val count: Int, initialIndex: Int = 0) {
    var index by mutableIntStateOf(initialIndex.coerceIn(0, (count - 1).coerceAtLeast(0)))
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    private var position: StoryTimeline.Position
        get() = StoryTimeline.Position(index, elapsedMs)
        set(value) {
            index = value.index
            elapsedMs = value.elapsedMs
        }

    /** Devuelve `true` si la historia ha llegado al final (el bucle de fotogramas puede parar). */
    fun tick(deltaMs: Long): Boolean {
        position = StoryTimeline.tick(position, deltaMs, count)
        return StoryTimeline.isFinished(position, count)
    }

    fun next() {
        position = StoryTimeline.next(position, count)
    }

    fun previous() {
        position = StoryTimeline.previous(position, count)
    }

    /** Segmento lleno o vacío sin reloj («reducir animaciones»: la actual cuenta como completa). */
    fun progress(segment: Int, static: Boolean): Float =
        if (static) (if (segment <= index) 1f else 0f) else StoryTimeline.segmentProgress(position, segment)

    val isLast: Boolean get() = index >= count - 1
}
