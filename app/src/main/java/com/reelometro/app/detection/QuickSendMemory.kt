package com.reelometro.app.detection

import android.content.Context
import com.reelometro.app.data.Platform

/**
 * Lo que el servicio recuerda del **envío rápido** entre envíos (SharedPreferences `quick_send`, solo en el
 * móvil y sin copia en la nube):
 *  - los ids del botón compartir y del buscador de la hoja la última vez que acertaron ([QuickSender.Hints]);
 *  - por fijado (con un hash del nombre, nunca el nombre), cuántas veces seguidas no salió en la cuadrícula y
 *    cuándo falló la última captura de su foto en un envío rápido (1.4.1: no se reintenta ese día);
 *  - la racha de burbujas escondidas en la «X» ([DismissStreak]).
 *
 * 1.10.0: **una memoria por app** (los ids de TikTok no son los de Instagram, y sus contactos tampoco). Instagram sigue
 * en las claves de siempre (lo aprendido no se pierde al actualizar); las demás apps llevan su prefijo
 * (`tiktok_share_id`, `tiktok_grid_misses_<hash>`…) y el hash del fijado es el de su app ([AvatarRules.fileName]).
 * La racha de la «X» es una sola: la pregunta de apagar el envío rápido es para las dos apps.
 * BORRAR TODO lo olvida todo ([clear]). Solo desde el hilo principal.
 */
internal class QuickSendMemory(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ids aprendidos en [platform] (compartir y buscador), comunes a todos sus fijados. */
    fun hints(platform: Platform = Platform.INSTAGRAM): QuickSender.Hints = QuickSender.Hints(
        shareId = prefs.getString(key(platform, SHARE_ID), null),
        searchId = prefs.getString(key(platform, SEARCH_ID), null),
    )

    /** [name] como destino de un envío en [platform]: directo al buscador si no suele salir en la cuadrícula. */
    fun target(name: String, wantAvatar: Boolean, platform: Platform = Platform.INSTAGRAM): QuickSender.Target =
        QuickSender.Target(name, skipGrid = QuickSender.skipGrid(prefs.getInt(missesKey(platform, name), 0)), wantAvatar = wantAvatar)

    /** Tras un envío en [platform]: guarda los ids que han funcionado y, por destino, la racha de «no salió en la cuadrícula». */
    fun learn(report: QuickSender.Report, results: List<QuickSender.DestResult>, sheetOpened: Boolean, platform: Platform = Platform.INSTAGRAM) {
        val edit = prefs.edit()
        if (sheetOpened) report.shareId?.let { edit.putString(key(platform, SHARE_ID), it) }
        // El buscador se aprende también al verlo recorriendo la hoja (1.4.1), no solo al escribir en él.
        report.searchId?.let { edit.putString(key(platform, SEARCH_ID), it) }
        // El buscador aprendido ya no sale (la app lo ha cambiado): se olvida (revisión de la 1.4.3).
        if (report.searchId == null && report.forgetSearchId) edit.remove(key(platform, SEARCH_ID))
        results.forEach { r ->
            val k = missesKey(platform, r.name)
            edit.putInt(k, QuickSender.nextGridMisses(prefs.getInt(k, 0), r))
        }
        edit.apply()
    }

    /** Cuándo falló la última captura de la foto de [name] en un envío rápido (no se reintenta ese día). */
    fun avatarFailedAt(name: String, platform: Platform = Platform.INSTAGRAM): Long? =
        prefs.getLong(avatarKey(platform, name), 0L).takeIf { it > 0L }

    fun avatarFailed(name: String, now: Long, platform: Platform = Platform.INSTAGRAM) {
        prefs.edit().putLong(avatarKey(platform, name), now).apply()
    }

    var streak: DismissStreak
        get() = DismissStreak(prefs.getInt(STREAK, 0), prefs.getBoolean(ASKED, false))
        set(value) {
            prefs.edit().putInt(STREAK, value.count).putBoolean(ASKED, value.asked).apply()
        }

    private fun missesKey(platform: Platform, name: String) = key(platform, MISSES + AvatarRules.fileName(name, platform).removeSuffix(".png"))

    private fun avatarKey(platform: Platform, name: String) = key(platform, AVATAR_FAILED + AvatarRules.fileName(name, platform).removeSuffix(".png"))

    companion object {
        private const val PREFS = "quick_send"
        private const val SHARE_ID = "share_id"
        private const val SEARCH_ID = "search_id"
        private const val MISSES = "grid_misses_"
        private const val AVATAR_FAILED = "avatar_failed_"
        private const val STREAK = "dismiss_streak"
        private const val ASKED = "dismiss_asked"

        /** Instagram, la clave de siempre; las demás apps, con su prefijo (`tiktok_…`). */
        internal fun key(platform: Platform, base: String): String =
            if (platform == Platform.INSTAGRAM) base else platform.name.lowercase() + "_" + base

        fun clear(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
