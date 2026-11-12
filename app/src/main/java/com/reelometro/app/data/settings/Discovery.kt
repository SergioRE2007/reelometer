package com.reelometro.app.data.settings

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Descubrimiento progresivo de las pestañas (lógica PURA, sin Android: se prueba con tests JVM).
 *
 * Quien instala la app de cero empieza solo con Hoy; las demás pestañas aparecen según cuenta días:
 * Hoy → +Estadísticas → +Resumen → +Flap (un prefijo que crece: los índices de la barra no cambian).
 * Quien ya usaba la app antes de esta versión ([DiscoveryState.progressive] = `false`) lo ve todo, como siempre.
 */

/** Pestañas que se descubren; [tabs] = cuántas pestañas hay a la vista cuando ya ha aparecido. */
enum class DiscoveryFeature(val tabs: Int) {
    STATS(2),
    WRAPPED(3),

    /** La pestaña de Flap (antes «Pasaporte»; el nombre interno se queda). */
    PASSPORT(4),
    ;

    companion object {
        fun forTabs(tabs: Int): DiscoveryFeature? = entries.firstOrNull { it.tabs == tabs }
    }
}

/**
 * Estado guardado del descubrimiento.
 *
 * @param progressive `false` = todo a la vista (usuarios de antes, o aún sin decidir).
 * @param tabs pestañas descubiertas (1..4). Solo sube.
 * @param tabsDay `epochDay` del último descubrimiento (una pestaña nueva como mucho por día).
 * @param announced hasta qué pestaña se ha presentado ya en Hoy (1 = ninguna).
 */
data class DiscoveryState(
    val progressive: Boolean = false,
    val tabs: Int = Discovery.ALL_TABS,
    val tabsDay: Long? = null,
    val announced: Int = Discovery.ALL_TABS,
) {
    companion object {
        /** Todo a la vista: usuarios de antes y estado sin decidir. */
        val ALL_VISIBLE = DiscoveryState()

        /** Instalación nueva: solo Hoy, nada presentado. */
        val FRESH = DiscoveryState(progressive = true, tabs = 1, announced = 1)
    }
}

object Discovery {
    const val ALL_TABS = 4

    /**
     * Días para cada pestaña. Son días de CALENDARIO desde el primero con datos hasta hoy (ambos incluidos),
     * no días con registros: un día sin ver vídeos cuenta igual. Es a propósito (sin consultas nuevas a la
     * base) y la banda «DESDE EL…» de Hoy usa la misma cuenta.
     */
    const val STATS_DAYS = 2
    const val WRAPPED_DAYS = 3
    const val PASSPORT_DAYS = 5

    /**
     * Con tanto historial (de calendario) y solo Hoy a la vista (copia restaurada, QA, quien no abre la app en
     * dos semanas), se descubre todo de golpe y sin anuncios.
     */
    const val VETERAN_DAYS = 14

    /**
     * Se decide una sola vez: progresivo solo en una instalación nueva (sin onboarding, sin recorrido y
     * sin haber aceptado la divulgación). Quien ya usaba la app lo sigue viendo todo.
     */
    fun startsProgressive(onboardingDone: Boolean, tourDone: Boolean, disclosureAccepted: Boolean): Boolean =
        !onboardingDone && !tourDone && !disclosureAccepted

    /** Días de calendario desde [firstDay] hasta [today], ambos incluidos (0 sin datos o con fechas raras). */
    fun trackedDays(firstDay: LocalDate?, today: LocalDate): Long {
        if (firstDay == null || firstDay.isAfter(today)) return 0
        return ChronoUnit.DAYS.between(firstDay, today) + 1
    }

    /**
     * Cuántas pestañas merece el historial (1..4), sin contar el límite de una por día:
     * - Estadísticas con 2 días (la misma condición que la banda «DESDE EL…» de Hoy).
     * - Resumen con 3 días y una semana cerrada con datos (el primer día es anterior al lunes de esta semana).
     * - Flap con 5 días y esa misma semana cerrada.
     */
    fun eligibleTabs(firstDay: LocalDate?, today: LocalDate): Int {
        val days = trackedDays(firstDay, today)
        if (firstDay == null || days < STATS_DAYS) return 1
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val closedWeek = firstDay.isBefore(monday)
        return when {
            closedWeek && days >= PASSPORT_DAYS -> 4
            closedWeek && days >= WRAPPED_DAYS -> 3
            else -> 2
        }
    }

