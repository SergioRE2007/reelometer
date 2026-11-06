package com.reelometro.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/** Pantalla provisional mientras se construye la definitiva. */
@Composable
internal fun PlaceholderScreen(name: String) {
    Box(
        Modifier.fillMaxSize().background(ReelTheme.colors.bg).safeDrawingPadding().padding(Space.screen),
    ) {
        Text(name.uppercase(), style = ReelTheme.type.label, color = ReelTheme.colors.textMuted)
    }
}
