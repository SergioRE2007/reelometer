package com.reelometro.app

/**
 * Por dónde se distribuye esta build. Sale de la propiedad de Gradle `-Pdistribution=github|play`
 * (por defecto `github`), que `app/build.gradle.kts` guarda en `BuildConfig.DISTRIBUTION`.
 *
 * - [Distribution.GITHUB]: la APK de las Releases de GitHub, la de siempre.
 * - [Distribution.PLAY]: el AAB para Google Play. Misma app; solo cambian textos que la política de pagos
 *   de Play no admite (nada de invitar a apoyar o a donar): ver `copy/SupportCopy`.
 */
enum class Distribution(val id: String) {
    GITHUB("github"),
    PLAY("play");

    companion object {
        /** [id] tal y como llega de Gradle; uno desconocido es un error (Gradle ya lo para antes). */
        fun fromId(id: String): Distribution =
            entries.firstOrNull { it.id == id } ?: error("Distribución desconocida: «$id» (github o play)")
    }
}

object AppDistribution {
    val current: Distribution = Distribution.fromId(BuildConfig.DISTRIBUTION)
    val isPlay: Boolean get() = current == Distribution.PLAY
}
