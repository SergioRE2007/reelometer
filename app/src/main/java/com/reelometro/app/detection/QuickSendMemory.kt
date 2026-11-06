package com.reelometro.app.detection

import android.content.Context

/**
 * Lo que el servicio recuerda del **envío rápido** entre envíos (SharedPreferences `quick_send`, solo en el
 * móvil y sin copia en la nube):
 *  - los ids del botón compartir y del buscador de la hoja la última vez que acertaron ([QuickSender.Hints]);
 *  - por fijado (con un hash del nombre, nunca el nombre), cuántas veces seguidas no salió en la cuadrícula y
 *    cuándo falló la última captura de su foto en un envío rápido (1.4.1: no se reintenta ese día);
 *  - la racha de burbujas escondidas en la «X» ([DismissStreak]).
 * BORRAR TODO lo olvida ([clear]). Solo desde el hilo principal.
 */
internal class QuickSendMemory(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ids aprendidos (compartir y buscador), comunes a todos los fijados. */
    fun hints(): QuickSender.Hints = QuickSender.Hints(
        shareId = prefs.getString(SHARE_ID, null),
        searchId = prefs.getString(SEARCH_ID, null),
    )

    /** [name] como destino de un envío: directo al buscador si no suele salir en la cuadrícula. */
    fun target(name: String, wantAvatar: Boolean): QuickSender.Target =
        QuickSender.Target(name, skipGrid = QuickSender.skipGrid(prefs.getInt(missesKey(name), 0)), wantAvatar = wantAvatar)

    /** Tras un envío: guarda los ids que han funcionado y, por destino, la racha de «no salió en la cuadrícula». */
    fun learn(report: QuickSender.Report, results: List<QuickSender.DestResult>, sheetOpened: Boolean) {
        val edit = prefs.edit()
        if (sheetOpened) report.shareId?.let { edit.putString(SHARE_ID, it) }
        // El buscador se aprende también al verlo recorriendo la hoja (1.4.1), no solo al escribir en él.
        report.searchId?.let { edit.putString(SEARCH_ID, it) }
        // El buscador aprendido ya no sale (Instagram lo ha cambiado): se olvida (revisión de la 1.4.3).
        if (report.searchId == null && report.forgetSearchId) edit.remove(SEARCH_ID)
        results.forEach { r -> edit.putInt(missesKey(r.name), QuickSender.nextGridMisses(prefs.getInt(missesKey(r.name), 0), r)) }
        edit.apply()
    }

    /** Cuándo falló la última captura de la foto de [name] en un envío rápido (no se reintenta ese día). */
    fun avatarFailedAt(name: String): Long? = prefs.getLong(avatarKey(name), 0L).takeIf { it > 0L }

    fun avatarFailed(name: String, now: Long) {
        prefs.edit().putLong(avatarKey(name), now).apply()
    }

    var streak: DismissStreak
        get() = DismissStreak(prefs.getInt(STREAK, 0), prefs.getBoolean(ASKED, false))
        set(value) {
            prefs.edit().putInt(STREAK, value.count).putBoolean(ASKED, value.asked).apply()
        }

    private fun missesKey(name: String) = MISSES + AvatarRules.fileName(name).removeSuffix(".png")

    private fun avatarKey(name: String) = AVATAR_FAILED + AvatarRules.fileName(name).removeSuffix(".png")

    companion object {
        private const val PREFS = "quick_send"
        private const val SHARE_ID = "share_id"
        private const val SEARCH_ID = "search_id"
        private const val MISSES = "grid_misses_"
        private const val AVATAR_FAILED = "avatar_failed_"
        private const val STREAK = "dismiss_streak"
        private const val ASKED = "dismiss_asked"

        fun clear(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
