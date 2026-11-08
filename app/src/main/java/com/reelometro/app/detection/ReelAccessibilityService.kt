package com.reelometro.app.detection

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.reelometro.app.BuildConfig
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.detection.CalibrationEventType.CLICK
import com.reelometro.app.detection.CalibrationEventType.INFO
import com.reelometro.app.detection.CalibrationEventType.LEAVE
import com.reelometro.app.detection.CalibrationEventType.SCROLL
import com.reelometro.app.detection.CalibrationEventType.SHARE
import com.reelometro.app.detection.CalibrationEventType.VIDEO_DISCARD
import com.reelometro.app.detection.CalibrationEventType.VIDEO_END
import com.reelometro.app.detection.CalibrationEventType.VIDEO_START
import com.reelometro.app.detection.CalibrationEventType.WINDOW
import com.reelometro.app.detection.FeedRules.Orientation
import com.reelometro.app.detection.FeedRules.Verdict
import com.reelometro.app.detection.FeedRules.WindowKind
import com.reelometro.app.widget.WidgetUpdater
import com.reelometro.app.work.LiveAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Servicio de accesibilidad: cuenta los vídeos cortos que se ven en TikTok, Instagram Reels y YouTube Shorts.
 *
 * - Solo recibe eventos de esas apps (`android:packageNames` en `accessibility_service_config.xml`):
 *   scroll (cambio de vídeo), cambio de ventana (en qué pantalla estás) y clic (botón compartir).
 * - [FeedRules] decide si cada scroll es el feed de vídeos cortos; [ViewTracker] cuenta y mide;
 *   [ContentExtractor] lee autor/descripción una vez asentado el vídeo (con retardo, fuera del hilo
 *   principal y con límite de nodos); [ShareDetector] detecta «compartir» y, en Instagram y TikTok
 *   ([AppSettings.QUICK_SEND_PLATFORMS]) y solo con el ajuste «A quién envías» encendido, [RecipientDetector] lee a
 *   quién se envía en la hoja de compartir.
 * - **Envío rápido** (Instagram y TikTok, con el ajuste encendido): [QuickSendOverlay] pone sobre los vídeos del feed
 *   de la app que se ve una burbuja (la foto de perfil o las iniciales) por persona o grupo fijado **en esa app** y,
 *   al tocarla, [QuickSender] decide qué pulsar («Compartir», ese nombre y «Enviar»), que el servicio pulsa con
 *   `performAction` (sin gestos). Es lo único que pulsa. Un envío empieza y acaba en su app ([quickPlatform], con su
 *   [PlatformProfile]): solo se leen y se pulsan sus ventanas. La foto la recorta [AvatarCapture] de la hoja de
 *   compartir (Android 11+): es la única captura de pantalla que hace.
 * - Solo debug: [ACTION_DUMP_SHEET] vuelca al log el árbol de la app que se ve y [ACTION_QUICK_SEND] lanza un envío
 *   rápido sin tocar las burbujas (comandos en el KDoc de cada uno).
 * - En las apps con [FeedRules.PlatformRules.contentCheck] (YouTube Shorts) hay una segunda señal: se
 *   compara la [ShortSignature] (autor + título) del vídeo visible tras cada scroll y en cada comprobación
 *   periódica. Cuenta los deslizamientos que no llegan como scroll y une los vídeos contados dos veces.
 * - «Se ha ido» sin eventos de otras apps: pantalla apagada, cambio de actividad, una comprobación
 *   ligera cada [PRESENCE_INTERVAL_MS] solo mientras hay un vídeo activo (ventanas visibles y feed
 *   visible) y la duración máxima por vídeo.
 * - Nunca debe tumbarse: cada evento va protegido y los logs solo salen en debug (tag `Reelometro`).
 */
