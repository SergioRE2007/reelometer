package com.reelometro.app.debug

import android.content.Context

/** Versión vacía para release: ver la de `src/debug`. */
object DebugTools {
    const val AVAILABLE = false

    const val DEFAULT_SEED = 0L

    @Suppress("UNUSED_PARAMETER")
    suspend fun seedSampleData(context: Context, weeks: Int = 8, seed: Long = DEFAULT_SEED): Int = 0

    @Suppress("UNUSED_PARAMETER")
    suspend fun clearData(context: Context) = Unit
}
