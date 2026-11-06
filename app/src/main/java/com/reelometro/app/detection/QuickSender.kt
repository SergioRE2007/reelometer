package com.reelometro.app.detection

import com.reelometro.app.stats.Format

/**
 * **Envío rápido** (solo Instagram y solo con el ajuste encendido): manda el reel que se está viendo a
 * una o varias personas o grupos fijados, y solo esto: «Compartir», esos nombres y un único «Enviar».
 *
 * Es una máquina de estados sin Android. El servicio la arranca con [start] al tocar la burbuja (o al soltar
 * una encima de otra) y, cada ~100 ms (antes si Instagram avisa de un cambio), le pasa las raíces de las
 * ventanas de Instagram (de la de más arriba a la de más abajo) con [tick]. Cada tick devuelve un [Step] que el
 * servicio ejecuta (pulsar, escribir) o nada ([Step.Wait]), hasta [Step.Done]:
 *  1. [Phase.SHARE]: el botón compartir del reel visible, con las mismas reglas que [ShareDetector].
 *  2. [Phase.SHEET]: espera a que se abra la hoja (un campo de texto fuera del visor o sus opciones,
 *     como «Copiar enlace»). Desde aquí solo se mira la ventana de la hoja.
 *  3. [Phase.TARGET], una vez por destino: su nombre en la hoja, con la normalización y limpieza de
 *     [RecipientDetector]. Si su fila tiene su propio «Enviar» (diseño antiguo), se pulsa ese. Si no aparece
 *     en [Config.searchAfterMs] (o la cuadrícula ya ha cargado sin él), se escribe en el buscador
 *     ([Phase.RESULT]). Un destino que no sale o sale repetido falla solo, y los demás siguen.
 *  4. [Phase.SEND]: un solo «Enviar» (o «Enviar por separado») para todos los marcados. Vale el de la fila del
 *     nombre o, si no, el de más abajo, pero nunca el de la fila de otra persona. «Crear grupo» nunca.
 *  5. [Phase.SENT]: solo da el envío por bueno si se ve: la hoja se cierra, la fila dice «Deshacer» o
 *     «Enviado», o el «Enviar» pulsado desaparece. Si no, en [Config.confirmMs] termina sin éxito.
 *
 * Mientras no se haya pulsado «Enviar», [add] suma otro destino al mismo envío (se marca en la misma hoja); si
 * ya se pulsó, devuelve `false` y el servicio lo encola para después ([start] con `waitForClosedSheet`).
 *
 * El servicio cuenta con [onPerformed] si Android aceptó cada acción: si la rechaza, se vuelve a la fase
 * anterior y se reintenta dentro del mismo plazo. Si al empezar ya hay una hoja de compartir abierta, no hace
 * nada (o, con `waitForClosedSheet`, espera a que se cierre).
 *
 * Nunca se toca nada dentro del visor de Reels (ids `clips_*`) salvo el botón compartir, ni un campo de
 * texto salvo para escribir el nombre en el buscador. Cada fase tiene [Config.stepTimeoutMs]: si se
 * pasa, para y la hoja se queda abierta para que el usuario termine a mano. Los motivos y lo que se
 * pulsa ([Step.Click.what]) van al registro de Calibración: nunca llevan el nombre.
 *
 * **Rapidez**. Con un [finder], cada fase prueba antes un atajo: que Instagram busque por texto (el nombre,
 * «Enviar», «Copiar enlace»…) o por id (los que [Hints] trae aprendidos) en una sola llamada. Lo que devuelve
 * pasa las mismas reglas de siempre. Si el atajo no decide, se recorre el árbol, pero **sin entrar en el visor**
 * (1.4.1: el visor de Reels son cientos de nodos y la hoja nunca está dentro), y como mucho cada
 * [Config.walkEveryMs] dentro de una misma fase (el primero de cada fase va sin esperar). Además:
 *  - con la cuadrícula de la hoja ya cargada (se ven fotos de perfil) y sin el nombre, se escribe en el
 *    buscador en ese momento, sin esperar [Config.searchAfterMs];
 *  - con [Target.skipGrid] (el fijado no salió en la cuadrícula las últimas veces) se va directo al buscador;
 *  - en cuanto Android acepta el clic en «Enviar», [sentProvisionally] dice que la burbuja ya puede decir
 *    «enviado»; la confirmación sigue por detrás y, si no llega, el envío termina en fallo.
 * Al terminar, [lastReport] trae los tiempos de cada fase (sin nombres), cómo se decidió cada una (atajo o
 * recorrido), lo que costó leer la pantalla ([noteRead]) y la foto ([noteAvatar]); [lastResults], cómo le fue
 * a cada destino.
 */
