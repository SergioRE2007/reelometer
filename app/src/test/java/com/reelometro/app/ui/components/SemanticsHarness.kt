@file:OptIn(InternalComposeUiApi::class)

package com.reelometro.app.ui.components

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.reelometro.app.ui.theme.ReelTheme
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Arnés mínimo para comprobar la semántica (lo que lee TalkBack) de un componente sin `ui-test`:
 * monta [content] en una actividad de Robolectric y devuelve la raíz del árbol de semántica.
 * Robolectric usa gráficos «legacy» (sin nativos), así que solo sirve para semántica y posiciones
 * relativas, nunca para medidas de texto.
 */
internal fun renderSemantics(
    fontScale: Float = 1f,
    merged: Boolean = true,
    content: @Composable () -> Unit,
): SemanticsNode {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    activity.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            ReelTheme(darkTheme = true) { content() }
        }
    }
    val looper = shadowOf(Looper.getMainLooper())
    looper.idle()
    val root = findRoot(activity.window.decorView)
        ?: error("No hay ningún ViewRootForTest: ¿se montó la composición?")
    looper.idleFor(Duration.ofMillis(200))
    root.measureAndLayoutForTest()
    looper.idle()
    return if (merged) root.semanticsOwner.rootSemanticsNode else root.semanticsOwner.unmergedRootSemanticsNode
}

private fun findRoot(view: View): ViewRootForTest? {
    if (view is ViewRootForTest) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) findRoot(view.getChildAt(i))?.let { return it }
    }
    return null
}

internal fun SemanticsNode.all(): List<SemanticsNode> = listOf(this) + children.flatMap { it.all() }

/** Lo que lee TalkBack de este nodo: su descripción o, si no, su texto. */
internal fun SemanticsNode.spoken(): String? =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
