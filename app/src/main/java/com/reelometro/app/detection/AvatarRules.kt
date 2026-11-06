package com.reelometro.app.detection

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reglas puras de la **foto de perfil** de las burbujas del envío rápido (sin Android):
 *  - [initials]: lo que lleva el círculo si no hay foto;
 *  - [fileName], [croppedFileName], [chosenFileName]: los ficheros de cada fijado en `filesDir/avatars/` (un hash
 *    del nombre, nunca el nombre) y [pick], cuál se pinta;
 *  - [needsRefresh] y [captureInQuickSend]: como mucho una captura al día por fijado, y ninguna si el usuario eligió
 *    la foto de su galería;
 *  - [photoOf] y [cropBox]: dónde está la foto en la hoja de compartir y qué cuadrado se recorta;
 *  - [centerSquare], [sampleSize], [exifRotation] y [exifMirror]: la foto elegida de la galería (1.4.3).
 */
object AvatarRules {

    /** Lado del PNG guardado (px). */
    const val SIZE_PX = 144

    /** Una foto vale un día: después, la próxima vez que su celda esté a la vista se vuelve a recortar. */
    const val REFRESH_MS = 24L * 60 * 60 * 1000

    /** Una foto más pequeña que esto (px) no merece la pena. */
    const val MIN_SIDE_PX = 24

    /** Ancho / alto: la foto es (casi) cuadrada. */
    private const val MAX_ASPECT = 1.3f

    private val LOCALE: Locale = Locale.forLanguageTag("es-ES")

    /** Palabras que no dan inicial salvo si son la primera («Los del piso» → «LP»). */
    private val LINKS = setOf("de", "del", "la", "las", "el", "los", "y", "e", "and", "of", "the")

