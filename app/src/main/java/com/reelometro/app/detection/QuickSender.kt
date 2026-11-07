package com.reelometro.app.detection

import com.reelometro.app.stats.Format

/**
 * **Envío rápido** (Instagram y TikTok, solo con el ajuste encendido): manda el vídeo que se está viendo a
 * una o varias personas o grupos fijados, y solo esto: «Compartir», esos nombres y un único «Enviar».
 *
 * 1.10.0: lo que cambia de una app a otra viene en el [PlatformProfile] de [start] (ids, palabras, ventanas). Con la
 * hoja en su propia ventana ([PlatformProfile.sheetOwnWindow], TikTok) las fases son las mismas con sus reglas: la hoja
 * es la ventana con `tv_title` que no es la del visor, el nombre es la **celda** de la fila de contactos cuya
 * descripción es el nombre, el buscador se abre con la lupa de la cabecera ([Phase.SEARCH]) y el envío se confirma
 * cuando esa ventana desaparece. En esa hoja solo se pulsan una celda o fila de contacto, la lupa de la cabecera y
 * «Enviar», y solo se escribe en el campo del buscador. 1.10.1: la lupa y «Enviar» se encuentran también por su sitio (en
 * cualquier idioma), el buscador se busca en su propia ventana y una fila de contactos abierta desplazada se vuelve al
 * principio una vez (la única acción que no es un [Step]: [UiNode.scrollBackward]).
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
        /** 1.10.0, hoja en su propia ventana (TikTok): pulsada la lupa de la cabecera, espera el buscador y mira los recientes. */
        SEARCH("abre el buscador", "lupa"),
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

    /**
     * Un nodo visto en el recorrido de una ventana (o devuelto por el atajo, con sus antepasados mirados). [real]: [window]
     * es el `windowId` de Android (no la posición de la raíz).
     */
    private class Seen(val node: UiNode, val inFeed: Boolean, val inField: Boolean, val window: Int, val real: Boolean = false)

    /** Hoja en su propia ventana: lo que se pulsa por un destino ([click], la celda o la fila) y el nodo que dice su nombre. */
    private class Pick(val click: UiNode, val label: UiNode)

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

    /** La app del envío en curso ([start]). */
    private var profile: PlatformProfile = PlatformProfile.INSTAGRAM

    /** La app del último [start] (el servicio la usa para repartir el resultado). */
    val platformProfile: PlatformProfile get() = profile

    /** La hoja de esta app va en su propia ventana (TikTok): su propio recorrido de cada fase. */
    private val own: Boolean get() = profile.sheetOwnWindow

    /**
     * Paquete de la app en este envío (TikTok tiene dos): sabido, cada id se pide una vez y no una por paquete. Sale del
     * id de cualquier nodo que devuelva un atajo (el «Compartir» del primer tick) o del buscador aprendido.
     */
    private var pkg: String? = null

    /** Hoja en su propia ventana: la ventana del visor (la del «Compartir»). La hoja nunca está en ella. */
    private var feedWindow: Int? = null

    /** Hoja en su propia ventana: se pulsó la lupa y la hoja enseña el buscador (otra pantalla en la misma ventana). */
    private var searchOpen = false

    /** Hoja en su propia ventana: ya se vio la fila de contactos cargada (con alguna celda). */
    private var rowLoaded = false

    /** Hoja en su propia ventana: se buscó el campo del buscador (si el aprendido no salió, se olvida). */
    private var ownFieldSought = false

    /**
     * 1.10.1, hoja en su propia ventana: la fila de contactos se volvió al principio en este envío (TikTok la abre a veces
     * desplazada). Solo una vez por envío; [rowScrolledAt], cuándo, para darle [ROW_SCROLL_SETTLE_MS] antes de ir a la lupa.
     */
    private var rowScrolled = false
    private var rowScrolledAt: Long? = null

    /**
     * 1.10.1: la etiqueta de la lupa pulsada, tal cual (la de su idioma, aunque no esté en ninguna lista): con ella se pide al
     * atajo la lupa del buscador, que lleva la misma, y vale como «lupa al lado» del campo ([besideSearchButton]).
     */
    private var lupaWord: String? = null

    /** 1.10.1: la etiqueta del «Enviar» pulsado, tal cual: la confirmación lo busca por ella (y no por todas las palabras). */
    private var sentWord: String? = null

    /** El «Enviar» pulsado llevaba un id conocido ([PlatformProfile.sendIds]): la confirmación lo puede pedir por él. */
    private var sentById = false

    /**
     * Revisión de la 1.10.1: con la fila recién vuelta al principio (aún moviéndose), la celda del destino solo se pulsa si
     * sale en el mismo sitio en dos lecturas seguidas (una celda reciclada a mitad de movimiento podría ser otra persona).
     * La clave del destino y dónde se vio la última vez.
     */
    private var settlingCell: Pair<String, NodeBounds>? = null
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
        val searchOpen: Boolean = false,
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
    fun start(
        targets: List<Target>,
        now: Long,
        hints: Hints = Hints(),
        waitForClosedSheet: Boolean = false,
        profile: PlatformProfile = PlatformProfile.INSTAGRAM,
    ) {
        // 1.10.0: la app de este envío (Instagram o TikTok); lo que cambia de una a otra viene en el perfil.
        this.profile = profile
        pkg = hints.searchId?.let(::packageOf)
        // Las raíces de la hoja son de la app de este envío: tras uno de TikTok sin paquete sabido, Instagram no puede
        // usar las de TikTok con la misma clave (revisión de la 1.10.0: volvía a subir hasta la raíz, como antes de la 1.4.3).
        rootIdsCache = null
        feedWindow = null
        searchOpen = false
        rowLoaded = false
        ownFieldSought = false
        rowScrolled = false
        rowScrolledAt = null
        lupaWord = null
        sentWord = null
        sentById = false
        settlingCell = null
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
        searchOpen = b.searchOpen
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
            return if ((phase == Phase.TARGET || phase == Phase.SEARCH || phase == Phase.RESULT) && dest != null) {
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
            Phase.TARGET, Phase.SEARCH, Phase.RESULT -> lookForTarget(screen, now)
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
        if (own) return ownShareStep(screen, now)
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
        // En TikTok el id de «Compartir» es el de «Me gusta»: nunca se aprende (PlatformProfile.learnShareId).
        learnedShareId = share.viewId?.takeIf { it.isNotEmpty() && profile.learnShareId }
        snapshot(null)
        enter(Phase.SHEET, now, how)
        return Step.Click(share, "compartir", maxClimbWidth = (screen.w * SHARE_MAX_WIDTH).toInt())
    }

    private fun sheetStep(screen: Screen, now: Long): Step {
        if (own) return ownSheetStep(screen, now)
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
        if (own) return ownConfirmSent(screen, now)
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
        if (own) return ownLookForTarget(screen, now)
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
        // Hoja en su propia ventana: la lupa se pulsó pero el buscador no llegó a abrirse (el título de la hoja sigue a la
        // vista): el siguiente destino se busca otra vez en la fila de contactos (revisión de la 1.10.0). Solo al dar el
        // destino por fallado: justo tras la lupa, la hoja aún enseña su título mientras llega el buscador.
        if (own && searchOpen && phase == Phase.SEARCH && ownTitleVisible(screen)) searchOpen = false
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
        // En TikTok se escribe lo mismo que se pide al atajo ([ownQuery]: un grupo, por su primer miembro); la fila se sigue
        // eligiendo por el nombre exacto. Instagram, el nombre entero, como siempre.
        return Step.SetText(field, if (own) ownQuery(dest) else dest.target.name)
    }

    private fun sendStep(screen: Screen, now: Long): Step {
        if (own) return ownSendStep(screen, now)
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

    /** El «Enviar» de abajo por su id de hoy ([PlatformProfile.sendIds]), en la ventana de la hoja; nunca «Crear grupo». */
    private fun sheetSendById(screen: Screen): List<UiNode> =
        fullIds(profile.sendIds).flatMap { screen.id(it) }
            .filter { !it.inFeed && !it.inField && (sheetWindow == null || it.window == sheetWindow) && it.node.bounds != shareBounds && isSendId(it.node) }
            .map { it.node }
            .distinctBy { it.bounds }

    private fun clickSend(send: UiNode, fast: Boolean, now: Long): Step {
        sentBounds = send.bounds
        sentWord = labelsOf(send).firstOrNull()?.value
        sentById = shortViewId(send.viewId).let { it.isNotEmpty() && it in profile.sendIds }
        snapshot(null)
        enter(Phase.SENT, now, if (fast) How.FAST else How.WALK)
        sentFast = fast
        sendPending = true
        return Step.Click(send, "enviar")
    }

    // ── Hoja en su propia ventana (TikTok, 1.10.0) ───────────────────────────────────────────────

    /**
     * Compartir con la hoja en su propia ventana. «Compartir» del visor por sus palabras (nunca por id: en TikTok es el
     * de «Me gusta»), con las reglas de [ShareDetector]; de paso dice el paquete de la app y la ventana del visor. Antes de
     * pulsarlo, «¿ya hay una hoja abierta?»: una ventana encima del visor con [PlatformProfile.openSheetIds]
     * (`touch_outside`, el de cualquier hoja de abajo) o con las marcas de la hoja. Nunca se pulsa «Compartir» detrás.
     */
    private fun ownShareStep(screen: Screen, now: Long): Step {
        var seen = if (finder != null) fastShareSeen(screen) else null
        seen?.let(::noteFeedWindow)
        // Con la ventana del visor sabida, estas búsquedas solo van a las demás ventanas: sin ninguna, no cuestan nada.
        var open = seen != null && ownSheetOpenFast(screen)
        var how = How.FAST
        if (!open && seen == null) {
            if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "no encuentro compartir") }
            open = screen.outside.any { w -> w.any { !it.inFeed && isOwnSheetOpenMark(it.node) } }
            if (!open) {
                seen = findShareSeen(screen.windows, screen.w, screen.h)
                seen?.let(::noteFeedWindow)
                how = How.WALK
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
        val found = seen?.node ?: return timeoutOr(now, "no encuentro compartir")
        if (how == How.FAST) fastHits++
        return clickShare(found, screen, now, how)
    }

    private fun noteFeedWindow(seen: Seen) {
        if (seen.real) feedWindow = seen.window
    }

    /** ¿Hay una hoja encima del visor? `touch_outside` o las marcas de la hoja, en las ventanas que no son la del visor. */
    private fun ownSheetOpenFast(screen: Screen): Boolean =
        fullIds(profile.openSheetIds).any { id -> screen.awayId(id).any { !it.inFeed } } ||
            profile.markerQueries.any { q -> screen.awayText(q).any { !it.inFeed && hasMarker(it.node) } }

    /** La hoja: la ventana con su título (`tv_title`) que no es la del visor. Desde aquí, solo esa ventana. */
    private fun ownSheetStep(screen: Screen, now: Long): Step {
        val header = if (finder != null) ownHeader(screen) else null
        val how: How
        if (header != null) {
            fastHits++
            sheetWindow = header.window
            how = How.FAST
        } else {
            if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "la hoja de compartir no se abre") }
            ownSheetOf(screen.outside) ?: return timeoutOr(now, "la hoja de compartir no se abre")
            how = How.WALK
        }
        sheetAt = now
        enter(Phase.TARGET, now, how)
        // Con el nombre ya a la vista se pulsa en este mismo tick.
        return ownLookForTarget(screen, now)
    }

    /**
     * ¿Se ve el título de la hoja de contactos? Solo por su id (`tv_title`): la etiqueta «Enviar a» también sale en el
     * buscador (con otro id), y ahí el buscador sí se ha abierto.
     */
    private fun ownTitleVisible(screen: Screen): Boolean =
        if (finder != null) {
            ownHeader(screen) != null
        } else {
            ownSheetOf(screen.outside)?.any { s -> !s.inFeed && shortViewId(s.node.viewId).let { it.isNotEmpty() && it in profile.sheetIds } } == true
        }

    /** El título de la hoja (`tv_title`), fuera del visor y, si ya se sabe, en la ventana de la hoja. Una vez por tick. */
    private fun ownHeader(screen: Screen): Seen? {
        if (screen.headerAsked) return screen.header
        screen.headerAsked = true
        val inSheet = screen.sheetRoot() != null
        return fullIds(profile.sheetIds).firstNotNullOfOrNull { id ->
            (if (inSheet) screen.sheetOnlyId(id) else screen.awayId(id)).firstOrNull { !it.inFeed && !it.inField }
        }.also { screen.header = it }
    }

    /**
     * El nombre en la hoja: la **celda** de la fila de contactos cuya descripción es el nombre (su texto puede ir cortado).
     * Si no está (la fila ya cargó sin él, o pasado [Config.searchAfterMs]) o suele no estar ([Target.skipGrid]), la lupa
     * de la cabecera abre el buscador ([openSearch]); antes, si la fila se abrió desplazada, se vuelve al principio una vez
     * ([scrollRowBack]). Con el buscador abierto, [ownLookInSearch].
     */
    private fun ownLookForTarget(screen: Screen, now: Long): Step {
        val dest = current() ?: return afterTargets(screen, now)
        if (searchOpen) return ownLookInSearch(dest, screen, now)
        val notFound = "no encuentro el nombre"
        // Suele no estar en la fila (aprendido): directo a la lupa, sin mirarla.
        if (!dest.searched && dest.target.skipGrid) return openSearch(dest, screen, now, "directo, aprendido", rowSeen = true, walk = null)
        if (finder != null) {
            val header = ownHeader(screen)
            // Una llamada: la búsqueda del nombre en la ventana de la hoja devuelve la celda por su descripción.
            val cells = ownCells(dest, screen.sheetOnlyText(ownQuery(dest)), header?.node?.bounds, divider = null)
            if (cells.isNotEmpty()) {
                fastHits++
                return onOwnTargets(dest, cells, screen, now, How.FAST, Found.GRID)
            }
            if (!dest.searched) {
                // La celda de la fila también dice si la fila se puede volver al principio (solo hasta que se vuelva una vez).
                val cell = if (header != null && (!rowLoaded || !rowScrolled)) probeRow(header.node, screen.w) else null
                if (cell != null) rowLoaded = true
                val loaded = header != null && rowLoaded
                if (loaded) scrollRowBack(cell, screen, now)?.let { return it }
                if (rowSettling(now)) {
                    waitWhy = "fila vuelta al principio"
                    return destTimeoutOr(dest, screen, now, notFound)
                }
                val waited = now - (sheetAt ?: now) >= config.searchAfterMs
                if (loaded || waited) {
                    return openSearch(dest, screen, now, if (loaded) "fila sin él" else "pasado el plazo sin fila", rowSeen = loaded, walk = null)
                }
            }
            if (!walkAllowed(now)) {
                waitWhy = "fila sin cargar"
                return destTimeoutOr(dest, screen, now, notFound)
            }
        }
        if (!walkAllowed(now)) return paused(now).let { destTimeoutOr(dest, screen, now, notFound) }
        val sheet = ownSheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
        val header = walkHeader(sheet)
        val divider = header?.let { walkDivider(sheet, it, screen.w) }
        val cells = ownCells(dest, sheet, header, divider)
        if (cells.isNotEmpty()) return onOwnTargets(dest, cells, screen, now, How.WALK, Found.GRID)
        val cell = header?.let { h -> sheet.firstOrNull { !it.inFeed && isCellLike(it.node) && inContactRow(it.node.bounds, h, divider) } }
        val loaded = cell != null
        if (loaded) rowLoaded = true
        if (!dest.searched) {
            if (loaded) scrollRowBack(cell?.node, screen, now)?.let { return it }
            if (rowSettling(now)) {
                waitWhy = "fila vuelta al principio"
                return destTimeoutOr(dest, screen, now, notFound)
            }
        }
        val waited = now - (sheetAt ?: now) >= config.searchAfterMs
        if (!dest.searched && (loaded || waited)) {
            return openSearch(dest, screen, now, if (loaded) "fila sin él" else "pasado el plazo sin fila", rowSeen = loaded, walk = sheet)
        }
        waitWhy = if (loaded) "fila sin él" else "fila sin cargar"
        return destTimeoutOr(dest, screen, now, notFound)
    }

    /**
     * Pulsa la lupa de la **cabecera de la hoja** (nunca la del visor: solo se mira la ventana de la hoja, a la altura de su
     * título). 1.10.1: primero por su sitio ([siteLupa], cualquier idioma) y, si no, por sus palabras (al atajo, solo el español
     * y el inglés; recorriendo, todas). [rowSeen]: se vio la fila de contactos sin él (si no, no se aprende «no sale en la fila»).
     */
    private fun openSearch(dest: Dest, screen: Screen, now: Long, why: String, rowSeen: Boolean, walk: List<Seen>?): Step {
        var lupa: UiNode? = null
        var how = How.FAST
        if (walk == null && finder != null) {
            val header = ownHeader(screen)
            lupa = header?.let { siteLupa(it.node, screen.w) } ?: header?.node?.bounds?.let { h ->
                profile.searchButtonWords.take(PRIMARY_WORDS).firstNotNullOfOrNull { q -> screen.sheetOnlyText(q).firstOrNull { isLupa(it, h) }?.node }
            }
        }
        if (lupa == null) {
            val sheet = walk ?: run {
                if (!walkAllowed(now)) {
                    waitWhy = "sin la lupa"
                    return destTimeoutOr(dest, screen, now, "no encuentro el buscador")
                }
                ownSheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
            }
            val title = walkTitle(sheet)
            lupa = title?.let { siteLupa(it, screen.w) } ?: title?.bounds?.let { h -> sheet.firstOrNull { isLupa(it, h) }?.node }
            how = How.WALK
        }
        if (lupa == null) {
            waitWhy = "sin la lupa"
            return destTimeoutOr(dest, screen, now, "no encuentro el buscador")
        }
        if (how == How.FAST) fastHits++
        snapshot(dest)
        dest.searched = true
        dest.gridHidden = !rowSeen
        searchOpen = true
        searches += why
        lupaWord = labelsOf(lupa).firstOrNull()?.value
        enter(Phase.SEARCH, now, how)
        return Step.Click(lupa, "lupa del buscador")
    }

    /**
     * 1.10.1, la lupa por su **sitio**, en cualquier idioma: de los botones pulsables que acompañan al título de la hoja (sus
     * hermanos: en TikTok 47.7, la lupa y «Cerrar»), el primero, si es el único del lado donde empieza la lectura (la izquierda;
     * la derecha si el título se escribe de derecha a izquierda, como en árabe, donde la cabecera va al revés). Si no está
     * claro (ninguno o dos de ese lado, o el primero es el del otro lado), nada: las palabras de respaldo o el recorrido.
     *
     * Revisión de la 1.10.1: lo que se sabe va antes que el sitio. Si uno de esos botones dice una palabra de la lupa
     * ([PlatformProfile.searchButtonLabels]), es ese, esté donde esté; y el del sitio no vale si dice la de otro botón conocido
     * («Cerrar» en cualquiera de sus idiomas, «Enviar», el título o una opción de la hoja).
     */
    private fun siteLupa(title: UiNode, screenWidth: Int): UiNode? {
        val t = title.bounds
        if (t.isEmpty) return null
        val buttons = title.parent?.children.orEmpty().filter { c ->
            val b = c.bounds
            c !== title && b != t && c.isClickable && c.isVisible && !isField(c) && !b.isEmpty && b.width < screenWidth / 3 &&
                b.centerY >= t.top - t.height && b.centerY <= t.bottom + t.height
        }
        buttons.firstOrNull { b -> labelsOf(b).any { plain(it.value) in profile.searchButtonLabels } }?.let { return it }
        val rtl = rightToLeft(title.text)
        val start = buttons.filter { if (rtl) it.bounds.centerX > screenWidth / 2 else it.bounds.centerX < screenWidth / 2 }
        val first = buttons.firstOrNull() ?: return null
        return first.takeIf { start.size == 1 && start.single() === first && !isOtherKnownButton(it) }
    }

    /** ¿Dice la etiqueta de otro botón conocido de la hoja (cerrar, «Enviar», el título, una opción) y no la de la lupa? */
    private fun isOtherKnownButton(node: UiNode): Boolean = labelsOf(node).any { l ->
        plain(l.value).let {
            it !in profile.searchButtonLabels &&
                (it in profile.closeLabels || it in profile.sendLabels || it in profile.titleLabels || it in profile.sheetOptions)
        }
    }

    /** ¿El texto empieza (su primera letra con dirección) de derecha a izquierda? Árabe, hebreo, persa… */
    private fun rightToLeft(text: CharSequence?): Boolean {
        for (c in text ?: return false) {
            when (Character.getDirectionality(c)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            }
        }
        return false
    }

    /** La lupa de la cabecera: pulsable, dice exactamente «Buscar»/«Search» y está en la fila del título de la hoja. */
    private fun isLupa(seen: Seen, header: NodeBounds): Boolean {
        val n = seen.node
        if (seen.inFeed || seen.inField || isField(n) || !n.isClickable) return false
        if (labelsOf(n).none { plain(it.value) in profile.searchButtonLabels }) return false
        val b = n.bounds
        return !b.isEmpty && b.top < header.bottom && b.centerY >= header.top - header.height && b.centerY <= header.bottom + header.height
    }

    /**
     * Con el buscador abierto (tras la lupa): espera su campo, mira antes las filas que ya salen («Chats recientes»: así
     * valen los nombres de emojis) y, si no está, escribe el nombre. Vale la fila cuyo **nombre visible** es exactamente
     * ese (nunca por el usuario de debajo: [searchRows]); un grupo («Ana, Bea, Carlos») nunca vale por llevar a una persona
     * en su nombre.
     */
    private fun ownLookInSearch(dest: Dest, screen: Screen, now: Long): Step {
        val notFound = when (phase) {
            Phase.RESULT -> "el buscador no lo encuentra"
            Phase.SEARCH -> "no se abre el buscador"
            else -> "no encuentro el nombre"
        }
        if (finder != null) {
            // Recién pulsada la lupa, primero su campo: dice que ya está la otra pantalla (y prueba el id aprendido).
            val field = if (phase == Phase.SEARCH) ownSearchField(screen) else null
            if (phase != Phase.SEARCH || field != null) {
                val rows = searchRows(dest, screen.sheetOnlyText(ownQuery(dest)), screen)
                if (rows.isNotEmpty()) {
                    fastHits++
                    return onOwnTargets(dest, rows, screen, now, How.FAST, Found.SEARCH)
                }
                if (dest.key !in typed) {
                    (field ?: ownSearchField(screen))?.let { f ->
                        fastHits++
                        return ownType(dest, f, now, How.FAST)
                    }
                }
            }
            if (!walkAllowed(now)) {
                waitWhy = if (phase == Phase.RESULT) "esperando al buscador" else "esperando el campo del buscador"
                return destTimeoutOr(dest, screen, now, notFound)
            }
        }
        if (!walkAllowed(now)) return paused(now).let { destTimeoutOr(dest, screen, now, notFound) }
        val sheet = ownSheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
        // Con el título de la hoja de contactos a la vista (tv_title), el buscador no se ha abierto y el único campo es el del
        // mensaje: nunca es el buscador. Segunda pasada de la revisión (1.10.0): en fr/it/pt/de, con la lupa sin abrir el buscador
        // en 1 s, el nombre del siguiente destino se escribía en «Écrire un message…», se enviaba como mensaje y se aprendía ese
        // campo como buscador. Así el destino falla en SEARCH y el siguiente vuelve a buscarse en la fila.
        val contacts = sheet.any { s -> !s.inFeed && shortViewId(s.node.viewId).let { it.isNotEmpty() && it in profile.sheetIds } }
        val field = if (contacts) null else walkSearchField(sheet)
        if (field != null) {
            val rows = searchRows(dest, sheet, screen)
            if (rows.isNotEmpty()) return onOwnTargets(dest, rows, screen, now, How.WALK, Found.SEARCH)
            if (dest.key !in typed) return ownType(dest, field, now, How.WALK)
        }
        waitWhy = if (phase == Phase.RESULT) "esperando al buscador" else "esperando el campo del buscador"
        return destTimeoutOr(dest, screen, now, notFound)
    }

    /**
     * Lo que se pide al atajo de texto por [dest] en la hoja. Android busca «contiene» en el texto crudo, y TikTok pinta los
     * grupos con marcas de aislamiento (U+2068/U+2069) alrededor de cada miembro: pedido entero, un grupo no sale nunca. Se pide
     * su primer miembro (ese sí va seguido) y lo que vuelve se sigue filtrando por el nombre **exacto** ([ownCells],
     * [searchRows]): una persona que se llame como el miembro no vale (revisión de la 1.10.0).
     */
    private fun ownQuery(dest: Dest): String {
        val name = dest.target.name.filterNot { it in BIDI_MARKS }
        return name.substringBefore(", ").trim().ifEmpty { name.trim() }.ifEmpty { dest.target.name }
    }

    /** Escribe el nombre de [dest] en el buscador. Si no se miró la fila por él (el buscador ya estaba abierto), no se aprende de esto. */
    private fun ownType(dest: Dest, field: UiNode, now: Long, how: How): Step {
        val hidden = dest.gridHidden || !dest.searched
        val why = if (phase == Phase.SEARCH) "no está en los recientes" else "con el buscador ya abierto"
        return typeInSearch(dest, field, now, how, why).also { dest.gridHidden = dest.gridHidden || hidden }
    }

    /**
     * El campo del buscador de la hoja: por el id aprendido (ofuscado: cambia con cada versión) o, si no, el `EditText` junto
     * a la lupa de su cabecera (la búsqueda de texto no ve la pista «Buscar» del campo vacío, pero sí la lupa). Nunca el de
     * «Escribe un mensaje…».
     *
     * Por el id aprendido, el campo solo vale si tiene **la lupa al lado** ([besideSearchButton]), como por la lupa. Verificación
     * final de la 1.10.0: un id ofuscado de otra versión de TikTok que hoy cayera en un campo del mensaje (`pja` en la hoja de
     * contactos, `h84` en el buscador con alguien marcado), en un idioma cuya pista no está en [MESSAGE_HINTS], habría escrito
     * ahí el nombre del siguiente destino (y se habría enviado como mensaje). En los volcados de la 47.7, el buscador (`vlq`)
     * siempre va junto a la lupa «Buscar» y los campos del mensaje no tienen hermanos. Sin consultas de más.
     *
     * 1.10.1: al atajo de texto se le pide la etiqueta de la lupa pulsada ([lupaWord], la de su idioma) y, de respaldo, las
     * palabras en español e inglés (no todas: en un idioma sin palabras serían dos docenas de consultas por tick).
     *
     * 1.10.1, **la ventana del buscador**: tras la lupa, TikTok abre el buscador en **otra ventana** (otro diálogo) que la de
     * la hoja de contactos ([PlatformProfile]: en los volcados, la ventana de la hoja tiene un nivel más sobre su contenedor que
     * la del buscador). Con [sheetWindow] aún en la de la hoja, el atajo no veía el campo y se esperaba al recorrido (~1 s,
     * medido en el móvil). Si en la ventana de la hoja no está, se busca igual (el id aprendido o la lupa con el campo al lado)
     * en las demás ventanas de la app que no son el visor, y solo vale si esa ventana es una hoja de abajo (lleva
     * [PlatformProfile.openSheetIds], `touch_outside`): entonces pasa a ser la de la hoja. Así sigue sin mirarse nada fuera de
     * las ventanas de la hoja.
     */
    private fun ownSearchField(screen: Screen): UiNode? {
        ownFieldSought = true
        searchFieldIn(screen::sheetOnlyId, screen::sheetOnlyText)?.let { return accept(it) }
        if (!searchOpen) return null
        val old = sheetWindow
        val moved = searchFieldIn(
            { id -> screen.awayId(id, also = old).filter { it.window != old } },
            { q -> screen.awayText(q, also = old).filter { it.window != old } },
        ) ?: return null
        if (fullIds(profile.openSheetIds).none { id -> screen.windowOnlyId(moved.seen.window, id).any { !it.inFeed } }) return null
        sheetWindow = moved.seen.window
        return accept(moved)
    }

    /** Un campo del buscador encontrado por [searchFieldIn] y por qué id (el aprendido que se probó, o `null`). */
    private class SearchHit(val seen: Seen, val byLearned: String?)

    /**
     * El campo ya vale (en la ventana de la hoja o en una que es hoja de abajo): solo ahora se aprende su id (revisión de la
     * 1.10.1: antes se aprendía también el de una ventana que luego no valía).
     */
    private fun accept(hit: SearchHit): UiNode {
        val learned = hit.byLearned
        if (learned != null) {
            if (learned == hints.searchId) sawLearnedSearch = true
            learnedSearchId = learned
        } else {
            hit.seen.node.viewId?.takeIf { it.isNotEmpty() }?.let { learnedSearchId = it }
        }
        return hit.seen.node
    }

    /**
     * El campo del buscador con [byId] (el id aprendido) o [byText] (la lupa y su hermano), con las reglas de [ownSearchField].
     * Siempre con la lupa al lado ([besideSearchButton]), también si lo devuelto ya es un campo (revisión de la 1.10.1: con la
     * etiqueta de la lupa en cualquier idioma, un campo del mensaje cuya pista la contuviera pasaba solo por [MESSAGE_HINTS]).
     * No aprende nada: eso, [accept].
     */
    private fun searchFieldIn(byId: (String) -> List<Seen>, byText: (String) -> List<Seen>): SearchHit? {
        val learned = learnedSearchId ?: hints.searchId
        if (learned != null) {
            byId(learned).firstOrNull { !it.inFeed && isOwnSearchField(it.node) && besideSearchButton(it.node) }?.let { return SearchHit(it, learned) }
        }
        for (q in searchQueries()) {
            val hits = byText(q).filter { !it.inFeed }
            if (hits.isEmpty()) continue
            val field = hits.firstOrNull { isOwnSearchField(it.node) && besideSearchButton(it.node) }
                ?: hits.firstNotNullOfOrNull { s ->
                    if (isField(s.node)) return@firstNotNullOfOrNull null
                    s.node.parent?.children?.firstOrNull { isOwnSearchField(it) && besideSearchButton(it) }
                        ?.let { Seen(it, inFeed = false, inField = true, window = s.window, real = s.real) }
                }
            // La lupa salió (en esta lengua): no se pregunta en la otra.
            return field?.let { SearchHit(it, null) }
        }
        return null
    }

    /** Lo que se pide al atajo para encontrar la lupa del buscador: la etiqueta de la pulsada y el español y el inglés. */
    private fun searchQueries(): List<String> =
        (listOfNotNull(lupaWord) + profile.searchButtonWords.take(PRIMARY_WORDS)).distinctBy { plain(it) }

    /** Recorriendo la hoja: el campo de más arriba que no es el del mensaje. */
    private fun walkSearchField(sheet: List<Seen>): UiNode? {
        ownFieldSought = true
        val field = sheet.filter { !it.inFeed && isOwnSearchField(it.node) }.minByOrNull { it.node.bounds.top }?.node
        field?.viewId?.takeIf { it.isNotEmpty() }?.let { learnedSearchId = it }
        return field
    }

    /**
     * ¿Tiene el campo la lupa al lado (un hermano, que no es un campo, con la etiqueta «Buscar», «Search»… o la de la lupa
     * pulsada en este envío, en su idioma)?
     */
    private fun besideSearchButton(node: UiNode): Boolean {
        val pressed = lupaWord?.let(::plain)
        return node.parent?.children.orEmpty().any { c ->
            !isField(c) && labelsOf(c).any { plain(it.value).let { l -> l in profile.searchButtonLabels || l == pressed } }
        }
    }

    private fun isOwnSearchField(node: UiNode): Boolean =
        isField(node) && node.isVisible && labelsOf(node).none { l -> plain(l.value).let { p -> MESSAGE_HINTS.any { p.startsWith(it) } } }

    /**
     * Filas del buscador con el nombre de [dest]: una fila pulsable de ancho entero y con su `CheckBox` cuyo **nombre
     * visible** (su primer texto) es, limpio, **exactamente** el nombre. El usuario de debajo nunca vale por sí solo: los
     * fijados son nombres visibles («A quién envías» apunta la celda o el primer texto de la fila) y el usuario de otra
     * persona puede ser justo ese nombre (revisión de la 1.10.0). Una por fila; más de una = repetido. Nunca un emoji de
     * reacción (su celda no es una fila) ni un grupo que lo lleve en su nombre.
     */
    private fun searchRows(dest: Dest, candidates: List<Seen>, screen: Screen): List<Pick> {
        val out = ArrayList<Pick>()
        for (s in candidates) {
            if (s.inFeed || s.inField) continue
            val t = s.node.text ?: continue
            if (ownName(t) != dest.key) continue
            val row = searchRowOf(s.node, screen) ?: continue
            // Por el nombre y no por el nodo: en el móvil, el del atajo y el leído bajando por la fila son objetos distintos.
            val shown = below(row, config.rowMaxNodes, config.rowMaxDepth).firstOrNull { !it.text.isNullOrBlank() }?.text ?: continue
            if (ownName(shown) != dest.key) continue
            if (out.none { overlaps(it.click.bounds, row.bounds) }) out += Pick(row, s.node)
        }
        return out
    }

    /** La fila del buscador de [node]: su primer antepasado pulsable, si es de ancho entero, bajo y con `CheckBox`. */
    private fun searchRowOf(node: UiNode, screen: Screen): UiNode? {
        var current = node.parent
        repeat(config.maxRowAncestors) {
            val n = current ?: return null
            if (n.bounds.height > screen.h * config.maxRowHeight) return null
            if (n.isClickable) {
                val ok = n.bounds.width >= screen.w * SEARCH_ROW_WIDTH && below(n, config.rowMaxNodes, config.rowMaxDepth).any(::isCheckBox)
                return n.takeIf { ok }
            }
            current = n.parent
        }
        return null
    }

    /**
     * Celdas de la fila de contactos con el nombre de [dest]: pulsables, con la **descripción** exactamente el nombre, en la
     * primera fila debajo del título (o antes de la primera línea divisoria) y fuera de una cuadrícula (los emojis de
     * reacción). Una por celda: dos celdas con el nombre son dos personas (no se pulsa ninguna).
     */
    private fun ownCells(dest: Dest, candidates: List<Seen>, header: NodeBounds?, divider: NodeBounds?): List<Pick> {
        if (header == null) return emptyList()
        val found = ArrayList<Pick>()
        for (s in candidates) {
            val n = s.node
            if (s.inFeed || s.inField || !n.isClickable) continue
            val d = n.contentDescription ?: continue
            if (ownName(d) != dest.key || !inContactRow(n.bounds, header, divider) || inGrid(n)) continue
            if (found.none { it.click.bounds == n.bounds }) found += Pick(n, n)
        }
        return found
    }

    /** El nombre limpio (normalizado) de una celda o fila; `null` si no es un nombre o es una opción de la hoja. */
    private fun ownName(raw: CharSequence): String? =
        recipients.clean(raw.toString(), fromText = true, symbolNames = profile.symbolNames)
            ?.let(RecipientDetector::normalize)
            ?.takeUnless { it in profile.sheetOptions }

    private fun inContactRow(b: NodeBounds, header: NodeBounds, divider: NodeBounds?): Boolean {
        if (b.isEmpty || b.top < header.bottom) return false
        return if (divider != null) b.top < divider.top && b.bottom <= divider.top + 2 else b.top - header.bottom <= b.height * FIRST_ROW_GAP
    }

    private fun inGrid(node: UiNode): Boolean {
        var p = node.parent
        repeat(2) {
            val n = p ?: return false
            if (n.className?.contains("GridView") == true) return true
            p = n.parent
        }
        return false
    }

    private fun isCellLike(node: UiNode): Boolean = node.isClickable && node.contentDescription?.let { ownName(it) } != null

    private fun isCheckBox(node: UiNode): Boolean = node.className?.contains("CheckBox") == true

    private fun isDivider(node: UiNode, screenWidth: Int): Boolean =
        node.bounds.height in 1..4 && node.bounds.width >= screenWidth * 0.9f && labelsOf(node).isEmpty()

    /**
     * ¿Ha cargado la fila de contactos? Desde el título: su fila (la cabecera), la columna de la hoja y, tras la cabecera
     * y hasta la primera línea divisoria, alguna celda (la devuelve: su padre es la fila, [scrollRowBack]). Unas pocas
     * llamadas, solo si el nombre no salió.
     */
    private fun probeRow(title: UiNode, screenWidth: Int): UiNode? {
        val headerRow = title.parent ?: return null
        val column = headerRow.parent ?: return null
        var after = false
        for (child in column.children) {
            if (!after) {
                if (child === headerRow || child.bounds == headerRow.bounds) after = true
                continue
            }
            if (isDivider(child, screenWidth)) break
            below(child, config.rowMaxNodes, config.rowMaxDepth).firstOrNull { isCellLike(it) && inContactRow(it.bounds, title.bounds, null) }
                ?.let { return it }
        }
        return null
    }

    /**
     * 1.10.1: el fijado no está en la fila de contactos y la fila **no empieza en su primer elemento** (se puede desplazar
     * hacia atrás: TikTok la abre a veces desplazada, «idx 2→8 de 61», y los primeros, los de siempre, no se ven). Se vuelve al
     * principio (`ACTION_SCROLL_BACKWARD`, [UiNode.scrollBackward]) y se mira otra vez antes de ir a la lupa. Una sola vez por
     * envío. Solo la fila de la hoja: el padre de una celda de contacto, en la ventana de la hoja y de la altura de una fila
     * (nunca el visor, que pasaría de vídeo). `null` si no hay que desplazar (o Android no quiere): se sigue como siempre.
     */
    private fun scrollRowBack(cell: UiNode?, screen: Screen, now: Long): Step? {
        if (rowScrolled || cell == null) return null
        val row = cell.parent ?: return null
        val window = sheetWindow
        if (window != null && row.windowId != UiNode.NO_WINDOW && row.windowId != window) return null
        if (row.bounds.height > screen.h * config.maxRowHeight || isFeedNode(row) || !row.canScrollBackward) return null
        rowScrolled = true
        val start = clock?.invoke()
        val ok = row.scrollBackward()
        val performMs = start?.let { s -> clock?.invoke()?.minus(s) } ?: -1L
        actions += ActionTime("vuelve la fila al principio", (now - startedAt).coerceAtLeast(0L), performMs, ok)
        if (!ok) return null
        rowScrolledAt = now
        return wait("fila vuelta al principio")
    }

    /** La fila se acaba de volver al principio y aún se está moviendo: se mira el nombre otra vez antes de ir a la lupa. */
    private fun rowSettling(now: Long): Boolean = rowScrolledAt?.let { now - it in 0 until ROW_SCROLL_SETTLE_MS } == true

    /**
     * La hoja recorrida: la ventana (fuera del visor) con su título o sus marcas; la ya conocida, si sigue.
     *
     * 1.10.1: con la lupa ya pulsada, el buscador va en otra ventana ([ownSearchField]) y, en un idioma sin palabras, no lleva
     * ninguna marca que se reconozca: valen también las ventanas que son una hoja de abajo ([PlatformProfile.openSheetIds],
     * `touch_outside`), y va primero la que tiene un campo y no el título de la hoja de contactos (el buscador), aunque la de la
     * hoja siga debajo. Sin buscador en ninguna, como siempre (la de la hoja: con su título a la vista no hay buscador).
     */
    private fun ownSheetOf(windows: List<List<Seen>>): List<Seen>? {
        val fw = feedWindow
        val marked = windows.filter { w ->
            w.firstOrNull()?.window.let { it == null || it != fw } &&
                w.any { !it.inFeed && (isOwnSheetMark(it.node) || (searchOpen && isOpenSheetId(it.node))) }
        }
        val search = if (searchOpen) {
            marked.firstOrNull { w -> w.none { !it.inFeed && isTitleId(it.node) } && w.any { !it.inFeed && isOwnSearchField(it.node) } }
        } else {
            null
        }
        val sheet = search ?: marked.firstOrNull { it.firstOrNull()?.window == sheetWindow } ?: marked.firstOrNull()
        sheet?.firstOrNull()?.let { sheetWindow = it.window }
        return sheet
    }

    private fun isTitleId(node: UiNode): Boolean = shortViewId(node.viewId).let { it.isNotEmpty() && it in profile.sheetIds }

    private fun isOpenSheetId(node: UiNode): Boolean = shortViewId(node.viewId).let { it.isNotEmpty() && it in profile.openSheetIds }

    /**
     * Marca de la hoja: su id de título, una de sus opciones o el título en cualquiera de sus idiomas
     * ([PlatformProfile.titleLabels], como [walkHeader]). En el buscador el título lleva otro id (`title`) y, fuera del
     * español y el inglés, «Envoyer à» no es una opción: sin esto, el recorrido daba la hoja por cerrada (revisión 1.10.0).
     */
    private fun isOwnSheetMark(node: UiNode): Boolean =
        shortViewId(node.viewId).let { it.isNotEmpty() && it in profile.sheetIds } || hasMarker(node) ||
            labelsOf(node).any { plain(it.value) in profile.titleLabels }

    private fun isOwnSheetOpenMark(node: UiNode): Boolean =
        isOwnSheetMark(node) || shortViewId(node.viewId).let { it.isNotEmpty() && it in profile.openSheetIds }

    private fun walkHeader(sheet: List<Seen>): NodeBounds? = walkTitle(sheet)?.bounds

    /** Recorriendo la hoja: su título (por su id o por su texto en cualquiera de sus idiomas). */
    private fun walkTitle(sheet: List<Seen>): UiNode? = sheet.firstOrNull { s ->
        !s.inFeed && (
            shortViewId(s.node.viewId).let { it.isNotEmpty() && it in profile.sheetIds } ||
                labelsOf(s.node).any { plain(it.value) in profile.titleLabels }
            )
    }?.node

    private fun walkDivider(sheet: List<Seen>, header: NodeBounds, screenWidth: Int): NodeBounds? =
        sheet.firstOrNull { !it.inFeed && it.node.bounds.top >= header.bottom && isDivider(it.node, screenWidth) }?.node?.bounds

    /**
     * Pulsa la celda o la fila de [dest]. Si ya está marcada (su `CheckBox`; el `selected` de las celdas recicladas
     * miente), no se pulsa: lo desmarcaría. La foto, de dentro de la celda (o de la fila del buscador).
     */
    private fun onOwnTargets(dest: Dest, picks: List<Pick>, screen: Screen, now: Long, how: How, where: Found): Step {
        if (picks.size > 1) return failDest(dest, "nombre repetido en la hoja", screen, now, how)
        val pick = picks.first()
        // Con la fila aún moviéndose tras volverla al principio: la celda, en el mismo sitio en dos lecturas seguidas.
        if (where == Found.GRID && rowSettling(now)) {
            val seen = dest.key to pick.click.bounds
            if (settlingCell != seen) {
                settlingCell = seen
                waitWhy = "fila moviéndose"
                return destTimeoutOr(dest, screen, now, "no encuentro el nombre")
            }
        }
        val inside = below(pick.click, config.rowMaxNodes, config.rowMaxDepth)
        val marked = profile.selectedByCheckbox && inside.any { isCheckBox(it) && it.isChecked }
        // Con la fila aún moviéndose (recién vuelta al principio), la foto saldría corrida: hoy no se recorta.
        val photo = if (dest.target.wantAvatar && !marked && !rowSettling(now)) {
            if (where == Found.GRID) cellPhoto(inside) else AvatarRules.photo(pick.label, dest.target.name, screen.h, config.maxRowAncestors, config.maxRowHeight)
        } else {
            null
        }
        snapshot(dest)
        dest.foundIn = where
        dest.row = pick.click.bounds
        dest.status = Status.SELECTED
        val more = current() != null
        enter(if (more) Phase.TARGET else Phase.SEND, now, how)
        if (marked) {
            // Nada que pulsar: se sigue en este mismo tick.
            before = null
            return if (more) ownLookForTarget(screen, now) else ownSendStep(screen, now)
        }
        val avatar = photo?.bounds
        return Step.Click(
            pick.click, if (where == Found.SEARCH) "nombre del buscador" else "nombre",
            avatar = avatar, avatarName = dest.target.name.takeIf { avatar != null }, avatarGroup = photo?.group == true,
        )
    }

    /** La foto de una celda: la imagen casi cuadrada más grande de dentro. */
    private fun cellPhoto(nodes: List<UiNode>): AvatarRules.Photo? =
        nodes.filter { it.isVisible && it.className?.contains("Image") == true && squarish(it.bounds) }
            .maxByOrNull { it.bounds.width.toLong() * it.bounds.height }
            ?.let { AvatarRules.Photo(it.bounds) }

    private fun squarish(b: NodeBounds): Boolean =
        !b.isEmpty && minOf(b.width, b.height) >= MIN_PHOTO_PX && maxOf(b.width, b.height) * 4 <= minOf(b.width, b.height) * 5

    /**
     * «Enviar» de la hoja en su propia ventana: el botón exacto ([PlatformProfile.sendLabels]) o el de su id (`tv_send`,
     * en el buscador), pulsable, ancho y en la mitad de abajo. Nunca «Enviar a» (el título) ni «Crear grupo».
     *
     * 1.10.1, en cualquier idioma: si ni el id ni las palabras lo dan, **por su sitio** ([siteSend]): desde el título, la
     * columna de la hoja y, en ella, el único botón pulsable y ancho de debajo del campo del mensaje, con alguien marcado. Al
     * atajo de texto solo se le piden el español, el inglés y las palabras que casan con el título de la hoja («Envoyer à» →
     * «Envoyer»; en turco o coreano el título es la propia palabra), no las dos docenas en cada tick.
     */
    private fun ownSendStep(screen: Screen, now: Long): Step {
        if (finder != null) {
            val byId = { fullIds(profile.sendIds).flatMap { screen.sheetOnlyId(it) }.filter { isOwnSend(it, screen) } }
            fun byWords(words: List<String>) =
                words.firstNotNullOfOrNull { q -> screen.sheetOnlyText(q).filter { isOwnSend(it, screen) }.ifEmpty { null } }.orEmpty()
            val byText = {
                byWords(profile.sendWords.take(PRIMARY_WORDS)).ifEmpty { if (searchOpen) emptyList() else byWords(titleSendWords(screen)) }
            }
            // En el buscador lleva `tv_send`; en la hoja, su id cambia con cada versión: por sus palabras.
            val all = if (searchOpen) byId().ifEmpty { byText() } else byText().ifEmpty { byId() }
            all.maxByOrNull { it.node.bounds.top }?.let { send ->
                fastHits++
                return clickSend(send.node, fast = true, now)
            }
            // Por su sitio, desde el título de la hoja de contactos (en el buscador, `tv_send` o el recorrido).
            val site = if (searchOpen) null else ownHeader(screen)?.node?.let { siteSendNear(it, screen) }
            if (site != null) {
                fastHits++
                return clickSend(site, fast = true, now)
            }
        }
        if (!walkAllowed(now)) return paused(now).let { timeoutOr(now, "no encuentro enviar") }
        val sheet = ownSheetOf(screen.outside) ?: return finish(false, "la hoja se ha cerrado", now)
        val visible = sheet.filter { !it.inFeed }
        val send = visible.filter { isOwnSend(it, screen) }.maxByOrNull { it.node.bounds.top }?.node ?: siteSend(visible.map { it.node }, screen)
        return send?.let { clickSend(it, fast = false, now) } ?: timeoutOr(now, "no encuentro enviar")
    }

    /**
     * Las palabras de «Enviar» (fuera del español y el inglés) que casan con el título de la hoja (normalizado, empieza por
     * ellas): el idioma de la hoja dice cuál pedir. Una consulta de más (el título), solo si las primeras no salieron.
     */
    private fun titleSendWords(screen: Screen): List<String> {
        val title = ownHeader(screen)?.node?.text?.toString()?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return profile.sendWords.drop(PRIMARY_WORDS).filter { w -> plain(w).let { it.isNotEmpty() && title.startsWith(it) } }
    }

    /** [siteSend] en la columna de la hoja: el antepasado del título que llega hasta abajo (como mucho unos niveles). */
    private fun siteSendNear(title: UiNode, screen: Screen): UiNode? {
        var column = title.parent
        repeat(SITE_MAX_ANCESTORS) {
            val c = column ?: return null
            if (c.bounds.bottom >= screen.h * SITE_COLUMN_BOTTOM && c.bounds.width >= screen.w * SITE_COLUMN_WIDTH) {
                return siteSend(below(c, SITE_MAX_NODES, SITE_MAX_DEPTH), screen)
            }
            column = c.parent
        }
        return null
    }

    /**
     * 1.10.1, «Enviar» por su **sitio**, en cualquier idioma (TikTok 47.7: el `Button` de abajo, en la hoja con id ofuscado y
     * en el buscador `tv_send`). De [nodes] (los de la hoja): con alguien marcado (un `CheckBox` marcado), el campo del mensaje
     * (el de más abajo) y, debajo de él, el **único** nodo pulsable, ancho (como «Enviar») y en la mitad de abajo que no es un
     * campo, ni un emoji de reacción (la cuadrícula), ni el título, ni «Crear grupo». Dos o más: no está claro y no se pulsa
     * ninguno. Los contenedores que llevan el campo dentro empiezan por encima de él y no cuentan; las opciones (repostear…) y
     * los contactos van por encima del campo.
     */
    private fun siteSend(nodes: List<UiNode>, screen: Screen): UiNode? {
        if (nodes.none { isCheckBox(it) && it.isChecked }) return null
        val message = nodes.filter { isField(it) && it.isVisible && !it.bounds.isEmpty }.maxByOrNull { it.bounds.top } ?: return null
        val grids = nodes.filter { it.className?.contains("GridView") == true }.map { it.bounds }
        val candidates = nodes.filter { n ->
            val b = n.bounds
            n.isClickable && n.isVisible && !isField(n) && !b.isEmpty && b.top >= message.bounds.bottom && b != shareBounds &&
                grids.none { g -> b.left >= g.left && b.right <= g.right && b.top >= g.top && b.bottom <= g.bottom } && !inGrid(n) &&
                !isTitleId(n) && labelsOf(n).none { l -> plain(l.value).let { it in profile.titleLabels || RecipientDetector.CREATE_GROUP.matches(it) } } &&
                sendButtonWidth(n) >= screen.w * config.wideSend && b.centerY >= screen.h / 2
        }.distinctBy { it.bounds }
        return candidates.singleOrNull()
    }

    private fun isOwnSend(seen: Seen, screen: Screen): Boolean {
        val n = seen.node
        if (seen.inFeed || seen.inField || isField(n) || !n.isClickable || n.bounds == shareBounds || isTitleId(n)) return false
        val labels = labelsOf(n).map { plain(it.value) }
        val byId = shortViewId(n.viewId).let { it.isNotEmpty() && it in profile.sendIds }
        if (!byId && labels.none { it in profile.sendLabels }) return false
        if (labels.any { RecipientDetector.CREATE_GROUP.matches(it) }) return false
        return sendButtonWidth(n) >= screen.w * config.wideSend && n.bounds.centerY >= screen.h / 2
    }

    /**
     * Hoja en su propia ventana: enviado cuando su ventana desaparece (TikTok la cierra al momento; sin consultas) o, si
     * sigue, cuando el «Enviar» pulsado ya no está.
     */
    private fun ownConfirmSent(screen: Screen, now: Long): Step {
        if (screen.sheetRoot() == null) return finish(true, "enviado", now, How.FAST)
        // El «Enviar» pulsado (en el mismo sitio, pulsable): por su id y por su propia etiqueta (1.10.1: no por todas las
        // palabras, que serían dos docenas de consultas; y uno encontrado por su sitio puede no llevar ninguna conocida).
        fun isSent(seen: Seen) = !seen.inFeed && seen.node.bounds == sentBounds && seen.node.isClickable && !isField(seen.node)
        if (finder != null && (sentWord != null || sentById)) {
            val stillThere = (fullIds(profile.sendIds).flatMap { screen.sheetOnlyId(it) } + sentWord?.let { screen.sheetOnlyText(it) }.orEmpty())
                .any(::isSent)
            if (!stillThere) return finish(true, "enviado", now, How.FAST)
        } else if (finder != null) {
            // Revisión de la 1.10.1: un «Enviar» encontrado por su sitio, sin etiqueta ni id conocido, no se puede pedir al atajo
            // (antes «no está» = «enviado» en el primer tick). ¿Sigue en su sitio? Si no se sabe, el recorrido mira sus límites.
            val again = if (searchOpen) null else ownHeader(screen)?.node?.let { siteSendNear(it, screen) }
            if (again?.bounds != sentBounds && walkAllowed(now)) {
                val sheet = ownSheetOf(screen.outside) ?: return finish(true, "enviado", now, How.WALK)
                if (sheet.none(::isSent)) return finish(true, "enviado", now, How.WALK)
            }
        } else if (walkAllowed(now)) {
            val sheet = ownSheetOf(screen.outside) ?: return finish(true, "enviado", now, How.WALK)
            if (sheet.none(::isSent)) return finish(true, "enviado", now, How.WALK)
        }
        return if (now - phaseSince > config.confirmMs) finish(false, "no se confirma el envío", now) else wait("sin confirmar")
    }

    // ── Atajos ───────────────────────────────────────────────────────────────────────────────────

    /** El botón compartir del reel visible: por el id aprendido o por su etiqueta. Siempre dentro del visor. */
    private fun fastShare(screen: Screen): UiNode? = fastShareSeen(screen)?.node

    private fun fastShareSeen(screen: Screen): Seen? {
        fun valid(seen: Seen): Boolean {
            val b = seen.node.bounds
            return seen.inFeed && !seen.inField && !b.isEmpty && b.left >= 0 && b.top >= 0 && b.right <= screen.w &&
                b.bottom <= screen.h && shareDetector.evaluate(seen.node, screen.w, screen.h).isShare
        }
        // Por id solo donde no es el de otro botón (en TikTok, «Compartir» y «Me gusta» comparten id).
        if (profile.learnShareId) hints.shareId?.let { id -> screen.id(id).firstOrNull(::valid)?.let { return it } }
        for (q in SHARE_QUERIES) screen.text(q).firstOrNull(::valid)?.let { return it }
        return null
    }

    /** Alguna opción que solo sale en la hoja de compartir («Copiar enlace», «Escribe un mensaje»…), fuera del visor. */
    private fun fastSheetMarker(screen: Screen): Seen? =
        profile.markerQueries.firstNotNullOfOrNull { q -> screen.text(q).firstOrNull { !it.inFeed && hasMarker(it.node) } }

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
        val known = knownSearchId ?: return learned
        if (learned.isNotEmpty() || hints.searchId == known) return learned
        return screen.id(known).filter(::ok)
    }

    /** El id de hoy del buscador de la hoja ([PlatformProfile.searchFieldIds]; en Instagram, `search_edit_text`). */
    private val knownSearchId: String? get() = profile.searchFieldIds.firstOrNull()?.let { fullIds(listOf(it)).first() }

    /**
     * La hoja de hoy ya se vio en un envío anterior: el buscador aprendido es [knownSearchId]. Entonces sus ids
     * bastan y no hacen falta las búsquedas de texto de las opciones ni el recorrido (1.4.3).
     */
    private val knownLayout: Boolean get() = hints.searchId != null && hints.searchId == knownSearchId

    /**
     * ¿Hay una hoja abierta? Por los ids de hoy (tres búsquedas): el buscador fuera del visor, diga lo que diga (con un
     * nombre escrito de un envío que falló también cuenta), el contenedor de la hoja de compartir o el de cualquier
     * hoja de abajo de Instagram (comentarios, más opciones…): nunca se pulsa «Compartir» detrás de una.
     */
    private fun fastSheetOpenById(screen: Screen): Boolean =
        (hints.searchId ?: knownSearchId)?.let { id -> screen.id(id).any { !it.inFeed && isField(it.node) } }.also { if (it == true) sawLearnedSearch = true } == true ||
            fullIds(profile.openSheetIds).any { id -> screen.id(id).any { !it.inFeed } }

    /** La hoja por sus ids de hoy (contenedor o cuadrícula), fuera del visor: una llamada, sin recorrer. */
    private fun fastSheetContainer(screen: Screen): Seen? =
        fullIds(profile.sheetIds).firstNotNullOfOrNull { id -> screen.id(id).firstOrNull { !it.inFeed } }

    /** El id de hoy de una celda de la cuadrícula ([PlatformProfile.gridCellIds]). */
    private val knownGridCellId: String? get() = profile.gridCellIds.firstOrNull()?.let { fullIds(listOf(it)).first() }

    /**
     * La cuadrícula de la hoja ya ha cargado (1.4.2): se ve alguna celda (`direct_share_sheet_grid_view_pog`, que
     * Instagram describe «Nombre Chat sin seleccionar», sin «Foto del perfil de…»), dentro de la ventana de la hoja.
     */
    private fun fastGridLoaded(screen: Screen): Boolean {
        val id = knownGridCellId ?: return false
        return screen.id(id).any { !it.inFeed && (sheetWindow == null || it.window == sheetWindow) }
    }

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

    private fun findShare(windows: List<List<Seen>>, w: Int, h: Int): UiNode? = findShareSeen(windows, w, h)?.node

    private fun findShareSeen(windows: List<List<Seen>>, w: Int, h: Int): Seen? {
        for (window in windows) {
            for (seen in window) {
                val node = seen.node
                val b = node.bounds
                if (seen.inField || b.isEmpty || b.left < 0 || b.top < 0 || b.right > w || b.bottom > h) continue
                if (node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank() && node.viewId.isNullOrEmpty()) continue
                // Hoja en su propia ventana: «Compartir» solo dentro del visor (en la hoja, «Compartir» es repostear).
                if (own && !seen.inFeed) continue
                if (shareDetector.evaluate(node, w, h).isShare) return seen
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

    private fun hasMarker(node: UiNode): Boolean = labelsOf(node).any { plain(it.value) in profile.sheetMarkers }

    /**
     * La cuadrícula ya ha cargado: se ve alguna celda fuera del visor, por su id de hoy (1.4.2) o por una foto de
     * perfil («Foto del perfil de…», diseños anteriores).
     */
    private fun gridLoaded(sheet: List<Seen>): Boolean = sheet.any { seen ->
        !seen.inFeed && !seen.inField && (
            (seen.node.viewId != null && seen.node.viewId == knownGridCellId) ||
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
        val name = recipients.clean(raw.toString(), fromText, symbolNames = profile.symbolNames) ?: return false
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
        if (field.viewId != null && field.viewId == knownSearchId) return true
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
        isSendId(node) || labelsOf(node).any { RecipientDetector.SEND.matches(RecipientDetector.normalize(it.value)) }

    /** ¿Es el «Enviar» de abajo por su id ([PlatformProfile.sendIds])? Nunca «Crear grupo», lleve el id que lleve. */
    private fun isSendId(node: UiNode): Boolean {
        if (profile.platform == PlatformProfile.INSTAGRAM.platform) return RecipientDetector.isSendId(node)
        val id = shortViewId(node.viewId)
        return id.isNotEmpty() && id in profile.sendIds &&
            (listOf(node) + node.children.take(RecipientDetector.MAX_OWN_CHILDREN))
                .flatMap { listOfNotNull(it.text, it.contentDescription) }
                .none { RecipientDetector.CREATE_GROUP.matches(RecipientDetector.normalize(it.toString())) }
    }

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
                ?.takeUnless { plain(it) in profile.sheetMarkers }
                ?.let { recipients.clean(it, fromText = true, symbolNames = profile.symbolNames) }
                ?.let(RecipientDetector::normalize)
        }

    // ── Árbol ────────────────────────────────────────────────────────────────────────────────────

    /** Lo que se mira en un tick: los recorridos solo si hacen falta (perezosos) y los atajos. */
    private inner class Screen(val roots: List<UiNode>, val w: Int, val h: Int, val now: Long) {
        /** Recorrido entero, visor incluido: solo para buscar «Compartir» si el atajo no lo ve. */
        val windows: List<List<Seen>> by lazy {
            countWalk(now)
            timed({ walkMs += it }) { roots.mapIndexed { i, root -> snapshot(root, w, h, keyOf(i), pruneFeed = false, real = root.windowId != UiNode.NO_WINDOW) } }
        }

        /** Recorrido sin entrar en el visor (la hoja nunca está dentro): para todo lo demás. */
        val outside: List<List<Seen>> by lazy {
            countWalk(now)
            timed({ walkMs += it }) { roots.mapIndexed { i, root -> snapshot(root, w, h, keyOf(i), pruneFeed = true, real = root.windowId != UiNode.NO_WINDOW) } }
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

        /** Hoja en su propia ventana: [text] solo en la raíz de la hoja (una llamada en vez de una por ventana). */
        fun sheetOnlyText(query: String): List<Seen> = sheetRoot()?.let { i -> fast("t:$query", only = i) { f, root -> f.byText(root, query) } }.orEmpty()

        fun sheetOnlyId(viewId: String): List<Seen> = sheetRoot()?.let { i -> fast("i:$viewId", only = i) { f, root -> f.byViewId(root, viewId) } }.orEmpty()

        /** [id] solo en la ventana [window] (su `windowId` o su posición): una llamada. */
        fun windowOnlyId(window: Int, viewId: String): List<Seen> =
            roots.indices.firstOrNull { keyOf(it) == window }?.let { i -> fast("i:$viewId", only = i) { f, root -> f.byViewId(root, viewId) } }.orEmpty()

        /**
         * Hoja en su propia ventana: [id] en todas las ventanas menos la del visor (la hoja nunca está en ella) y, si se dice,
         * menos [also] (1.10.1: la de la hoja, ya mirada, al buscar el buscador en otra).
         */
        fun awayId(viewId: String, also: Int? = null): List<Seen> {
            val skip = feedWindow
            if (skip == null && also == null) return id(viewId)
            return fast("i:$viewId", skip = skip, skipToo = also) { f, root -> f.byViewId(root, viewId) }
        }

        fun awayText(query: String, also: Int? = null): List<Seen> {
            val skip = feedWindow
            if (skip == null && also == null) return text(query)
            return fast("t:$query", skip = skip, skipToo = also) { f, root -> f.byText(root, query) }
        }

        /** Índice de la raíz de la ventana de la hoja, o `null` si no está (o no se sabe cuál es). */
        fun sheetRoot(): Int? {
            val sheet = sheetWindow ?: return null
            return roots.indices.firstOrNull { keyOf(it) == sheet }
        }

        /** Hoja en su propia ventana: la cabecera de este tick ([ownHeader]), pedida una vez. */
        var header: Seen? = null
        var headerAsked = false

        private fun fast(
            key: String,
            only: Int? = null,
            skip: Int? = null,
            skipToo: Int? = null,
            find: (UiFinder, UiNode) -> List<UiNode>,
        ): List<Seen> {
            val f = finder ?: return emptyList()
            val scoped = only != null || skip != null || skipToo != null
            val cacheKey = when {
                only != null -> "s${keyOf(only)}|$key"
                skip != null || skipToo != null -> "a$skip,$skipToo|$key"
                else -> key
            }
            return cache.getOrPut(cacheKey) {
                val picked = roots.indices.filter { i ->
                    (only == null || i == only) && (skip == null || keyOf(i) != skip) && (skipToo == null || keyOf(i) != skipToo)
                }
                // Una consulta por búsqueda hecha (las acotadas, solo si se pidió a alguna ventana).
                if (!scoped || picked.isNotEmpty()) queries++
                timed({ queryMs += it }) {
                    picked.flatMap { i ->
                        val root = roots[i]
                        val found = try {
                            find(f, root)
                        } catch (e: RuntimeException) {
                            emptyList()
                        }
                        val key = keyOf(i)
                        val real = root.windowId != UiNode.NO_WINDOW
                        found.filter { it.isVisible && it.bounds.intersectsScreen(w, h) }.map { seenFromAncestors(it, key, real) }
                    }.also { if (pkg == null && profile.platform.packages.size > 1) it.firstNotNullOfOrNull { s -> packageOf(s.node.viewId) }?.let { p -> pkg = p } }
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
    private fun seenFromAncestors(node: UiNode, window: Int, real: Boolean = false): Seen {
        // 1.10.0, hoja en su propia ventana: lo de una ventana que no es la del visor no está en el visor, sin subir (la
        // hoja de TikTok va ~20 niveles por debajo de su raíz y cada antepasado puede costar una llamada a la app).
        val fw = feedWindow
        if (own && real && fw != null && window != fw) return Seen(node, inFeed = false, inField = isField(node), window = window, real = true)
        var feed = false
        var field = false
        var current: UiNode? = node
        var depth = 0
        val roots = sheetRootIds()
        while (current != null && depth <= config.maxDepth) {
            if (!feed && isFeedNode(current)) feed = true
            if (!field && isField(current)) field = true
            if (feed && field) break
            // Hoja en su propia ventana: en el visor no hay campos que importen (nada de él se pulsa salvo «Compartir»).
            if (feed && own) break
            // 1.4.3: la hoja de compartir no está dentro del visor; subir más allá de ella son ~20 llamadas a Instagram.
            if (!feed && current.viewId in roots) break
            current = current.parent
            depth++
        }
        return Seen(node, feed, field, window, real)
    }

    /** Ids completos de [PlatformProfile.sheetRootIds] (con el paquete de hoy). */
    private fun sheetRootIds(): Set<String> {
        val key = pkg
        rootIdsCache?.let { (p, ids) -> if (p == key) return ids }
        return fullIds(profile.sheetRootIds).toSet().also { rootIdsCache = key to it }
    }

    private var rootIdsCache: Pair<String?, Set<String>>? = null

    /** El paquete de [viewId] (`com.zhiliaoapp.musically:id/x` → `com.zhiliaoapp.musically`) si es de la app del envío. */
    private fun packageOf(viewId: String?): String? =
        viewId?.substringBefore(":id/", "")?.takeIf { it.isNotEmpty() && it in profile.platform.packages }

    /** Los ids completos de [shortIds]: con el paquete de este envío si ya se sabe; si no, en todos los de la app. */
    private fun fullIds(shortIds: List<String>): List<String> {
        val p = pkg
        return shortIds.flatMap { if (p != null) listOf("$p:id/$it") else profile.fullIds(it) }
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

    private fun isFeedNode(node: UiNode): Boolean = shortViewId(node.viewId).let { id -> id.isNotEmpty() && profile.feedIds.any { id.contains(it) } }

    /**
     * Nodos visibles y dentro de la pantalla, en orden de lectura y con límite. Con [pruneFeed], del visor solo
     * se apunta su contenedor (sin entrar): es la mayor parte del árbol y nada de la hoja está dentro.
     */
    private fun snapshot(root: UiNode, w: Int, h: Int, window: Int, pruneFeed: Boolean, real: Boolean = false): List<Seen> {
        val out = ArrayList<Seen>()
        fun visit(node: UiNode, depth: Int, inFeed: Boolean, inField: Boolean) {
            if (out.size >= config.maxNodes || !node.isVisible || !node.bounds.intersectsScreen(w, h)) return
            val feed = inFeed || isFeedNode(node)
            val field = inField || isField(node)
            out += Seen(node, feed, field, window, real)
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
        marks += Mark(next, now, if (next == Phase.TARGET || next == Phase.SEARCH || next == Phase.RESULT) current()?.index ?: 0 else 0)
    }

    /** Antes de una acción que el servicio aún tiene que hacer: se guarda cómo estaba ([onPerformed]). */
    private fun snapshot(dest: Dest?, typedKey: String? = null) {
        before = Before(phase, phaseSince, marks.size, marks.lastOrNull()?.how, sentFast, dest, dest?.copyState(), typedKey, rejectedSince, searchOpen)
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
            // Hoja en su propia ventana: el buscador solo sale tras la lupa; se olvida si se buscó y el aprendido no salió.
            forgetSearchId = hints.searchId != null && (if (own) ownFieldSought else sheetWalkedWithField) && !sawLearnedSearch && learnedSearchId == null,
            queries = queries,
            queryMs = queryMs,
            walkMs = walkMs,
        )
        running = false
        return done
    }

    companion object {

        private val SEARCH_PREFIXES = listOf("buscar", "search")

        /** Android rechaza una y otra vez la acción (nada pulsable a la medida). */
        private const val REJECTED = "Android no acepta la acción"

        /**
         * Ids de la hoja de compartir de Instagram vistos en el árbol real (Instagram 454, nov. 2026; los cortos están en
         * [PlatformProfile.INSTAGRAM]). Solo son atajos: lo que devuelven pasa las mismas reglas, y si cambian, se aprende
         * el buscador o se recorre como siempre.
         */
        const val KNOWN_SEARCH_ID = "com.instagram.android:id/search_edit_text"
        const val KNOWN_GRID_CELL_ID = "com.instagram.android:id/direct_share_sheet_grid_view_pog"

        /** [release]: «Enviar» aceptado y otro envío esperando; no se espera a confirmarlo. */
        const val RELEASED = "enviado sin confirmar"

        /** Lo que se pide a la app con el atajo (contiene, sin mayúsculas, con tildes). */
        private val SHARE_QUERIES = listOf("Compartir", "Share", "Enviar", "Send")

        /** Opciones de la hoja de Instagram que se ven como texto (las pistas de los campos vacíos no las encuentra Instagram). */
        val MARKER_QUERIES = PlatformProfile.INSTAGRAM.markerQueries

        /** ¿[label] es una opción que solo sale en la hoja de compartir de Instagram? (Para el recorte de fotos a mano.) */
        fun isSheetMarker(label: CharSequence): Boolean = isSheetMarker(label, PlatformProfile.INSTAGRAM)

        /** ¿[label] es una opción que solo sale en la hoja de compartir de la app de [profile]? */
        fun isSheetMarker(label: CharSequence, profile: PlatformProfile): Boolean =
            RecipientDetector.normalize(label.toString()).trimEnd('.', '…', ':', ' ') in profile.sheetMarkers

        /** El campo de «Escribe un mensaje…» de la hoja: nunca es el buscador (el nombre acabaría en el mensaje). */
        private val MESSAGE_HINTS = listOf(
            "escribe un mensaje", "write a message",
            // 1.10.0, sin ver en un móvil (TikTok en otros idiomas): defensa de más, la regla del título ya lo evita.
            "ecrire un message", "ecris un message", "scrivi un messaggio", "escreva uma mensagem", "nachricht schreiben",
        )

        /** Marcas de dirección invisibles (LRM/RLM, U+202A–U+202E, U+2066–U+2069): nunca van en lo que se pide al atajo. */
        private val BIDI_MARKS: Set<Char> = (listOf(0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)).mapTo(HashSet()) { Char(it) }

        /** Una fila del buscador de una hoja en su propia ventana ocupa casi todo el ancho; una celda o un emoji, no. */
        private const val SEARCH_ROW_WIDTH = 0.8f

        /**
         * Hoja en su propia ventana: la fila de contactos es la primera debajo del título. Una celda vale si empieza como
         * mucho a esta parte de su alto por debajo del título (en TikTok 47.7: 45 px con celdas de 220; las opciones,
         * a 335, y los emojis, más abajo).
         */
        private const val FIRST_ROW_GAP = 0.6f

        /** Lado mínimo (px) de la foto de una celda o fila para recortarla. */
        private const val MIN_PHOTO_PX = 48

        /**
         * 1.10.1, hoja en su propia ventana: de las palabras de un botón (lupa, «Enviar»), cuántas se piden siempre al atajo de
         * texto (las primeras: el español y el inglés). Las demás, solo si casan con lo que ya se ve, o en el recorrido.
         */
        private const val PRIMARY_WORDS = 2

        /**
         * 1.10.1: tras volver la fila de contactos al principio, lo que se le deja para moverse (RecyclerView anima una página
         * en ~0,6 s) mirando el nombre antes de ir a la lupa.
         */
        private const val ROW_SCROLL_SETTLE_MS = 700L

        /** 1.10.1, «Enviar» por su sitio: la columna de la hoja es el antepasado del título que llega a esta altura y anchura. */
        private const val SITE_MAX_ANCESTORS = 3
        private const val SITE_COLUMN_BOTTOM = 0.85f
        private const val SITE_COLUMN_WIDTH = 0.9f
        private const val SITE_MAX_NODES = 200
        private const val SITE_MAX_DEPTH = 8
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
