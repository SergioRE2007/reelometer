package com.reelometro.app.detection

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.util.Locale

/**
 * Un cierre del proceso de la app tal como lo guarda Android (`ApplicationExitInfo`), copiado a un tipo propio para
 * poder clasificarlo sin Android. [subreason] no es API pública: sale del texto de `toString()` (0 si no está).
 * [description] la rellena el sistema o el fabricante (p. ej. `SwipeUpClean` en HyperOS) y puede faltar.
 * [pssKb] y [rssKb]: memoria del proceso justo antes de morir, en kB (0 si Android no la apuntó). Sirven para saber si
 * lo cerraron por gordo o por política de la marca.
 */
data class ExitRecord(
    val reason: Int,
    val subreason: Int = 0,
    val description: String?,
    val importance: Int,
    val timestamp: Long,
    val pssKb: Long = 0,
    val rssKb: Long = 0,
)

/** Por qué se cerró la app, en categorías propias (deciden el texto del diagnóstico). */
enum class ExitCategory {
    /** Al quitarla de recientes o con «limpiar todo» (SwipeUpClean, OneKeyClean…). */
    RECENTS_CLEAN,

    /** El sistema la cerró por falta de memoria. */
    SYSTEM_LOW_MEMORY,

    /** El sistema la cerró por otro motivo (o uno que no se conoce). */
    SYSTEM_OTHER,

    /** Ahorro de batería o restricción: uso excesivo, congelada… */
    RESTRICTED,

    /** Forzar detención, datos borrados o una acción del usuario. */
    USER_STOPPED,

    /** Fallo de la propia app: CRASH, CRASH_NATIVE, ANR o arranque fallido. */
    APP_CRASH,

    /** Cierres que no explican nada (la app se cerró sola, una actualización). */
    IGNORED,
    ;

    /** Lo ha cerrado el móvil (no el usuario ni un fallo de la app). */
    val bySystem: Boolean get() = this == RECENTS_CLEAN || this == SYSTEM_LOW_MEMORY || this == SYSTEM_OTHER || this == RESTRICTED
}

/** Un cierre ya clasificado. [serviceBound]: el proceso tenía el servicio enlazado (importancia perceptible o más). */
data class ExitInfo(val record: ExitRecord, val category: ExitCategory, val serviceBound: Boolean)

/** Si se han podido leer los cierres que guarda Android (Calibración enseña una frase distinta en cada caso). */
enum class ExitAccess {
    /** Leídos: si no hay ninguno es que de verdad Android no guarda ninguno. */
    READ,

    /** Antes de Android 11 (API 30, minSdk es 26) Android no guarda los cierres. */
    UNAVAILABLE,

    /** Android los guarda, pero ha fallado al leerlos (una excepción o sin `ActivityManager`). */
    FAILED,
}

/** Lo leído de Android ([ProcessExits.history]): [records] solo puede llevar algo con [ExitAccess.READ]. */
data class ExitHistory(val access: ExitAccess, val records: List<ExitRecord> = emptyList())

/** Los últimos cierres ya clasificados, del más reciente al más antiguo, y si se han podido leer (cabecera de Calibración). */
data class RecentExits(val access: ExitAccess, val exits: List<ExitInfo> = emptyList())

/**
 * Reglas puras para leer los cierres de la app. Las constantes son las de `ApplicationExitInfo`, copiadas aquí porque
 * algunas no existen en minSdk 26 ni en API 30, donde aparece esta API (FREEZER es de API 33; PACKAGE_STATE_CHANGE y PACKAGE_UPDATED, de API 34) y las
 * subrazones no son API pública.
 */
object ExitReasons {
    const val REASON_UNKNOWN = 0
    const val REASON_EXIT_SELF = 1
    const val REASON_SIGNALED = 2
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_INITIALIZATION_FAILURE = 7
    const val REASON_PERMISSION_CHANGE = 8
    const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    const val REASON_USER_REQUESTED = 10
    const val REASON_USER_STOPPED = 11
    const val REASON_DEPENDENCY_DIED = 12
    const val REASON_OTHER = 13
    const val REASON_FREEZER = 14
    const val REASON_PACKAGE_STATE_CHANGE = 15
    const val REASON_PACKAGE_UPDATED = 16

