package com.reelometro.app.copy

import com.reelometro.app.R

/**
 * **Divulgación destacada** del servicio de accesibilidad (política de Google Play sobre
 * AccessibilityService y datos de usuario). Texto EXACTO (en `res/values/copy_disclosure.xml`): es el
 * mismo que se declara en la ficha de Google Play (y en el vídeo de la declaración de Play Console);
 * si cambias algo aquí, cámbialo allí.
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

    val LABEL: String get() = str(R.string.disclosure_label)

    val TITLE: String get() = str(R.string.disclosure_title)

    /** Párrafo principal: qué se lee, para qué y dónde se guarda. */
    val SUMMARY: String get() = str(R.string.disclosure_summary)

    /** Bloques con etiqueta, en este orden. */
    val SECTIONS: List<LabeledText>
        get() = listOf(
            LabeledText(str(R.string.disclosure_reads_label), str(R.string.disclosure_reads_text)),
            LabeledText(str(R.string.disclosure_not_reads_label), str(R.string.disclosure_not_reads_text)),
            LabeledText(str(R.string.disclosure_purpose_label), str(R.string.disclosure_purpose_text)),
            LabeledText(str(R.string.disclosure_storage_label), str(R.string.disclosure_storage_text)),
            LabeledText(str(R.string.disclosure_control_label), str(R.string.disclosure_control_text)),
        )

    /** Frase junto a los botones: qué pasa si aceptas y si no. */
    val CONSENT: String get() = str(R.string.disclosure_consent)

    /** Botón de aceptación explícita. */
    val ACCEPT: String get() = Buttons.ACCEPT_AND_ENABLE

    /** Botón para no aceptar (la app sigue funcionando, sin contar). */
    val DECLINE: String get() = Buttons.NOT_NOW

    /** Confirmación discreta tras pulsar [DECLINE]. */
    val DECLINED: String get() = str(R.string.disclosure_declined)

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
