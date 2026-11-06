package com.reelometro.app.copy

/**
 * **Divulgación destacada** del servicio de accesibilidad (política de Google Play sobre
 * AccessibilityService y datos de usuario). Texto EXACTO: es el mismo que se declara en la ficha de
 * Google Play (y en el vídeo de la declaración de Play Console); si cambias algo aquí, cámbialo allí.
 *
 * Reglas de presentación (para la pantalla que la pinte):
 *  - Se muestra en el flujo normal (paso 3 del onboarding) y **antes** de abrir los ajustes de
 *    accesibilidad; también al pulsar CONECTAR / VER EL PERMISO si aún no se ha aceptado.
 *  - Pantalla propia, sin mezclarla con otros textos legales; todo el texto visible (con scroll).
 *  - Aceptar exige pulsar [ACCEPT]. Atrás, Inicio, tocar fuera o [DECLINE] **no** cuentan como
 *    aceptación. Nada se cierra solo.
 *  - Tras aceptar: `SettingsRepository.setDisclosureAccepted(true)` y abrir Accesibilidad.
 */
object DisclosureCopy {

    const val LABEL = "PERMISO DE ACCESIBILIDAD"

    const val TITLE = "Para contar, tengo que mirar."

    /** Párrafo principal: qué se lee, para qué y dónde se guarda. */
    const val SUMMARY =
        "Reelómetro usa el servicio de accesibilidad de Android para leer la pantalla de TikTok, " +
            "Instagram y YouTube mientras las usas. Así detecta cada vídeo corto que ves y guarda, solo en " +
            "este móvil, en qué app lo viste, cuándo, durante cuánto tiempo, quién lo publicó y su " +
            "descripción, para mostrarte tus estadísticas."

    /** Bloques con etiqueta, en este orden. */
    val SECTIONS: List<LabeledText> = listOf(
        LabeledText(
            "QUÉ LEE",
            "En TikTok, Instagram y YouTube: cuándo pasas al siguiente vídeo corto y los textos visibles " +
                "de ese vídeo (el nombre del creador, la descripción y los hashtags). También cuándo pulsas " +
                "«Compartir» y, solo en Instagram y si activas «A quién envías» en Ajustes, el nombre de la " +
                "persona o el grupo al que se lo mandas. Si además usas el envío rápido, lee los nombres de la hoja " +
                "de compartir para encontrar a quien has fijado.",
        ),
        LabeledText(
            "QUÉ NO LEE",
            "Nada de las demás apps: de ellas solo sabe que has salido, para dejar de contar. No guarda lo " +
                "que escribes ni tus mensajes. Solo pulsa por ti si usas el envío rápido de Instagram, y " +
                "únicamente «Compartir», la persona o el grupo que has fijado y «Enviar», al tocar su botón; si no " +
                "lo ve, escribe su nombre en el buscador de la hoja. Solo con el envío rápido, recorta de la hoja de " +
                "compartir la foto de perfil de las personas o grupos que fijas, para enseñarla en su botón; la guarda " +
                "solo en este móvil y no hace ninguna otra captura de pantalla.",
        ),
        LabeledText(
            "PARA QUÉ",
            "Solo para contarte cuántos vídeos ves, cuánto tiempo, de qué temas y de qué creadores. No se " +
                "usa para publicidad ni para hacer perfiles.",
        ),
        LabeledText(
            "DÓNDE ACABA",
            "En este móvil y en ningún otro sitio. No se envía a ningún servidor ni se comparte con nadie: " +
                "la app ni siquiera tiene permiso para usar internet.",
        ),
        LabeledText(
            "TÚ MANDAS",
            "Puedes desactivar el permiso cuando quieras en Ajustes > Accesibilidad y borrar todos los " +
                "datos desde la propia app.",
        ),
    )

    /** Frase junto a los botones: qué pasa si aceptas y si no. */
    const val CONSENT =
        "Si aceptas, te llevo a los ajustes de Android para activar el permiso. Si no, la app no podrá " +
            "contar nada; puedes activarlo más adelante desde Hoy o desde Ajustes."

    /** Botón de aceptación explícita. */
    const val ACCEPT = Buttons.ACCEPT_AND_ENABLE

    /** Botón para no aceptar (la app sigue funcionando, sin contar). */
    const val DECLINE = Buttons.NOT_NOW

    /** Confirmación discreta tras pulsar [DECLINE]. */
    const val DECLINED = "Sin permiso no hay cuenta. Cuando quieras, está en Hoy y en Ajustes."

    /** Todo el texto visible, en orden de lectura (para la tienda, el vídeo y los tests). */
    val fullText: List<String>
        get() = buildList {
            add(LABEL)
            add(TITLE)
            add(SUMMARY)
            SECTIONS.forEach {
                add(it.label)
                add(it.text)
            }
            add(CONSENT)
            add(ACCEPT)
            add(DECLINE)
        }
}