    const val SUBREASON_TOO_MANY_CACHED = 2
    const val SUBREASON_MEMORY_PRESSURE = 6
    const val SUBREASON_EXCESSIVE_CPU = 7
    const val SUBREASON_CACHED_IDLE_FORCED_APP_STANDBY = 18
    const val SUBREASON_FREEZER_BINDER_IOCTL = 19
    const val SUBREASON_FREEZER_BINDER_TRANSACTION = 20
    const val SUBREASON_FORCE_STOP = 21
    const val SUBREASON_REMOVE_TASK = 22
    const val SUBREASON_PACKAGE_UPDATE = 25

    /** `RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE`: con el servicio enlazado el proceso está en este nivel o más. */
    const val IMPORTANCE_PERCEPTIBLE = 230

    /** Sin marca de «visto» (primera vez o preferencias borradas) solo valen los cierres de esta ventana. */
    const val FIRST_WINDOW_MS = 7 * 24 * 3_600_000L

    /** Un cierre con el servicio sin enlazar solo explica el fallo si es así de reciente. */
    const val UNBOUND_WINDOW_MS = 24 * 3_600_000L

    /** Cierres que se leen de Android (y los que salen en la cabecera de Calibración). */
    const val READ_MAX = 16
    const val LOG_MAX = 5

    /** Largo máximo de la descripción en el registro. */
    const val DESCRIPTION_MAX = 60

    private val CRASH = setOf(REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR, REASON_INITIALIZATION_FAILURE)
    private val USER = setOf(REASON_USER_REQUESTED, REASON_USER_STOPPED, REASON_PACKAGE_STATE_CHANGE, REASON_PERMISSION_CHANGE)
    private val RESTRICTED_SUB = setOf(
        SUBREASON_EXCESSIVE_CPU,
        SUBREASON_CACHED_IDLE_FORCED_APP_STANDBY,
        SUBREASON_FREEZER_BINDER_IOCTL,
        SUBREASON_FREEZER_BINDER_TRANSACTION,
    )
    private val LOW_MEMORY_SUB = SUBREASON_TOO_MANY_CACHED..SUBREASON_MEMORY_PRESSURE

    /** Datos borrados: va antes que «limpiar» para que «clear data» no se tome por limpiar recientes. */
    private val CLEAR_DATA = Regex("""clear[\s_-]*(app[\s_-]*)?data|data[\s_-]*clear|wipe[\s_-]*data""")

    /** Recientes y limpiadores de varias marcas (SwipeUpClean, OneKeyClean, RemoveTask, clear all, kill all…). */
    private val RECENTS = Regex("""swipe|one[\s_-]*key|clean|recent|remove[\s_-]*task|clear[\s_-]*all|kill[\s_-]*all|task[\s_-]*(clear|kill|remov)""")

    /** Ahorro de batería y congelado (Millet/«greezer» en MIUI, powerkeeper, hibernar…). */
    private val RESTRICT = Regex("""freez|greez|excessive|battery|power[\s_-]*(save|saving|keeper|guard)|standby|restrict|hibernat|doze""")

    private val LOW_MEMORY = Regex("""low[\s_-]*mem|lmk|\boom\b|memory|mem[\s_-]*pressure|too many (cached|empty)""")