    /**
     * Una o dos iniciales: la de la primera palabra y la de la última que cuente (con alguna letra y sin ser
     * «de», «la»…). «Ana García» → «AG», «marta» → «M», «Los del piso» → «LP», «@ana.g_92» → «AG»,
     * «Primos 2024» → «P». Sin letras ni cifras, «?».
     */
    fun initials(name: String): String {
        val words = name.split(Regex("[\\s._\\-@·,]+"))
            .map { w -> w.dropWhile { !it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return "?"
        val first = firstChar(words.first())
        val last = words.drop(1).lastOrNull { w -> w.lowercase(LOCALE) !in LINKS && w.any { it.isLetter() } }?.let(::firstChar)
        return (first + (last ?: "")).uppercase(LOCALE)
    }

    /** Primer carácter completo (un emoji o una letra con su tilde combinada no se parten). */
    private fun firstChar(word: String): String {
        val end = word.offsetByCodePoints(0, 1)
        return word.substring(0, end)
    }

    /**
     * `<hash>.png`: SHA-256 del nombre normalizado (sin tildes ni mayúsculas), en hexadecimal. Es la clave de cada
     * fijado (también en `QuickSendMemory`) y el fichero de las fotos recortadas **antes de la 1.4.3**, que podían
     * llevar la etiqueta de actividad de Instagram: se siguen pintando hasta que haya una nueva, pero cuentan como
     * «sin foto» para volver a recortarla ([croppedFileName]).
     */
    fun fileName(name: String): String = hash(name) + ".png"

    /** La foto recortada de la hoja con el recorte actual ([cropBox], sin el borde del círculo). */
    fun croppedFileName(name: String): String = hash(name) + ".c$CROP_VERSION.png"

    /** La foto que el usuario eligió de su galería: manda sobre la recortada y la captura nunca la toca. */
    fun chosenFileName(name: String): String = hash(name) + ".mine.png"

    /** Todos los ficheros de [name] (para no borrarlos mientras siga fijado). */
    fun fileNames(name: String): List<String> = listOf(chosenFileName(name), croppedFileName(name), fileName(name))

    /** Versión del recorte de la hoja: subirla hace que las fotos ya guardadas se recorten de nuevo. */
    const val CROP_VERSION = 2

    private fun hash(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(RecipientDetector.normalize(name).toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /** De dónde sale la foto de una burbuja. */
    enum class Source { CHOSEN, CROPPED, LEGACY }

    /** Qué foto se pinta: la elegida; si no, la recortada; si no, la recortada antes de la 1.4.3; si no, ninguna. */
    fun pick(chosen: Boolean, cropped: Boolean, legacy: Boolean): Source? = when {
        chosen -> Source.CHOSEN
        cropped -> Source.CROPPED
        legacy -> Source.LEGACY
        else -> null
    }

    /**
     * ¿Toca recortar otra vez? Sin foto, al día o si el reloj ha ido hacia atrás. Nunca si el usuario eligió la
     * foto de su galería ([chosen]).
     */
    fun needsRefresh(savedAt: Long?, now: Long, chosen: Boolean = false): Boolean =
        !chosen && (savedAt == null || savedAt <= 0L || now < savedAt || now - savedAt >= REFRESH_MS)

    /**
     * Margen hacia dentro del recorte (fracción del lado): arriba y a la izquierda [INSET_START], abajo y a la derecha
     * [INSET_END]. Instagram pinta encima del borde de abajo a la derecha del círculo la etiqueta de actividad
     * («47 m») o el punto verde de en línea: el recorte se queda dentro del círculo y algo desplazado hacia arriba a
     * la izquierda, lejos de esa franja (queda un cuadrado del 70 % del lado; 1.4.3, visto en el móvil: la etiqueta
     * ocupa el borde de abajo hacia el centro, no solo la esquina). La burbuja pinta la foto en círculo,
     * así que el borde perdido no se nota; en un grupo (dos círculos que se pisan) quedan los dos, algo recortados.
     */
    const val INSET_START = 0.08f
    const val INSET_END = 0.22f

    /**
     * En un grupo (avatar compuesto) el margen es pequeño y simétrico: la cara de delante está justo abajo a la
     * derecha y los grupos no llevan la etiqueta de actividad (revisión de la 1.4.3).
     */
    const val GROUP_INSET = 0.04f

    /**
     * El cuadrado que se recorta: el lado menor de [photo], centrado, menos los márgenes [INSET_START] e
     * [INSET_END] (para dejar fuera la etiqueta de actividad), y entero dentro de la pantalla. `null` si la foto no
     * es casi cuadrada, es muy pequeña o se sale de la pantalla (a medio desplazar).
     */
    fun cropBox(photo: NodeBounds, screenWidth: Int, screenHeight: Int, group: Boolean = false): NodeBounds? {
        if (photo.isEmpty) return null
        val w = photo.width
        val h = photo.height
        if (maxOf(w, h).toFloat() / minOf(w, h) > MAX_ASPECT) return null
        val full = minOf(w, h)
        if (full < MIN_SIDE_PX) return null
        val start = (full * (if (group) GROUP_INSET else INSET_START)).roundToInt()
        val side = full - start - (full * (if (group) GROUP_INSET else INSET_END)).roundToInt()
        val left = photo.centerX - full / 2 + start
        val top = photo.centerY - full / 2 + start
        val box = NodeBounds(left, top, left + side, top + side)
        if (box.left < 0 || box.top < 0 || box.right > screenWidth || box.bottom > screenHeight) return null
        return box
    }

    /**
     * Dónde está la foto de [name] en la hoja, a partir del nodo donde se lee su nombre ([nameNode]):
     *  1. si el propio nodo es la foto («Foto del perfil de Ana»), ese;
     *  2. en su fila o celda (como mucho [maxAncestors] antepasados, ninguno más alto que [maxRowHeight] de la
     *     pantalla), el nodo cuya descripción es «Foto del perfil de» + ese nombre;
     *  3. si no, la imagen casi cuadrada más grande de la fila que queda encima o a la izquierda del nombre; las que se
     *     pisan (el avatar compuesto de un grupo, dos círculos) cuentan juntas, con el rectángulo que las envuelve.
     */
    fun photoOf(
        nameNode: UiNode,
        name: String,
        screenHeight: Int,
        maxAncestors: Int = 3,
        maxRowHeight: Float = 0.25f,
        maxNodes: Int = 30,
    ): NodeBounds? = photo(nameNode, name, screenHeight, maxAncestors, maxRowHeight, maxNodes)?.bounds

    /** Una foto en la hoja: dónde está y si es el avatar compuesto de un grupo (dos círculos que se pisan). */
    data class Photo(val bounds: NodeBounds, val group: Boolean = false)

    /** Como [photoOf], diciendo además si es la de un grupo (para [cropBox]). */
    fun photo(
        nameNode: UiNode,
        name: String,
        screenHeight: Int,
        maxAncestors: Int = 3,
        maxRowHeight: Float = 0.25f,
        maxNodes: Int = 30,
    ): Photo? {
        val key = RecipientDetector.normalize(name)
        if (isPhotoOf(nameNode, key)) return Photo(nameNode.bounds)
        var ancestor = nameNode.parent
        var best: Photo? = null
        repeat(maxAncestors) {
            val row = ancestor ?: return best
            if (row.bounds.height > screenHeight * maxRowHeight) return best
            val nodes = below(row, maxNodes)
            nodes.firstOrNull { isPhotoOf(it, key) }?.let { return Photo(it.bounds) }
            if (best == null) {
                val images = nodes.filter { n ->
                    n !== nameNode && n.className?.contains("Image") == true && n.isVisible && squareish(n.bounds) &&
                        (n.bounds.bottom <= nameNode.bounds.centerY || n.bounds.right <= nameNode.bounds.centerX)
                }.map { it.bounds }
                // Un grupo lleva un avatar compuesto (dos círculos que se pisan): vale el rectángulo que los envuelve.
                // Solo imágenes casi cuadradas (cada círculo lo es): un fondo o una franja ancha no se suman.
                best = clusters(images).filter(::squareish).maxByOrNull { it.width.toLong() * it.height }
                    // Si no es una imagen suelta, es la unión de varias: el avatar compuesto de un grupo.
                    ?.let { b -> Photo(b, group = images.none { it == b }) }
            }
            ancestor = row.parent
        }
        return best
    }

    /**
     * Junta las imágenes que se pisan (el avatar compuesto de un grupo: dos círculos montados) y devuelve el
     * rectángulo que envuelve cada grupo. Una imagen suelta queda como está.
     */
    internal fun clusters(images: List<NodeBounds>): List<NodeBounds> {
        val out = ArrayList<NodeBounds>()
        for (b in images) {
            var merged = b
            var changed = true
            while (changed) {
                changed = false
                val hit = out.firstOrNull { overlap(it, merged) } ?: continue
                out.remove(hit)
                merged = union(hit, merged)
                changed = true
            }
            out += merged
        }
        return out
    }

    private fun overlap(a: NodeBounds, b: NodeBounds): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun union(a: NodeBounds, b: NodeBounds) =
        NodeBounds(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

    /** Tras una captura fallida (o sin foto localizable), no se reintenta en el envío rápido hasta pasado esto. */
    const val QUICK_RETRY_MS = REFRESH_MS

    /**
     * ¿Se recorta la foto en un **envío rápido**? (1.4.1) Solo si el fijado aún no tiene ninguna y no falló en el
     * último día: la captura retrasa el clic en su nombre, así que no se repite en cada envío. Renovar la foto
     * (al día) se hace solo en los envíos a mano, donde no retrasa nada.
     */
    fun captureInQuickSend(savedAt: Long?, failedAt: Long?, now: Long, chosen: Boolean = false): Boolean {
        if (chosen) return false // la eligió el usuario de su galería: no se recorta nunca
        if (savedAt != null && savedAt > 0L) return false
        if (failedAt == null || failedAt <= 0L) return true
        return now < failedAt || now - failedAt >= QUICK_RETRY_MS
    }

    // ------------------------------------------------------------------ foto elegida de la galería (1.4.3)

    /** Más píxeles que esto no se decodifican de una foto de la galería (≈ 16 MB en ARGB_8888). */
    const val MAX_DECODE_PIXELS = 4_000_000L

    /** El cuadrado centrado más grande de una imagen de [width] × [height]. `null` si está vacía. */
    fun centerSquare(width: Int, height: Int): NodeBounds? {
        if (width <= 0 || height <= 0) return null
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return NodeBounds(left, top, left + side, top + side)
    }

    /**
     * El `inSampleSize` (potencia de 2) para decodificar una foto de [width] × [height]: el mayor que deja el lado
     * menor en [SIZE_PX] o más, y siempre lo bastante grande para no pasar de [MAX_DECODE_PIXELS] (una foto de
     * 50 MP no se carga entera).
     */
    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= SIZE_PX) sample *= 2
        while ((width.toLong() / sample) * (height.toLong() / sample) > MAX_DECODE_PIXELS) sample *= 2
        return sample
    }

    /** Grados (en el sentido del reloj) que hay que girar una foto con la orientación EXIF [orientation] (1–8). */
    fun exifRotation(orientation: Int): Int = when (orientation) {
        3, 4 -> 180
        5, 6 -> 90
        7, 8 -> 270
        else -> 0
    }

    /**
     * ¿Hay que darle la vuelta en espejo (horizontal, **después** de girarla [exifRotation])? Orientaciones EXIF 2, 4,
     * 5 y 7 (5 = girar 90° y espejo = trasponer; 7 = girar 270° y espejo).
     */
    fun exifMirror(orientation: Int): Boolean = orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7

    private fun isPhotoOf(node: UiNode, key: String): Boolean {
        val desc = node.contentDescription?.toString() ?: return false
        val normalized = RecipientDetector.normalize(desc)
        val prefix = RecipientDetector.PREFIXES.firstOrNull { normalized.startsWith(it) } ?: return false
        val rest = normalized.removePrefix(prefix).substringBefore(", ").trim()
        val clean = RecipientDetector.SUFFIXES.fold(rest) { acc, s -> acc.removeSuffix(s) }.trim()
        return clean == key && squareish(node.bounds)
    }

    private fun squareish(b: NodeBounds): Boolean =
        !b.isEmpty && maxOf(b.width, b.height).toFloat() / minOf(b.width, b.height) <= MAX_ASPECT && minOf(b.width, b.height) >= MIN_SIDE_PX

    private fun below(node: UiNode, maxNodes: Int): List<UiNode> {
        val out = ArrayList<UiNode>()
        fun visit(n: UiNode, depth: Int) {
            if (out.size >= maxNodes || !n.isVisible) return
            out += n
            if (depth < 4) n.children.forEach { visit(it, depth + 1) }
        }
        node.children.forEach { visit(it, 1) }
        return out
    }
}
