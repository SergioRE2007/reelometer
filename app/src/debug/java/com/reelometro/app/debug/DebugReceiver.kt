package com.reelometro.app.debug

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.core.graphics.createBitmap
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.CsvImportOutcome
import com.reelometro.app.data.export.ImportMode
import com.reelometro.app.data.settings.ThemeMode
import com.reelometro.app.ui.support.SupportPrompt
import com.reelometro.app.ui.support.SupportPromptState
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.widget.TodayWidgetContent
import com.reelometro.app.widget.TodayWidgetReceiver
import com.reelometro.app.widget.WidgetData
import com.reelometro.app.widget.WidgetSizes
import com.reelometro.app.widget.WidgetUpdater
import com.reelometro.app.work.SummaryNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Ganchos de QA solo para debug. Cada acción deja una
 * línea en Logcat con la etiqueta `Reelometro` al terminar.
 *
 * ```
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.SEED --ei weeks 8 [--el seed 42]
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.CLEAR
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.EXPORT [--es path copia.csv]
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.IMPORT --es path copia.csv [--ez replace true]
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.ONBOARDING --ez done true
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.THEME --es mode dark
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.GOAL --ei goal 150
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.UNLOCK_CALIBRATION
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.RESET_SETTINGS
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.DISCLOSURE --ez accepted true
 * # Recorrido guiado: pendiente otra vez (sale al volver a Hoy) o, con --ez done true, marcado como visto.
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.TOUR [--ez done true]
 * # Modo pasaporte encendido o apagado (pestaña, celebraciones, aviso de nivel y Flap en el widget).
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.PASSPORT --ez enabled true
 * ```
 *
 * Widget y avisos:
 * ```
 * # Aviso diario ya, con los datos de hoy (--ez late true: el de «ayer», como si llegara pasada la
 * # medianoche). Se salta los ajustes y el estado del servicio, nunca el permiso; antes:
 * #   adb shell pm grant com.reelometro.app android.permission.POST_NOTIFICATIONS
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.NOTIFY_DAILY [--ez late true]
 * # Resumen del mes pasado (abre story:MONTH:-1).
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.NOTIFY_MONTHLY
 * # Repinta los widgets ya, sin el límite de frecuencia.
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.WIDGET_UPDATE
 * # Diálogo del launcher para fijar el widget. Android solo lo permite con la app en primer plano:
 * #   adb shell am start -n com.reelometro.app/.MainActivity   (y después, el broadcast)
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.WIDGET_PIN
 * # Los 6 tamaños del widget en claro (arriba) y en oscuro (abajo) en un PNG. Con --ez preview true,
 * # con la cifra de la vista previa y además widget-preview-{light,dark}.png (el 2×2, para previewImage).
 * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.WIDGET_SNAPSHOT [--ez preview true]
 * adb pull /sdcard/Android/data/com.reelometro.app/files/widget-snapshot.png
 * ```
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val app = ReelometroApp.from(appContext)
        val pending = goAsync()
        app.appScope.launch {
            try {
                Log.i(TAG, handle(appContext, app, action, intent))
            } catch (e: Exception) {
                Log.e(TAG, "DebugReceiver: ${action.removePrefix(PREFIX)} ha fallado", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, app: ReelometroApp, action: String, intent: Intent): String =
        when (action) {
            ACTION_SEED -> {
                val weeks = intent.getIntExtra(EXTRA_WEEKS, 8).coerceIn(1, SampleDataGenerator.MAX_WEEKS)
                val seed = intent.getLongExtra(EXTRA_SEED, DebugTools.DEFAULT_SEED)
                val count = DebugTools.seedSampleData(context, weeks, seed)
                "SEED: $count vídeos de ejemplo en $weeks semanas (semilla $seed)"
            }
            ACTION_CLEAR -> {
                DebugTools.clearData(context)
                "CLEAR: datos de uso borrados"
            }
            ACTION_EXPORT -> {
                // Como EXPORTAR CSV de Ajustes, a un fichero de getExternalFilesDir (adb pull para sacarlo).
                val base = context.getExternalFilesDir(null) ?: throw IllegalStateException("sin almacenamiento externo")
                val name = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty().ifEmpty { "reelometro-export.csv" }
                val file = File(base, name).canonicalFile
                require(file.path.startsWith(base.canonicalPath)) { "la ruta tiene que estar dentro de $base" }
                val videos = file.outputStream().use { app.repository.exportCsv(it) }
                "EXPORT: $videos vídeos en ${file.path}"
            }
            ACTION_IMPORT -> {
                // Solo ficheros dentro de getExternalFilesDir (adb push a /sdcard/Android/data/com.reelometro.app/files/).
                val base = context.getExternalFilesDir(null) ?: throw IllegalStateException("sin almacenamiento externo")
                val name = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
                require(name.isNotEmpty()) { "falta --es path <fichero dentro de $base>" }
                val file = File(base, name).canonicalFile
                require(file.path.startsWith(base.canonicalPath)) { "la ruta tiene que estar dentro de $base" }
                val mode = if (intent.getBooleanExtra(EXTRA_REPLACE, false)) ImportMode.REPLACE else ImportMode.ADD
                val outcome = file.inputStream().use { app.repository.importCsv(it, mode) }
                if (outcome is CsvImportOutcome.Done) {
                    com.reelometro.app.work.LevelAlert.onPassportEnabled(context)
                    WidgetUpdater.requestUpdate(context)
                }
                "IMPORT $mode: $outcome"
            }
            ACTION_ONBOARDING -> {
                val done = intent.getBooleanExtra(EXTRA_DONE, true)
                app.settings.setOnboardingDone(done)
                "ONBOARDING: done=$done"
            }
            ACTION_THEME -> {
                val mode = when (intent.getStringExtra(EXTRA_MODE)?.trim()?.lowercase()) {
                    "dark" -> ThemeMode.DARK
                    "light" -> ThemeMode.LIGHT
                    "system", null -> ThemeMode.SYSTEM
                    else -> throw IllegalArgumentException("modo desconocido; usa dark|light|system")
                }
                app.settings.setThemeMode(mode)
                "THEME: $mode"
            }
            ACTION_GOAL -> {
                val goal = intent.getIntExtra(EXTRA_GOAL, 0)
                app.settings.setDailyGoal(goal.takeIf { it > 0 })
                val saved = app.settings.current().dailyGoal
                "GOAL: ${saved ?: "sin objetivo"}"
            }
            ACTION_UNLOCK_CALIBRATION -> {
                val unlocked = intent.getBooleanExtra(EXTRA_UNLOCKED, true)
                app.settings.setCalibrationUnlocked(unlocked)
                "UNLOCK_CALIBRATION: unlocked=$unlocked"
            }
            ACTION_RESET_SETTINGS -> {
                app.settings.reset()
                "RESET_SETTINGS: ajustes por defecto (los datos de uso no se tocan)"
            }
            ACTION_DISCLOSURE -> {
                // Para capturar la guía de «Conectar» y el aviso CONECTAR de Hoy sin pulsar ACEPTAR.
                val accepted = intent.getBooleanExtra(EXTRA_ACCEPTED, true)
                app.settings.setDisclosureAccepted(accepted)
                "DISCLOSURE: accepted=$accepted"
            }
            ACTION_TOUR -> {
                val done = intent.getBooleanExtra(EXTRA_DONE, false)
                app.settings.setTourDone(done)
                "TOUR: done=$done"
            }
            ACTION_PASSPORT -> {
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                app.settings.setPassportEnabled(enabled)
                WidgetUpdater.requestUpdate(context)
                "PASSPORT: enabled=$enabled"
            }
            ACTION_NOTIFY_DAILY -> {
                val late = intent.getBooleanExtra(EXTRA_LATE, false)
                val today = LocalDate.now()
                val date = if (late) today.minusDays(1) else today
                "NOTIFY_DAILY: ${SummaryNotifier.daily(context, date, late, force = true)}"
            }
            ACTION_NOTIFY_MONTHLY ->
                "NOTIFY_MONTHLY: ${SummaryNotifier.monthly(context, YearMonth.now().minusMonths(1), force = true)}"
            ACTION_WIDGET_UPDATE -> "WIDGET_UPDATE: ${WidgetUpdater.refreshNow(context)} widgets repintados"
            ACTION_WIDGET_PIN -> {
                val manager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, TodayWidgetReceiver::class.java)
                val asked = manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(provider, null, null)
                "WIDGET_PIN: " + if (asked) "diálogo del launcher abierto" else "el launcher no admite fijar widgets"
            }
            ACTION_WIDGET_SNAPSHOT ->
                "WIDGET_SNAPSHOT: " + widgetSnapshot(context, intent.getBooleanExtra(EXTRA_PREVIEW, false))
            // Pasaporte. GAME_RESET borra solo su memoria (sellos guardados, celebraciones,
            // nivel más alto, retos cerrados, eventos); en el siguiente cálculo vuelve a salir del
            // historial y lo anterior a ayer se da por celebrado.
            //   adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.GAME_RESET
            // GAME_CELEBRATE deja pendiente una celebración de prueba (sale al abrir la app):
            //   … -a com.reelometro.app.debug.GAME_CELEBRATE [--es kind stamp|level|challenge] [--es stamp RACHA_7]
            ACTION_GAME_RESET -> {
                app.passport.clearAll()
                "GAME_RESET: memoria del pasaporte borrada (los datos de uso no se tocan)"
            }
            ACTION_GAME_CELEBRATE -> {
                val done = app.passport.forceCelebration(
                    kind = intent.getStringExtra(EXTRA_KIND),
                    stamp = intent.getStringExtra(EXTRA_STAMP),
                )
                "GAME_CELEBRATE: $done pendiente"
            }
            // Aviso en directo ya, con el recuento de hoy: salta el ajuste y el «ya enviado hoy», nunca el
            // objetivo (pásate de él con GOAL --ei goal 5) ni el permiso. No lo marca como enviado.
            ACTION_NOTIFY_GOAL -> "NOTIFY_GOAL: ${com.reelometro.app.work.GoalAlert.check(context, force = true)}"
            // «Nivel nuevo: <nivel actual>» (abre "passport"), aunque no sea nuevo; nunca con el modo pasaporte apagado.
            ACTION_NOTIFY_LEVEL -> "NOTIFY_LEVEL: ${com.reelometro.app.work.LevelAlert.check(context, force = true)}"
            ACTION_SUPPORT_PROMPT -> {
                app.settings.setSupportPrompt(SupportPrompt.readyForQa(LocalDate.now().toEpochDay()))
                "SUPPORT_PROMPT: listo (${SupportPrompt.FIRST_AFTER_USAGE_DAYS} días de uso, 0 veces enseñado); " +
                    "sale unos segundos después de entrar a una pestaña en una sesión nueva"
            }
            ACTION_SUPPORT_RESET -> {
                app.settings.setSupportPrompt(SupportPromptState())
                "SUPPORT_RESET: aviso de apoyo como recién instalado"
            }
            ACTION_SUPPORT_DONE -> {
                app.settings.setSupportPrompt(SupportPromptState(finished = true))
                "SUPPORT_DONE: el aviso de apoyo ya no sale (para capturas limpias)"
            }
            else -> "DebugReceiver: acción desconocida $action"
        }

    /**
     * Pinta el widget como lo haría el launcher (las RemoteViews de Glance, aplicadas aquí mismo) en
     * los 6 tamaños, en claro y en oscuro, y lo guarda en los ficheros externos de la app.
     */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun widgetSnapshot(context: Context, preview: Boolean): String {
        val data = if (preview) WidgetData.PREVIEW else WidgetUpdater.loadFresh(context)
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt()
        // Por tema: una fila con los de 1 celda de alto y otra con los de 2.
        val rows = listOf(
            listOf(WidgetSizes.NARROW, WidgetSizes.SMALL, WidgetSizes.WIDE),
            listOf(WidgetSizes.NARROW_TALL, WidgetSizes.SQUARE, WidgetSizes.LARGE),
        )
        val margin = px(16f)
        val width = rows.maxOf { row -> row.sumOf { px(it.width.value) } + margin * (row.size + 1) }
        val heights = rows.map { row -> row.maxOf { px(it.height.value) } }
        val out = createBitmap(width, 2 * (heights.sum() + margin * rows.size) + margin)
        val canvas = Canvas(out)
        canvas.drawColor(DarkReelColors.lineStrong.toArgb())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        var y = margin
        for (dark in listOf(false, true)) {
            rows.forEachIndexed { r, row ->
                var x = margin
                for (size in row) {
                    val single = render(context, size, dark, data)
                    canvas.drawBitmap(single, x.toFloat(), y.toFloat(), null)
                    if (preview && size == WidgetSizes.SQUARE) {
                        save(single, File(dir, "widget-preview-${if (dark) "dark" else "light"}.png"))
                    }
                    x += single.width + margin
                }
                y += heights[r] + margin
            }
        }
        val file = File(dir, "widget-snapshot.png")
        save(out, file)
        return file.absolutePath
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun render(context: Context, size: DpSize, dark: Boolean, data: WidgetData?): Bitmap {
        val views = GlanceRemoteViews().compose(context, size, null, Bundle()) { TodayWidgetContent(data) }.remoteViews
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = context.createConfigurationContext(config)
        val density = context.resources.displayMetrics.density
        val w = (size.width.value * density).roundToInt()
        val h = (size.height.value * density).roundToInt()
        return withContext(Dispatchers.Main) {
            val view = views.apply(themed, FrameLayout(themed))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, w, h)
            createBitmap(w, h).also { view.draw(Canvas(it)) }
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        const val TAG = "Reelometro"
        private const val PREFIX = "com.reelometro.app.debug."

        const val ACTION_SEED = PREFIX + "SEED"
        const val ACTION_CLEAR = PREFIX + "CLEAR"
        const val ACTION_ONBOARDING = PREFIX + "ONBOARDING"
        const val ACTION_THEME = PREFIX + "THEME"
        const val ACTION_GOAL = PREFIX + "GOAL"
        const val ACTION_UNLOCK_CALIBRATION = PREFIX + "UNLOCK_CALIBRATION"
        const val ACTION_RESET_SETTINGS = PREFIX + "RESET_SETTINGS"
        const val ACTION_DISCLOSURE = PREFIX + "DISCLOSURE"
        const val ACTION_NOTIFY_DAILY = PREFIX + "NOTIFY_DAILY"
        const val ACTION_NOTIFY_MONTHLY = PREFIX + "NOTIFY_MONTHLY"
        const val ACTION_WIDGET_UPDATE = PREFIX + "WIDGET_UPDATE"
        const val ACTION_WIDGET_PIN = PREFIX + "WIDGET_PIN"
        const val ACTION_WIDGET_SNAPSHOT = PREFIX + "WIDGET_SNAPSHOT"
        const val ACTION_TOUR = PREFIX + "TOUR"
        const val ACTION_PASSPORT = PREFIX + "PASSPORT"

        const val EXTRA_WEEKS = "weeks"
        const val EXTRA_SEED = "seed"
        const val EXTRA_DONE = "done"
        const val EXTRA_MODE = "mode"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_UNLOCKED = "unlocked"
        const val EXTRA_ACCEPTED = "accepted"
        const val EXTRA_LATE = "late"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_ENABLED = "enabled"

        /**
         * Importa un CSV de getExternalFilesDir (como IMPORTAR CSV de Ajustes, sin la vista previa):
         * ```
         * adb push copia.csv /sdcard/Android/data/com.reelometro.app/files/copia.csv
         * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.IMPORT --es path copia.csv [--ez replace true]
         * ```
         */
        const val ACTION_IMPORT = PREFIX + "IMPORT"

        /** Exporta el CSV a getExternalFilesDir: `--es path copia.csv` (por defecto reelometro-export.csv). */
        const val ACTION_EXPORT = PREFIX + "EXPORT"
        const val EXTRA_PATH = "path"
        const val EXTRA_REPLACE = "replace"

        const val ACTION_GAME_RESET = PREFIX + "GAME_RESET"
        const val ACTION_GAME_CELEBRATE = PREFIX + "GAME_CELEBRATE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_STAMP = "stamp"
        const val ACTION_NOTIFY_GOAL = PREFIX + "NOTIFY_GOAL"
        const val ACTION_NOTIFY_LEVEL = PREFIX + "NOTIFY_LEVEL"

        /**
         * Aviso de apoyo listo para salir (3 días de uso, 0 veces enseñado, no terminado). Sale unos segundos
         * después de entrar a una pestaña en una sesión nueva de la actividad (y con onboarding y recorrido hechos,
         * sin celebraciones del Pasaporte pendientes):
         * ```
         * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.SUPPORT_PROMPT
         * adb shell am force-stop com.reelometro.app && adb shell am start -n com.reelometro.app/.MainActivity
         * ```
         */
        const val ACTION_SUPPORT_PROMPT = PREFIX + "SUPPORT_PROMPT"

        /**
         * Aviso de apoyo como recién instalado (0 días de uso; el de hoy se apunta al volver a abrir la app):
         * ```
         * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.SUPPORT_RESET
         * ```
         */
        const val ACTION_SUPPORT_RESET = PREFIX + "SUPPORT_RESET"

        /**
         * Aviso de apoyo terminado: no sale más (para capturas de QA sin el aviso encima):
         * ```
         * adb shell am broadcast -n com.reelometro.app/.debug.DebugReceiver -a com.reelometro.app.debug.SUPPORT_DONE
         * ```
         */
        const val ACTION_SUPPORT_DONE = PREFIX + "SUPPORT_DONE"
    }
}