    fun category(reason: Int, subreason: Int, description: String?): ExitCategory {
        val d = description?.lowercase(Locale.ROOT).orEmpty()
        return when {
            reason in CRASH -> ExitCategory.APP_CRASH
            reason == REASON_EXIT_SELF -> ExitCategory.IGNORED
            reason == REASON_PACKAGE_UPDATED || subreason == SUBREASON_PACKAGE_UPDATE -> ExitCategory.IGNORED
            CLEAR_DATA.containsMatchIn(d) -> ExitCategory.USER_STOPPED
            subreason == SUBREASON_REMOVE_TASK || RECENTS.containsMatchIn(d) -> ExitCategory.RECENTS_CLEAN
            reason in USER || subreason == SUBREASON_FORCE_STOP -> ExitCategory.USER_STOPPED
            reason == REASON_EXCESSIVE_RESOURCE_USAGE || reason == REASON_FREEZER || subreason in RESTRICTED_SUB ||
                RESTRICT.containsMatchIn(d) -> ExitCategory.RESTRICTED
            reason == REASON_LOW_MEMORY || subreason in LOW_MEMORY_SUB || LOW_MEMORY.containsMatchIn(d) ->
                ExitCategory.SYSTEM_LOW_MEMORY
            else -> ExitCategory.SYSTEM_OTHER
        }
    }

    fun classify(r: ExitRecord): ExitInfo = ExitInfo(
        record = r,
        category = category(r.reason, r.subreason, r.description),
        // Aproximación: 100 y 200 (primer plano, visible) también caen aquí aunque el servicio no estuviera enlazado.
        // 0 = sin dato (algunos registros no lo traen): no se da por enlazado.
        serviceBound = r.importance in 1..IMPORTANCE_PERCEPTIBLE,
    )

    /** Clasificados, del más reciente al más antiguo. */
    fun recent(records: List<ExitRecord>, max: Int = LOG_MAX): List<ExitInfo> =
        records.sortedByDescending { it.timestamp }.take(max).map(::classify)

    /** Los mismos, sin perder si se han podido leer ([ExitHistory.access]). */
    fun recent(history: ExitHistory, max: Int = LOG_MAX): RecentExits = RecentExits(history.access, recent(history.records, max))

    /**
     * El cierre que explica por qué el servicio no funciona: de los posteriores a [seenUpTo] (o, sin marca, a los
     * últimos [FIRST_WINDOW_MS]) que importan (no [ExitCategory.IGNORED]), el más reciente con el servicio enlazado
     * (ese es el que deja a Android sin volver a enlazarlo). Sin ninguno enlazado solo vale uno sin enlazar de las
     * últimas [UNBOUND_WINDOW_MS]: uno antiguo no explica nada. null si no hay nada nuevo.
     */
    fun relevant(records: List<ExitRecord>, seenUpTo: Long?, now: Long = System.currentTimeMillis()): ExitInfo? {
        val floor = seenUpTo ?: (now - FIRST_WINDOW_MS)
        val fresh = records
            .filter { it.timestamp > floor }
            .map(::classify)
            .filter { it.category != ExitCategory.IGNORED }
            .sortedByDescending { it.record.timestamp }
        return fresh.firstOrNull { it.serviceBound }
            ?: fresh.firstOrNull { now - it.record.timestamp <= UNBOUND_WINDOW_MS }
    }

    /** Marca que hay que guardar como «ya visto» (el cierre más reciente), o la de antes si no hay ninguno más nuevo. */
    fun seenMark(records: List<ExitRecord>, previous: Long?): Long? {
        val newest = records.maxOfOrNull { it.timestamp } ?: return previous
        return if (previous == null || newest > previous) newest else previous
    }

    /**
     * La descripción para enseñarla (Calibración y el registro que se comparte): solo letras y cifras ASCII y unos
     * pocos signos (sin rutas raras ni texto libre largo), con los espacios juntados, los nombres de paquete de otras
     * apps cambiados por `packageToken(n)` ([CalibrationRedactor.redactPackages]; los de Reelometer se quedan) y
     * recortada a [DESCRIPTION_MAX] (después de tapar: un paquete cortado no se escapa y el marcador cuenta en el
     * largo). null si queda vacía. Solo para enseñar: [category] mira siempre la descripción original. Con el mismo
     * [numbers] en varias descripciones (las líneas de un registro), el mismo paquete lleva el mismo número en todas.
     */
    fun safeDescription(
        description: String?,
        numbers: MutableMap<String, Int> = HashMap(),
        packageToken: (Int) -> String = { "[#$it]" },
    ): String? {
        if (description.isNullOrBlank()) return null
        val filtered = description
            .map { c -> if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in SAFE_SIGNS) c else ' ' }
            .joinToString("")
            .replace(SPACES, " ")
            .trim()
        if (filtered.isEmpty()) return null
        val kept = CalibrationRedactor.redactPackages(filtered, packageToken, numbers)
        return if (kept.length <= DESCRIPTION_MAX) kept else kept.take(DESCRIPTION_MAX).trimEnd() + "…"
    }

    private const val SAFE_SIGNS = " ._:-()/=,#"
    private val SPACES = Regex("""\s+""")

    /** La subrazón del texto de `ApplicationExitInfo.toString()` («… subreason=21 (FORCE STOP) …»); 0 si no está. */
    fun parseSubreason(text: String?): Int =
        text?.let { SUBREASON.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

    private val SUBREASON = Regex("""subreason=(-?\d+)""")
}

