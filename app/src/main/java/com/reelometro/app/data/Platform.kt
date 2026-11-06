package com.reelometro.app.data

/** Plataformas de vídeo corto que vigilamos. */
enum class Platform(
    val displayName: String,
    val shortName: String,
    val color: Long,
    val packages: Set<String>,
) {
    TIKTOK("TikTok", "TikTok", 0xFF25F4EE, setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    INSTAGRAM("Instagram Reels", "Reels", 0xFFE1306C, setOf("com.instagram.android")),
    YOUTUBE("YouTube Shorts", "Shorts", 0xFFFF3B30, setOf("com.google.android.youtube"));

    companion object {
        fun fromPackage(pkg: CharSequence?): Platform? {
            val p = pkg?.toString() ?: return null
            return entries.firstOrNull { p in it.packages }
        }

        fun fromName(name: String): Platform? = entries.firstOrNull { it.name == name }
    }
}
