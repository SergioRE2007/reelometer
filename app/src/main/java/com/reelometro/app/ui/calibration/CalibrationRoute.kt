package com.reelometro.app.ui.calibration

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.copy.CalibrationCopy

/**
 * Calibración (oculta: 7 toques en la versión, en Ajustes). Consola en vivo de lo que recibe el
 * servicio, para ajustar `FeedRules` en un móvil real. La captura solo está encendida mientras esta
 * pantalla existe (también si se sale a TikTok con ella abierta, que es justo para lo que sirve).
 */
@Composable
fun CalibrationRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: CalibrationViewModel = viewModel { CalibrationViewModel.create(context) }
    val state by vm.state.collectAsStateWithLifecycle()

    DisposableEffect(vm) {
        vm.start()
        onDispose { vm.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    CalibrationScreen(
        state = state,
        onBack = onBack,
        onCapture = vm::setCapturing,
        onPlatform = vm::setPlatform,
        onType = vm::setType,
        onCopy = {
            if (copyToClipboard(context, vm.exportText())) vm.onCopied()
        },
        onShare = { shareText(context, vm.exportText()) },
        onClear = vm::clear,
    )
}

private fun copyToClipboard(context: Context, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(CalibrationCopy.SHARE_TITLE, text))
}.isSuccess

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, CalibrationCopy.SHARE_TITLE)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, CalibrationCopy.SHARE_TITLE)) }
}