class QuickSender(
    private val config: Config = Config(),
    private val shareDetector: ShareDetector = ShareDetector(),
    private val recipients: RecipientDetector = RecipientDetector(),
    private val finder: UiFinder? = null,
    /** Reloj solo para medir (en el móvil, `SystemClock.uptimeMillis`); sin él, las consultas y recorridos no se cronometran. */
    private val clock: (() -> Long)? = null,
) {

    data class Config(
        /** Tiempo máximo de cada fase (y de cada destino). */
        val stepTimeoutMs: Long = 3_000L,
        /** Con la hoja abierta y sin el nombre a la vista, se escribe en el buscador pasado este tiempo. */
        val searchAfterMs: Long = 600L,
        /** Nodos que se miran como mucho por ventana y tick (cada uno puede costar una llamada entre procesos). */
        val maxNodes: Int = 900,
        val maxDepth: Int = 40,
        /** Una fila (o una celda de la cuadrícula) no pasa de esta parte de la altura de la pantalla. */
        val maxRowHeight: Float = 0.25f,
        val maxRowAncestors: Int = 3,
        /** Bajo una fila, nodos y profundidad que se miran para encontrar su «Enviar». */
        val rowMaxNodes: Int = 30,
        val rowMaxDepth: Int = 3,
        /** Tras pulsar «Enviar», tiempo para ver que se ha enviado. */
        val confirmMs: Long = 2_000L,
        /** Un «Enviar» al menos así de ancho (parte de la pantalla) es el botón de abajo, no el de una fila. */
        val wideSend: Float = 0.5f,
        /** Con [finder], dentro de una fase, el recorrido (el respaldo del atajo) se repite como mucho cada tanto. */
        val walkEveryMs: Long = 300L,
        /**
         * Con [finder], en las fases de la hoja no se recorre el árbol hasta pasado esto desde que empieza la fase
         * (1.4.2, medido en un Pixel 10a: recorrer la hoja mientras se abre o se anima costó 0,9–1,25 s por lectura,
         * porque cada nodo espera al hilo de la interfaz de Instagram; una búsqueda del atajo cuesta lo de una).
         */
        val walkGraceMs: Long = 1_000L,
    )

    /**
     * Lo aprendido de envíos anteriores: ids del botón compartir y del buscador (se prueban primero) y, para
     * [start] con un solo nombre, si el fijado suele no salir en la cuadrícula ([skipGrid]: directo al buscador).
     */
    data class Hints(val shareId: String? = null, val searchId: String? = null, val skipGrid: Boolean = false)

    /**
     * Un destino del envío. [skipGrid]: directo al buscador. [wantAvatar]: hay que recortar su foto
     * ([Step.Click.avatar]).
     */
    data class Target(val name: String, val skipGrid: Boolean = false, val wantAvatar: Boolean = false)

    /** Dónde apareció un destino. */
    enum class Found { GRID, SEARCH }

    /** Cómo se decidió el final de una fase: con el atajo (una búsqueda de Instagram) o recorriendo el árbol. */
    enum class How(val label: String) { FAST("atajo"), WALK("recorrido") }

    /** Una fase: cuánto duró, cómo se decidió y, en las de buscar nombres, de qué destino (1, 2…; 0 si no va). */
    data class PhaseTime(val phase: Phase, val ms: Long, val how: How? = null, val dest: Int = 0)

    /** Cómo le fue a un destino (lleva el nombre: solo para el servicio, nunca para Calibración). */
    data class DestResult(
        val name: String,
        val ok: Boolean,
        val reason: String,
        val foundIn: Found?,
        val skippedGrid: Boolean,
        /** Se llegó a marcar en la hoja (si el envío falla, sigue marcado para terminar a mano). */
        val selected: Boolean = false,
    )

    /**
     * Cómo fue el último envío, para Calibración y para aprender (nunca lleva el nombre).
     * [phases]: cada fase en orden (una fase puede salir dos veces si se reintenta o hay varios destinos).
     * [fastHits]: decisiones tomadas con el atajo; [walks]: recorridos del árbol.
     * [ticks], [readMs], [readMaxMs]: lecturas de la pantalla ([noteRead]). [avatarMs], [avatarOk]: la espera
     * de la captura de la foto antes de pulsar el nombre ([noteAvatar]; `null` si no se intentó).
     */
    data class Report(
        val ok: Boolean,
        val phases: List<PhaseTime>,
        val totalMs: Long,
        val fastHits: Int,
        val walks: Int,
        val foundIn: Found?,
        val skippedGrid: Boolean,
        val provisional: Boolean,
        val shareId: String?,
        val searchId: String?,
        val ticks: Int = 0,
        val readMs: Long = 0L,
        val readMaxMs: Long = 0L,
        val avatarMs: Long = 0L,
        val avatarOk: Boolean? = null,
        val dests: Int = 1,
        val sent: Int = if (ok) 1 else 0,
        /** Cada acción (sin nombres): qué, cuándo desde el toque, lo que tardó Android en hacerla y si la aceptó. */
        val actions: List<ActionTime> = emptyList(),
        /** Ticks que se quedaron en [Step.Wait], por fase y motivo («nombre: cuadrícula sin cargar» → 3). */
        val waits: Map<String, Int> = emptyMap(),
        /** Por qué se escribió en el buscador, cada vez («cuadrícula cargada sin él», «pasado el plazo»…). */
        val searches: List<String> = emptyList(),
        /** Búsquedas del atajo (cada una, una llamada que espera al hilo de Instagram) y lo que tardaron; lo que tardaron los recorridos (con reloj). */
        val queries: Int = 0,
        val queryMs: Long = 0L,
        val walkMs: Long = 0L,
        /**
         * La hoja se abrió y el buscador aprendido no salió (ni se aprendió otro): Instagram lo ha cambiado. Se olvida
         * para no quedarse con atajos que ya no valen (revisión de la 1.4.3).
         */
        val forgetSearchId: Boolean = false,
    ) {
        /**
         * «compartir 0 ms (atajo) · hoja 300 ms (atajo) · nombre 0 ms (atajo) · enviar 200 ms (atajo) ·
         * confirmar 200 ms (atajo) · total 700 ms · atajos 4 · recorridos 1 · lecturas 5: 120 ms, máx. 40 ms».
         */
        fun describe(): String = buildString {
            phases.forEach { p ->
                append(p.phase.short)
                if (dests > 1 && p.dest > 0) append(' ').append(Format.integer(p.dest.toLong()))
                append(' ').append(Format.integer(p.ms)).append(" ms")
                p.how?.let { append(" (").append(it.label).append(')') }
                append(" · ")
            }
            append("total ").append(Format.integer(totalMs)).append(" ms · atajos ").append(Format.integer(fastHits))
            append(" · recorridos ").append(Format.integer(walks))
            if (ticks > 0) {
                append(" · lecturas ").append(Format.integer(ticks)).append(": ").append(Format.integer(readMs))
                append(" ms, máx. ").append(Format.integer(readMaxMs)).append(" ms")
            }
            avatarOk?.let { append(" · foto ").append(Format.integer(avatarMs)).append(" ms ").append(if (it) "(acertó)" else "(falló)") }
            if (dests > 1) append(" · destinos ").append(Format.integer(sent.toLong())).append(" de ").append(Format.integer(dests.toLong()))
            if (skippedGrid) append(" · directo al buscador")
            if (actions.isNotEmpty()) {
                append(" · reloj: ")
                append(
                    actions.joinToString(" · ") { a ->
                        buildString {
                            append(a.what).append(" +").append(Format.integer(a.atMs)).append(" ms")
                            if (a.performMs >= 0) append(" (").append(Format.integer(a.performMs)).append(" ms)")
                            if (a.accepted == false) append(" rechazado")
                        }
                    },
                )
            }
            if (waits.isNotEmpty()) {
                append(" · esperas: ")
                append(waits.entries.joinToString(" · ") { (k, v) -> "$k ${Format.integer(v.toLong())}" })
            }
            if (searches.isNotEmpty()) append(" · buscador: ").append(searches.joinToString(" · "))
            if (queries > 0 && queryMs > 0) append(" · consultas ").append(Format.integer(queries.toLong())).append(": ").append(Format.integer(queryMs)).append(" ms")
            if (walkMs > 0) append(" · recorrer ").append(Format.integer(walkMs)).append(" ms")
        }
    }

    /**
     * Una acción del envío: [what] (lo de [Step.Click.what] o «escribe»), [atMs] desde el toque, [performMs] lo que
     * tardó `performAction` (una llamada al hilo de la interfaz de Instagram; -1 si no se sabe) y si Android la aceptó.
     */
    data class ActionTime(val what: String, val atMs: Long, val performMs: Long = -1L, val accepted: Boolean? = null)

    enum class Phase(val label: String, val short: String) {
        SHARE("busca compartir", "compartir"),
        SHEET("espera la hoja", "hoja"),
        TARGET("busca el nombre", "nombre"),
        RESULT("espera el resultado del buscador", "buscador"),
        SEND("busca enviar", "enviar"),
        SENT("enviado", "confirmar"),
    }

    sealed interface Step {
        /**
         * Pulsar [node] (o su primer antepasado pulsable). [what] va a Calibración: nunca es un nombre.
         * [maxClimbWidth]: si no es `null`, al buscar el antepasado pulsable no se sube a uno más ancho (px);
         * así el clic en «Compartir» nunca acaba en el vídeo (que pausaría o silenciaría).
         * [avatar]: al pulsar un destino con [Target.wantAvatar], dónde está su foto de perfil en pantalla, para
         * recortarla **antes** del clic (después, Instagram la tapa con la marca de seleccionado); [avatarName], de quién.
         */
        data class Click(
            val node: UiNode,
            val what: String,
            val maxClimbWidth: Int? = null,
            val avatar: NodeBounds? = null,
            val avatarName: String? = null,
            /** La foto es el avatar compuesto de un grupo (otro recorte: [AvatarRules.cropBox]). */
            val avatarGroup: Boolean = false,
        ) : Step

        /** Escribir [text] en el campo [node] (el buscador de la hoja). */
        data class SetText(val node: UiNode, val text: String) : Step

        data object Wait : Step

        /**
         * Fin. [ok]: se ha enviado (al menos a un destino; cada uno en [lastResults]).
         * [sheetOpened]: la hoja se llegó a abrir (si falla, el usuario puede terminar a mano).
         * [targetSelected]: se pulsó algún nombre (en la hoja queda marcado aunque falle «Enviar»).
         */
        data class Done(val ok: Boolean, val reason: String, val sheetOpened: Boolean, val targetSelected: Boolean) : Step
    }

    /** Un nodo visto en el recorrido de una ventana (o devuelto por el atajo, con sus antepasados mirados). */
    private class Seen(val node: UiNode, val inFeed: Boolean, val inField: Boolean, val window: Int)

    private enum class Status { PENDING, SELECTED, ROW_SENT, FAILED }

    /** Un destino en curso. */
    private class Dest(val target: Target, val index: Int) {
        val key = RecipientDetector.normalize(target.name)
        var status = Status.PENDING
        var reason = ""
        var searched = false
        var foundIn: Found? = null
        var row: NodeBounds? = null

        /** Se buscó con el buscador aún ocupado por otro nombre: la cuadrícula no se vio, no cuenta para aprender. */
        var gridHidden = false

        fun copyState() = DestState(status, reason, searched, foundIn, row, gridHidden)

        fun restore(s: DestState) {
            status = s.status
            reason = s.reason
            searched = s.searched
            foundIn = s.foundIn
            row = s.row
            gridHidden = s.gridHidden
        }
    }

    private class DestState(
        val status: Status, val reason: String, val searched: Boolean, val foundIn: Found?, val row: NodeBounds?, val gridHidden: Boolean,
    )

    private class Mark(val phase: Phase, val at: Long, val dest: Int, var how: How? = null)

    @Volatile
    private var running = false

    /**
     * Cuándo rechazó Android por primera vez una acción de la fase actual: si sigue rechazándola pasado
     * [Config.stepTimeoutMs] desde entonces, se para (nunca reintenta sin fin).
     */
    private var rejectedSince: Long? = null

    /** El `now` del último [tick] (para fechar un rechazo en [onPerformed]). */
    private var lastTickAt = 0L

    /** Android rechazó el clic en «Compartir»: si aun así la hoja se abre, es la nuestra y se sigue. */
    private var shareRejected = false
    private val dests = ArrayList<Dest>()
    private var hints = Hints()
    private var waitForClosedSheet = false
    private var phase = Phase.SHARE
    private var phaseSince = 0L
    private var startedAt = 0L
    private var sheetAt: Long? = null
    private var shareBounds: NodeBounds? = null
    private var sentBounds: NodeBounds? = null

    /** Lo escrito en el buscador (normalizado): ese campo sigue siendo el buscador aunque ya no diga «Buscar». */
    private val typed = HashSet<String>()

    /** El «Enviar» pulsado se encontró con el atajo: la confirmación también lo busca así. */
    private var sentFast = false
    private var learnedShareId: String? = null
    private var learnedSearchId: String? = null
    private var fastHits = 0
    private var walks = 0
    private var lastWalkAt: Long? = null
    private var lastWalkMarks = -1
    private var ticks = 0
    private var readMs = 0L
    private var readMaxMs = 0L
    private var avatarMs = 0L
    private var avatarOk: Boolean? = null
    private val actions = ArrayList<ActionTime>()
    private val waits = LinkedHashMap<String, Int>()
    private val searches = ArrayList<String>()

    /** El buscador aprendido salió en este envío (si se abrió la hoja y no salió, se olvida: [Report.forgetSearchId]). */
    private var sawLearnedSearch = false

    /**
     * Se recorrió la hoja (y tenía algún campo): solo entonces se sabe que el buscador aprendido ya no está. Con la hoja
     * vista solo por su contenedor (entrando) y cerrada antes, no se olvida (segunda pasada de la 1.4.3).
     */
    private var sheetWalkedWithField = false
    private var queries = 0
    private var queryMs = 0L
    private var walkMs = 0L

    /** Por qué el tick en curso devuelve [Step.Wait] (para [Report.waits]). */
    private var waitWhy: String? = null

    /**
     * Ventana de la hoja de compartir (su `windowId` o, si no se sabe, su posición). El atajo solo vale dentro de
     * ella: un aviso de Instagram con el mismo nombre, en otra ventana, nunca se pulsa.
     */
    private var sheetWindow: Int? = null

    /** Entrada en cada fase (para los tiempos de [Report]). */
    private val marks = ArrayList<Mark>()

    /** La última acción fue pulsar «Enviar»: si Android la acepta, ya se puede decir «enviado» ([sentProvisionally]). */
    private var sendPending = false

    @Volatile
    private var provisional = false

    @Volatile
    var lastReport: Report? = null
        private set

    @Volatile
    var lastResults: List<DestResult> = emptyList()
        private set

    /** Estado antes de la última acción, por si Android la rechaza ([onPerformed]). */
    private class Before(
        val phase: Phase, val phaseSince: Long, val marks: Int, val lastHow: How?, val sentFast: Boolean,
        val dest: Dest?, val destState: DestState?, val typedKey: String?, val rejectedSince: Long?,
    )

    private var before: Before? = null

    val isRunning: Boolean get() = running
    val currentPhase: Phase? get() = if (running) phase else null

    /**
     * Android ya aceptó el clic en «Enviar»: la burbuja puede decir «enviado» sin esperar a la confirmación,
     * que sigue por detrás (si no llega, el envío acaba en [Step.Done] con `ok = false`).
     */
    val sentProvisionally: Boolean get() = provisional

    /**
     * Destinos cuyo «Enviar» ya aceptó Android (el de abajo, o el de su fila en el diseño antiguo): sus burbujas
     * pueden decir «enviado». Nunca uno que ha fallado o que aún se está buscando.
     */
    val provisionalNames: List<String>
        @Synchronized get() = if (!provisional) {
            emptyList()
        } else {
            dests.filter { it.status == Status.ROW_SENT || (it.status == Status.SELECTED && phase == Phase.SENT) }.map { it.target.name }
        }

    /** Nombres de este envío (los de [start] y los sumados con [add]). */
    val names: List<String> @Synchronized get() = dests.map { it.target.name }

    /** Empieza a enviar el reel visible a [name] (un nombre fijado, tal como lo guardó [RecipientDetector]). */
    fun start(name: String, now: Long, hints: Hints = Hints(), wantAvatar: Boolean = false) =
        start(listOf(Target(name, skipGrid = hints.skipGrid, wantAvatar = wantAvatar)), now, hints)

    /**
     * Empieza a enviar el reel visible a [targets] (en una sola hoja y con un solo «Enviar»). [hints]: lo aprendido
     * de otros envíos. [waitForClosedSheet]: si aún hay una hoja abierta (la del envío anterior, cerrándose), se
     * espera a que se cierre en vez de rendirse.
     */
    @Synchronized
    fun start(targets: List<Target>, now: Long, hints: Hints = Hints(), waitForClosedSheet: Boolean = false) {
        dests.clear()
        targets.distinctBy { RecipientDetector.normalize(it.name) }.forEach { dests += Dest(it, dests.size + 1) }
        running = dests.isNotEmpty()
        this.hints = hints
        this.waitForClosedSheet = waitForClosedSheet
        phase = Phase.SHARE
        phaseSince = now
        startedAt = now
        sheetAt = null
        shareBounds = null
        sentBounds = null
        typed.clear()
        sentFast = false
        learnedShareId = null
        learnedSearchId = null
        fastHits = 0
        walks = 0
        lastWalkAt = null
        lastWalkMarks = -1
        ticks = 0
        readMs = 0L
        readMaxMs = 0L
        avatarMs = 0L
        avatarOk = null
        actions.clear()
        waits.clear()
        searches.clear()
        sawLearnedSearch = false
        sheetWalkedWithField = false
        queries = 0
        queryMs = 0L
        walkMs = 0L
        waitWhy = null
        sheetWindow = null
        marks.clear()
        marks += Mark(Phase.SHARE, now, 0)
        sendPending = false
        provisional = false
        rejectedSince = null
        lastTickAt = now
        shareRejected = false
        lastReport = null
        lastResults = emptyList()
        before = null
    }

    /**
     * Suma [target] al envío en curso. `true` si se marcará en la misma hoja (aún no se ha pulsado «Enviar», o ya
     * estaba en el envío); `false` si ya no puede (sin envío o con «Enviar» ya pulsado): el servicio lo encola.
     */
    @Synchronized
    fun add(target: Target): Boolean {
        if (!running || phase == Phase.SENT) return false
        val key = RecipientDetector.normalize(target.name)
        if (dests.any { it.key == key }) return true
        dests += Dest(target, dests.size + 1)
        return true
    }

    /**
     * El servicio ha intentado la acción del último [Step.Click] o [Step.SetText]. Con `false` (Android no la
     * aceptó: nodo caducado, nada pulsable…), vuelve a la fase anterior con su plazo de entonces y reintenta.
     */
    @Synchronized
    fun onPerformed(accepted: Boolean?, performMs: Long = -1L) {
        val last = actions.lastIndex
        if (last >= 0 && actions[last].accepted == null && actions[last].performMs < 0) {
            actions[last] = actions[last].copy(performMs = performMs.coerceAtLeast(-1L), accepted = accepted)
        }
        val b = before ?: return
        before = null
        val wasSend = sendPending
        sendPending = false
        if (!running) return
        if (accepted == true && wasSend) provisional = true
        if (accepted != false) return
        phase = b.phase
        phaseSince = b.phaseSince
        sentFast = b.sentFast
        while (marks.size > b.marks) marks.removeAt(marks.size - 1)
        marks.lastOrNull()?.how = b.lastHow
        if (b.dest != null && b.destState != null) b.dest.restore(b.destState)
        b.typedKey?.let { typed.remove(it) }
        // El primer rechazo de esta fase (enter() lo borró al intentarlo: se recupera de antes).
        rejectedSince = b.rejectedSince ?: lastTickAt
        if (b.phase == Phase.SHARE) shareRejected = true
    }

    /**
     * 1.4.2: la confirmación no va en el camino crítico. Si Android ya aceptó «Enviar» ([sentProvisionally]) y solo
     * falta confirmarlo, termina ya como enviado («enviado sin confirmar») para que otro envío pueda empezar; si no,
     * `null` y no cambia nada.
     */
    @Synchronized
    fun release(now: Long): Step.Done? =
        if (running && provisional && phase == Phase.SENT) finish(true, RELEASED, now) else null

    /** Lo para el servicio (tarda demasiado): termina sin éxito, con informe. */
    @Synchronized
    fun abort(reason: String, now: Long): Step.Done =
        if (running) finish(false, reason, now) else Step.Done(false, reason, sheetOpened = false, targetSelected = false)

    /** Lo que ha tardado el servicio en leer la pantalla en un tick (para [Report]). */
    @Synchronized
    fun noteRead(ms: Long) {
        val v = ms.coerceAtLeast(0L)
        if (running) {
            ticks++
            readMs += v
            readMaxMs = maxOf(readMaxMs, v)
        } else {
            // El último tick (el que terminó) también cuenta.
            lastReport = lastReport?.let { it.copy(ticks = it.ticks + 1, readMs = it.readMs + v, readMaxMs = maxOf(it.readMaxMs, v)) }
        }
    }

    /** Lo que se esperó a la captura de la foto antes de pulsar un nombre, y si salió (para [Report]). */
    @Synchronized
    fun noteAvatar(ms: Long, ok: Boolean) {
        avatarMs += ms.coerceAtLeast(0L)
        avatarOk = (avatarOk ?: true) && ok
    }

    @Synchronized
    fun cancel() {
        running = false
    }

    @Synchronized
    fun tick(roots: List<UiNode>, screenWidth: Int, screenHeight: Int, now: Long): Step {
        if (!running) return Step.Done(false, "sin envío en curso", sheetOpened = false, targetSelected = false)
        if (screenWidth <= 0 || screenHeight <= 0) return finish(false, "sin tamaño de pantalla", now)
        val screen = Screen(roots, screenWidth, screenHeight, now)
        before = null
        sendPending = false
        lastTickAt = now
        // Android rechaza una y otra vez la misma acción (nada pulsable): pasado el plazo desde el primer rechazo, se para.
        val rejected = rejectedSince
        if (rejected != null && phase != Phase.SENT && now - rejected > config.stepTimeoutMs) {
            val dest = current()
            return if ((phase == Phase.TARGET || phase == Phase.RESULT) && dest != null) {
                failDest(dest, REJECTED, screen, now, null)
            } else {
                finish(false, REJECTED, now)
            }
        }
        waitWhy = null
        val phaseAtStart = phase
        val step = when (phase) {
            Phase.SHARE -> shareStep(screen, now)
            Phase.SHEET -> sheetStep(screen, now)
            Phase.TARGET, Phase.RESULT -> lookForTarget(screen, now)
            // Se ha sumado un destino ([add]) antes de pulsar «Enviar»: primero se marca.
            Phase.SEND -> if (current() != null) {
                enter(Phase.TARGET, now, null)
                lookForTarget(screen, now)
            } else {
                sendStep(screen, now)
            }
            Phase.SENT -> confirmSent(screen, now)
        }
        when (step) {
            is Step.Click -> actions += ActionTime(step.what, (now - startedAt).coerceAtLeast(0L))
            is Step.SetText -> actions += ActionTime("escribe", (now - startedAt).coerceAtLeast(0L))
            Step.Wait -> {
                val key = phase.short + ": " + (waitWhy ?: if (phase != phaseAtStart) "cambio de fase" else "sin motivo")
                waits[key] = (waits[key] ?: 0) + 1
            }
            is Step.Done -> Unit
        }
        return step
    }

    // ── Fases ────────────────────────────────────────────────────────────────────────────────────

    private fun current(): Dest? = dests.firstOrNull { it.status == Status.PENDING }

    private fun shareStep(screen: Screen, now: Long): Step {
        // Con una hoja de compartir ya abierta, el «Enviar» de una fila podría pasar por «compartir», y pulsar el
        // compartir del reel de detrás abriría otra. El atajo va primero (no tiene el límite de nodos del recorrido)
        // y un recorrido cubre las pistas («Escribe un mensaje…» de un campo vacío), que la búsqueda de Instagram no
        // ve: siempre sin entrar en el visor (así el límite de nodos no se gasta en él y la hoja se ve aunque vaya en
        // la misma ventana). El recorrido entero solo para buscar «Compartir» si el atajo no lo ve. Como en las demás
        // fases, los recorridos van como mucho cada walkEveryMs (1.4.1).
        // 1.4.3: con la hoja de hoy ya vista en un envío anterior (el buscador aprendido es el de hoy), «¿ya hay una hoja
        // abierta?» son dos búsquedas por id, sin las de texto ni el recorrido (medido: este tick costaba 230–460 ms).
        val known = finder != null && knownLayout
        var open = finder != null && (if (known) fastSheetOpenById(screen) else fastSheetMarker(screen) != null)
        var share: UiNode? = null
        var how = How.FAST
        if (!open && known) {
            share = fastShare(screen)
            if (share == null) {
                if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "no encuentro compartir") }
                share = findShare(screen.windows, screen.w, screen.h)
                how = How.WALK
            }
        } else if (!open) {
            if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "no encuentro compartir") }
            open = screen.outside.any { w -> w.any { !it.inFeed && hasMarker(it.node) } }
            if (!open) {
                share = if (finder != null) fastShare(screen) else null
                if (share == null) {
                    share = findShare(screen.windows, screen.w, screen.h)
                    how = How.WALK
                }
            }
        }
        if (open) {
            if (shareRejected) {
                // Android dijo que no al clic en «Compartir», pero la hoja se ha abierto: es la nuestra.
                shareRejected = false
                enter(Phase.SHEET, now, null)
                return sheetStep(screen, now)
            }
            return if (waitForClosedSheet) timeoutOr(now, "ya hay una hoja abierta") else finish(false, "ya hay una hoja abierta", now)
        }
        val found = share ?: return timeoutOr(now, "no encuentro compartir")
        if (how == How.FAST) fastHits++
        return clickShare(found, screen, now, how)
    }

    private fun clickShare(share: UiNode, screen: Screen, now: Long, how: How): Step {
        shareBounds = share.bounds
        learnedShareId = share.viewId?.takeIf { it.isNotEmpty() }
        snapshot(null)
        enter(Phase.SHEET, now, how)
        return Step.Click(share, "compartir", maxClimbWidth = (screen.w * SHARE_MAX_WIDTH).toInt())
    }

    private fun sheetStep(screen: Screen, now: Long): Step {
        val fastWindow = if (finder == null) {
            null
        } else {
            val field = fastSearchFields(screen).firstOrNull()
            field?.node?.viewId?.takeIf { it.isNotEmpty() }?.let { learnedSearchId = it }
            // Con la hoja de hoy conocida, sin las cuatro búsquedas de texto de sus opciones (1.4.3).
            (field ?: fastSheetContainer(screen) ?: if (knownLayout) null else fastSheetMarker(screen))?.window
        }
        val how: How
        if (fastWindow != null) {
            fastHits++
            sheetWindow = fastWindow
            how = How.FAST
        } else {
            if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "la hoja de compartir no se abre") }
            sheetOf(screen.outside) ?: return timeoutOr(now, "la hoja de compartir no se abre")
            how = How.WALK
        }
        sheetAt = now
        enter(Phase.TARGET, now, how)
        // Con el nombre ya a la vista se pulsa en este mismo tick (dentro de la ventana de la hoja).
        return lookForTarget(screen, now)
    }

    /** ¿Se ha enviado de verdad? La hoja se cierra, la fila dice «Deshacer»/«Enviado» o el «Enviar» ya no está. */
    private fun confirmSent(screen: Screen, now: Long): Step {
        if (sentFast && finder != null) {
            // Por el id (cualquier idioma) y, si no, por las palabras.
            val stillThere = sheetSendById(screen).any { it.bounds == sentBounds } ||
                screen.firstSheetText(SEND_QUERIES) { !it.inFeed && !it.inField && it.node.bounds == sentBounds && isSendButton(it.node) } != null
            if (!stillThere) return finish(true, "enviado", now, How.FAST)
            val undone = screen.firstSheetText(UNDO_QUERIES) { seen ->
                !seen.inFeed && !seen.inField && labelsOf(seen.node).any { UNDO_OR_SENT.matches(plain(it.value)) }
            } != null
            if (undone) return finish(true, "enviado", now, How.FAST)
            return if (now - phaseSince > config.confirmMs) finish(false, "no se confirma el envío", now) else wait("sin confirmar")
        }
        if (!walkAllowed(now)) return paused(now).let { if (now - phaseSince > config.confirmMs) finish(false, "no se confirma el envío", now) else wait("sin confirmar") }
        val sheet = sheetOf(screen.outside) ?: return finish(true, "enviado", now, How.WALK)
        val visible = sheet.filter { !it.inFeed && !it.inField }
        if (visible.any { seen -> labelsOf(seen.node).any { plain(it.value).let { l -> UNDO_OR_SENT.matches(l) } } }) {
            return finish(true, "enviado", now, How.WALK)
        }
        if (visible.none { it.node.bounds == sentBounds && isSendButton(it.node) }) return finish(true, "enviado", now, How.WALK)
        return if (now - phaseSince > config.confirmMs) finish(false, "no se confirma el envío", now) else wait("sin confirmar")
    }

    private fun lookForTarget(screen: Screen, now: Long): Step {
        val dest = current() ?: return afterTargets(screen, now)
        val notFound = if (phase == Phase.RESULT) "el buscador no lo encuentra" else "no encuentro el nombre"
        // 1. Atajo: Instagram busca el nombre (solo en la ventana de la hoja).
        val fast = screen.fastTargets(dest)
        if (fast.isNotEmpty()) {
            fastHits++
            return onTargets(dest, fast, screen, now, How.FAST)
        }
        // 2. Suele no salir en la cuadrícula: directo al buscador.
        if (phase == Phase.TARGET && !dest.searched && dest.target.skipGrid) {
            fastSearchFields(screen).firstOrNull()?.let { field ->
                fastHits++
                return typeInSearch(dest, field.node, now, How.FAST, "directo, aprendido")
            }
        }
        // 3. 1.4.2, con atajo: la cuadrícula cargada (por sus celdas) sin el nombre, o pasado el plazo → el buscador,
        // sin recorrer la hoja.
        if (finder != null && phase == Phase.TARGET && !dest.searched) {
            val loaded = fastGridLoaded(screen)
            val waited = now - (sheetAt ?: now) >= config.searchAfterMs
            if (loaded || waited) {
                fastSearchFields(screen).firstOrNull()?.let { field ->
                    fastHits++
                    // Sin cuadrícula a la vista no se sabe si estaba: no se aprende de esto.
                    return typeInSearch(dest, field.node, now, How.FAST, if (loaded) "cuadrícula cargada sin él" else "pasado el plazo sin cuadrícula")
                        .also { if (!loaded) dest.gridHidden = true }
                }
            }
            if (!walkAllowed(now)) {
                waitWhy = if (loaded) "cuadrícula sin él" else "cuadrícula sin cargar"
                return destTimeoutOr(dest, screen, now, notFound)
            }
        }
        if (finder != null && phase == Phase.RESULT && !walkAllowed(now)) {
            waitWhy = "esperando al buscador"
            return destTimeoutOr(dest, screen, now, notFound)
        }
        // 4. Recorrido de lo que no es el visor.
        if (!walkAllowed(now)) return paused(now).let { destTimeoutOr(dest, screen, now, notFound) }
        val sheet = sheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
        val matches = findTarget(dest, sheet, screen.h)
        if (matches.isNotEmpty()) return onTargets(dest, matches, screen, now, How.WALK)
        val waited = now - (sheetAt ?: now) >= config.searchAfterMs
        val loaded = gridLoaded(sheet)
        if (phase == Phase.TARGET && !dest.searched && (dest.target.skipGrid || waited || loaded)) {
            val why = when {
                dest.target.skipGrid -> "directo, aprendido"
                loaded -> "cuadrícula cargada sin él"
                else -> "pasado el plazo sin cuadrícula"
            }
            searchField(sheet)?.let { field ->
                // Sin la cuadrícula a la vista no se sabe si estaba: no se aprende «no sale» (como en el atajo; revisión 1.4.3).
                return typeInSearch(dest, field, now, How.WALK, why).also { if (!dest.target.skipGrid && !loaded) dest.gridHidden = true }
            }
        }
        waitWhy = when {
            phase == Phase.RESULT -> "esperando al buscador"
            loaded -> "cuadrícula sin él"
            else -> "cuadrícula sin cargar"
        }
        return destTimeoutOr(dest, screen, now, notFound)
    }

    private fun onTargets(dest: Dest, matches: List<UiNode>, screen: Screen, now: Long, how: How): Step {
        if (matches.size > 1) return failDest(dest, "nombre repetido en la hoja", screen, now, how)
        val found = matches.first()
        val row = rowOf(found, screen.h)
        val rowSend = row?.let { sendInRow(it, found) }
        val what = if (phase == Phase.RESULT) "nombre del buscador" else "nombre"
        val photo = if (dest.target.wantAvatar) AvatarRules.photo(found, dest.target.name, screen.h, config.maxRowAncestors, config.maxRowHeight) else null
        val avatar = photo?.bounds
        val where = if (phase == Phase.RESULT) Found.SEARCH else Found.GRID
        snapshot(dest)
        dest.foundIn = where
        dest.row = row?.bounds
        dest.status = if (rowSend != null) Status.ROW_SENT else Status.SELECTED
        val more = current() != null
        val next = when {
            more -> Phase.TARGET
            rowSend != null -> Phase.SENT
            else -> Phase.SEND
        }
        enter(next, now, how)
        val avatarName = dest.target.name.takeIf { avatar != null }
        return if (rowSend != null) {
            sentBounds = rowSend.bounds
            sentFast = false
            sendPending = true
            Step.Click(rowSend, "enviar de la fila", avatar = avatar, avatarName = avatarName, avatarGroup = photo?.group == true)
        } else {
            Step.Click(found, what, avatar = avatar, avatarName = avatarName, avatarGroup = photo?.group == true)
        }
    }

    /** Este destino no sale (o sale repetido): falla solo y el envío sigue con los demás. */
    private fun failDest(dest: Dest, reason: String, screen: Screen, now: Long, how: How?): Step {
        dest.status = Status.FAILED
        dest.reason = reason
        if (current() != null) {
            enter(Phase.TARGET, now, how)
            return lookForTarget(screen, now)
        }
        return afterTargets(screen, now, reason, how)
    }

    /** Sin destinos por buscar: «Enviar» si hay alguno marcado; confirmar si se envió fila a fila; si no, fallo. */
    private fun afterTargets(screen: Screen, now: Long, reason: String? = null, how: How? = null): Step = when {
        dests.any { it.status == Status.SELECTED } -> {
            if (phase != Phase.SEND) enter(Phase.SEND, now, how)
            sendStep(screen, now)
        }
        dests.any { it.status == Status.ROW_SENT } -> {
            if (phase != Phase.SENT) enter(Phase.SENT, now, how)
            Step.Wait
        }
        else -> finish(false, reason ?: dests.lastOrNull { it.status == Status.FAILED }?.reason ?: "no encuentro el nombre", now)
    }

    private fun destTimeoutOr(dest: Dest, screen: Screen, now: Long, reason: String): Step =
        if (now - phaseSince > config.stepTimeoutMs) failDest(dest, reason, screen, now, null) else wait(reason)

    private fun typeInSearch(dest: Dest, field: UiNode, now: Long, how: How, why: String): Step {
        snapshot(dest, typedKey = dest.key.takeIf { it !in typed })
        // El buscador aún dice otro nombre de este envío: la cuadrícula no se vio y no se aprende de esto.
        dest.gridHidden = (typed - dest.key).isNotEmpty()
        dest.searched = true
        typed += dest.key
        enter(Phase.RESULT, now, how)
        learnedSearchId = field.viewId?.takeIf { it.isNotEmpty() } ?: learnedSearchId
        searches += why
        return Step.SetText(field, dest.target.name)
    }

    private fun sendStep(screen: Screen, now: Long): Step {
        if (finder != null) {
            // Con la hoja ya abierta del todo: ¿sigue el buscador aprendido? (si no salió en todo el envío, se olvida).
            if (hints.searchId != null && !sawLearnedSearch) fastSearchFields(screen)
            // 1.4.4: el «Enviar» de abajo por su id, en cualquier idioma; si no está (otro diseño), por las palabras.
            // «Enviar» encuentra también «Enviar por separado»; «Send» solo si no hay ninguno (móvil en inglés).
            val all = sheetSendById(screen).ifEmpty { null } ?: screen.allSheetText(SEND_QUERIES) { !it.inFeed && !it.inField && it.node.bounds != shareBounds && isSendButton(it.node) }
                .map { it.node }
                .distinctBy { it.bounds }
            chooseSend(all, screen.w, screen.h)?.let { send ->
                fastHits++
                return clickSend(send, fast = true, now)
            }
        }
        if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "no encuentro enviar") }
        val sheet = sheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
        return findSend(sheet, screen.w, screen.h)?.let { clickSend(it, fast = false, now) } ?: timeoutOr(now, "no encuentro enviar")
    }

    /** El «Enviar» de abajo por su id de hoy ([RecipientDetector.SEND_BUTTON_ID]), en la ventana de la hoja; nunca «Crear grupo». */
    private fun sheetSendById(screen: Screen): List<UiNode> =
        screen.id(RecipientDetector.SEND_BUTTON_ID)
            .filter { !it.inFeed && !it.inField && (sheetWindow == null || it.window == sheetWindow) && it.node.bounds != shareBounds && RecipientDetector.isSendId(it.node) }
            .map { it.node }
            .distinctBy { it.bounds }

    private fun clickSend(send: UiNode, fast: Boolean, now: Long): Step {
        sentBounds = send.bounds
        snapshot(null)
        enter(Phase.SENT, now, if (fast) How.FAST else How.WALK)
        sentFast = fast
        sendPending = true
        return Step.Click(send, "enviar")
    }

    // ── Atajos ───────────────────────────────────────────────────────────────────────────────────

    /** El botón compartir del reel visible: por el id aprendido o por su etiqueta. Siempre dentro del visor. */
    private fun fastShare(screen: Screen): UiNode? {
        fun valid(seen: Seen): Boolean {
            val b = seen.node.bounds
            return seen.inFeed && !seen.inField && !b.isEmpty && b.left >= 0 && b.top >= 0 && b.right <= screen.w &&
                b.bottom <= screen.h && shareDetector.evaluate(seen.node, screen.w, screen.h).isShare
        }
        hints.shareId?.let { id -> screen.id(id).firstOrNull(::valid)?.let { return it.node } }
        for (q in SHARE_QUERIES) screen.text(q).firstOrNull(::valid)?.let { return it.node }
        return null
    }

    /** Alguna opción que solo sale en la hoja de compartir («Copiar enlace», «Escribe un mensaje»…), fuera del visor. */
    private fun fastSheetMarker(screen: Screen): Seen? =
        MARKER_QUERIES.firstNotNullOfOrNull { q -> screen.text(q).firstOrNull { !it.inFeed && hasMarker(it.node) } }

    /**
     * El buscador de la hoja por el id aprendido (dentro de la ventana de la hoja si ya se conoce). Por texto no:
     * la búsqueda de Instagram no ve la pista «Buscar» de un campo vacío.
     */
    private fun fastSearchFields(screen: Screen): List<Seen> {
        fun ok(seen: Seen) = !seen.inFeed && isField(seen.node) && (sheetWindow == null || seen.window == sheetWindow) &&
            isSearchField(seen.node, allowEmpty = true)
        // 1.4.2: sin id aprendido, el que tiene Instagram hoy (visto en el árbol real de la hoja).
        val learned = hints.searchId?.let { id -> screen.id(id).filter(::ok) }.orEmpty()
        if (learned.isNotEmpty()) sawLearnedSearch = true
        if (learned.isNotEmpty() || hints.searchId == KNOWN_SEARCH_ID) return learned
        return screen.id(KNOWN_SEARCH_ID).filter(::ok)
    }

    /**
     * La hoja de hoy ya se vio en un envío anterior: el buscador aprendido es [KNOWN_SEARCH_ID]. Entonces sus ids
     * bastan y no hacen falta las búsquedas de texto de las opciones ni el recorrido (1.4.3).
     */
    private val knownLayout: Boolean get() = hints.searchId == KNOWN_SEARCH_ID

    /**
     * ¿Hay una hoja abierta? Por los ids de hoy (tres búsquedas): el buscador fuera del visor, diga lo que diga (con un
     * nombre escrito de un envío que falló también cuenta), el contenedor de la hoja de compartir o el de cualquier
     * hoja de abajo de Instagram (comentarios, más opciones…): nunca se pulsa «Compartir» detrás de una.
     */
    private fun fastSheetOpenById(screen: Screen): Boolean =
        screen.id(hints.searchId ?: KNOWN_SEARCH_ID).any { !it.inFeed && isField(it.node) }.also { if (it) sawLearnedSearch = true } ||
            OPEN_SHEET_IDS.any { id -> screen.id(id).any { !it.inFeed } }

    /** La hoja por sus ids de hoy (contenedor o cuadrícula), fuera del visor: una llamada, sin recorrer. */
    private fun fastSheetContainer(screen: Screen): Seen? =
        KNOWN_SHEET_IDS.firstNotNullOfOrNull { id -> screen.id(id).firstOrNull { !it.inFeed } }

    /**
     * La cuadrícula de la hoja ya ha cargado (1.4.2): se ve alguna celda (`direct_share_sheet_grid_view_pog`, que
     * Instagram describe «Nombre Chat sin seleccionar», sin «Foto del perfil de…»), dentro de la ventana de la hoja.
     */
    private fun fastGridLoaded(screen: Screen): Boolean =
        screen.id(KNOWN_GRID_CELL_ID).any { !it.inFeed && (sheetWindow == null || it.window == sheetWindow) }

    /** Lo que encuentra el atajo para el nombre de [dest], agrupado por fila como [findTarget]. */
    private fun fastTargets(screen: Screen, dest: Dest): List<UiNode> {
        val hits = screen.sheetText(dest.target.name).filter { !it.inFeed && !it.inField }
        val texts = hits.filter { seen -> seen.node.text?.let { matches(it, fromText = true, dest.key) } == true }.map { it.node }
        val found = texts.ifEmpty {
            hits.filter { seen -> seen.node.contentDescription?.let { matches(it, fromText = false, dest.key) } == true }.map { it.node }
        }
        return groupByRow(found, screen.h).ifEmpty { truncatedMatch(dest, hits, screen.h) }
    }

    // ── Hoja ─────────────────────────────────────────────────────────────────────────────────────

    private fun findShare(windows: List<List<Seen>>, w: Int, h: Int): UiNode? {
        for (window in windows) {
            for (seen in window) {
                val node = seen.node
                val b = node.bounds
                if (seen.inField || b.isEmpty || b.left < 0 || b.top < 0 || b.right > w || b.bottom > h) continue
                if (node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank() && node.viewId.isNullOrEmpty()) continue
                if (shareDetector.evaluate(node, w, h).isShare) return node
            }
        }
        return null
    }

    /** La hoja: la ventana más alta con un campo de texto fuera del visor o con sus opciones. */
    private fun sheetOf(windows: List<List<Seen>>): List<Seen>? = windows.firstOrNull { window ->
        window.any { !it.inFeed && (isField(it.node) || hasMarker(it.node)) }
    }?.also { sheet ->
        sheet.firstOrNull()?.let { sheetWindow = it.window }
        // El buscador visto al recorrer: la próxima vez se prueba su id con el atajo.
        if (learnedSearchId == null) learnedSearchId = searchField(sheet)?.viewId?.takeIf { it.isNotEmpty() }
        if (sheet.any { !it.inFeed && isField(it.node) }) sheetWalkedWithField = true
    }

    private fun hasMarker(node: UiNode): Boolean = labelsOf(node).any { plain(it.value) in SHEET_MARKERS }

    /**
     * La cuadrícula ya ha cargado: se ve alguna celda fuera del visor, por su id de hoy (1.4.2) o por una foto de
     * perfil («Foto del perfil de…», diseños anteriores).
     */
    private fun gridLoaded(sheet: List<Seen>): Boolean = sheet.any { seen ->
        !seen.inFeed && !seen.inField && (
            seen.node.viewId == KNOWN_GRID_CELL_ID ||
                seen.node.contentDescription?.let { d ->
                    val n = RecipientDetector.normalize(d.toString())
                    RecipientDetector.PREFIXES.any { n.startsWith(it) }
                } == true
            )
    }

    /**
     * Dónde sale el nombre de [dest]: los textos visibles y, si no hay ninguno, las descripciones («Foto del
     * perfil de…»). Uno por fila o celda: el nombre y el usuario de la misma fila («Marta» y «marta») cuentan una
     * vez. Más de uno = dos personas o grupos con el mismo nombre.
     */
    private fun findTarget(dest: Dest, sheet: List<Seen>, screenHeight: Int): List<UiNode> {
        val candidates = sheet.filter { !it.inFeed && !it.inField }
        val texts = candidates.filter { seen -> seen.node.text?.let { matches(it, fromText = true, dest.key) } == true }.map { it.node }
        val found = texts.ifEmpty {
            candidates.filter { seen -> seen.node.contentDescription?.let { matches(it, fromText = false, dest.key) } == true }.map { it.node }
        }
        return groupByRow(found, screenHeight).ifEmpty { truncatedMatch(dest, candidates, screenHeight) }
    }

    /**
     * 1.4.4: un fijado guardado **cortado**. La cuadrícula parte los nombres largos en dos líneas («Viaje fin de» /
     * «curso») y hasta la 1.4.3 «A quién envías» se quedaba con la primera; el buscador enseña el nombre entero y
     * no coincidía nunca («el buscador no lo encuentra», medido en un móvil real). Solo en los resultados del
     * buscador vale un nombre que **empieza** por el fijado con palabras enteras, si es el único y su fila no lleva
     * otro nombre debajo (un grupo; una persona lleva su usuario, y «Pablo» nunca debe valer por «Pablo Pérez»).
     */
    private fun truncatedMatch(dest: Dest, candidates: List<Seen>, screenHeight: Int): List<UiNode> {
        if (phase != Phase.RESULT) return emptyList()
        val prefix = dest.key + " "
        val found = candidates.filter { seen ->
            !seen.inFeed && !seen.inField && seen.node.text?.let { t ->
                recipients.clean(t.toString(), fromText = true)?.let { RecipientDetector.normalize(it).startsWith(prefix) }
            } == true
        }.map { it.node }
        val node = groupByRow(found, screenHeight).singleOrNull() ?: return emptyList()
        val row = rowOf(node, screenHeight) ?: return emptyList()
        // Sin `skip`: en el móvil, el nodo del atajo y el mismo leído bajando por la fila son objetos distintos (cada
        // uno su AccessibilityUiNode) y el propio nombre contaba como «otro»; hasta el recorrido, a ~1 s (1.4.4, medido).
        val own = node.text?.let { recipients.clean(it.toString(), fromText = true) }?.let(RecipientDetector::normalize)
        return if ((namesBelow(row) - setOfNotNull(own)).isEmpty()) listOf(node) else emptyList()
    }

    private fun groupByRow(found: List<UiNode>, screenHeight: Int): List<UiNode> {
        val groups = mutableListOf<Pair<NodeBounds, UiNode>>()
        for (node in found) {
            val group = rowOf(node, screenHeight)?.bounds ?: node.parent?.bounds ?: node.bounds
            if (groups.none { (g, _) -> overlaps(g, group) }) groups += group to node
        }
        return groups.map { it.second }
    }

    private fun overlaps(a: NodeBounds, b: NodeBounds): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun matches(raw: CharSequence, fromText: Boolean, key: String): Boolean {
        val name = recipients.clean(raw.toString(), fromText) ?: return false
        return RecipientDetector.normalize(name) == key
    }

    private fun searchLabel(field: UiNode): String = labelsOf(field).joinToString(" ") { plain(it.value) }

    /**
     * ¿Es el buscador? El id de hoy ([KNOWN_SEARCH_ID]) basta; si no, alguna de sus etiquetas, **cada una por
     * separado**, dice «Buscar…»/«Search…» o es un nombre que ya se escribió ahí en este envío (revisión de la 1.4.3:
     * el buscador real lleva la descripción «Buscar» además del texto escrito, y unidas, «primos buscar», no valían).
     * Sin ninguna etiqueta, [allowEmpty].
     */
    private fun isSearchField(field: UiNode, allowEmpty: Boolean): Boolean {
        if (field.viewId == KNOWN_SEARCH_ID) return true
        val labels = labelsOf(field).map { plain(it.value) }
        if (labels.isEmpty()) return allowEmpty
        return labels.any { l -> SEARCH_PREFIXES.any { l.startsWith(it) } || l in typed }
    }

    /**
     * El buscador de la hoja: el campo que dice «Buscar…»/«Search…» (o lo que ya se escribió en él) o, si ninguno
     * dice nada, el de más arriba sin etiqueta. Nunca el de «Escribe un mensaje» (el nombre acabaría en el mensaje).
     */
    private fun searchField(sheet: List<Seen>): UiNode? {
        val fields = sheet.filter { !it.inFeed && isField(it.node) }.map { it.node }
        return fields.firstOrNull { f -> isSearchField(f, allowEmpty = false) }
            ?: fields.filter { searchLabel(it).isEmpty() }.minByOrNull { it.bounds.top }
    }

    private fun findSend(sheet: List<Seen>, screenWidth: Int, screenHeight: Int): UiNode? {
        val all = sheet.filter { seen ->
            !seen.inFeed && !seen.inField && seen.node.bounds != shareBounds && isSendButton(seen.node)
        }.map { it.node }
        return chooseSend(all, screenWidth, screenHeight)
    }

    /** De los «Enviar» de la hoja: el de la fila del nombre o el de más abajo, nunca el de la fila de otro. */
    private fun chooseSend(all: List<UiNode>, screenWidth: Int, screenHeight: Int): UiNode? {
        val candidates = all.filterNot { inOtherRow(it, screenHeight) }
        val row = dests.lastOrNull { it.status == Status.SELECTED }?.row
        return candidates.firstOrNull { row != null && it.bounds.centerY in row.top..row.bottom }
            ?: candidates.maxByOrNull { it.bounds.top }
            // El único «Enviar», y ancho: es el de abajo aunque al lado haya algo con pinta de nombre.
            ?: all.singleOrNull()?.takeIf { sendButtonWidth(it) >= screenWidth * config.wideSend }
    }

    /** Ancho del «Enviar» o, si la etiqueta va en el texto de un botón, del botón que la contiene. */
    private fun sendButtonWidth(node: UiNode): Int {
        val parent = node.parent?.takeIf { it.className?.contains("Button") == true }
        return maxOf(node.bounds.width, parent?.bounds?.width ?: 0)
    }

    // ── Filas ────────────────────────────────────────────────────────────────────────────────────

    /**
     * La fila (o celda) del nombre: el antepasado más grande que sigue siendo pequeño y no tiene más de
     * un «Enviar» (si tiene varios, ya es la lista entera del diseño antiguo).
     */
    private fun rowOf(node: UiNode, screenHeight: Int): UiNode? {
        var row: UiNode? = null
        var ancestor = node.parent
        repeat(config.maxRowAncestors) {
            val current = ancestor ?: return row
            if (current.bounds.height > screenHeight * config.maxRowHeight) return row
            if (below(current, config.rowMaxNodes, config.rowMaxDepth).count(::isSendButton) > 1) return row
            row = current
            ancestor = current.parent
        }
        return row
    }

    private fun sendInRow(row: UiNode, name: UiNode): UiNode? =
        below(row, config.rowMaxNodes, config.rowMaxDepth).firstOrNull { it !== name && !isField(it) && isSendButton(it) }

    /**
     * Un «Enviar» (o «Enviar por separado»): el nodo que lleva la etiqueta (no el padre que la tiene en un hijo,
     * que podría ser la fila entera). Al pulsarlo, el servicio sube hasta el primer antepasado pulsable. «Crear
     * grupo» no lo es nunca.
     */
    private fun isSendButton(node: UiNode): Boolean =
        RecipientDetector.isSendId(node) || labelsOf(node).any { RecipientDetector.SEND.matches(RecipientDetector.normalize(it.value)) }

    /** «Enviar» de la fila de otra persona (diseño antiguo): no se pulsa nunca. */
    private fun inOtherRow(node: UiNode, screenHeight: Int): Boolean {
        val keys = dests.mapTo(HashSet()) { it.key }
        var ancestor = node.parent
        repeat(config.maxRowAncestors) {
            val current = ancestor ?: return false
            if (current.bounds.height > screenHeight * config.maxRowHeight) return false
            val names = namesBelow(current, skip = node)
            if (names.isNotEmpty()) return names.none { it in keys }
            ancestor = current.parent
        }
        return false
    }

    /**
     * Nombres (normalizados) que se leen bajo [node], sin mirar [skip]: solo textos visibles (los iconos con
     * descripción, como «Cámara», no son nombres), fuera de campos de texto («Escribe un mensaje…») y que no
     * sean opciones de la hoja.
     */
    private fun namesBelow(node: UiNode, skip: UiNode? = null): Set<String> =
        below(node, config.rowMaxNodes, config.rowMaxDepth, skip, skipFields = true).mapNotNullTo(mutableSetOf()) { n ->
            n.text?.toString()
                ?.takeUnless { plain(it) in SHEET_MARKERS }
                ?.let { recipients.clean(it, fromText = true) }
                ?.let(RecipientDetector::normalize)
        }

    // ── Árbol ────────────────────────────────────────────────────────────────────────────────────

    /** Lo que se mira en un tick: los recorridos solo si hacen falta (perezosos) y los atajos. */
    private inner class Screen(val roots: List<UiNode>, val w: Int, val h: Int, val now: Long) {
        /** Recorrido entero, visor incluido: solo para buscar «Compartir» si el atajo no lo ve. */
        val windows: List<List<Seen>> by lazy {
            countWalk(now)
            timed({ walkMs += it }) { roots.mapIndexed { i, root -> snapshot(root, w, h, keyOf(i), pruneFeed = false) } }
        }

        /** Recorrido sin entrar en el visor (la hoja nunca está dentro): para todo lo demás. */
        val outside: List<List<Seen>> by lazy {
            countWalk(now)
            timed({ walkMs += it }) { roots.mapIndexed { i, root -> snapshot(root, w, h, keyOf(i), pruneFeed = true) } }
        }

        /** Identidad de la ventana de [roots]`[i]`: su `windowId` o, si no se sabe, su posición. */
        fun keyOf(i: Int): Int = roots[i].windowId.takeIf { it != UiNode.NO_WINDOW } ?: i

        /** Como [text], pero solo en la ventana de la hoja; sin hoja conocida, nada (el recorrido decide). */
        fun sheetText(query: String): List<Seen> {
            val sheet = sheetWindow ?: return emptyList()
            return text(query).filter { it.window == sheet }
        }

        /** El primero que vale de [queries] en la hoja, preguntando en orden y parando al primero que acierta. */
        fun firstSheetText(queries: List<String>, valid: (Seen) -> Boolean): Seen? =
            queries.firstNotNullOfOrNull { q -> sheetText(q).firstOrNull(valid) }

        /** Todos los que valen de la primera consulta de [queries] que devuelva alguno (las demás no se piden). */
        fun allSheetText(queries: List<String>, valid: (Seen) -> Boolean): List<Seen> {
            for (q in queries) {
                val hits = sheetText(q).filter(valid)
                if (hits.isNotEmpty()) return hits
            }
            return emptyList()
        }

        private val targets = HashMap<String, List<UiNode>>()

        fun fastTargets(dest: Dest): List<UiNode> =
            if (finder == null) emptyList() else targets.getOrPut(dest.key) { fastTargets(this, dest) }

        private val cache = HashMap<String, List<Seen>>()

        fun text(query: String): List<Seen> = fast("t:$query") { f, root -> f.byText(root, query) }

        fun id(viewId: String): List<Seen> = fast("i:$viewId") { f, root -> f.byViewId(root, viewId) }

        private fun fast(key: String, find: (UiFinder, UiNode) -> List<UiNode>): List<Seen> {
            val f = finder ?: return emptyList()
            return cache.getOrPut(key) {
                queries++
                timed({ queryMs += it }) {
                    roots.flatMapIndexed { i, root ->
                        val found = try {
                            find(f, root)
                        } catch (e: RuntimeException) {
                            emptyList()
                        }
                        val key = keyOf(i)
                        found.filter { it.isVisible && it.bounds.intersectsScreen(w, h) }.map { seenFromAncestors(it, key) }
                    }
                }
            }
        }
    }

    /** [block] cronometrado con [clock] (si lo hay): lo que tardó va a [add]. */
    private inline fun <T> timed(add: (Long) -> Unit, block: () -> T): T {
        val c = clock ?: return block()
        val start = c()
        return block().also { add((c() - start).coerceAtLeast(0L)) }
    }

    private fun countWalk(now: Long) {
        walks++
        lastWalkAt = now
        lastWalkMarks = marks.size
    }

    /** [Seen] de un nodo suelto (del atajo): mira sus antepasados para saber si está en el visor o en un campo. */
    private fun seenFromAncestors(node: UiNode, window: Int): Seen {
        var feed = false
        var field = false
        var current: UiNode? = node
        var depth = 0
        while (current != null && depth <= config.maxDepth) {
            if (!feed && isFeedNode(current)) feed = true
            if (!field && isField(current)) field = true
            if (feed && field) break
            // 1.4.3: la hoja de compartir no está dentro del visor; subir más allá de ella son ~20 llamadas a Instagram.
            if (!feed && current.viewId in SHEET_ROOT_IDS) break
            current = current.parent
            depth++
        }
        return Seen(node, feed, field, window)
    }

    /**
     * Sin atajo, siempre. Con él, el primer recorrido de cada fase va sin esperar (1.4.1: en la 1.4.0 la hoja
     * podía esperar hasta 300 ms al recorrido de «compartir») y los siguientes, como mucho cada [Config.walkEveryMs].
     */
    private fun walkAllowed(now: Long): Boolean {
        if (finder == null) return true
        // 1.4.2: mientras la hoja se abre o se anima, solo atajos (una llamada); el recorrido, de respaldo pasado un rato.
        if (phase != Phase.SHARE && now - phaseSince < config.walkGraceMs && now >= phaseSince) return false
        val last = lastWalkAt ?: return true
        if (lastWalkMarks != marks.size) return true
        return now - last >= config.walkEveryMs || now < last
    }

    private fun isFeedNode(node: UiNode): Boolean = shortViewId(node.viewId).let { id -> RecipientDetector.FEED_IDS.any { id.contains(it) } }

    /**
     * Nodos visibles y dentro de la pantalla, en orden de lectura y con límite. Con [pruneFeed], del visor solo
     * se apunta su contenedor (sin entrar): es la mayor parte del árbol y nada de la hoja está dentro.
     */
    private fun snapshot(root: UiNode, w: Int, h: Int, window: Int, pruneFeed: Boolean): List<Seen> {
        val out = ArrayList<Seen>()
        fun visit(node: UiNode, depth: Int, inFeed: Boolean, inField: Boolean) {
            if (out.size >= config.maxNodes || !node.isVisible || !node.bounds.intersectsScreen(w, h)) return
            val feed = inFeed || isFeedNode(node)
            val field = inField || isField(node)
            out += Seen(node, feed, field, window)
            if (pruneFeed && feed) return
            if (depth < config.maxDepth) node.children.forEach { visit(it, depth + 1, feed, field) }
        }
        visit(root, 0, inFeed = false, inField = false)
        return out
    }

    private fun below(node: UiNode, maxNodes: Int, maxDepth: Int, skip: UiNode? = null, skipFields: Boolean = false): List<UiNode> {
        val out = ArrayList<UiNode>()
        fun visit(n: UiNode, depth: Int) {
            if (out.size >= maxNodes || n === skip || !n.isVisible || (skipFields && isField(n))) return
            out += n
            if (depth < maxDepth) n.children.forEach { visit(it, depth + 1) }
        }
        node.children.forEach { visit(it, 1) }
        return out
    }

    private class Label(val value: String)

    private fun labelsOf(node: UiNode): List<Label> =
        listOfNotNull(node.text, node.contentDescription).map { it.toString() }.filter { it.isNotBlank() }.map(::Label)

    private fun isField(node: UiNode): Boolean = node.className?.contains("EditText") == true

    /** Normalizado y sin puntos suspensivos ni puntuación al final («Escribe un mensaje…» → «escribe un mensaje»). */
    private fun plain(text: String): String = RecipientDetector.normalize(text).trimEnd('.', '…', ':', ' ')

    // ── Estado ───────────────────────────────────────────────────────────────────────────────────

    /** Pasa a [next]; [how] dice cómo se decidió el final de la fase que se deja. */
    private fun enter(next: Phase, now: Long, how: How?) {
        rejectedSince = null
        marks.lastOrNull()?.let { if (how != null) it.how = how }
        phase = next
        phaseSince = now
        marks += Mark(next, now, if (next == Phase.TARGET || next == Phase.RESULT) current()?.index ?: 0 else 0)
    }

    /** Antes de una acción que el servicio aún tiene que hacer: se guarda cómo estaba ([onPerformed]). */
    private fun snapshot(dest: Dest?, typedKey: String? = null) {
        before = Before(phase, phaseSince, marks.size, marks.lastOrNull()?.how, sentFast, dest, dest?.copyState(), typedKey, rejectedSince)
    }

    /** Un [Step.Wait] con su motivo para [Report.waits] (si ya había uno más concreto en este tick, se queda ese). */
    private fun wait(why: String): Step {
        if (waitWhy == null) waitWhy = why
        return Step.Wait
    }

    /**
     * El recorrido de respaldo espera: mientras la hoja se abre (solo atajos, [Config.walkGraceMs]) o su turno
     * ([Config.walkEveryMs]).
     */
    private fun paused(now: Long) {
        waitWhy = if (phase != Phase.SHARE && now - phaseSince < config.walkGraceMs) "solo atajos mientras se abre" else "recorrido en pausa"
    }

    private fun timeoutOr(now: Long, reason: String): Step =
        if (now - phaseSince > config.stepTimeoutMs) finish(false, reason, now) else wait(reason)

    private fun finish(ok: Boolean, reason: String, now: Long, how: How? = null): Step.Done {
        if (how != null) marks.lastOrNull()?.how = how
        val chosen = dests.filter { it.status == Status.SELECTED || it.status == Status.ROW_SENT }
        val sentCount = if (ok) chosen.size else 0
        val anyOk = ok && sentCount > 0
        val finalReason = if (anyOk && sentCount < dests.size) "enviado a ${Format.integer(sentCount.toLong())} de ${Format.integer(dests.size.toLong())}" else reason
        val done = Step.Done(anyOk, finalReason, sheetOpened = sheetAt != null, targetSelected = chosen.isNotEmpty())
        val phases = marks.mapIndexed { i, m -> PhaseTime(m.phase, ((marks.getOrNull(i + 1)?.at ?: now) - m.at).coerceAtLeast(0L), m.how, m.dest) }
        lastResults = dests.map { d ->
            val sent = anyOk && d in chosen
            DestResult(
                d.target.name, sent,
                when {
                    sent -> "enviado"
                    d.status == Status.FAILED -> d.reason
                    else -> reason
                },
                d.foundIn.takeUnless { d.gridHidden && it == Found.SEARCH }, d.target.skipGrid, selected = d in chosen,
            )
        }
        lastReport = Report(
            ok = anyOk,
            phases = phases,
            totalMs = (now - startedAt).coerceAtLeast(0L),
            fastHits = fastHits,
            walks = walks,
            foundIn = dests.firstOrNull()?.foundIn,
            skippedGrid = dests.any { it.target.skipGrid },
            provisional = provisional,
            shareId = learnedShareId,
            searchId = learnedSearchId,
            ticks = ticks,
            readMs = readMs,
            readMaxMs = readMaxMs,
            avatarMs = avatarMs,
            avatarOk = avatarOk,
            dests = dests.size,
            sent = sentCount,
            actions = actions.toList(),
            waits = LinkedHashMap(waits),
            searches = searches.toList(),
            forgetSearchId = hints.searchId != null && sheetWalkedWithField && !sawLearnedSearch && learnedSearchId == null,
            queries = queries,
            queryMs = queryMs,
            walkMs = walkMs,
        )
        running = false
        return done
    }

    companion object {

        /** Opciones que solo salen en la hoja de compartir (normalizadas). */
        private val SHEET_MARKERS = setOf(
            "copiar enlace", "copy link", "anadir a la historia", "anadir a historia", "add to story",
            "add to your story", "escribe un mensaje", "write a message",
        )
        private val SEARCH_PREFIXES = listOf("buscar", "search")

        /** Android rechaza una y otra vez la acción (nada pulsable a la medida). */
        private const val REJECTED = "Android no acepta la acción"

        /**
         * Ids de la hoja de compartir de Instagram vistos en el árbol real (Instagram 454, nov. 2026). Solo son atajos:
         * lo que devuelven pasa las mismas reglas, y si cambian, se aprende el buscador o se recorre como siempre.
         */
        const val KNOWN_SEARCH_ID = "com.instagram.android:id/search_edit_text"
        const val KNOWN_GRID_CELL_ID = "com.instagram.android:id/direct_share_sheet_grid_view_pog"
        private val KNOWN_SHEET_IDS = listOf(
            "com.instagram.android:id/direct_private_share_container_view",
            "com.instagram.android:id/direct_private_share_recipients_recycler_view",
        )

        /** Una hoja abierta (la de compartir o cualquier hoja de abajo de Instagram), para «¿ya hay una hoja abierta?». */
        private val OPEN_SHEET_IDS = listOf(
            "com.instagram.android:id/direct_private_share_container_view",
            "com.instagram.android:id/layout_container_bottom_sheet",
        )

        /** Al mirar los antepasados de un nodo del atajo, al llegar a uno de estos ya se sabe que no está en el visor. */
        private val SHEET_ROOT_IDS = KNOWN_SHEET_IDS.toSet() + setOf(
            "com.instagram.android:id/layout_container_bottom_sheet",
            "com.instagram.android:id/bottom_sheet_container",
        )

        /** [release]: «Enviar» aceptado y otro envío esperando; no se espera a confirmarlo. */
        const val RELEASED = "enviado sin confirmar"

        /** Lo que se pide a Instagram con el atajo (contiene, sin mayúsculas, con tildes). */
        private val SHARE_QUERIES = listOf("Compartir", "Share", "Enviar", "Send")

        /** Opciones de la hoja que se ven como texto (las pistas de los campos vacíos no las encuentra Instagram). */
        val MARKER_QUERIES = listOf("Copiar enlace", "Copy link", "Añadir a", "Add to")

        /** ¿[label] es una opción que solo sale en la hoja de compartir? (Para el recorte de fotos a mano.) */
        fun isSheetMarker(label: CharSequence): Boolean =
            RecipientDetector.normalize(label.toString()).trimEnd('.', '…', ':', ' ') in SHEET_MARKERS
        private val SEND_QUERIES = listOf("Enviar", "Send", "Envia", "Envoyer", "Invia", "Senden", "Verzenden", "Versturen", "Bidali")
        private val UNDO_QUERIES = listOf("Deshacer", "Undo", "Enviad", "Sent", "Desf", "Annul", "Envoy", "Inviat", "Gesendet")

        /** La fila o la hoja dicen que ya se ha enviado (normalizado). */
        private val UNDO_OR_SENT = Regex(
            "^(?:deshacer|undo|enviado|sent|enviada|desfazer|desfer|annuler|annulla|ruckgangig machen|ongedaan maken|envoye|inviato|" +
                "gesendet|verzonden|enviat)$",
        )

        /** Como [ShareDetector.Config.maxWidth]: el botón compartir no es más ancho que esto. */
        private const val SHARE_MAX_WIDTH = 0.35f

        /** Tras estas veces seguidas sin salir en la cuadrícula, el fijado va directo al buscador. */
        const val SKIP_GRID_AFTER = 2

        /** Veces seguidas (guardadas por fijado) que no salió en la cuadrícula, tras [report]. */
        fun nextGridMisses(previous: Int, report: Report): Int = nextGridMisses(previous, report.foundIn, report.skippedGrid)

        /** Lo mismo con el resultado de un destino. */
        fun nextGridMisses(previous: Int, result: DestResult): Int = nextGridMisses(previous, result.foundIn, result.skippedGrid)

        fun nextGridMisses(previous: Int, foundIn: Found?, skippedGrid: Boolean): Int = when (foundIn) {
            Found.GRID -> 0
            // Directo al buscador: baja uno, para que de vez en cuando se vuelva a probar la cuadrícula.
            Found.SEARCH -> if (skippedGrid) (previous - 1).coerceAtLeast(0) else (previous + 1).coerceAtMost(9)
            null -> previous
        }

        fun skipGrid(misses: Int): Boolean = misses >= SKIP_GRID_AFTER
    }
}
