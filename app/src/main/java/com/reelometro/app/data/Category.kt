package com.reelometro.app.data

/**
 * Categorías en las que se clasifica cada vídeo a partir de su descripción y hashtags.
 * Las palabras clave viven en `detection/CategoryKeywords.kt`.
 *
 * `emoji` SOLO se usa dentro del texto de la imagen compartible (nunca como icono en la interfaz).
 */
enum class Category(val label: String, val emoji: String) {
    HUMOR("Humor", "😂"),
    DEPORTE("Deporte", "⚽"),
    FITNESS("Fitness", "💪"),
    COMIDA("Comida", "🍳"),
    MUSICA("Música", "🎵"),
    BAILE("Baile", "💃"),
    GAMING("Gaming", "🎮"),
    APRENDIZAJE("Aprendizaje", "🧠"),
    TECNOLOGIA("Tecnología", "📱"),
    BELLEZA("Moda y belleza", "💄"),
    ANIMALES("Animales", "🐶"),
    VIAJES("Viajes", "✈️"),
    MOTOR("Motor", "🚗"),
    DINERO("Dinero y negocios", "💸"),
    NOTICIAS("Noticias y política", "📰"),
    RELACIONES("Pareja y relaciones", "❤️"),
    OTROS("Sin clasificar", "✨");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTROS
    }
}
