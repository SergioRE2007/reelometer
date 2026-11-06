package com.reelometro.app.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.reelometro.app.ui.components.flap.flapShade
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.ui.theme.LightReelColors
import com.reelometro.app.ui.theme.ReelColors

/**
 * Colores del widget: los tokens de la app (docs/DESIGN.md §1) en pares día/noche. En API 31+ el
 * sistema elige el par según el modo oscuro sin volver a pintar el widget; antes, al pintarlo.
 */
internal object WidgetColors {
    val bg: ColorProvider = pair { it.bg }
    val tile: ColorProvider = pair { it.bgSunken }
    val text: ColorProvider = pair { it.text }
    val textMuted: ColorProvider = pair { it.textMuted }
    val textFaint: ColorProvider = pair { it.textFaint }
    val signal: ColorProvider = pair { it.signal }
    val signalText: ColorProvider = pair { it.signalText }

    /** Sombra estática de la mitad inferior de las fichas (la misma que dibuja `FlapBoard`). */
    val tileShade: ColorProvider = pair { flapShade(it).let { c -> c.copy(alpha = c.alpha * STATIC_SHADE) } }

    /** `STATIC_SHADE` de `FlapBoard`: la mitad de abajo, apenas más oscura. */
    private const val STATIC_SHADE = 0.07f

    private fun pair(token: (ReelColors) -> Color): ColorProvider =
        ColorProvider(day = token(LightReelColors), night = token(DarkReelColors))
}
