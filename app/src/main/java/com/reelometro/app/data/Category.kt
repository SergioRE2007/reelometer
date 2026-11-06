package com.reelometro.app.data

import com.reelometro.app.R
import com.reelometro.app.copy.str

/**
 * Categorías en las que se clasifica cada vídeo a partir de su descripción y hashtags.
 * Las palabras clave viven en `detection/CategoryKeywords.kt`.
 *
 * El nombre que se ve ([label]) sale de `res/values/copy_categories.xml`; [name] es lo que se
 * guarda en Room y no se traduce nunca.
 *
 * `emoji` SOLO se usa dentro del texto de la imagen compartible (nunca como icono en la interfaz).
 *
 * [csvLabel] es el nombre en el CSV de exportación: fijo en español, para que el formato del
 * archivo no cambie con el idioma de la app (los que ya lo han importado en otro sitio).
 */
enum class Category(private val labelId: Int, val emoji: String, val csvLabel: String) {
    HUMOR(R.string.category_humor, "😂", "Humor"),
    DEPORTE(R.string.category_deporte, "⚽", "Deporte"),
    FITNESS(R.string.category_fitness, "💪", "Fitness"),
    COMIDA(R.string.category_comida, "🍳", "Comida"),
    MUSICA(R.string.category_musica, "🎵", "Música"),
    BAILE(R.string.category_baile, "💃", "Baile"),
    GAMING(R.string.category_gaming, "🎮", "Gaming"),
    APRENDIZAJE(R.string.category_aprendizaje, "🧠", "Aprendizaje"),
    TECNOLOGIA(R.string.category_tecnologia, "📱", "Tecnología"),
    BELLEZA(R.string.category_belleza, "💄", "Moda y belleza"),
    ANIMALES(R.string.category_animales, "🐶", "Animales"),
    VIAJES(R.string.category_viajes, "✈️", "Viajes"),
    MOTOR(R.string.category_motor, "🚗", "Motor"),
    DINERO(R.string.category_dinero, "💸", "Dinero y negocios"),
    NOTICIAS(R.string.category_noticias, "📰", "Noticias y política"),
    RELACIONES(R.string.category_relaciones, "❤️", "Pareja y relaciones"),
    OTROS(R.string.category_otros, "✨", "Sin clasificar");

    /** Nombre en el idioma de la app: «Humor», «Moda y belleza», «Sin clasificar». */
    val label: String get() = str(labelId)

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTROS
    }
}