    /**
     * Sube el estado según el historial: una pestaña más como mucho por día, nunca baja (ni al borrar los
     * datos) y, con solo Hoy a la vista y [VETERAN_DAYS] días o más, lo descubre todo sin anuncios.
     */
    fun advance(state: DiscoveryState, firstDay: LocalDate?, today: LocalDate): DiscoveryState {
        if (!state.progressive) return state
        val epochDay = today.toEpochDay()
        if (state.tabs <= 1 && trackedDays(firstDay, today) >= VETERAN_DAYS) {
            return state.copy(tabs = ALL_TABS, announced = ALL_TABS, tabsDay = epochDay)
        }
        val eligible = eligibleTabs(firstDay, today)
        if (eligible <= state.tabs || state.tabsDay == epochDay) return state
        return state.copy(tabs = (state.tabs + 1).coerceAtMost(ALL_TABS), tabsDay = epochDay)
    }

    private fun maxTabs(passportEnabled: Boolean) = if (passportEnabled) ALL_TABS else ALL_TABS - 1

    /** Pestañas en la barra: todas sin el modo progresivo (3 con Flap apagado); si no, las descubiertas. */
    fun visibleTabs(state: DiscoveryState, passportEnabled: Boolean): Int {
        val max = maxTabs(passportEnabled)
        return if (!state.progressive) max else state.tabs.coerceIn(1, max)
    }

    /** La siguiente pestaña descubierta y sin presentar en Hoy (en orden), o `null`. */
    fun pendingNews(state: DiscoveryState, passportEnabled: Boolean): DiscoveryFeature? {
        if (!state.progressive) return null
        return if (state.announced < visibleTabs(state, passportEnabled)) DiscoveryFeature.forTabs(state.announced + 1) else null
    }

    /** Lo que aún falta por aparecer (sin Flap si está apagado en Ajustes). */
    fun hidden(state: DiscoveryState, passportEnabled: Boolean): List<DiscoveryFeature> {
        if (!state.progressive) return emptyList()
        return DiscoveryFeature.entries.filter { it.tabs > state.tabs && (passportEnabled || it != DiscoveryFeature.PASSPORT) }
    }

    /**
     * La novedad de [feature] se ha visto (VER… o ENTENDIDO): pasa a la siguiente. Solo avanza si es la que
     * tocaba presentar: un segundo toque sobre una novedad ya cerrada (el botón tarda en desaparecer) no se
     * come la siguiente sin que llegue a salir.
     */
    fun announce(state: DiscoveryState, feature: DiscoveryFeature): DiscoveryState {
        if (!state.progressive || feature.tabs != state.announced + 1 || feature.tabs > state.tabs) return state
        return state.copy(announced = feature.tabs)
    }

    /**
     * Han llevado al usuario a una pestaña aún oculta (enlace, banda, aviso): se descubre hasta ella, para que
     * nadie se quede en una pantalla sin su pestaña. Solo se da por presentada si era la siguiente novedad;
     * las pestañas intermedias que se saltan conservan la suya (Estadísticas y Resumen no pierden su «NUEVO»
     * porque un enlace lleve directo a Flap).
     */
    fun revealTo(state: DiscoveryState, tabs: Int, epochDay: Long): DiscoveryState {
        if (!state.progressive) return state
        val target = tabs.coerceIn(1, ALL_TABS)
        val newTabs = maxOf(state.tabs, target)
        val newAnnounced = if (target > state.tabs && target == state.announced + 1) target else state.announced
        if (newTabs == state.tabs && newAnnounced == state.announced) return state
        return state.copy(
            tabs = newTabs,
            announced = newAnnounced,
            tabsDay = if (newTabs != state.tabs) epochDay else state.tabsDay,
        )
    }

    /** ENSEÑARLO TODO YA: todas las pestañas, sin anuncios. */
    fun showAll(state: DiscoveryState): DiscoveryState {
        if (!state.progressive || (state.tabs == ALL_TABS && state.announced == ALL_TABS)) return state
        return state.copy(tabs = ALL_TABS, announced = ALL_TABS)
    }
}
