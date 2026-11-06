package com.reelometro.app.detection

/**
 * «Huella» del vídeo visible: autor y título normalizados, leídos con [ContentExtractor]. Es la segunda
 * señal del detector en YouTube Shorts ([FeedRules.PlatformRules.contentCheck]): si la huella cambia
 * sin un scroll que lo explique, el usuario ha pasado de vídeo; si un vídeo «nuevo» tiene la misma
 * huella que el anterior, es el mismo vídeo contado dos veces.
 *
 * Las lecturas de pantalla no son perfectas (a veces falta el título, el autor sale como nombre o como
 * @handle…), así que las comparaciones son prudentes:
 *  - [sameAs] exige autor **y** título iguales.
 *  - [differsFrom] exige que **todo lo comparable** sea distinto. Si solo se puede comparar el autor, el
 *    cambio es «débil» ([Change.WEAK]) y el servicio lo confirma con una segunda lectura; si solo se puede
 *    comparar el título, no cuenta (el texto de abajo cambia a veces sin cambiar de vídeo).
 */
data class ShortSignature(val creator: String?, val caption: String?) {

    enum class Change {
        /** Nada que comparar o algún campo coincide: es el mismo vídeo (o no se sabe). */
        NONE,

        /** Solo se puede comparar el autor y es distinto: hay que confirmarlo. */
        WEAK,

        /** Autor y título distintos: otro vídeo seguro. */
        STRONG,
    }

    val isEmpty: Boolean get() = creator == null && caption == null

    /** Mismo autor y mismo título (los dos leídos en ambas huellas). */
    fun sameAs(other: ShortSignature): Boolean =
        creator != null && creator == other.creator && caption != null && caption == other.caption

    fun differsFrom(other: ShortSignature): Change {
        var comparable = 0
        var different = 0
        if (creator != null && other.creator != null) {
            comparable++
            if (creator != other.creator) different++
        }
        if (caption != null && other.caption != null) {
            comparable++
            if (caption != other.caption) different++
        }
        val creatorDiffers = creator != null && other.creator != null && creator != other.creator
        return when {
            comparable == 0 || different < comparable -> Change.NONE
            comparable == 2 -> Change.STRONG
            // Solo el título no basta: YouTube cambia a veces el texto de abajo (comentario destacado…).
            creatorDiffers -> Change.WEAK
            else -> Change.NONE
        }
    }

    /** Completa los campos que faltan con los de [other] (lecturas sucesivas del mismo vídeo). */
    fun enrichedWith(other: ShortSignature): ShortSignature =
        ShortSignature(creator ?: other.creator, caption ?: other.caption)

    companion object {
        /** Caracteres del título que se comparan: los títulos largos se cortan distinto según el estado. */
        const val CAPTION_KEY_LENGTH = 40

        private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        fun of(info: ContentExtractor.VideoInfo): ShortSignature? = of(info.creator, info.caption)

        fun of(creator: String?, caption: String?): ShortSignature? {
            val c = creator?.let(::key)?.ifEmpty { null }
            val t = caption?.let(::key)?.take(CAPTION_KEY_LENGTH)?.ifEmpty { null }
            return ShortSignature(c, t).takeUnless { it.isEmpty }
        }

        /** Minúsculas, sin acentos, sin @, espacios ni signos: «@Cocina Fácil» y «cocinafacil» son iguales. */
        private fun key(raw: String): String =
            CategoryClassifier.normalize(raw).replace(NOT_ALNUM, "")
    }
}
