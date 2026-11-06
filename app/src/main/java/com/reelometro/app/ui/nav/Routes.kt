package com.reelometro.app.ui.nav

import kotlinx.serialization.Serializable

/*
 * Destinos de navegación (rutas tipadas de Navigation Compose).
 * Las cuatro primeras son pestañas (con barra inferior; Pasaporte solo con el modo pasaporte);
 * el resto se apilan encima.
 */

@Serializable data object TodayDest

@Serializable data object StatsDest

@Serializable data object WrappedDest

/** Modo Pasaporte (Flap, niveles, sellos y reto semanal). Pestaña opcional (Ajustes › Modo pasaporte). */
@Serializable data object PassportDest

@Serializable data object SettingsDest

@Serializable data object CalibrationDest

@Serializable data object OnboardingDest

/**
 * Conectar el servicio de accesibilidad fuera del onboarding (desde Hoy y Ajustes): divulgación
 * destacada (si no se ha aceptado) + guía paso a paso + «Ajuste restringido».
 */
@Serializable data object ServiceSetupDest

/** Detalle de un día concreto (`LocalDate.toEpochDay()`). */
@Serializable data class DayDest(val epochDay: Long)

/** Wrapped a pantalla completa. `kind` = WEEK | MONTH | YEAR | ALL; `offset` ≤ 0 (0 = periodo actual). */
@Serializable data class StoryDest(val kind: String, val offset: Int)

/** Pestañas en orden de la barra inferior (con el modo pasaporte activo). */
val TabDests: List<Any> = listOf(TodayDest, StatsDest, WrappedDest, PassportDest)

/** Pestañas visibles: sin el modo pasaporte, la cuarta no existe. */
fun tabDests(passportEnabled: Boolean): List<Any> = if (passportEnabled) TabDests else TabDests.take(3)

/**
 * Traduce el extra `route` de MainActivity (notificaciones, widget, QA por adb) a un destino:
 * `today | stats | wrapped | passport | settings | calibration | onboarding | setup | day:<epochDay> | story:<KIND>:<offset>`.
 * `passport` se ignora con el modo pasaporte apagado (lo decide `AppShell`).
 */
object DeepLinks {
    fun parse(route: String?): Any? {
        val r = route?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = r.split(':')
        return when (parts[0].lowercase()) {
            "today", "hoy" -> TodayDest
            "stats", "estadisticas" -> StatsDest
            "wrapped", "resumen" -> WrappedDest
            "passport", "pasaporte" -> PassportDest
            "settings", "ajustes" -> SettingsDest
            "calibration", "calibracion" -> CalibrationDest
            "onboarding" -> OnboardingDest
            "setup", "conectar" -> ServiceSetupDest
            "day" -> parts.getOrNull(1)?.toLongOrNull()?.let(::DayDest)
            "story" -> {
                val kind = parts.getOrNull(1)?.uppercase()?.takeIf { it in STORY_KINDS } ?: "MONTH"
                val offset = parts.getOrNull(2)?.toIntOrNull()?.coerceAtMost(0) ?: 0
                StoryDest(kind, offset)
            }
            else -> null
        }
    }

    fun forDay(epochDay: Long) = "day:$epochDay"
    fun forStory(kind: String, offset: Int) = "story:$kind:$offset"

    private val STORY_KINDS = setOf("WEEK", "MONTH", "YEAR", "ALL")
}