class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    // Un solo hilo para la base de datos: el INSERT de un vídeo siempre va antes que sus UPDATE/DELETE.
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Lecturas del árbol fuera del hilo principal, de una en una.
    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var repository: ReelRepository? = null
    private var tracker: ViewTracker? = null
    private val shareDetector = ShareDetector()
    private val recipientDetector = RecipientDetector()
    private val quickSender = QuickSender(shareDetector = shareDetector, finder = AccessibilityUiFinder, clock = SystemClock::uptimeMillis)

    // Fotos de perfil de los fijados: se leen y guardan en disco aquí, de una en una.
    private val fileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Todo lo que sigue se usa solo desde el hilo principal.
    private var enabledPlatforms: Set<Platform> = Platform.entries.toSet()

    /** Ajuste «A quién envías» (Instagram y TikTok, uno para las dos). Apagado, la hoja de compartir no se lee. */
    private var recordRecipients = false

    /** Ajuste «Envío rápido» (uno para las dos apps) y, por app, sus fijados ([AppSettings.quickSendTargets]). */
    private var quickSendEnabled = false
    private var quickSendTargets: Map<Platform, List<String>> = emptyMap()

    /** Dónde dejó el usuario las burbujas en cada app (sin entrada = por defecto, [BubbleLayout.defaultFor]). */
    private var quickSendBubbles: Map<Platform, BubbleSpot?> = emptyMap()
    private var overlay: QuickSendOverlay? = null
    private var quickMemory: QuickSendMemory? = null
    private var avatarStore: AvatarStore? = null

    /** Fotos ya cargadas de los fijados, por app (las que hay; sin foto, la burbuja lleva las iniciales). */
    private var avatarCache: Map<Platform, Map<String, Bitmap?>> = emptyMap()

    /** Cuándo se guardó la foto de cada fijado, por app (para no mirar el disco desde el hilo principal). */
    private var avatarSavedAt: Map<Platform, Map<String, Long?>> = emptyMap()

    /** Fijados con foto elegida de la galería (1.4.3), por app: la captura no la renueva nunca. */
    private var avatarChosen: Map<Platform, Set<String>> = emptyMap()

    /**
     * App del envío rápido en curso o del último (1.10.0): todo el envío pasa en ella (raíces, eventos, clics propios,
     * resultado). Solo se cambia al empezar uno ([startQuickSend]).
     */
    private var quickPlatform: Platform = Platform.INSTAGRAM

    /** App cuyas burbujas se ven o se vieron por última vez: a ella van la posición al soltarlas y la vigilancia. */
    private var bubblesPlatform: Platform = Platform.INSTAGRAM

    /** La app del envío rápido dejó de estar delante en mitad de un envío: las burbujas no se enseñan hasta el siguiente. */
    private var quickAway = false

    /** Qué burbujas se ven y con qué marca (enviando, enviado, fallo), por reel (1.4.1). */
    private val board = BubbleBoard()

    /** El tick del envío en curso está leyendo la pantalla; llegó algún evento mientras ([quickDirty]). */
    private var quickTickInFlight = false
    private var quickDirty = false
    private var quickTickAt: Long? = null

    /** Ya se ha enseñado «enviado» (en cuanto Android aceptó «Enviar», antes de confirmarlo). */
    private var quickProvisionalShown = false

    /** Intentos de recortar las fotos en un envío a mano: uno nuevo invalida los anteriores. */
    private var manualAvatarRun = 0L

    /**
     * Envío rápido en curso: a quién (uno o varios, en la misma hoja) y desde qué vídeo. Es el hilo principal quien
     * decide si hay uno en marcha (no [QuickSender.isRunning], que cambia en `readScope`); [quickRun] descarta los
     * pasos de un envío anterior que lleguen tarde. [quickQueue]: los que se tocaron con «Enviar» ya pulsado; salen
     * justo después, en otro envío.
     */
    private val quickNames = ArrayList<String>()
    private val quickQueue = ArrayList<String>()
    private var quickVideoId: Long? = null
    private var quickRun = 0L

    /** Cuándo empezó el envío en curso (`uptimeMillis`): pasado [QUICK_MAX_MS], se para pase lo que pase. */
    private var quickStartedAt = 0L

    /** Cuándo acabó bien el último envío rápido (`uptimeMillis`) y en qué app: su hoja puede estar aún cerrándose. */
    private var quickLastOkAt = -QUICK_SHEET_CLOSING_MS
    private var quickLastOkPlatform: Platform? = null

    /** Clics de la app del envío rápido ([quickPlatform]) que no se miran (los provoca él) hasta este instante. */
    private var ignoreClicksUntil = 0L

    /** Cuándo pulsó el envío rápido el «Compartir» del visor (reloj de pared), por si su evento llega con retraso. */
    private var quickShareClickedAt: Long? = null

    private val quickTickRunnable = Runnable {
        quickTickAt = null
        runQuickTick()
    }
    private val overlayRunnable = Runnable { updateOverlay() }

    /** Con las burbujas a la vista: si su app deja de estar delante, se van sin esperar a la comprobación de 4 s. */
    private val overlayWatchRunnable = Runnable { watchOverlay() }
    private var screenWidth = 0
    private var screenHeight = 0
    private var receiverRegistered = false

    /** Actividad actual de cada app (último `TYPE_WINDOW_STATE_CHANGED` que era una actividad). */
    private val screens = HashMap<Platform, String>()
    private val activityCache = HashMap<String, Boolean>()

    /** Orientación aprendida de cada contenedor (por identidad del nodo). */
    private val orientations = lruMap<Int, Orientation>(64)
    private val videoPlatforms = lruMap<Long, Platform>(200)
    private val infoDone = lruMap<Long, Unit>(200)

    // Segunda señal por contenido (solo plataformas con contentCheck).
    private val signatures = lruMap<Long, ShortSignature>(200)
    private val startedAtOf = lruMap<Long, Long>(200)
    private val finishedAt = lruMap<Long, Long>(200)
    private val currentVideo = HashMap<Platform, Long>()
    private val previousVideo = HashMap<Platform, Long>()
    private val lastScrollAt = HashMap<Platform, Long>()

    /** Cuándo empezó o se reanudó el vídeo activo (para no fechar un cambio antes de él). */
    private var videoChangedAt = 0L

    /** Cambio «débil» (solo el autor distinto) pendiente de confirmar con otra lectura. */
    private var pendingChange: ShortSignature? = null
    private var contentCheckRunning = false

    /** Nota del próximo VIDEO_DISCARD en Calibración (si no es un rebote). */
    private var discardNote: String? = null
    private val contentCheckRunnable = Runnable { runContentCheck() }

    /** Contenedor del feed del último scroll aceptado (para leer su página visible y comprobar que se ve). */
    private var feedNode: AccessibilityNodeInfo? = null
    private var feedPlatform: Platform? = null

    /** Última prueba de que el usuario seguía en la app del vídeo activo (evento o comprobación). */
    private var lastEvidenceAt = 0L
    private var presenceScheduled = false
    private val presenceRunnable = Runnable {
        presenceScheduled = false
        checkPresence()
    }

    private var dumpRegistered = false

    /** Solo debug: [ACTION_DUMP_SHEET] (el árbol al log, fuera del hilo principal) y [ACTION_QUICK_SEND]. */
    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!BuildConfig.DEBUG) return
            if (intent.action == ACTION_QUICK_SEND) onDebugQuickSend(intent) else dumpTree(intent)
        }
    }

    /**
     * Solo debug: vuelca al log las ventanas de la app que se ve (Instagram o TikTok, de arriba abajo: en TikTok la hoja
     * de compartir es otra ventana). `--es platform tiktok|instagram` elige la app; `--ez active true`, la ventana activa
     * de cualquier app (YouTube…). Cada nodo con id, texto o descripción, y `C` si es pulsable, `✓` si está marcado.
     */
    private fun dumpTree(intent: Intent) {
        val active = intent.getBooleanExtra("active", false)
        val asked = intent.getStringExtra("platform")?.let { p ->
            Platform.entries.firstOrNull { it.name.equals(p, ignoreCase = true) || it.shortName.equals(p, ignoreCase = true) }
        }
        val playing = tracker?.currentPlatform
        readScope.launch {
            val lines = mutableListOf<String>()
            fun visit(n: UiNode, depth: Int) {
                if (lines.size >= 600 || depth > 40) return
                val b = n.bounds
                val label = listOfNotNull(
                    shortViewId(n.viewId).ifEmpty { null },
                    simpleClassName(n.className),
                    n.text?.let { "t=«$it»" },
                    n.contentDescription?.let { "d=«$it»" },
                    "C".takeIf { n.isClickable },
                    "✓".takeIf { n.isChecked },
                    "[${b.left},${b.top}][${b.right},${b.bottom}]",
                ).joinToString(" ")
                if (n.text != null || n.contentDescription != null || !n.viewId.isNullOrEmpty()) lines += "  ".repeat(depth.coerceAtMost(20)) + label
                n.children.forEach { visit(it, depth + 1) }
            }
            val platform = asked ?: foregroundQuickPlatform() ?: playing?.takeIf { quickApp(it) } ?: Platform.INSTAGRAM
            val roots = if (active) listOfNotNull(rootInActiveWindow?.let { AccessibilityUiNode(it) }) else appRoots(platform)
            roots.forEachIndexed { i, root ->
                lines += "── raíz $i" + if (active) "" else " (${platform.shortName}, ventana ${root.windowId})"
                visit(root, 0)
            }
            lines.chunked(40).forEach { Log.d(TAG, "⇢ árbol\n" + it.joinToString("\n")) }
            Log.d(TAG, "⇢ árbol: fin (${lines.size} líneas)")
        }
    }

    /**
     * Solo debug: un envío rápido como si se tocara la burbuja de `name` (con `name2`, como soltar una encima de la otra),
     * para medir en el móvil sin tocar la pantalla. Los nombres tienen que estar fijados en la app del vídeo que se ve, con
     * el envío rápido y «A quién envías» encendidos. Pasa por [onQuickSendRequest], como un toque. El log no lleva nombres.
     */
    private fun onDebugQuickSend(intent: Intent) {
        val names = listOfNotNull(intent.getStringExtra("name"), intent.getStringExtra("name2")).filter { it.isNotBlank() }
        val platform = tracker?.currentPlatform
        val pinned = targetsOf(platform)
        Log.d(
            TAG,
            "⇢ depuración: envío rápido en ${platform?.shortName ?: "ningún vídeo"} · ${names.size} nombre(s), " +
                "${names.count { it in pinned }} fijado(s) en esa app · activo: ${quickSendActive()}",
        )
        // `--ez search true`: directo al buscador (como un fijado que no suele salir en la fila), para medir ese camino.
        debugForceSearch = intent.getBooleanExtra("search", false)
        if (names.isNotEmpty()) onQuickSendRequest(names, gesture = "depuración")
        debugForceSearch = false
    }

    /** Solo debug ([onDebugQuickSend]): el envío que se lanza ahora va directo al buscador. */
    private var debugForceSearch = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            leave(System.currentTimeMillis(), "pantalla apagada", tracker?.currentPlatform)
        }
    }

    private val recorder = object : ViewTracker.Recorder {
        override fun videoStarted(id: Long, platform: Platform, startedAt: Long) {
            val now = System.currentTimeMillis()
            videoPlatforms[id] = platform
            currentVideo.put(platform, id)?.let { previousVideo[platform] = it }
            startedAtOf[id] = startedAt
            pendingChange = null
            videoChangedAt = now
            repository?.let { repo ->
                dbScope.launch {
                    runCatching { repo.recordStart(id, platform, startedAt) }.onFailure { debugError("recordStart", it) }
                    WidgetUpdater.requestUpdate(applicationContext)
                    LiveAlerts.onVideoStarted(applicationContext) // aviso al pasar el objetivo y de nivel nuevo
                }
            }
            ServiceStatus.onVideo(now)
            scheduleInfo(id, platform, INFO_DELAY_MS, attempt = 1)
            CalibrationLog.log {
                val estimated = now - startedAt > 1_000
                CalibrationEvent(
                    now, VIDEO_START, platform = platform, videoId = id,
                    note = if (estimated) "vídeo que ya estaba en pantalla (inicio estimado hace ${(now - startedAt) / 1000} s)" else null,
                )
            }
            debug { "▶ Nuevo vídeo en ${platform.shortName} (id=$id)" }
        }

        override fun videoFinished(id: Long, durationMs: Long) {
            finishedAt[id] = System.currentTimeMillis()
            repository?.let { repo ->
                dbScope.launch {
                    runCatching { repo.recordDuration(id, durationMs) }.onFailure { debugError("recordDuration", it) }
                    // El tiempo del widget incluye el vídeo recién cerrado (al salir de la app, sobre todo).
                    WidgetUpdater.requestUpdate(applicationContext)
                }
            }
            CalibrationLog.log {
                CalibrationEvent(System.currentTimeMillis(), VIDEO_END, platform = videoPlatforms[id], videoId = id, durationMs = durationMs)
            }
        }

        override fun videoDiscarded(id: Long) {
            repository?.let { repo ->
                dbScope.launch {
                    runCatching { repo.discard(id) }.onFailure { debugError("discard", it) }
                    WidgetUpdater.requestUpdate(applicationContext)
                }
            }
            infoDone.remove(id)
            signatures.remove(id)
            val reason = discardNote ?: "menos de ${ViewTracker.MIN_DWELL_MS} ms en pantalla (rebote)"
            CalibrationLog.log {
                CalibrationEvent(System.currentTimeMillis(), VIDEO_DISCARD, platform = videoPlatforms[id], videoId = id, note = reason)
            }
            debug { "✕ Descartado id=$id (rebote)" }
        }

        override fun videoResumed(id: Long) {
            val platform = videoPlatforms[id]
            if (platform != null) currentVideo[platform] = id
            pendingChange = null
            videoChangedAt = System.currentTimeMillis()
            CalibrationLog.log {
                CalibrationEvent(System.currentTimeMillis(), VIDEO_START, platform = platform, videoId = id, note = "reanudado: no cuenta otra vez")
            }
            if (platform != null && !infoDone.containsKey(id)) scheduleInfo(id, platform, INFO_DELAY_MS, attempt = 1)
        }
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val app = ReelometroApp.from(this)
            repository = app.repository
            tracker = ViewTracker(recorder)
            updateScreenSize()
            ContextCompat.registerReceiver(
                this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
            if (BuildConfig.DEBUG) {
                // Solo debug: `adb shell am broadcast -a com.reelometro.app.debug.DUMP_SHEET` escribe en el log el árbol
                // de la app que se ve (ids, textos, descripciones). uiautomator no puede leerlo con un vídeo
                // reproduciéndose. `…debug.QUICK_SEND --es name "…"` lanza un envío rápido (ver [onDebugQuickSend]).
                // La APK que se publica es la debug: solo quien tenga el permiso DUMP (el shell de adb lo tiene; una app
                // instalada, no) puede mandar estos avisos. Sin él, cualquier app podría lanzar un envío rápido o un volcado.
                val filter = IntentFilter(ACTION_DUMP_SHEET).apply { addAction(ACTION_QUICK_SEND) }
                ContextCompat.registerReceiver(
                    this, dumpReceiver, filter, DEBUG_HOOKS_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
                )
                dumpRegistered = true
            }
            mainScope.launch {
                app.settings.settings
                    .map { it.enabledPlatforms }
                    .distinctUntilChanged()
                    .catch { debugError("ajustes", it) }
                    .collect { onEnabledPlatformsChanged(it) }
            }
            mainScope.launch {
                app.settings.settings
                    .map { it.recordRecipients }
                    .distinctUntilChanged()
                    .catch { debugError("ajustes", it) }
                    .collect { on ->
                        recordRecipients = on
                        if (!on) recipientDetector.stop()
                    }
            }
            quickMemory = QuickSendMemory(this)
            avatarStore = AvatarStore(this)
            mainScope.launch {
                // 1.10.0: los fijados y la posición de cada app (Instagram y TikTok).
                app.settings.settings
                    .map { s -> Triple(s.quickSendEnabled, s.allQuickSendTargets(), AppSettings.QUICK_SEND_PLATFORMS.associateWith { s.quickSendBubble(it) }) }
                    .distinctUntilChanged()
                    .catch { debugError("ajustes", it) }
                    .collect { (on, targets, bubbles) ->
                        val reload = targets != quickSendTargets
                        quickSendEnabled = on
                        quickSendTargets = targets
                        quickSendBubbles = bubbles
                        if (reload) {
                            // Mismo hilo que los guardados: una foto recortada justo al quitar el fijado no sobrevive.
                            // 1.10.0: los fijados de todas las apps, para no borrar las fotos de los de la otra.
                            avatarStore?.let { store -> fileScope.launch { store.retainOnly(app.settings.current().allQuickSendTargets()) } }
                            reloadAvatars()
                        }
                        updateOverlay()
                    }
            }
            // Una foto nueva (recortada aquí) o borrada (al quitar un fijado o con BORRAR TODO).
            mainScope.launch { AvatarStore.changes.collect { reloadAvatars() } }
            readScope.launch { CalibrationLog.environment = runCatching { describeEnvironment() }.getOrNull() }
            ServiceStatus.onConnected()
            debug { "Servicio conectado (pantalla ${screenWidth}×$screenHeight)" }
        } catch (e: Exception) {
            debugError("onServiceConnected", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenSize()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        handler.removeCallbacksAndMessages(null)
        mainScope.cancel()
        readScope.cancel()
        fileScope.cancel()
        // Deja terminar las escrituras pendientes antes de cancelar
        dbScope.launch { }.invokeOnCompletion { dbScope.cancel() }
        super.onDestroy()
    }

    private fun shutdown() {
        try {
            tracker?.onLeave(System.currentTimeMillis())
            if (receiverRegistered) {
                runCatching { unregisterReceiver(screenOffReceiver) }
                receiverRegistered = false
            }
            if (dumpRegistered) {
                runCatching { unregisterReceiver(dumpReceiver) }
                dumpRegistered = false
            }
            handler.removeCallbacks(presenceRunnable)
            handler.removeCallbacks(contentCheckRunnable)
            handler.removeCallbacks(quickTickRunnable)
            handler.removeCallbacks(overlayRunnable)
            handler.removeCallbacks(overlayWatchRunnable)
            quickTickAt = null
            quickTickInFlight = false
            presenceScheduled = false
            quickSender.cancel()
            quickNames.clear()
            quickQueue.clear()
            manualAvatarRun++
            setContentEvents(false)
            overlay?.hide()
            overlay?.hideQuestion()
        } catch (e: Exception) {
            debugError("shutdown", e)
        }
        ServiceStatus.onDisconnected()
    }

    // ── Eventos ──────────────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || tracker == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Llega a montones: solo sirve para que el envío rápido en curso reaccione antes, y solo los de su app.
            if (quickBusy && Platform.fromPackage(event.packageName) == quickPlatform) onQuickSendEvent()
            return
        }
        try {
            handleEvent(event)
        } catch (e: Exception) {
            debugError("evento ${event.eventType}", e)
        }
    }

    @SuppressLint("SwitchIntDef") // Solo interesan los tipos que pide accessibility_service_config.xml.
    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackage(pkg) ?: return
        val now = System.currentTimeMillis()
        ServiceStatus.onEvent(now)
        if (platform !in enabledPlatforms) {
            CalibrationLog.log {
                CalibrationEvent(
                    now, calibrationTypeOf(event), pkg, platform, className = event.className?.toString(),
                    decision = false, rule = "plataforma desactivada en Ajustes",
                )
            }
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowStateChanged(event, platform, pkg, now)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onViewScrolled(event, platform, pkg, now)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onViewClicked(event, platform, pkg, now)
        }
        if (tracker?.currentPlatform == platform) lastEvidenceAt = now
        if (quickApp(platform)) {
            if (quickBusy && platform == quickPlatform && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onQuickSendEvent()
            if (!quickBusy && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) checkSheetGone(platform)
            updateOverlay()
        }
    }

    /** En qué pantalla está el usuario: actividad nueva = otra pantalla; diálogos y hojas no cambian nada. */
    private fun onWindowStateChanged(event: AccessibilityEvent, platform: Platform, pkg: String, now: Long) {
        val t = tracker ?: return
        val cls = event.className?.toString()
        watchSheetClose(event, platform, now)
        val kind = windowKindOf(pkg, cls)
        t.onAppForeground(platform, now)
        var decision: Boolean? = null
        var rule = when (kind) {
            WindowKind.ACTIVITY -> "actividad (misma pantalla)"
            WindowKind.OVERLAY -> "encima: diálogo, hoja o menú (el vídeo sigue)"
            WindowKind.OTHER -> "ventana sin clasificar"
        }
        if (kind == WindowKind.ACTIVITY && cls != null) {
            val previous = screens.put(platform, cls)
            val excluded = FeedRules.excludedScreen(platform, cls)
            if (excluded != null) {
                t.onScreenChanged(platform, now)
                decision = false
                rule = "pantalla excluida ($excluded)"
            } else if (previous != null && previous != cls) {
                t.onScreenChanged(platform, now)
                rule = "cambio de pantalla"
            }
            if (recipientDetector.platform == platform && previous != cls) recipientDetector.stop()
        }
        CalibrationLog.log {
            CalibrationEvent(
                now, WINDOW, pkg, platform, className = cls, screenWidth = screenWidth, screenHeight = screenHeight,
                decision = decision, rule = rule,
                note = event.contentChangeTypes.takeIf { it != 0 }?.let { "contentChangeTypes=$it" },
            )
        }
        debug { "ventana ${platform.shortName} ${simpleClassName(cls)} · $rule" }
    }

    private fun onViewScrolled(event: AccessibilityEvent, platform: Platform, pkg: String, now: Long) {
        val t = tracker ?: return
        val source = event.source
        val r = Rect()
        source?.getBoundsInScreen(r)
        val viewId = source?.viewIdResourceName
        val cls = event.className?.toString() ?: source?.className?.toString()
        var dx: Int? = null
        var dy: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dx = event.scrollDeltaX.takeIf { it != UNDEFINED_DELTA }
            dy = event.scrollDeltaY.takeIf { it != UNDEFINED_DELTA }
        }
        val nodeKey = source?.hashCode()
        val orientation = orientationOf(source, nodeKey, dx, dy)
        val screen = screens[platform]
        val decision = FeedRules.evaluate(
            FeedRules.ScrollSample(
                platform, viewId, cls, r.left, r.top, r.width(), r.height(), screenWidth, screenHeight,
                event.fromIndex, event.toIndex, event.itemCount, dx, dy, screen, orientation,
            ),
        )
        val wasActive = t.isActive
        var note: String? = null
        when (decision.verdict) {
            Verdict.FEED -> {
                feedNode = source
                feedPlatform = platform
                shareDetector.onFeedActivity(platform)
                if (quickApp(platform)) board.onFeedMoved(now)
                val started = t.onFeedScroll(
                    ViewTracker.FeedScroll(
                        platform = platform,
                        feedKey = "${platform.name}|${viewId ?: cls}|$nodeKey",
                        fromIndex = event.fromIndex,
                        toIndex = event.toIndex,
                        itemCount = event.itemCount,
                        deltaY = dy,
                        pageHeight = r.height(),
                        indexStyle = FeedRules.indexStyleFor(cls),
                        now = now,
                        settleTolerance = FeedRules.rulesFor(platform).settleTolerance,
                        resumeOnReturn = FeedRules.rulesFor(platform).resumeOnReturn,
                    ),
                )
                if (started) {
                    note = "vídeo nuevo"
                    if (recipientDetector.platform == platform) recipientDetector.stop()
                }
                ensurePresenceChecks()
            }
            Verdict.OTHER_SCREEN -> {
                t.onLeaveFeed(platform, now)
                if (wasActive && !t.isActive) note = "vídeo en pausa"
            }
            Verdict.IGNORED -> Unit
        }
        lastScrollAt[platform] = now
        if (t.currentPlatform == platform && FeedRules.rulesFor(platform).contentCheck) {
            // Debounce: se lee cuando el scroll se ha parado (el vídeo nuevo ya está asentado).
            handler.removeCallbacks(contentCheckRunnable)
            handler.postDelayed(contentCheckRunnable, CONTENT_CHECK_DELAY_MS)
        }
        CalibrationLog.log {
            CalibrationEvent(
                now, SCROLL, pkg, platform, viewId, cls, event.fromIndex, event.toIndex, event.itemCount, dy, dx,
                r.width(), r.height(), screenWidth, screenHeight, decision.isFeed, decision.rule,
                note = listOfNotNull(
                    note,
                    orientation?.let { "orientación ${it.name.lowercase()}" },
                    screen?.let { "pantalla ${simpleClassName(it)}" },
                ).joinToString(" · ").ifEmpty { null },
            )
        }
        if (BuildConfig.DEBUG) {
            Log.v(
                TAG, "scroll ${platform.shortName} ${if (decision.isFeed) "✓" else "✗"} [${decision.rule}] id=$viewId " +
                    "clase=${simpleClassName(cls)} ${event.fromIndex}→${event.toIndex}/${event.itemCount} dy=$dy dx=$dx " +
                    "nodo=${r.width()}×${r.height()} pantalla=${screenWidth}×$screenHeight",
            )
        }
    }

    /**
     * Solo con un vídeo activo en el feed de esa app y si el detector no está en silencio tras un
     * compartido. La excepción es la hoja de compartir de Instagram o TikTok con «A quién envías» encendido,
     * que va a [onShareSheetClicked]; con el ajuste apagado no se lee nada de la hoja ni del destinatario.
     */
    private fun onViewClicked(event: AccessibilityEvent, platform: Platform, pkg: String, now: Long) {
        val sessionStarted = quickApp(platform) && !quickBusy && quickSendActive() && checkUserSheet(event, platform, now)
        // Los clics que provoca el envío rápido no cuentan (los apunta él al terminar). Solo los de su app. 1.10.1: con el envío
        // ya terminado, el «Compartir» del visor es del usuario (el envío rápido no pulsa nada al acabar) y sí cuenta.
        if (platform == quickPlatform && (quickBusy || now < ignoreClicksUntil)) {
            val viewerShare = !quickBusy && isViewerShare(event, platform)
            if (RecipientDetector.isOwnQuickClick(quickBusy, now, ignoreClicksUntil, viewerShare, quickShareClickedAt)) {
                CalibrationLog.log {
                    CalibrationEvent(now, CLICK, pkg, platform, className = event.className?.toString(), decision = false, rule = "envío rápido: clic propio")
                }
                return
            }
            CalibrationLog.log { CalibrationEvent(now, INFO, platform = platform, rule = "envío rápido terminado: «Compartir» del visor a mano (cuenta)") }
        }
        // Ya lo ha recogido checkUserSheet (sesión de «A quién envías» abierta): el propio clic en «Compartir» no es de la hoja.
        if (sessionStarted) return
        // La hoja de compartir puede tapar el vídeo (o dejarlo en pausa): va antes de mirar si hay vídeo activo.
        if (recordRecipients && recipientDetector.isOpen(now) && recipientDetector.platform == platform) {
            onShareSheetClicked(event, platform, pkg, now)
            return
        }
        val t = tracker ?: return
        val videoId = t.currentVideoId
        if (videoId == null || t.currentPlatform != platform) {
            CalibrationLog.log {
                CalibrationEvent(now, CLICK, pkg, platform, className = event.className?.toString(), decision = false, rule = "sin vídeo activo en el feed")
            }
            return
        }
        if (!shareDetector.isArmed(platform, now)) return
        val source = event.source ?: return
        val node = AccessibilityUiNode(source)
        val result = shareDetector.evaluate(node, screenWidth, screenHeight)
        CalibrationLog.log {
            CalibrationEvent(
                now, CLICK, pkg, platform, viewId = source.viewIdResourceName, className = source.className?.toString(),
                nodeWidth = node.bounds.width, nodeHeight = node.bounds.height, screenWidth = screenWidth,
                screenHeight = screenHeight, decision = result.isShare, rule = result.rule, videoId = videoId,
            )
        }
        if (!result.isShare) return
        shareDetector.onShareRecorded(platform, now)
        repository?.let { repo ->
            dbScope.launch { runCatching { repo.recordShare(platform, now, videoId) }.onFailure { debugError("recordShare", it) } }
        }
        CalibrationLog.log { CalibrationEvent(now, SHARE, pkg, platform, videoId = videoId, rule = result.rule) }
        debug { "↗ Compartido en ${platform.shortName} (vídeo $videoId, ${result.rule})" }
        val profile = recipientProfile(platform)
        if (profile != null && recordRecipients) {
            recipientDetector.start(now, videoId, profile = profile)
            snapshotSentBanners(platform, now)
            scheduleManualAvatars(platform)
        }
    }

    /**
     * Burbujas y hojas abiertas a mano (1.4.1). Un clic en un botón del visor que abre una hoja encima (compartir,
     * comentarios, más opciones: [ShareDetector.opensSheet]) las esconde hasta que se cierre; sin depender de isArmed
     * ni de la pausa tras un envío rápido, así que un compartir o unos comentarios justo después también cuentan.
     * Otro clic dentro del visor (me gusta, el vídeo…) dice que ya no hay hoja encima: vuelven. Solo botones del
     * visor ([PlatformProfile.feedIds]: `clips_*` en Instagram, `viewpager` en TikTok); lo de la hoja no se mira aquí.
     * Devuelve si ha abierto una sesión de «A quién envías» (un «Compartir» a mano con el detector en pausa).
     */
    private fun checkUserSheet(event: AccessibilityEvent, platform: Platform, now: Long): Boolean {
        val profile = PlatformProfile.of(platform) ?: return false
        val node = event.source?.let { AccessibilityUiNode(it) } ?: return false
        if (!recipientDetector.insideFeed(node, profile.feedIds)) return false
        if (!shareDetector.opensSheet(node, screenWidth, screenHeight, platform)) {
            if (board.sheetOpen) {
                board.onSheetClosed()
                CalibrationLog.log { CalibrationEvent(now, INFO, platform = platform, rule = "envío rápido: clic en el visor (burbujas de vuelta)") }
            }
            return false
        }
        board.onUserSheetOpened(now)
        CalibrationLog.log { CalibrationEvent(now, INFO, platform = platform, rule = "envío rápido: hoja abierta a mano (burbujas fuera)") }
        // 1.10.2: si la app no llega a abrir la hoja (un toque que ignora, p. ej. justo tras un envío rápido), no hay cambio de
        // ventana que lo diga: se mira igual, con margen para que la hoja tenga tiempo de salir.
        checkSheetGone(platform, firstDelay = SHEET_CLICK_CHECK_MS)
        // Un compartir a mano en el mismo reel tras un envío rápido: el detector está en pausa (no lo cuenta otra vez),
        // pero lo que se envíe desde la hoja se apunta y, al enviarlo, la hoja se da por cerrada.
        val t = tracker
        if (recordRecipients && !shareDetector.isArmed(platform, now) && !recipientDetector.isOpen(now) &&
            t?.currentPlatform == platform && shareDetector.evaluate(node, screenWidth, screenHeight).isShare
        ) {
            recipientDetector.start(now, t.currentVideoId, profile = profile)
            snapshotSentBanners(platform, now)
            return true
        }
        return false
    }

    /** ¿Es [event] un clic en el «Compartir» del visor de [platform] (dentro de [PlatformProfile.feedIds])? */
    private fun isViewerShare(event: AccessibilityEvent, platform: Platform): Boolean {
        val profile = PlatformProfile.of(platform) ?: return false
        val node = event.source?.let { AccessibilityUiNode(it) } ?: return false
        return recipientDetector.insideFeed(node, profile.feedIds) && shareDetector.evaluate(node, screenWidth, screenHeight).isShare
    }

    /**
     * Un clic con la hoja de compartir de Instagram o TikTok abierta ([RecipientDetector], con el perfil de la app de la
     * sesión). El nombre solo va a la base de datos: ni al registro de Calibración (que el usuario puede exportar) ni al
     * logcat.
     */
    private fun onShareSheetClicked(event: AccessibilityEvent, platform: Platform, pkg: String, now: Long) {
        // 1.10.0, medido en el móvil: el «Enviar» de TikTok cierra la hoja al momento y su clic llega sin nodo. Entonces vale
        // lo que trae el evento (texto, descripción y clase): «Enviar» se reconoce por su texto exacto. Solo en las apps cuya
        // hoja va en su propia ventana: en Instagram, un clic sin nodo se descarta como hasta la 1.9.0.
        val source = event.source
        val node: UiNode = source?.let { AccessibilityUiNode(it) }
            ?: EventUiNode.of(event)?.takeIf { recipientDetector.acceptsNodelessClicks }
            ?: run {
                debug { "clic sin nodo en la hoja de ${platform.shortName}: se descarta (clase ${event.className})" }
                return
            }
        if (source == null) debug { "clic sin nodo en la hoja de ${platform.shortName}: se usa el del evento (clase ${event.className})" }
        val outcome = recipientDetector.onClick(node, screenHeight, now)
        val note = when (outcome) {
            is RecipientDetector.Outcome.Ignored -> null
            is RecipientDetector.Outcome.Toggled -> "marcados: ${outcome.selected} (nombre de ${outcome.name.length} letras)"
            is RecipientDetector.Outcome.Sent -> "enviado a ${outcome.names.size}"
            is RecipientDetector.Outcome.Undone -> "envío deshecho"
        }
        CalibrationLog.log {
            CalibrationEvent(
                now, CLICK, pkg, platform, viewId = node.viewId, className = node.className?.toString(),
                nodeWidth = node.bounds.width, nodeHeight = node.bounds.height, screenWidth = screenWidth,
                screenHeight = screenHeight, decision = outcome !is RecipientDetector.Outcome.Ignored,
                rule = "hoja de compartir: ${outcome.rule}" + if (source == null) " (clic sin nodo)" else "", note = note,
            )
        }
        if (outcome is RecipientDetector.Outcome.Toggled) noteStaleSentBanners(platform, now)
        val repo = repository ?: return
        when (outcome) {
            is RecipientDetector.Outcome.Sent -> onRecipientsSent(outcome, platform, now)
            is RecipientDetector.Outcome.Undone -> dbScope.launch {
                runCatching { repo.undoRecipient(outcome.name, outcome.since) }.onFailure { debugError("undoRecipient", it) }
            }
            else -> Unit
        }
    }

    /** «A quién envías»: un envío a mano ya decidido (por el clic en «Enviar» o, en TikTok, por su aviso de enviado). */
    private fun onRecipientsSent(outcome: RecipientDetector.Outcome.Sent, platform: Platform, now: Long) {
        val repo = repository ?: return
        dbScope.launch {
            runCatching { repo.recordRecipients(platform, now, outcome.reelId, outcome.names) }
                .onFailure { debugError("recordRecipients", it) }
        }
        // La hoja se ha cerrado: un segundo «compartir» del mismo vídeo vuelve a contar.
        if (outcome.closesSession) {
            // Y una vigilancia del cierre de la hoja de TikTok que estuviera en marcha ya no tiene nada que apuntar: si siguiera,
            // vería el aviso de este envío y lo apuntaría otra vez (revisión de los arreglos de la verificación final).
            sheetCloseRun++
            shareDetector.onFeedActivity(platform)
            if (quickApp(platform)) board.onSheetClosed()
            // 1.10.1: la ventana de su aviso «Enviado a …», para que una sesión que empiece a ciegas no lo tome por el suyo.
            rememberSentBannerWindows(platform)
        }
        debug { "✉ Enviado a ${outcome.names.size} en ${platform.shortName} (${outcome.rule})" }
    }

    /**
     * Cada cierre de la hoja de TikTok que se comprueba: uno nuevo deja sin efecto el anterior, empezar un envío rápido
     * también (su aviso «Enviado a …» no es de esa hoja) y un envío ya apuntado que cierra la sesión ([onRecipientsSent]).
     * Cambia en el hilo principal y se lee en [readScope].
     */
    @Volatile
    private var sheetCloseRun = 0

    /**
     * Los avisos «Enviado a …» (texto y ventana) que ya se veían en la sesión de «A quién envías» que empezó en `first`: son
     * de un envío anterior y no dicen nada de esta hoja ([RecipientDetector.freshSentBanners]). Solo se toca en [readScope].
     */
    private var staleSentBanners: Pair<Long, Set<RecipientDetector.SentBanner>>? = null

    /**
     * 1.10.1: la sesión que empezó en `first` hizo su lectura del principio **a ciegas** (la hoja ya estaba abierta encima y
     * tapaba el aviso de debajo); `second` es la ventana de esa hoja: solo un aviso de una ventana posterior puede ser de este
     * envío ([RecipientDetector.freshSentBanners]). Solo se toca en [readScope].
     */
    private var blindSheetWindow: Pair<Long, Int>? = null

    /** Ventanas de los avisos vistos tras los envíos ya apuntados ([rememberSentBannerWindows]). Solo se toca en [readScope]. */
    private val sentBannerWindows = SentBannerWindows()

    /**
     * Cambia (en el hilo principal) al empezar una sesión de «A quién envías» o un envío rápido: las lecturas de
     * [rememberSentBannerWindows] que sigan en marcha paran, porque un aviso que salga desde entonces puede ser de un envío
     * que aún no se ha apuntado.
     */
    @Volatile
    private var sentBannerMemoryGen = 0

    /**
     * TikTok: acaba de apuntarse un envío (envío rápido terminado, o un `Sent` de «A quién envías»). Su aviso «Enviado a …»
     * sale enseguida en una ventana nueva: se lee unas veces en poco más de un segundo y se guarda su ventana en
     * [sentBannerWindows], para que una sesión que empiece a ciegas no lo tome luego por el de su envío. Para en cuanto ve
     * un aviso de una ventana que no tenía guardada (si solo ve los de envíos anteriores, sigue mirando) o si empieza otra
     * sesión u otro envío rápido ([sentBannerMemoryGen]).
     */
    private fun rememberSentBannerWindows(platform: Platform) {
        if (recipientProfile(platform)?.sheetOwnWindow != true) return
        val gen = sentBannerMemoryGen
        readScope.launch {
            for (wait in SENT_BANNER_MEMORY_WAITS_MS) {
                delay(wait)
                if (gen != sentBannerMemoryGen) return@launch
                val seen = RecipientDetector.sentBanners(appRoots(platform), AccessibilityUiFinder, bannerQueries())
                if (seen.isEmpty() || gen != sentBannerMemoryGen) continue
                val now = SystemClock.uptimeMillis()
                // Si solo se ven avisos ya recordados (el de un envío anterior que sigue abajo), el de este aún no ha salido.
                val newWindow = seen.any { it.windowId != UiNode.NO_WINDOW && it.windowId !in sentBannerWindows.known(now) }
                sentBannerWindows.remember(seen.map { it.windowId }, now)
                if (newWindow) return@launch
            }
        }
    }

    /**
     * Las consultas del aviso «Enviado a …» para los idiomas del sistema (todos los de su lista), más español e inglés
     * ([RecipientDetector.sentBannerQueriesFor]): cada una es una llamada entre procesos por ventana, en el hilo de los pasos
     * del envío rápido. Se guardan mientras no cambie la lista de idiomas. Además de la del proceso, la del sistema: si el
     * usuario elige otro idioma solo para esta app, TikTok sigue con el del sistema.
     */
    private fun bannerQueries(): List<String> {
        val system = runCatching { android.content.res.Resources.getSystem().configuration.locales.toLanguageTags() }.getOrDefault("")
        val tags = LocaleList.getDefault().toLanguageTags() + "," + system
        val cached = bannerQueriesCache
        if (cached != null && cached.first == tags) return cached.second
        val queries = RecipientDetector.sentBannerQueriesFor(tags.split(',').filter { it.isNotBlank() })
        bannerQueriesCache = tags to queries
        return queries
    }

    @Volatile
    private var bannerQueriesCache: Pair<String, List<String>>? = null

    /** La ventana de la hoja de compartir de [profile] en [roots] (la primera con sus ids), o `null`. */
    private fun sheetWindowIn(roots: List<UiNode>, profile: PlatformProfile): Int? =
        roots.firstOrNull { r -> profile.openSheetIds.any { id -> profile.fullIds(id).any { AccessibilityUiFinder.byViewId(r, it).isNotEmpty() } } }
            ?.windowId?.takeIf { it != UiNode.NO_WINDOW }

    /** ¿Sigue abierta en [roots] la hoja de compartir de [profile] (por sus ids, `touch_outside` en TikTok)? */
    private fun sheetOpenIn(roots: List<UiNode>, profile: PlatformProfile): Boolean =
        roots.any { r -> profile.openSheetIds.any { id -> profile.fullIds(id).any { AccessibilityUiFinder.byViewId(r, it).isNotEmpty() } } }

    /**
     * TikTok: empieza una sesión de «A quién envías» (se acaba de pulsar «Compartir», o un envío rápido dejó la hoja abierta).
     * El envío de esta hoja aún no ha ocurrido, así que los avisos «Enviado a …» que se ven ahora son de un envío anterior: se
     * apuntan para que [watchSheetClose] no los tome por el de esta hoja. Segunda pasada de la revisión: se leen aquí, cuando
     * aún se ve la actividad, porque con la hoja abierta (un diálogo encima) el aviso de debajo no se ve, y al cerrarse la
     * hoja el aviso de este envío sale a los pocos milisegundos del cambio de ventana (leerlo entonces lo daría por viejo).
     * Si la sesión empieza con la hoja ya abierta (un envío rápido la dejó así), esta lectura es a ciegas: no ve el aviso de
     * debajo. 1.10.1: entonces se guarda la ventana de la hoja ([blindSheetWindow]; el aviso de este envío saldrá en una
     * posterior) y, en cualquier caso, los avisos de envíos ya apuntados ([sentBannerWindows]) nunca valen como nuevos.
     */
    private fun snapshotSentBanners(platform: Platform, startedAt: Long) {
        val profile = recipientProfile(platform)?.takeIf { it.sheetOwnWindow } ?: return
        sentBannerMemoryGen++
        readScope.launch {
            val roots = appRoots(platform)
            val seen = RecipientDetector.sentBanners(roots, AccessibilityUiFinder, bannerQueries())
            val previous = staleSentBanners?.takeIf { it.first == startedAt }?.second.orEmpty()
            staleSentBanners = startedAt to (previous + seen)
            if (blindSheetWindow?.first != startedAt) sheetWindowIn(roots, profile)?.let { blindSheetWindow = startedAt to it }
        }
    }

    /**
     * TikTok: se acaba de marcar o desmarcar a alguien, con la hoja abierta. Lo que se vea ahora tampoco es de esta hoja: se
     * suma a lo leído al empezar la sesión ([snapshotSentBanners]); sin esa lectura no se crea aquí (con la hoja abierta no
     * se ve el aviso de debajo, y una lectura vacía no puede pasar por «no había ninguno»). Solo si la hoja sigue abierta en
     * esa misma lectura: si llega tarde, con la hoja ya cerrada, lo que se ve puede ser el aviso de este envío (revisión de
     * los arreglos de la verificación final).
     */
    private fun noteStaleSentBanners(platform: Platform, now: Long) {
        val profile = recipientProfile(platform)?.takeIf { it.sheetOwnWindow } ?: return
        val startedAt = recipientDetector.tikTokSelection(now)?.startedAt ?: return
        readScope.launch {
            val previous = staleSentBanners?.takeIf { it.first == startedAt }?.second ?: return@launch
            val roots = appRoots(platform)
            if (!sheetOpenIn(roots, profile)) return@launch
            staleSentBanners = startedAt to (previous + RecipientDetector.sentBanners(roots, AccessibilityUiFinder, bannerQueries()))
        }
    }

    /**
     * 1.10.0, medido en el móvil (TikTok 47.7): su «Enviar» cierra la hoja sin `TYPE_VIEW_CLICKED`, así que «A quién envías»
     * nunca veía el envío (y sin envíos a mano no hay a quién fijar en TikTok). Cuando, con alguien marcado, llega un cambio de
     * ventana de la app que no es la propia hoja (la actividad recupera el foco), se mira unas veces en poco más de un segundo
     * si la hoja ya no está y TikTok enseña abajo un aviso «Enviado a …» **nuevo** ([RecipientDetector.sentBanners] y
     * [RecipientDetector.freshSentBanners]): entonces se apunta; si la hoja se cerró sin ese aviso («X», atrás, tocar fuera),
     * no. Los avisos que ya se veían al empezar la sesión ([snapshotSentBanners]) son de un envío anterior y no valen (cada
     * aviso va en su propia ventana, nueva), ni los de envíos ya apuntados ([sentBannerWindows]), ni, si esa lectura fue a
     * ciegas, los de ventanas anteriores a la hoja ([blindSheetWindow]) (1.10.1). El aviso de este envío sale a los pocos
     * milisegundos del cambio de ventana de la actividad. Solo en las apps cuya hoja va en su propia ventana. (El 7 nov, en una segunda tanda, la misma TikTok 47.7.3
     * sí emitió el clic de «Enviar», sin nodo, en 2 envíos de 2: entonces lo apunta [onShareSheetClicked], la sesión se cierra,
     * se anula la vigilancia que hubiera ([onRecipientsSent]) y aquí ya no hay nada marcado. Esto queda de red.)
     */
    private fun watchSheetClose(event: AccessibilityEvent, platform: Platform, now: Long) {
        if (!recordRecipients || recipientDetector.platform != platform) return
        val profile = recipientProfile(platform)?.takeIf { it.sheetOwnWindow } ?: return
        // Los eventos de la propia hoja (también al abrir su buscador) traen su título.
        if (event.text.orEmpty().any { it != null && RecipientDetector.normalize(it.toString()) in profile.titleLabels }) return
        val selection = recipientDetector.tikTokSelection(now) ?: return
        val run = ++sheetCloseRun
        readScope.launch {
            // Lo leído al empezar la sesión (y al marcar). Nunca una lectura ahora: el aviso de este envío sale a los pocos
            // milisegundos del cambio de ventana y se tomaría por viejo (segunda pasada de la revisión). Sin lectura, todo aviso
            // vale como nuevo, como antes de los arreglos.
            val before = staleSentBanners?.takeIf { it.first == selection.startedAt }?.second.orEmpty()
            // 1.10.1: si la lectura del principio fue a ciegas, solo avisos de ventanas posteriores a la hoja; y nunca los de
            // envíos ya apuntados.
            val newerThan = blindSheetWindow?.takeIf { it.first == selection.startedAt }?.second
            var closed = false
            for (wait in SENT_BANNER_WAITS_MS) {
                delay(wait)
                if (run != sheetCloseRun) return@launch
                val roots = appRoots(platform)
                if (sheetOpenIn(roots, profile)) continue
                closed = true
                val seen = RecipientDetector.sentBanners(roots, AccessibilityUiFinder, bannerQueries())
                val fresh = RecipientDetector.freshSentBanners(before, seen, sentBannerWindows.known(SystemClock.uptimeMillis()), newerThan)
                if (fresh.isNotEmpty()) {
                    sentBannerWindows.remember(fresh.map { it.windowId }, SystemClock.uptimeMillis())
                    withContext(Dispatchers.Main) { onSheetClosedChecked(platform, selection, sent = true, run) }
                    return@launch
                }
            }
            // Cerrada y sin aviso en todo ese rato: no se envió. Si sigue abierta, no era un cierre (nada cambia).
            if (closed) withContext(Dispatchers.Main) { onSheetClosedChecked(platform, selection, sent = false, run) }
        }
    }

    private fun onSheetClosedChecked(platform: Platform, selection: RecipientDetector.Selection, sent: Boolean, run: Int) {
        // Con «A quién envías» apagado mientras tanto, no se apunta nada.
        if (run != sheetCloseRun || !recordRecipients) return
        val now = System.currentTimeMillis()
        val outcome = recipientDetector.onTikTokSheetClosed(selection, sent)
        CalibrationLog.log {
            CalibrationEvent(
                now, INFO, platform = platform, decision = outcome is RecipientDetector.Outcome.Sent,
                rule = "hoja de compartir: ${outcome.rule}",
                note = (outcome as? RecipientDetector.Outcome.Sent)?.let { "enviado a ${it.names.size}" },
            )
        }
        if (outcome is RecipientDetector.Outcome.Sent) onRecipientsSent(outcome, platform, now)
    }

    private fun onEnabledPlatformsChanged(enabled: Set<Platform>) {
        val removed = enabledPlatforms - enabled
        enabledPlatforms = enabled
        val now = System.currentTimeMillis()
        for (platform in removed) {
            tracker?.onPlatformDisabled(platform, now)
            if (feedPlatform == platform) {
                feedNode = null
                feedPlatform = null
            }
            CalibrationLog.log { CalibrationEvent(now, LEAVE, platform = platform, note = "plataforma desactivada en Ajustes") }
        }
    }

    // ── Autor, descripción y categoría ───────────────────────────────────────────────────────────

    private class InfoResult(val info: ContentExtractor.VideoInfo, val category: Category, val fromFeed: Boolean)

    private fun scheduleInfo(id: Long, platform: Platform, delayMs: Long, attempt: Int) {
        handler.postDelayed({ readInfo(id, platform, attempt) }, delayMs)
    }

    private fun readInfo(id: Long, platform: Platform, attempt: Int) {
        val t = tracker ?: return
        if (t.currentVideoId != id || infoDone.containsKey(id)) return
        val version = t.version
        val container = feedNode?.takeIf { feedPlatform == platform }?.let(::copyOf)
        val width = screenWidth
        val height = screenHeight
        readScope.launch {
            val result = try {
                readVideoInfo(platform, container, width, height)
            } catch (e: Exception) {
                debugError("lectura de pantalla", e)
                null
            }
            withContext(Dispatchers.Main) { onInfoRead(id, platform, version, attempt, result) }
        }
    }

    /** Hilo de fondo. Primero la página visible del feed; si no hay nada, la ventana del feed entera. */
    private fun readVideoInfo(platform: Platform, container: AccessibilityNodeInfo?, width: Int, height: Int): InfoResult? {
        var info: ContentExtractor.VideoInfo? = null
        var fromFeed = false
        val containerAlive = container?.refresh() == true
        if (container != null && containerAlive) {
            info = ContentExtractor.extract(platform, AccessibilityUiNode(container), width, height, rootIsFeed = true)
            fromFeed = true
        }
        if (info == null || info.isEmpty) {
            val root = rootInActiveWindow
            // Solo la ventana del propio feed: si encima hay un diálogo (comentarios…) no se lee.
            val sameWindow = container == null || (containerAlive && root?.windowId == container.windowId)
            if (root != null && sameWindow && Platform.fromPackage(root.packageName) == platform) {
                info = ContentExtractor.extract(platform, AccessibilityUiNode(root), width, height, rootIsFeed = false)
                fromFeed = false
            }
        }
        val found = info ?: return null
        return InfoResult(found, CategoryClassifier.classify(found.caption, found.hashtags), fromFeed)
    }

    private fun onInfoRead(id: Long, platform: Platform, version: Long, attempt: Int, result: InfoResult?) {
        val t = tracker ?: return
        val now = System.currentTimeMillis()
        if (t.currentVideoId != id || t.version != version) {
            CalibrationLog.log {
                CalibrationEvent(now, INFO, platform = platform, videoId = id, decision = false, rule = "descartado: el vídeo cambió mientras se leía")
            }
            return
        }
        if (result == null || result.info.isEmpty) {
            if (attempt < MAX_INFO_ATTEMPTS) {
                scheduleInfo(id, platform, INFO_RETRY_MS, attempt + 1)
            } else {
                CalibrationLog.log {
                    CalibrationEvent(now, INFO, platform = platform, videoId = id, decision = false, rule = "sin autor ni descripción en pantalla")
                }
            }
            return
        }
        infoDone[id] = Unit
        val info = result.info
        if (FeedRules.rulesFor(platform).contentCheck) {
            ShortSignature.of(info)?.let { read ->
                val sig = signatures[id]?.let(read::enrichedWith) ?: read
                signatures[id] = sig
                if (mergeWithPrevious(id, platform, sig, now)) return
            }
        }
        repository?.let { repo ->
            dbScope.launch {
                runCatching { repo.recordInfo(id, info.creator, info.caption, result.category) }.onFailure { debugError("recordInfo", it) }
            }
        }
        CalibrationLog.log {
            val tags = info.hashtags.take(5).joinToString(" ") { "#$it" }
            CalibrationEvent(
                now, INFO, platform = platform, videoId = id, decision = true,
                rule = if (result.fromFeed) "página visible del feed" else "ventana del feed",
                note = "autor=${info.creator ?: "—"} · ${result.category.label}" + if (tags.isEmpty()) "" else " · $tags",
            )
        }
        debug { "ℹ id=$id autor=${info.creator} categoría=${result.category.label} tags=${info.hashtags}" }
    }

    // ── Segunda señal: ¿sigue siendo el mismo vídeo? ─────────────────────────────────────────────

    /** Lee la huella del vídeo visible y la compara con la del vídeo activo. Solo con contentCheck. */
    private fun runContentCheck() {
        val t = tracker ?: return
        val platform = t.currentPlatform ?: return
        if (!FeedRules.rulesFor(platform).contentCheck || contentCheckRunning) return
        val id = t.currentVideoId ?: return
        // Sin huella del vídeo activo no hay con qué comparar: la pondrá su lectura INFO.
        if (signatures[id] == null) return
        val version = t.version
        val container = feedNode?.takeIf { feedPlatform == platform }?.let(::copyOf)
        val width = screenWidth
        val height = screenHeight
        contentCheckRunning = true
        readScope.launch {
            val result = try {
                readVideoInfo(platform, container, width, height)
            } catch (e: Exception) {
                debugError("comprobación de contenido", e)
                null
            }
            withContext(Dispatchers.Main) {
                contentCheckRunning = false
                onContentRead(id, platform, version, result)
            }
        }
    }

    private fun onContentRead(id: Long, platform: Platform, version: Long, result: InfoResult?) {
        val t = tracker ?: return
        if (t.currentVideoId != id || t.version != version) return
        val known = signatures[id] ?: return
        val seen = result?.info?.let(ShortSignature::of) ?: return
        val now = System.currentTimeMillis()
        when (known.differsFrom(seen)) {
            ShortSignature.Change.NONE -> {
                pendingChange = null
                signatures[id] = known.enrichedWith(seen)
            }
            ShortSignature.Change.WEAK -> if (pendingChange == seen) {
                onMissedSwipe(platform, seen, now, "confirmado en dos lecturas")
            } else {
                pendingChange = seen
                CalibrationLog.log {
                    CalibrationEvent(now, INFO, platform = platform, videoId = id, decision = false, rule = "autor distinto (sin título que comparar): se confirma en la próxima lectura")
                }
                handler.removeCallbacks(contentCheckRunnable)
                handler.postDelayed(contentCheckRunnable, CONTENT_CONFIRM_MS)
            }
            ShortSignature.Change.STRONG -> onMissedSwipe(platform, seen, now, "autor y título distintos")
        }
    }

    /** El vídeo visible ya no es el activo y no llegó ningún scroll que lo dijera. */
    private fun onMissedSwipe(platform: Platform, seen: ShortSignature, now: Long, why: String) {
        val t = tracker ?: return
        pendingChange = null
        val previous = previousVideo[platform]
        val back = previous != null && signatures[previous]?.sameAs(seen) == true
        // El cambio fue en el último scroll de esa app si es reciente y posterior al vídeo activo (con su
        // permanencia mínima); si no, ahora. Nunca antes: el vídeo activo se quedaría sin duración.
        val at = lastScrollAt[platform]
            ?.takeIf { now - it in 0..PRESENCE_INTERVAL_MS && it >= videoChangedAt + ViewTracker.MIN_DWELL_MS }
            ?: now
        val started = t.onContentChanged(platform, at, step = if (back) -1 else 1)
        t.currentVideoId?.let { newId -> signatures[newId] = signatures[newId]?.enrichedWith(seen) ?: seen }
        CalibrationLog.log {
            CalibrationEvent(
                now, INFO, platform = platform, videoId = t.currentVideoId, decision = true,
                rule = "cambio de vídeo sin scroll ($why)",
                note = when {
                    back -> "vuelve al vídeo anterior"
                    started -> "vídeo nuevo"
                    else -> null
                },
            )
        }
        debug { "⇅ Cambio de vídeo sin scroll en ${platform.shortName} ($why)" }
    }

    /**
     * El vídeo [id] tiene la misma huella que el anterior y empezó justo cuando acabó aquel: es el mismo
     * vídeo contado dos veces (la app rehízo el feed, un scroll falso…). Se une al anterior.
     */
    private fun mergeWithPrevious(id: Long, platform: Platform, sig: ShortSignature, now: Long): Boolean {
        val t = tracker ?: return false
        val previous = previousVideo[platform] ?: return false
        if (previous == id || signatures[previous]?.sameAs(sig) != true) return false
        val ended = finishedAt[previous] ?: return false
        val started = startedAtOf[id] ?: return false
        if (abs(started - ended) > MERGE_WINDOW_MS) return false
        discardNote = "mismo autor y título que el anterior: se une a él"
        val merged = try {
            t.mergeActiveInto(previous, now)
        } finally {
            discardNote = null
        }
        if (merged) {
            previousVideo.remove(platform)
            CalibrationLog.log {
                CalibrationEvent(now, INFO, platform = platform, videoId = previous, decision = true, rule = "vídeo repetido: no cuenta otra vez")
            }
            debug { "⇄ id=$id era el mismo vídeo que id=$previous" }
        }
        return merged
    }

    // ── Envío rápido ─────────────────────────────────────────────────────────────────────────────

    private val quickBusy: Boolean get() = quickNames.isNotEmpty()

    /** ¿[platform] tiene envío rápido y «A quién envías»? (Instagram y TikTok: [AppSettings.QUICK_SEND_PLATFORMS].) */
    private fun quickApp(platform: Platform?): Boolean =
        platform != null && platform in AppSettings.QUICK_SEND_PLATFORMS && PlatformProfile.of(platform) != null

    /** El perfil de «A quién envías» en [platform], o `null` si esa app no lo tiene (YouTube). */
    private fun recipientProfile(platform: Platform): PlatformProfile? = if (quickApp(platform)) PlatformProfile.of(platform) else null

    /** Los fijados de [platform] (vacío sin app o en una sin envío rápido). */
    private fun targetsOf(platform: Platform?): List<String> = platform?.let { quickSendTargets[it] }.orEmpty()

    /** Dónde van las burbujas de [platform]: donde las dejó el usuario o, si no, el sitio por defecto de esa app. */
    private fun bubbleSpotOf(platform: Platform): BubbleSpot = quickSendBubbles[platform] ?: BubbleLayout.defaultFor(platform)

    /**
     * Las burbujas se ven con el envío rápido y «A quién envías» encendidos (el envío rápido lee los nombres
     * de la hoja, y la divulgación solo lo dice con ese ajuste), algún fijado **en la app del vídeo** y un vídeo activo
     * en el feed de Instagram o TikTok; qué se ve y con qué marca lo decide [BubbleBoard] (1.4.1: ya no depende de
     * [ShareDetector.isArmed], así que no se esconden y vuelven tras enviar). Se enseñan los fijados de esa app, en su
     * sitio; durante un envío, los de la app del envío ([quickPlatform]). Mientras el dedo las arrastra no se tocan.
     */
    private fun updateOverlay() {
        try {
            if (overlay?.isDragging == true) {
                // Se vuelve a mirar cuando acabe el gesto (la despegada tarda un momento en llegar a su sitio).
                handler.removeCallbacks(overlayRunnable)
                handler.postDelayed(overlayRunnable, OVERLAY_AFTER_DRAG_MS)
                return
            }
            val now = System.currentTimeMillis()
            val t = tracker
            val app = if (quickBusy) quickPlatform else t?.currentPlatform?.takeIf { quickApp(it) } ?: bubblesPlatform
            val targets = targetsOf(app)
            val onReel = quickSendActive() && targets.isNotEmpty() && app in enabledPlatforms &&
                t != null && t.isActive && t.currentPlatform == app
            val reel = t?.currentVideoId
            // Pasar de vídeo borra las marcas de enviado (salir del feed no: al volver al mismo, siguen).
            if (onReel && reel != null) board.onReel(reel)
            if (!quickBusy && quickAway) {
                // Se fue de la app del envío en mitad de él: si sigue fuera, se cierra el vídeo y no se enseña nada.
                // Si lo que tiene delante es un vídeo de la otra app (pasó de TikTok a Instagram), ese no se cierra.
                quickAway = false
                if (isAppOnScreen(quickPlatform) == false && (t?.currentPlatform == null || t.currentPlatform == quickPlatform)) {
                    leave(estimatedLeaveAt(now), "${quickPlatform.shortName} ya no está en pantalla", quickPlatform)
                    return
                }
            }
            val visible = !quickAway && targets.isNotEmpty() && board.visible(onReel, reel, now)
            if (visible) {
                bubblesPlatform = app
                val view = overlay ?: QuickSendOverlay(this, overlayListener).also { overlay = it }
                view.show(targets, avatarCache[app].orEmpty(), bubbleSpotOf(app), screenWidth, screenHeight)
                view.setStates(board.shownMarks.mapValues { (_, mark) -> stateOf(mark) })
                // Vuelven en otro reel: la pregunta de apagarlo (si estaba) ya no viene a cuento.
                if (!board.isDismissedOn(reel)) view.hideQuestion()
                handler.removeCallbacks(overlayWatchRunnable)
                handler.postDelayed(overlayWatchRunnable, OVERLAY_WATCH_MS)
            } else {
                overlay?.hide()
                if (!onReel) overlay?.hideQuestion()
                handler.removeCallbacks(overlayWatchRunnable)
            }
            // Si algo cambia solo con el tiempo (el «!» de un fallo antes de esconderlas), se vuelve a mirar entonces.
            handler.removeCallbacks(overlayRunnable)
            board.nextChangeAt(now)?.let { at -> handler.postDelayed(overlayRunnable, at - now + 20) }
        } catch (e: Exception) {
            debugError("burbujas del envío rápido", e)
        }
    }

    private fun stateOf(mark: BubbleBoard.Mark): QuickSendOverlay.State = when (mark) {
        BubbleBoard.Mark.IDLE -> QuickSendOverlay.State.IDLE
        BubbleBoard.Mark.SENDING -> QuickSendOverlay.State.SENDING
        BubbleBoard.Mark.SENT -> QuickSendOverlay.State.SENT
        BubbleBoard.Mark.FAILED -> QuickSendOverlay.State.FAILED
    }

    private fun quickSendActive(): Boolean = quickSendEnabled && recordRecipients

    private val overlayListener = object : QuickSendOverlay.Listener {
        override fun onTap(name: String) = onQuickSendRequest(listOf(name), gesture = "toque")

        override fun onPair(first: String, second: String) = onQuickSendRequest(listOf(first, second), gesture = "una encima de otra")

        override fun onMoved(spot: BubbleSpot) {
            // 1.10.0: cada app guarda su posición; estas burbujas son las de la app que se ve.
            val platform = bubblesPlatform
            quickSendBubbles = quickSendBubbles + (platform to spot)
            val settings = ReelometroApp.from(this@ReelAccessibilityService).settings
            mainScope.launch { runCatching { settings.setQuickSendBubble(platform, spot) }.onFailure { debugError("posición de las burbujas", it) } }
            CalibrationLog.log {
                // Sin nombres: solo dónde queda el centro de la pila (en % de la pantalla) y si va en fila o columna.
                val fmt = com.reelometro.app.stats.Format
                val shape = if (BubbleLayout.orientationFor(spot.y) == BubbleOrientation.ROW) "fila" else "columna"
                CalibrationEvent(
                    System.currentTimeMillis(), INFO, platform = platform, rule = "envío rápido: burbujas movidas",
                    note = "x ${fmt.percent(spot.x.toDouble())} · y ${fmt.percent(spot.y.toDouble())} · $shape",
                )
            }
        }

        override fun onDismissed(): Boolean = onQuickSendDismissed()
    }

    /** Soltadas en la «X»: fuera hasta el siguiente reel. A la tercera seguida, pregunta (una vez) si se apaga. */
    private fun onQuickSendDismissed(): Boolean {
        if (quickBusy) return false
        val now = System.currentTimeMillis()
        val videoId = tracker?.currentVideoId
        board.onDismissed(videoId)
        handler.removeCallbacks(overlayRunnable)
        overlay?.hide()
        handler.removeCallbacks(overlayWatchRunnable)
        val memory = quickMemory
        val (streak, ask) = (memory?.streak ?: DismissStreak()).dismissed()
        memory?.streak = streak
        CalibrationLog.log {
            CalibrationEvent(now, INFO, platform = bubblesPlatform, videoId = videoId, rule = "envío rápido: burbujas escondidas hasta el siguiente reel", note = if (ask) "pregunta si se apaga" else null)
        }
        if (ask) {
            val settings = ReelometroApp.from(this).settings
            val platform = bubblesPlatform
            overlay?.showQuestion(
                onTurnOff = {
                    mainScope.launch { runCatching { settings.setQuickSendEnabled(false) }.onFailure { debugError("apagar el envío rápido", it) } }
                    CalibrationLog.log { CalibrationEvent(System.currentTimeMillis(), INFO, platform = platform, rule = "envío rápido: apagado desde la pregunta") }
                },
                onKeep = {},
            )
        }
        return true
    }

    /** Burbujas a la vista: ¿sigue delante su app? (otra app, el lanzador…) */
    private fun watchOverlay() {
        if (overlay?.isShown != true) return
        try {
            val platform = if (quickBusy) quickPlatform else bubblesPlatform
            if (isAppOnScreen(platform) == false) {
                val now = System.currentTimeMillis()
                if (quickBusy) {
                    // En mitad de un envío: fuera las burbujas; el envío acaba solo por tiempo.
                    quickAway = true
                    overlay?.hide()
                    overlay?.hideQuestion()
                    return
                }
                val current = tracker?.currentPlatform
                if (current == null || current == platform) {
                    leave(estimatedLeaveAt(now), "${platform.shortName} ya no está en pantalla", platform)
                } else {
                    // Ya hay un vídeo de la otra app delante: ese no se cierra; se pintan sus burbujas.
                    updateOverlay()
                }
                return
            }
        } catch (e: Exception) {
            debugError("burbujas del envío rápido", e)
        }
        handler.postDelayed(overlayWatchRunnable, OVERLAY_WATCH_MS)
    }

    /** Lee de disco las fotos de los fijados de cada app (fuera del hilo principal) y las pasa a las burbujas. */
    private fun reloadAvatars() {
        val store = avatarStore ?: return
        val targets = quickSendTargets
        fileScope.launch {
            val loaded = targets.mapValues { (platform, names) -> names.associateWith { store.load(it, platform) } }
            val savedAt = targets.mapValues { (platform, names) -> names.associateWith { store.savedAt(it, platform) } }
            val chosen = targets.mapValues { (platform, names) -> names.filterTo(HashSet()) { store.hasChosen(it, platform) } }
            withContext(Dispatchers.Main) {
                if (targets != quickSendTargets) return@withContext
                avatarCache = loaded
                avatarSavedAt = savedAt
                avatarChosen = chosen
                // Las burbujas que se ven son las de una sola app: solo esas fotos.
                loaded[bubblesPlatform]?.forEach { (name, bitmap) -> overlay?.setAvatar(name, bitmap) }
            }
        }
    }

    /**
     * Guarda la foto recortada de [name] en [platform] (si sigue fijado ahí) y la pone en su burbuja. Si el usuario
     * eligió una de su galería mientras tanto, no se guarda (la elegida manda y la captura no la toca).
     */
    private fun saveAvatar(platform: Platform, name: String, bitmap: Bitmap) {
        val store = avatarStore ?: return
        fileScope.launch {
            val stillPinned = withContext(Dispatchers.Main) { name in targetsOf(platform) }
            if (!stillPinned || store.hasChosen(name, platform) || !store.save(name, bitmap, platform)) return@launch
            // AvatarStore.changes recarga las burbujas y las filas de Ajustes.
        }
    }

    /**
     * ¿Hay que renovar la foto de [name] (fijado en [platform]) en un envío a mano? Solo en Android 11+ (antes no hay
     * captura) y nunca si el usuario eligió una de su galería.
     */
    private fun wantsAvatar(platform: Platform, name: String, now: Long): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && avatarStore != null &&
            AvatarRules.needsRefresh(avatarSavedAt[platform]?.get(name), now, chosen = avatarChosen[platform]?.contains(name) == true)

    /**
     * ¿Se recorta la foto de [name] (fijado en [platform]) en un **envío rápido**? (1.4.1) Solo si aún no tiene
     * ninguna (ni elegida de la galería) y no falló en el último día: la captura retrasa el clic en el nombre.
     * Renovarla al día se hace en los envíos a mano.
     */
    private fun wantsQuickAvatar(platform: Platform, name: String, now: Long): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && avatarStore != null &&
            AvatarRules.captureInQuickSend(
                avatarSavedAt[platform]?.get(name), quickMemory?.avatarFailedAt(name, platform), now,
                chosen = avatarChosen[platform]?.contains(name) == true,
            )

    /**
     * Envío a mano en [platform] con «A quién envías» y el envío rápido encendidos: si algún fijado de esa app necesita
     * foto, se busca su celda en la hoja que se acaba de abrir y se recorta (antes de que el usuario marque a nadie,
     * para que no salga la marca de seleccionado). Dos intentos; el primero que acierta cancela el otro.
     */
    private fun scheduleManualAvatars(platform: Platform) {
        if (!quickSendActive() || quickBusy) return
        val now = System.currentTimeMillis()
        val names = targetsOf(platform).filter { wantsAvatar(platform, it, now) }
        if (names.isEmpty()) return
        val run = ++manualAvatarRun
        MANUAL_AVATAR_DELAYS_MS.forEach { delay -> handler.postDelayed({ captureManualAvatars(run, platform, names) }, delay) }
    }

    private fun captureManualAvatars(run: Long, platform: Platform, candidates: List<String>) {
        val now = System.currentTimeMillis()
        if (run != manualAvatarRun || quickBusy || !quickSendActive()) return
        if (!recipientDetector.isOpen(now) || recipientDetector.platform != platform || recipientDetector.selectedCount > 0) return
        val profile = PlatformProfile.of(platform) ?: return
        val names = candidates.filter { it in targetsOf(platform) && wantsAvatar(platform, it, now) }
        if (names.isEmpty()) return
        val width = screenWidth
        val height = screenHeight
        readScope.launch {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@launch
            val roots = appRoots(platform)
            val found = names.mapNotNull { name -> locateAvatar(roots, name, width, height, profile)?.let { name to it } }
            if (found.isEmpty()) return@launch
            val crops = AvatarCapture.crop(this@ReelAccessibilityService, found.map { it.second }) ?: return@launch
            var saved = 0
            found.zip(crops).forEach { (pair, bitmap) ->
                if (bitmap != null) {
                    saveAvatar(platform, pair.first, bitmap)
                    saved++
                }
            }
            withContext(Dispatchers.Main) {
                if (run == manualAvatarRun && saved == names.size) manualAvatarRun++ // todo hecho: sobra el segundo intento
                CalibrationLog.log {
                    CalibrationEvent(System.currentTimeMillis(), INFO, platform = platform, rule = "envío rápido: foto de perfil recortada", note = "$saved de ${names.size} (envío a mano)")
                }
            }
        }
    }

    /**
     * El cuadrado de la foto de [name] en la hoja de compartir de [profile], buscando su nombre con el atajo. Solo en la
     * ventana de la hoja (la que tiene alguna de sus marcas: «Copiar enlace» en Instagram; «Enviar a» en TikTok, cuya
     * hoja es otra ventana) y nunca dentro del visor.
     */
    private fun locateAvatar(roots: List<UiNode>, name: String, width: Int, height: Int, profile: PlatformProfile): NodeBounds? {
        val key = RecipientDetector.normalize(name)
        // En TikTok la descripción de la celda es el nombre entero (con comas, si es un grupo, o solo emojis).
        val tiktok = profile.platform == Platform.TIKTOK
        for (root in roots) {
            val isSheet = profile.markerQueries.any { q ->
                AccessibilityUiFinder.byText(root, q).any { n ->
                    n.isVisible && !recipientDetector.insideFeed(n, profile.feedIds) &&
                        listOfNotNull(n.text, n.contentDescription).any { isSheetMarker(it, profile) }
                }
            }
            if (!isSheet) continue
            for (node in AccessibilityUiFinder.byText(root, name)) {
                if (!node.isVisible || node.className?.contains("EditText") == true || recipientDetector.insideFeed(node, profile.feedIds)) continue
                val fromText = node.text?.let { recipientDetector.clean(it.toString(), fromText = true, symbolNames = profile.symbolNames) }
                val fromDesc = node.contentDescription?.let {
                    recipientDetector.clean(it.toString(), fromText = tiktok, symbolNames = profile.symbolNames)
                }
                if (listOfNotNull(fromText, fromDesc).none { RecipientDetector.normalize(it) == key }) continue
                val photo = AvatarRules.photo(node, name, height) ?: continue
                AvatarRules.cropBox(photo.bounds, width, height, group = photo.group)?.let { return it }
            }
        }
        return null
    }

    /** ¿[label] es una opción que solo sale en la hoja de compartir de [profile]? (Como `QuickSender.isSheetMarker`.) */
    private fun isSheetMarker(label: CharSequence, profile: PlatformProfile): Boolean =
        RecipientDetector.normalize(label.toString()).trimEnd('.', '…', ':', ' ') in profile.sheetMarkers

    /**
     * Tocar una burbuja ([names] = una) o soltar una encima de otra (dos). Sin envío en curso, empieza uno. Con uno
     * en curso (1.4.1) ya no se ignora: si aún no se ha pulsado «Enviar», se marca en la misma hoja; si ya se pulsó,
     * se encola y sale justo después. Nunca hay dos hojas a la vez.
     */
    /**
     * [allowRelease]: un toque con «Enviar» ya aceptado puede cerrar ese envío ([QuickSender.release]); una vez.
     * 1.10.0: la app es la del vídeo activo (Instagram o TikTok) y los nombres tienen que estar fijados en ella; con un
     * envío en curso en la otra app, el toque no hace nada (un envío empieza y acaba en su app).
     */
    private fun onQuickSendRequest(names: List<String>, gesture: String, allowRelease: Boolean = true) {
        val t = tracker ?: return
        val platform = t.currentPlatform ?: return
        if (!quickSendActive() || !quickApp(platform)) return
        if (quickBusy && platform != quickPlatform) return
        val pinned = targetsOf(platform)
        val wanted = names.distinct().filter { it in pinned }
        if (wanted.isEmpty()) return
        if (!quickBusy) {
            startQuickSend(wanted, waitForClosedSheet = false, gesture = gesture, platform = platform)
            return
        }
        if (quickProvisionalShown && allowRelease) {
            // 1.4.2: Android ya aceptó «Enviar» y solo falta confirmarlo. La confirmación no va en el camino crítico:
            // ese envío se da por hecho y el nuevo empieza ya (esperando a que se cierre la hoja anterior), también
            // en otro reel o con la misma burbuja.
            val run = quickRun
            readScope.launch {
                val at = System.currentTimeMillis()
                val done = quickSender.release(at)
                val report = if (done != null) quickSender.lastReport else null
                val results = if (done != null) quickSender.lastResults else emptyList()
                withContext(Dispatchers.Main) {
                    if (done != null && run == quickRun && quickBusy) onQuickSendDone(done, report, results, at)
                    when {
                        !quickBusy -> startQuickSend(wanted, waitForClosedSheet = true, gesture = gesture, platform = platform)
                        // Ya hay otro envío (lo encolado salió): se suma o se encola como siempre.
                        !quickProvisionalShown -> onQuickSendRequest(wanted, gesture, allowRelease = false)
                        // Aún no se puede cerrar (diseño antiguo: «Enviar» de una fila y otro destino por marcar): se
                        // suma o se encola como siempre, nunca se pierde el toque (revisión de la 1.4.3).
                        else -> onQuickSendRequest(wanted, gesture, allowRelease = false)
                    }
                }
            }
            return
        }
        val fresh = wanted.filter { it !in quickNames && it !in quickQueue }
        if (fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        val run = quickRun
        val reel = quickVideoId
        val targets = fresh.map { quickMemory?.target(it, wantAvatar = wantsQuickAvatar(platform, it, now), platform = platform) ?: QuickSender.Target(it) }
        board.onSending(fresh, quickVideoId)
        overlay?.setStates(board.shownMarks.mapValues { (_, mark) -> stateOf(mark) })
        quickMemory?.let { it.streak = it.streak.used() }
        // En el hilo de los pasos: entre dos pasos, nunca a medias de uno.
        readScope.launch {
            val joined = targets.filter { quickSender.add(it) }.map { it.name }
            withContext(Dispatchers.Main) {
                val rest = fresh - joined.toSet()
                when {
                    run == quickRun && quickBusy -> {
                        quickNames += joined.filter { it !in quickNames }
                        quickQueue += rest.filter { it !in quickQueue }
                    }
                    // El envío acabó mientras tanto: estos salen ya, en uno nuevo, pero solo con el mismo vídeo delante.
                    !quickBusy -> if (sameReel(reel, platform)) {
                        startQuickSend(fresh, waitForClosedSheet = true, gesture = "en cola", reel = reel, platform = platform)
                    } else {
                        board.onFinished(fresh.associateWith { false }, sheetLeftOpen = false, System.currentTimeMillis())
                        updateOverlay()
                    }
                    else -> quickQueue += fresh.filter { it !in quickQueue && it !in quickNames }
                }
                CalibrationLog.log {
                    CalibrationEvent(
                        System.currentTimeMillis(), INFO, platform = quickPlatform, videoId = quickVideoId,
                        rule = "envío rápido: otra burbuja durante el envío",
                        note = "se suman ${joined.size} a la misma hoja · se encolan ${rest.size}",
                    )
                }
            }
        }
    }

    /**
     * ¿Sigue delante el vídeo [videoId] de [platform]? Lo encolado solo sale con el vídeo que se eligió; sin vídeo
     * activo (se ha ido del visor) o en otra app no sale.
     */
    private fun sameReel(videoId: Long?, platform: Platform = quickPlatform): Boolean {
        val t = tracker ?: return false
        return videoId != null && t.currentPlatform == platform && t.currentVideoId == videoId
    }

    /**
     * [reel]: el vídeo desde el que se pidió (lo encolado lleva el del envío en el que se tocó). [platform]: su app; todo
     * el envío pasa en ella ([quickPlatform], con su [PlatformProfile]).
     */
    private fun startQuickSend(
        names: List<String>,
        waitForClosedSheet: Boolean,
        gesture: String,
        platform: Platform,
        reel: Long? = tracker?.currentVideoId,
    ) {
        val t = tracker ?: return
        val profile = PlatformProfile.of(platform) ?: return
        val now = System.currentTimeMillis()
        // 1.4.2: justo después de un envío que salió bien, su hoja aún se puede estar cerrando: se espera a que se
        // cierre en vez de rendirse con «ya hay una hoja abierta». Solo si fue en la misma app.
        val afterSend = quickLastOkPlatform == platform && SystemClock.uptimeMillis() - quickLastOkAt < QUICK_SHEET_CLOSING_MS
        val waitClosed = waitForClosedSheet || afterSend
        handler.removeCallbacks(overlayWatchRunnable)
        quickPlatform = platform
        bubblesPlatform = platform
        quickRun++
        manualAvatarRun++
        quickNames.clear()
        quickNames += names
        quickVideoId = reel ?: t.currentVideoId
        quickStartedAt = SystemClock.uptimeMillis()
        quickProvisionalShown = false
        quickDirty = false
        quickAway = false
        setContentEvents(true)
        ignoreClicksUntil = now + QUICK_IGNORE_AFTER_MS
        recipientDetector.stop()
        // Una comprobación de cierre de la hoja que siga pendiente queda anulada: el aviso «Enviado a …» que salga ahora es
        // de este envío rápido, no de lo que se marcó en esa hoja.
        sheetCloseRun++
        // Y las lecturas de avisos de un envío anterior paran: lo que salga ahora es de este (se lee al terminar).
        sentBannerMemoryGen++
        val memory = quickMemory
        memory?.let { it.streak = it.streak.used() }
        val hints = memory?.hints(platform) ?: QuickSender.Hints()
        val targets = names.map { memory?.target(it, wantAvatar = wantsQuickAvatar(platform, it, now), platform = platform) ?: QuickSender.Target(it) }
            .map { if (debugForceSearch) it.copy(skipGrid = true) else it }
        // En el hilo de los pasos (start está sincronizado con tick: así no espera aquí a un paso que acaba).
        readScope.launch { quickSender.start(targets, now, hints, waitClosed, profile = profile) }
        board.onSending(names, quickVideoId)
        updateOverlay()
        // Si el dedo aún suelta la despegada (updateOverlay no toca nada mientras), las marcas van igual.
        overlay?.setStates(board.shownMarks.mapValues { (_, mark) -> stateOf(mark) })
        val pinned = targetsOf(platform)
        CalibrationLog.log {
            CalibrationEvent(
                now, INFO, platform = platform, videoId = quickVideoId, rule = "envío rápido: empieza",
                note = listOfNotNull(
                    "burbuja " + names.joinToString(" y ") { "${pinned.indexOf(it) + 1}" } + " de ${pinned.size}",
                    gesture,
                    "ids aprendidos: ${if (hints.shareId != null || hints.searchId != null) "sí" else "no"}",
                    "directo al buscador: ${targets.count { it.skipGrid }}".takeIf { targets.any { it.skipGrid } },
                    "recorta la foto: ${targets.count { it.wantAvatar }}".takeIf { targets.any { it.wantAvatar } },
                    "espera a que se cierre la hoja anterior".takeIf { waitClosed },
                ).joinToString(" · "),
            )
        }
        scheduleQuickTick(0)
    }

    /**
     * Un evento de la app del envío mientras envía (la hoja se abre, cambia su contenido…): el siguiente paso va en
     * [QUICK_EVENT_DEBOUNCE_MS] como mucho, sin esperar al reloj. Si ya hay un paso leyendo la pantalla, el
     * siguiente irá en cuanto acabe.
     */
    private fun onQuickSendEvent() {
        if (!quickBusy) return
        if (quickTickInFlight) {
            quickDirty = true
            return
        }
        scheduleQuickTick(QUICK_EVENT_DEBOUNCE_MS)
    }

    /**
     * `typeWindowContentChanged` solo mientras dura un envío: llega a montones (de las tres apps), así que no está en
     * `accessibility_service_config.xml` y se pide al empezar y se quita al acabar. Aun así, si llega sin envío en
     * curso o de otra app que la del envío ([quickPlatform]) se descarta nada más entrar ([onAccessibilityEvent]).
     */
    private fun setContentEvents(on: Boolean) {
        try {
            val info = serviceInfo ?: return
            val types = if (on) {
                info.eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            } else {
                info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED.inv()
            }
            if (types == info.eventTypes) return
            info.eventTypes = types
            serviceInfo = info
        } catch (e: RuntimeException) {
            debugError("eventos de contenido", e)
        }
    }

    /** El siguiente paso dentro de [delayMs] (si ya había uno antes, se queda ese: los eventos no lo retrasan). */
    private fun scheduleQuickTick(delayMs: Long) {
        val at = SystemClock.uptimeMillis() + delayMs
        val current = quickTickAt
        if (current != null && current <= at) return
        handler.removeCallbacks(quickTickRunnable)
        quickTickAt = at
        handler.postAtTime(quickTickRunnable, at)
    }

    private fun cancelQuickTick() {
        handler.removeCallbacks(quickTickRunnable)
        quickTickAt = null
    }

    /** Lo que vuelve de un paso al hilo principal. [platform]: la app del envío de ese paso. */
    private class QuickResult(
        val step: QuickSender.Step,
        val accepted: Boolean?,
        val provisional: List<String>,
        val report: QuickSender.Report?,
        val results: List<QuickSender.DestResult>,
        val avatarName: String?,
        val avatar: Bitmap?,
        val platform: Platform,
    )

    /** Un paso: lee las ventanas de la app del envío y pulsa o escribe, fuera del hilo principal. */
    private fun runQuickTick() {
        if (!quickBusy) return
        if (quickTickInFlight) {
            quickDirty = true
            return
        }
        quickTickInFlight = true
        quickDirty = false
        val run = quickRun
        val platform = quickPlatform
        val width = screenWidth
        val height = screenHeight
        // Se pulsa «Compartir» solo en el reel desde el que se pidió el envío (lo encolado espera a que se cierre la
        // hoja anterior: si entretanto se pasa de reel, no se manda otro).
        // Sin vídeo activo (se ha ido del visor) tampoco: «Compartir» podría ser el de otra publicación.
        val reelChanged = quickVideoId != null && tracker?.currentVideoId != quickVideoId
        val tooLong = SystemClock.uptimeMillis() - quickStartedAt > QUICK_MAX_MS
        readScope.launch {
            val now = System.currentTimeMillis()
            var accepted: Boolean? = null
            var avatar: Bitmap? = null
            var avatarName: String? = null
            val step = if (tooLong) {
                // Red de seguridad: ningún envío sigue indefinidamente (cada fase ya tiene su plazo).
                quickSender.abort("tarda demasiado", now)
            } else try {
                val readStart = SystemClock.uptimeMillis()
                // Solo las ventanas de la app del envío: nunca se lee ni se pulsa nada de otra.
                val roots = appRoots(platform)
                val ticked = quickSender.tick(roots, width, height, now)
                val readMs = SystemClock.uptimeMillis() - readStart
                quickSender.noteRead(readMs)
                // Solo en debug y sin nombres: el paso y lo que costó leer, con la hora desde el toque.
                debug {
                    val what = when (ticked) {
                        is QuickSender.Step.Click -> "pulsa ${ticked.what}"
                        is QuickSender.Step.SetText -> "escribe"
                        QuickSender.Step.Wait -> "espera"
                        is QuickSender.Step.Done -> "fin (${ticked.reason})"
                    }
                    "⇢ paso +${SystemClock.uptimeMillis() - quickStartedAt} ms · fase ${quickSender.currentPhase?.short ?: "-"} · $what · " +
                        "lectura $readMs ms · raíces ${roots.size}"
                }
                if (reelChanged && (ticked as? QuickSender.Step.Click)?.what == "compartir") {
                    quickSender.cancel()
                    QuickSender.Step.Done(false, "el reel ha cambiado", sheetOpened = false, targetSelected = false)
                } else ticked.also { step ->
                    // (Lo que ha costado leer la pantalla en este paso ya va a «envío rápido: tiempos».)
                    // La foto se recorta antes de pulsar el nombre: después la app le pone encima la marca de marcado.
                    val click = step as? QuickSender.Step.Click
                    val photo = click?.avatar
                    avatarName = click?.avatarName
                    if (avatarName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val waitStart = SystemClock.uptimeMillis()
                        val box = photo?.let { AvatarRules.cropBox(it, width, height, group = click?.avatarGroup == true) }
                        if (box != null) {
                            // Que nuestras burbujas no salgan en la foto: si la tapan, se esconden un momento.
                            val hidden = withContext(Dispatchers.Main) { overlay?.hideForCapture(box) == true }
                            if (hidden) delay(CAPTURE_HIDE_MS)
                            try {
                                avatar = AvatarCapture.crop(this@ReelAccessibilityService, listOf(box), QUICK_CAPTURE_TIMEOUT_MS)?.firstOrNull()
                            } finally {
                                if (hidden) withContext(Dispatchers.Main) { overlay?.showAfterCapture() }
                            }
                        }
                        quickSender.noteAvatar(SystemClock.uptimeMillis() - waitStart, avatar != null)
                    }
                    val performStart = SystemClock.uptimeMillis()
                    accepted = perform(step, width)
                    val performMs = SystemClock.uptimeMillis() - performStart
                    // Si Android rechaza la acción, la máquina vuelve atrás y lo reintenta en el siguiente paso.
                    quickSender.onPerformed(accepted, performMs)
                }
            } catch (e: Exception) {
                debugError("envío rápido", e)
                quickSender.cancel()
                QuickSender.Step.Done(false, "error al leer la pantalla", sheetOpened = false, targetSelected = false)
            }
            val result = QuickResult(
                step, accepted, quickSender.provisionalNames,
                report = if (step is QuickSender.Step.Done) quickSender.lastReport else null,
                results = if (step is QuickSender.Step.Done) quickSender.lastResults else emptyList(),
                avatarName = avatarName,
                avatar = avatar,
                platform = platform,
            )
            withContext(Dispatchers.Main) {
                quickTickInFlight = false
                result.avatarName?.let { name ->
                    val bitmap = result.avatar
                    // Una captura fallida (o una foto que no se encuentra) no se reintenta en cada envío: espera un día.
                    if (bitmap != null) {
                        saveAvatar(result.platform, name, bitmap)
                    } else {
                        quickMemory?.avatarFailed(name, System.currentTimeMillis(), result.platform)
                    }
                }
                if (run == quickRun && quickBusy) onQuickStep(result) else if (quickBusy) scheduleQuickTick(0)
            }
        }
    }

    /**
     * Ventanas de [platform], de la de más arriba a la de más abajo (en Instagram la hoja va en la del visor; en TikTok,
     * en un diálogo propio encima del feed). Solo se pide la raíz de las ventanas de apps (1.4.1): las del sistema, el
     * teclado y las propias burbujas no son de la app y cada raíz cuesta una llamada entre procesos. Hilo de fondo.
     */
    private fun appRoots(platform: Platform): List<UiNode> {
        val list = try {
            windows
        } catch (e: RuntimeException) {
            null
        }
        val roots = list.orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .mapNotNull { window -> runCatching { window.root }.getOrNull() }
            .filter { Platform.fromPackage(it.packageName) == platform }
        val found = roots.ifEmpty {
            listOfNotNull(rootInActiveWindow?.takeIf { Platform.fromPackage(it.packageName) == platform })
        }
        return found.map { AccessibilityUiNode(it) }
    }

    /** Solo debug: la app con envío rápido de la ventana de app más alta (la que se ve), o `null`. Hilo de fondo. */
    private fun foregroundQuickPlatform(): Platform? {
        val list = try {
            windows
        } catch (e: RuntimeException) {
            null
        }
        val top = list.orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .firstNotNullOfOrNull { window -> runCatching { Platform.fromPackage(window.root?.packageName) }.getOrNull() }
        return top?.takeIf { quickApp(it) }
    }

    /** Pulsa (en el nodo o su primer antepasado pulsable) o escribe. `null` si el paso no hace nada. */
    private fun perform(step: QuickSender.Step, screenWidth: Int): Boolean? = when (step) {
        is QuickSender.Step.Click -> (step.node as? AccessibilityUiNode)?.node?.let {
            clickNodeOrAncestor(it, step.maxClimbWidth ?: screenWidth)
        } ?: false
        is QuickSender.Step.SetText -> (step.node as? AccessibilityUiNode)?.node?.let { field ->
            runCatching { field.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
            }
            runCatching { field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
        } ?: false
        else -> null
    }

    /** Sube hasta el primer antepasado pulsable, pero nunca a uno más ancho que [maxWidth] (el vídeo, la hoja). */
    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo, maxWidth: Int): Boolean {
        var current: AccessibilityNodeInfo? = node
        var level = 0
        val r = Rect()
        while (current != null && level < MAX_CLICK_ANCESTORS) {
            val n: AccessibilityNodeInfo = current
            runCatching { n.getBoundsInScreen(r) }
            if (r.width() > maxWidth) break
            if (runCatching { n.isClickable }.getOrDefault(false)) {
                return runCatching { n.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            }
            current = runCatching { n.parent }.getOrNull()
            level++
        }
        // Sin antepasado pulsable a la medida: se pide el clic al propio nodo (Android dirá si lo acepta).
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    /** Hilo principal. Cada paso va a Calibración sin nombres (solo qué se pulsa y en qué fase). */
    private fun onQuickStep(result: QuickResult) {
        val step = result.step
        val accepted = result.accepted
        val now = System.currentTimeMillis()
        ignoreClicksUntil = now + QUICK_IGNORE_AFTER_MS
        if ((step as? QuickSender.Step.Click)?.what == "compartir") quickShareClickedAt = now
        // 1.4.2: tras marcar un nombre (Android lo aceptó), «Enviar» se busca ya, sin esperar al reloj. 1.10.0: lo mismo tras
        // la lupa de TikTok (su buscador sale al momento).
        val nameAccepted = accepted == true && (step as? QuickSender.Step.Click)?.what?.let { it.startsWith("nombre") || it == "lupa del buscador" } == true
        val next = when {
            nameAccepted -> 0L
            quickDirty -> QUICK_EVENT_DEBOUNCE_MS
            else -> QUICK_TICK_MS
        }
        quickDirty = false
        when (step) {
            is QuickSender.Step.Click, is QuickSender.Step.SetText -> {
                CalibrationLog.log {
                    CalibrationEvent(
                        now, INFO, platform = result.platform, videoId = quickVideoId, decision = accepted,
                        rule = "envío rápido: " + if (step is QuickSender.Step.Click) "pulsa ${step.what}" else "escribe en el buscador",
                        note = listOfNotNull(
                            "Android no aceptó la acción".takeIf { accepted == false },
                            "foto recortada".takeIf { result.avatar != null },
                            "foto sin recortar (no se reintenta hoy)".takeIf { result.avatarName != null && result.avatar == null },
                        ).joinToString(" · ").ifEmpty { null },
                    )
                }
                // Android aceptó «Enviar»: esas burbujas (solo las de destinos marcados) dicen «enviado» ya; la
                // confirmación sigue por detrás.
                if (result.provisional.isNotEmpty()) {
                    quickProvisionalShown = true
                    board.onProvisionallySent(result.provisional)
                    overlay?.setStates(board.shownMarks.mapValues { (_, mark) -> stateOf(mark) })
                }
                scheduleQuickTick(next)
            }
            QuickSender.Step.Wait -> scheduleQuickTick(next)
            is QuickSender.Step.Done -> onQuickSendDone(step, result.report, result.results, now)
        }
    }

    /** Fin del envío en curso: se apunta en su app ([quickPlatform]), como si lo hubiera hecho el usuario. */
    private fun onQuickSendDone(done: QuickSender.Step.Done, report: QuickSender.Report?, results: List<QuickSender.DestResult>, now: Long) {
        val names = quickNames.toList()
        val videoId = quickVideoId
        val platform = quickPlatform
        quickNames.clear()
        cancelQuickTick()
        setContentEvents(false)
        if (done.ok) {
            quickLastOkAt = SystemClock.uptimeMillis()
            quickLastOkPlatform = platform
        }
        CalibrationLog.log {
            CalibrationEvent(
                now, INFO, platform = platform, videoId = videoId, decision = done.ok,
                rule = "envío rápido: ${done.reason}",
                note = listOfNotNull(
                    "la hoja se queda abierta para terminar a mano".takeIf { !done.ok && done.sheetOpened },
                    "ya decía enviado: pasa a fallo".takeIf { !done.ok && quickProvisionalShown },
                    // Por destino, sin nombres: «destino 2: no encuentro el nombre».
                    results.withIndex().filter { !it.value.ok && results.size > 1 }
                        .joinToString(" · ") { "destino ${it.index + 1}: ${it.value.reason}" }.ifEmpty { null },
                ).joinToString(" · ").ifEmpty { null },
            )
        }
        if (report != null) {
            // Tiempos de cada fase, para comparar con un log real (sin nombres).
            CalibrationLog.log {
                CalibrationEvent(now, INFO, platform = platform, videoId = videoId, decision = done.ok, rule = "envío rápido: tiempos", note = report.describe())
            }
            quickMemory?.learn(report, results, done.sheetOpened, platform)
            debug { "⇢ Envío rápido: ${report.describe()}" }
        }
        val repo = repository
        if (done.ok || done.sheetOpened) {
            // Se ha pulsado «Compartir» de verdad: cuenta como compartido, como si lo hubiera hecho el usuario.
            shareDetector.onShareRecorded(platform, now)
            repo?.let { dbScope.launch { runCatching { it.recordShare(platform, now, videoId) }.onFailure { e -> debugError("recordShare", e) } } }
            CalibrationLog.log {
                CalibrationEvent(now, SHARE, platform = platform, videoId = videoId, rule = if (done.ok) "envío rápido" else "envío rápido a medias")
            }
        }
        val sent = results.filter { it.ok }.map { it.name }
        if (sent.isNotEmpty() && recordRecipients) {
            // Cada destino, en su ranking de «A quién envías» (el de su app).
            repo?.let { dbScope.launch { runCatching { it.recordRecipients(platform, now, videoId, sent) }.onFailure { e -> debugError("recordRecipients", e) } } }
        }
        val leftOpen = !done.ok && done.sheetOpened
        // 1.10.1: la ventana de su aviso «Enviado a …», para que una sesión que empiece a ciegas no lo tome por el suyo.
        if (sent.isNotEmpty() && !leftOpen) rememberSentBannerWindows(platform)
        val profile = recipientProfile(platform)
        if (leftOpen && recordRecipients && profile != null) {
            // Lo que el usuario termine a mano se apunta como siempre (con los fijados ya marcados, si lo estaban).
            recipientDetector.start(now, videoId, preselected = results.filter { it.selected }.map { it.name }, profile = profile)
            snapshotSentBanners(platform, now)
        }
        val outcome = (names.associateWith { false } + results.associate { it.name to it.ok })
        val queued = quickQueue.toList()
        quickQueue.clear()
        if (queued.isNotEmpty() && leftOpen) {
            // La hoja se queda abierta para terminar a mano: lo encolado no sale (nunca dos hojas a la vez).
            board.onFinished(outcome + queued.associateWith { false }, sheetLeftOpen = true, now)
        } else {
            board.onFinished(outcome, sheetLeftOpen = leftOpen, now)
        }
        updateOverlay()
        debug { "⇢ Envío rápido: ${done.reason}" }
        if (queued.isNotEmpty() && !leftOpen && sameReel(videoId, platform)) {
            // Lo que se tocó con «Enviar» ya pulsado sale ahora, en cuanto se cierre la hoja de este.
            startQuickSend(queued, waitForClosedSheet = true, gesture = "en cola", platform = platform, reel = videoId)
        } else if (queued.isNotEmpty() && !leftOpen) {
            board.onFinished(queued.associateWith { false }, sheetLeftOpen = false, now)
            updateOverlay()
        }
    }

    // ── ¿Sigue mirando? ──────────────────────────────────────────────────────────────────────────

    private fun ensurePresenceChecks() {
        if (presenceScheduled || tracker?.isActive != true) return
        presenceScheduled = true
        handler.postDelayed(presenceRunnable, PRESENCE_INTERVAL_MS)
    }

    /** Cada pocos segundos, solo con un vídeo activo: pantalla, ventanas visibles y feed visible. */
    private fun checkPresence() {
        val t = tracker ?: return
        val platform = t.currentPlatform ?: return
        val now = System.currentTimeMillis()
        try {
            t.tick(now)
            if (!t.isActive) {
                CalibrationLog.log { CalibrationEvent(now, LEAVE, platform = platform, note = "duración máxima por vídeo alcanzada") }
                return
            }
            if (!isInteractive()) {
                leave(now, "pantalla apagada", platform)
                return
            }
            when (isAppOnScreen(platform)) {
                false -> {
                    leave(estimatedLeaveAt(now), "${platform.shortName} ya no está en pantalla", platform)
                    return
                }
                true -> lastEvidenceAt = max(lastEvidenceAt, now)
                null -> Unit
            }
            if (!isFeedOnScreen(platform)) {
                t.onLeaveFeed(platform, estimatedLeaveAt(now))
                updateOverlay()
                CalibrationLog.log { CalibrationEvent(now, LEAVE, platform = platform, note = "el feed ya no se ve (otra pantalla de la app)") }
                debug { "⏸ El feed de ${platform.shortName} ya no se ve" }
            } else {
                runContentCheck()
            }
        } catch (e: Exception) {
            debugError("comprobación periódica", e)
        } finally {
            ensurePresenceChecks()
        }
    }

    private fun leave(at: Long, reason: String, platform: Platform?) {
        val t = tracker ?: return
        val wasActive = t.isActive
        t.onLeave(at)
        updateOverlay()
        CalibrationLog.log {
            CalibrationEvent(System.currentTimeMillis(), LEAVE, platform = platform, note = reason + if (wasActive) " · vídeo cerrado" else "")
        }
        debug { "⏏ $reason" }
    }

    /** El usuario se fue entre la última prueba de que estaba y ahora: se toma el punto medio. */
    private fun estimatedLeaveAt(now: Long): Long =
        if (lastEvidenceAt in 1..now) lastEvidenceAt + (now - lastEvidenceAt) / 2 else now

    private fun isInteractive(): Boolean =
        runCatching { getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true

    /**
     * ¿Sigue la app en pantalla? true = sí; false = hay otra app delante; null = no se sabe (solo
     * ventanas del sistema: barra de notificaciones, teclado…; se trata como «sigue»).
     */
    private fun isAppOnScreen(platform: Platform): Boolean? {
        val list = try {
            windows
        } catch (e: RuntimeException) {
            null
        }
        if (list.isNullOrEmpty()) {
            val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            return if (pkg != null && pkg in platform.packages) true else null
        }
        var otherApp = false
        for (window in list) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull() ?: continue
            if (pkg in platform.packages) return true
            otherApp = true
        }
        return if (otherApp) false else null
    }

    /** ¿Sigue visible el contenedor del feed? (otra pestaña, el perfil del creador, un vídeo normal…) */
    private fun isFeedOnScreen(platform: Platform): Boolean {
        val node = feedNode ?: return true
        if (feedPlatform != platform) return true
        return try {
            if (!node.refresh()) {
                // Medido en TikTok 47.7: con su hoja abierta, el nodo del feed deja de refrescarse (la ventana de detrás no
                // contesta) aunque el feed siga ahí.
                val sheet = sheetOverFeed(platform, node)
                debug { "feed de ${platform.shortName}: el nodo no se refresca · hoja encima: $sheet" }
                return sheet
            }
            if (!node.isVisibleToUser) {
                val sheet = sheetOverFeed(platform, node)
                debug { "feed de ${platform.shortName} no visible · hoja encima: $sheet" }
                return sheet
            }
            val r = Rect()
            node.getBoundsInScreen(r)
            r.width() >= screenWidth / 2 && r.height() >= screenHeight / 2
        } catch (e: RuntimeException) {
            true
        }
    }

    /**
     * 1.10.0, medido en el móvil: en TikTok la hoja de compartir (y la de comentarios) es un diálogo encima del feed, y con él
     * abierto el feed deja de contar como visible. Sigue ahí detrás, igual que en Instagram, donde la hoja va en la misma
     * ventana y el vídeo no se cierra: si encima del feed hay una hoja de abajo de la misma app ([PlatformProfile.openSheetIds]),
     * el feed se da por visto. Sin esto, abrir la hoja cerraba el vídeo (y el envío rápido se quedaba sin vídeo activo).
     */
    private fun sheetOverFeed(platform: Platform, feed: AccessibilityNodeInfo): Boolean {
        val profile = PlatformProfile.of(platform)?.takeIf { it.sheetOwnWindow } ?: return false
        val feedWindow = feed.windowId
        val ids = profile.openSheetIds.flatMap { profile.fullIds(it) }
        return try {
            windows.any { w ->
                w.type == AccessibilityWindowInfo.TYPE_APPLICATION && w.id != feedWindow &&
                    w.root?.let { root -> Platform.fromPackage(root.packageName) == platform && ids.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() } } == true
            }
        } catch (e: RuntimeException) {
            false
        }
    }

    /** Cada comprobación de [checkSheetGone]: una nueva deja sin efecto la anterior. Cambia en el hilo principal. */
    @Volatile
    private var sheetGoneRun = 0

    /**
     * 1.10.1: en TikTok la hoja abierta a mano (compartir, comentarios…) es otra ventana y el feed no se mueve al cerrarla con la
     * «X», atrás o tocando fuera, así que [BubbleBoard.onFeedMoved] no llegaba y las burbujas no volvían hasta tocar el visor.
     * Con cada cambio de ventana de la app y una hoja abierta, pasado [BubbleBoard.SETTLE_MS] se mira si queda alguna ventana de
     * la app encima de la del feed ([SheetWindows.sheetAbove]); si no queda ninguna, la hoja se da por cerrada
     * ([BubbleBoard.onSheetGone]). Con los comentarios u otra hoja abierta (otra ventana encima) siguen fuera. Solo en las apps
     * cuya hoja va en su propia ventana: en Instagram, todo como antes.
     */
    private fun checkSheetGone(platform: Platform, firstDelay: Long = BubbleBoard.SETTLE_MS) {
        if (PlatformProfile.of(platform)?.sheetOwnWindow != true) return
        val openedAt = board.sheetOpenedAt ?: return
        val feedWindow = feedNode?.takeIf { feedPlatform == platform }?.let { runCatching { it.windowId }.getOrNull() } ?: return
        val run = ++sheetGoneRun
        readScope.launch {
            delay(firstDelay)
            // 1.10.2: una ventana encima puede irse sin avisar (el aviso «Enviado a …» de TikTok, de pantalla completa, dura ~5 s
            // y al quitarse no hay cambio de ventana): se sigue mirando cada SHEET_POLL_MS hasta SHEET_WATCH_MS.
            var waited = firstDelay
            while (true) {
                if (run != sheetGoneRun || quickBusy) return@launch
                if (sheetWindowAbove(feedWindow) == false) break
                if (waited >= SHEET_WATCH_MS) return@launch
                delay(SHEET_POLL_MS)
                waited += SHEET_POLL_MS
            }
            withContext(Dispatchers.Main) {
                if (run != sheetGoneRun || quickBusy) return@withContext
                val now = System.currentTimeMillis()
                if (board.onSheetGone(openedAt, now)) {
                    CalibrationLog.log { CalibrationEvent(now, INFO, platform = platform, rule = "envío rápido: hoja cerrada, sin ventanas encima del feed (burbujas de vuelta)") }
                    updateOverlay()
                }
            }
        }
    }

    /**
     * [SheetWindows.sheetAbove] con las ventanas de ahora (`null` si no se sabe). Hilo de fondo. Sin leer raíces: una ventana
     * cuyo alto no se puede leer cuenta como entera (ante la duda, las burbujas siguen fuera).
     */
    private fun sheetWindowAbove(feedWindow: Int): Boolean? {
        val list = try {
            windows
        } catch (e: RuntimeException) {
            return null
        }
        val r = Rect()
        val samples = list.orEmpty().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.map { w ->
            r.setEmpty()
            val height = runCatching { w.getBoundsInScreen(r); r.height() }.getOrDefault(Int.MAX_VALUE)
            SheetWindows.Sample(w.id, w.layer, height)
        }
        return SheetWindows.sheetAbove(samples, feedWindow, screenHeight)
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────────────────────

    /** Orientación del contenedor: la que declara el nodo o, si no declara nada, la de su último movimiento. */
    private fun orientationOf(source: AccessibilityNodeInfo?, key: Int?, dx: Int?, dy: Int?): Orientation? {
        if (source == null || key == null) return null
        val declared = declaredOrientation(source)
        val x = dx ?: 0
        val y = dy ?: 0
        if (x != 0 || y != 0) orientations[key] = if (abs(x) > abs(y)) Orientation.HORIZONTAL else Orientation.VERTICAL
        return declared ?: orientations[key]
    }

    private fun declaredOrientation(node: AccessibilityNodeInfo): Orientation? {
        val info = runCatching { node.collectionInfo }.getOrNull()
        if (info != null) {
            val rows = info.rowCount
            val columns = info.columnCount
            when {
                rows > 1 && columns > 1 -> return Orientation.GRID
                columns > 1 -> return Orientation.HORIZONTAL
                rows > 1 -> return Orientation.VERTICAL
            }
        }
        val actions = runCatching { node.actionList }.getOrNull() ?: return null
        var vertical = false
        var horizontal = false
        for (action in actions) {
            when (action.id) {
                AccessibilityAction.ACTION_SCROLL_UP.id, AccessibilityAction.ACTION_SCROLL_DOWN.id -> vertical = true
                AccessibilityAction.ACTION_SCROLL_LEFT.id, AccessibilityAction.ACTION_SCROLL_RIGHT.id -> horizontal = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (action.id) {
                    AccessibilityAction.ACTION_PAGE_UP.id, AccessibilityAction.ACTION_PAGE_DOWN.id -> vertical = true
                    AccessibilityAction.ACTION_PAGE_LEFT.id, AccessibilityAction.ACTION_PAGE_RIGHT.id -> horizontal = true
                }
            }
        }
        return when {
            vertical && !horizontal -> Orientation.VERTICAL
            horizontal && !vertical -> Orientation.HORIZONTAL
            else -> null
        }
    }

    /** Actividad o no: por el nombre y, si no está claro, preguntando al PackageManager (con caché). */
    private fun windowKindOf(pkg: String, cls: String?): WindowKind {
        val kind = FeedRules.windowKind(cls)
        if (kind != WindowKind.OTHER || cls == null) return kind
        if (cls.startsWith("android.") || cls.startsWith("androidx.") || cls.startsWith("com.google.android.material.")) return kind
        if (activityCache.size > 200) activityCache.clear()
        val isActivity = activityCache.getOrPut(cls) {
            try {
                val component = ComponentName(pkg, cls)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getActivityInfo(component, 0)
                }
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: RuntimeException) {
                false
            }
        }
        return if (isActivity) WindowKind.ACTIVITY else kind
    }

    private fun updateScreenSize() {
        var width = 0
        var height = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds }.getOrNull()?.let {
                width = it.width()
                height = it.height()
            }
        }
        if (width <= 0 || height <= 0) {
            runCatching {
                val point = Point()
                @Suppress("DEPRECATION")
                getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(point)
                width = point.x
                height = point.y
            }
        }
        if (width <= 0 || height <= 0) {
            val metrics = resources.displayMetrics
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        screenWidth = width
        screenHeight = height
    }

    private fun copyOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityNodeInfo(node)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(node)
        }

    /** Cabecera del registro de calibración: móvil, Android y versión de cada app instalada. */
    private fun describeEnvironment(): String {
        val apps = Platform.entries.flatMap { platform ->
            platform.packages.mapNotNull { pkg -> versionOf(pkg)?.let { "${platform.shortName} $it" } }
        }
        return buildString {
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')')
            append(" · Reelometer ").append(BuildConfig.VERSION_NAME)
            apps.forEach { append(" · ").append(it) }
        }
    }

    private fun versionOf(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg, 0).versionName
        }
    } catch (e: Exception) {
        null
    }

    private fun calibrationTypeOf(event: AccessibilityEvent): CalibrationEventType = when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> SCROLL
        AccessibilityEvent.TYPE_VIEW_CLICKED -> CLICK
        else -> WINDOW
    }

    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private fun debugError(what: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Error en $what", error)
    }

    companion object {
        /** Tras un clic que debería abrir la hoja: lo que se espera antes de mirar si salió (en el móvil sale en ~0,3 s). */
        private const val SHEET_CLICK_CHECK_MS = 1_500L
        /** Cada cuánto se vuelve a mirar si queda una ventana encima del feed, y hasta cuándo (el aviso dura ~5 s). */
        private const val SHEET_POLL_MS = 500L
        private const val SHEET_WATCH_MS = 8_000L
        const val TAG = "Reelometro"

        /**
         * Solo debug: vuelca al log (tag `Reelometro`, líneas «⇢ árbol») las ventanas de la app que se ve, Instagram o
         * TikTok (para sacar ids reales sin uiautomator, que no puede leerlas con un vídeo reproduciéndose):
         * `adb shell am broadcast -a com.reelometro.app.debug.DUMP_SHEET [--es platform tiktok] [--ez active true]`.
         */
        private const val ACTION_DUMP_SHEET = "com.reelometro.app.debug.DUMP_SHEET"

        /**
         * Solo debug: un envío rápido sin tocar las burbujas, para medirlo en el móvil (pasos «⇢ paso» en logcat y
         * «envío rápido: tiempos» en Calibración). Con un vídeo de esa app en pantalla y los nombres fijados en ella:
         * `adb shell am broadcast -a com.reelometro.app.debug.QUICK_SEND --es name "<fijado>" [--es name2 "<otro fijado>"]`.
         */
        private const val ACTION_QUICK_SEND = "com.reelometro.app.debug.QUICK_SEND"

        /**
         * Lo que hay que tener para mandar [ACTION_DUMP_SHEET] y [ACTION_QUICK_SEND]: el permiso DUMP, que tiene el shell de
         * adb (`adb shell am broadcast` sigue valiendo) y que una app instalada no puede conseguir. Lo mismo exige el
         * `DebugReceiver` del manifiesto de debug.
         */
        private const val DEBUG_HOOKS_PERMISSION = android.Manifest.permission.DUMP

        /** Cada cuánto mira la pantalla mientras envía, como red de seguridad (los eventos de la app van antes). */
        private const val QUICK_TICK_MS = 100L

        /** Tras un evento de la app durante un envío, el siguiente paso va como mucho en este tiempo. */
        private const val QUICK_EVENT_DEBOUNCE_MS = 30L

        /**
         * Envío rápido: lo más que se espera a la captura de la foto antes de pulsar el nombre (1.4.1; antes,
         * [AvatarCapture.TIMEOUT_MS]). Solo pasa con un fijado sin foto y una vez al día como mucho.
         */
        private const val QUICK_CAPTURE_TIMEOUT_MS = 250L

        /**
         * Esperas (seguidas) para ver si la hoja de TikTok se ha cerrado con el aviso «Enviado a …» ([watchSheetClose]):
         * medido, el aviso sale en su propia ventana a los pocos milisegundos del cambio de ventana de la actividad (ya está a
         * los 0,4 s del toque) y dura unos 5 s (medido el 7 nov por la tarde: aún a 4,2 y 4,7 s del envío; ya no a 5,0 y 6,4 s).
         */
        private val SENT_BANNER_WAITS_MS = listOf(150L, 300L, 450L, 600L)

        /** Lecturas del aviso de un envío ya apuntado ([rememberSentBannerWindows]): a los 150, 400, 800 y 1.500 ms. */
        private val SENT_BANNER_MEMORY_WAITS_MS = listOf(150L, 250L, 400L, 700L)

        /** Lo que se espera, con las burbujas escondidas, a que la pantalla se repinte antes de la captura. */
        private const val CAPTURE_HIDE_MS = 64L

        /** Envío a mano: cuándo (tras pulsar «compartir») se busca la celda de un fijado para recortar su foto. */
        private val MANUAL_AVATAR_DELAYS_MS = listOf(700L, 1_500L)

        /** Tras el último paso del envío rápido, los clics de su app se siguen ignorando este tiempo. */
        private const val QUICK_IGNORE_AFTER_MS = 1_000L

        /** Ningún envío rápido dura más que esto (varios destinos con el buscador caben de sobra). */
        private const val QUICK_MAX_MS = 30_000L

        /** Tras un envío bueno, durante este tiempo uno nuevo espera a que se cierre la hoja anterior (1.4.2). */
        private const val QUICK_SHEET_CLOSING_MS = 2_000L

        /** Con el dedo sobre las burbujas, cuándo se vuelve a mirar qué se ve. */
        private const val OVERLAY_AFTER_DRAG_MS = 250L

        /** Con las burbujas a la vista, cada cuánto se mira si su app sigue delante. */
        private const val OVERLAY_WATCH_MS = 600L

        /** Antepasados que se suben como mucho buscando uno pulsable. */
        private const val MAX_CLICK_ANCESTORS = 8
        private const val INFO_DELAY_MS = 1_200L
        private const val INFO_RETRY_MS = 1_800L
        private const val MAX_INFO_ATTEMPTS = 2
        private const val PRESENCE_INTERVAL_MS = 4_000L

        /** Lectura del contenido tras el último scroll (debounce) y para confirmar un cambio débil. */
        private const val CONTENT_CHECK_DELAY_MS = 900L
        private const val CONTENT_CONFIRM_MS = 1_500L

        /** Para unir dos vídeos iguales: el segundo empezó como mucho esto antes o después del primero. */
        private const val MERGE_WINDOW_MS = 20_000L
        private const val UNDEFINED_DELTA = -1

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = ComponentName(context, ReelAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        private fun <K, V> lruMap(max: Int): LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
        }
    }
}
