package com.reelometro.app.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tamaño natural de los iconos (rejilla de 24 dp). */
val ReelIconSize: Dp = 24.dp

/**
 * Pinta un icono de [ReelIcons] con un tinte plano. Sustituye a `Icon` de Material.
 *
 * @param contentDescription texto para TalkBack; `null` si el icono es decorativo
 *   (o si lo explica el texto que lo acompaña).
 */
@Composable
fun ReelIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = ReelIconSize,
) {
    val painter = rememberVectorPainter(icon)
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Box(
        modifier
            .iconSemantics(contentDescription)
            .size(size)
            .paint(painter, colorFilter = filter),
    )
}

/**
 * Variante con el tinte leído en la fase de dibujo: para colores animados (pestañas, estados)
 * sin recomponer en cada fotograma. Pinta el icono en una capa aparte y lo recolorea con SrcIn.
 */
@Composable
fun ReelIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    size: Dp = ReelIconSize,
) {
    val painter = rememberVectorPainter(icon)
    Box(
        modifier
            .iconSemantics(contentDescription)
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(tint(), blendMode = BlendMode.SrcIn)
            }
            .paint(painter),
    )
}

private fun Modifier.iconSemantics(contentDescription: String?): Modifier =
    if (contentDescription == null) this
    else semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