/**
 * Lee los cierres de Android (`getHistoricalProcessExitReasons`, API 30+) y guarda hasta cuál se ha visto ya, en las
 * preferencias del aviso del servicio (`service_health`). Sin permisos: cada app solo ve los suyos. Nunca lanza.
 */
object ProcessExits {
    private const val LOG = "Reelometro"
    private const val PREFS = "service_health"
    private const val KEY_SEEN = "exit_seen_until"

    /**
     * Los cierres y si se han podido leer: [ExitAccess.UNAVAILABLE] antes de Android 11 (minSdk es 26), donde no hay
     * registro de cierres; [ExitAccess.FAILED] si el sistema falla al leerlos.
     */
    fun history(context: Context, max: Int = ExitReasons.READ_MAX): ExitHistory {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ExitHistory(ExitAccess.UNAVAILABLE)
        return try {
            val am = context.getSystemService(ActivityManager::class.java)
            if (am == null) {
                Log.w(LOG, "No se pueden leer los cierres de la app: sin ActivityManager")
                ExitHistory(ExitAccess.FAILED)
            } else {
                ExitHistory(ExitAccess.READ, am.getHistoricalProcessExitReasons(context.packageName, 0, max).map(::toRecord))
            }
        } catch (e: RuntimeException) {
            Log.w(LOG, "No se pueden leer los cierres de la app", e)
            ExitHistory(ExitAccess.FAILED)
        }
    }

    /**
     * Solo los cierres: vacía también antes de Android 11 o si no se pueden leer (para el diagnóstico de Hoy, conectar
     * y la prueba de un minuto da lo mismo: sin cierres todo sigue como antes).
     */
    fun read(context: Context, max: Int = ExitReasons.READ_MAX): List<ExitRecord> = history(context, max).records

    @RequiresApi(Build.VERSION_CODES.R)
    private fun toRecord(info: ApplicationExitInfo): ExitRecord = ExitRecord(
        reason = info.reason,
        subreason = runCatching { ExitReasons.parseSubreason(info.toString()) }.getOrDefault(0),
        description = info.description,
        importance = info.importance,
        timestamp = info.timestamp,
        pssKb = runCatching { info.pss }.getOrDefault(0L),
        rssKb = runCatching { info.rss }.getOrDefault(0L),
    )

    fun seenUpTo(context: Context): Long? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SEEN, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    }.getOrNull()

    /** El servicio funciona: los cierres de hasta ahora ya no explican nada que pase después. */
    fun markSeen(context: Context, records: List<ExitRecord> = read(context)) {
        runCatching {
            val previous = seenUpTo(context)
            val mark = ExitReasons.seenMark(records, previous) ?: return
            if (mark != previous) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_SEEN, mark) }
        }
    }

    /** El cierre nuevo que explica que el servicio no funcione (solo tiene sentido con [ServiceHealth.STALLED]). */
    fun stallReason(context: Context): ExitInfo? = runCatching { ExitReasons.relevant(read(context), seenUpTo(context)) }.getOrNull()

    /** Los últimos cierres para la cabecera de Calibración, con si se han podido leer (o por qué no). */
    fun recent(context: Context): RecentExits =
        runCatching { ExitReasons.recent(history(context)) }.getOrDefault(RecentExits(ExitAccess.FAILED))
}
