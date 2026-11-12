package com.reelometro.app.widget

import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.WidgetCopy
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.ui.components.flap.FlapCell
import com.reelometro.app.ui.components.flap.FlapContent
import com.reelometro.app.ui.components.flap.FlapPad
import com.reelometro.app.ui.components.flap.flapCells
import java.time.LocalDate

/** Lo que enseña el widget: la cuenta de hoy y si el servicio está contando. */
internal data class WidgetData(
    val date: LocalDate,
    val count: Int,
    val totalMs: Long,
    /** Objetivo diario («como mucho N»); `null` = sin objetivo. */
    val goal: Int?,
    /** El servicio de accesibilidad está activado en los ajustes del sistema. */
    val serviceOn: Boolean,
    /** Estado de Flap ([flapFor]); `null` con Flap apagado en Ajustes: no se pinta. */
    val flap: FlapMood? = null,
) {
    companion object {
        /**
         * Flap del widget: el mismo estado que la pestaña FLAP, solo con los vídeos de hoy frente a su
         * aguante (el objetivo o, sin objetivo, [FlapRules.DEFAULT_LIMIT]); sin historial. `null` con
         * Flap apagado en Ajustes ([enabled], el ajuste `passportEnabled`).
         */
        fun flapFor(enabled: Boolean, count: Int, goal: Int?): FlapMood? =
            if (enabled) FlapRules.moodFor(count, FlapRules.limitFor(goal)) else null

        /** Vista previa del selector de widgets: la cifra de referencia de docs/DESIGN.md §8. */
        val PREVIEW = WidgetData(
            date = LocalDate.of(2026, 3, 10),
            count = 212,
            totalMs = 47 * 60_000L,
            goal = null,
            serviceOn = true,
        )
    }
}

/**
 * Qué pinta el widget, ya decidido (lógica pura, sin Glance): las fichas con las mismas reglas que
 * el marcador de la app (`flapCells`: mínimo 3 columnas, ceros a la izquierda, punto de millar) y
 * las líneas de texto, en mayúsculas de `copy/`.
 */
internal data class WidgetModel(
    val cells: List<FlapCell>,
    /** Las fichas con `position >= dimFrom` son relleno (ceros a la izquierda, guiones): van apagadas. */
    val dimFrom: Int,
    /** «HOY». */
    val label: String,
    /** Bajo el marcador: «47 MIN», o «SIN SEÑAL» si el servicio está desactivado. */
    val detail: String?,
    val detailAlert: Boolean,
    /** Frente al objetivo, a la derecha: «QUEDAN 38», «EN EL LÍMITE», «+62 PASADO». */
    val status: String?,
    val statusAlert: Boolean,
    /** Parte del objetivo consumida, 0–1 (`null` = sin objetivo). */
    val goalFraction: Float?,
    /** Lo que lee TalkBack. */
    val description: String,
    /** Flap, si hay que pintarlo (solo cabe en el tamaño grande: lo decide [WidgetLayout]). */
    val flap: FlapMood? = null,
) {
    companion object {
        const val MIN_COLUMNS = 3

        fun of(data: WidgetData?): WidgetModel = when {
            data == null -> blank(detail = null, description = StateCopy.LOADING_DESCRIPTION)
            !data.serviceOn -> blank(detail = WidgetCopy.NO_SIGNAL, description = WidgetCopy.DESCRIPTION_OFF)
            else -> {
                val count = data.count.coerceAtLeast(0)
                val goal = data.goal?.takeIf { it > 0 }
                WidgetModel(
                    cells = flapCells(FlapContent.Number(count.toLong()), MIN_COLUMNS, FlapPad.Zeros),
                    dimFrom = count.toString().length,
                    label = WidgetCopy.LABEL,
                    detail = WidgetCopy.time(data.totalMs),
                    detailAlert = false,
                    status = goal?.let { WidgetCopy.goalStatus(count, it) },
                    statusAlert = goal != null && count > goal,
                    goalFraction = goal?.let { (count.toFloat() / it).coerceIn(0f, 1f) },
                    description = WidgetCopy.description(count, data.totalMs, goal),
                    flap = data.flap,
                )
            }
        }

        /** Fichas en blanco («– – –»), apagadas: cargando o sin señal. */
        private fun blank(detail: String?, description: String) = WidgetModel(
            cells = flapCells(FlapContent.Blank, MIN_COLUMNS, FlapPad.Zeros),
            dimFrom = 0,
            label = WidgetCopy.LABEL,
            detail = detail,
            detailAlert = detail != null,
            status = null,
            statusAlert = false,
            goalFraction = null,
            description = description,
        )
    }
}
