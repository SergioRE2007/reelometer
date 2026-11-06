package com.reelometro.app.copy

import com.reelometro.app.stats.Format

/**
 * **Onboarding** (primera vez, 4 pasos a pantalla completa):
 *  1. Qué hace · 2. Privacidad · 3. Permiso de accesibilidad ([DisclosureCopy] + guía + «Ajuste
 *  restringido») · 4. Avisos y objetivo diario opcional.
 *
 * Los nombres de menús de Android van entre comillas latinas y tal como salen en español
 * (AOSP/Pixel; los fabricantes a veces los cambian, por eso se dan alternativas).
 */
object OnboardingCopy {

    const val STEPS = 4

    /** «PASO 1 DE 4». */
    fun stepLabel(index: Int, total: Int = STEPS): String = "PASO $index DE $total"

    /** Para los pasos 1–2. Nunca debe saltarse la divulgación del paso 3. */
    const val SKIP = "SALTAR"

    // ------------------------------------------------------------------ 1. Qué hace

    const val WHAT_TITLE = "Cuenta lo que no cuentas."

    const val WHAT_BODY =
        "Reelómetro cuenta los vídeos cortos que ves en TikTok, Instagram Reels y YouTube Shorts: " +
            "cuántos, cuánto tiempo, de qué temas y de qué creadores. El dato es tuyo; las conclusiones, también."

    /** Hasta dónde cuenta solo el marcador de demostración. */
    const val WHAT_DEMO_COUNT = 212

    /** Tiempo de la demostración: 47 minutos, los de la frase de referencia. */
    const val WHAT_DEMO_MS = 47 * 60_000L

    /** Línea bajo el marcador de demostración: «47 MIN DE PANTALLA». */
    fun demoTime(ms: Long): String = "${Words.durationLabel(ms)} DE PANTALLA"

    /** contentDescription del marcador de demostración: «Un marcador que cuenta hasta 212 vídeos.». */
    fun demoDescription(count: Int): String = "Un marcador que cuenta hasta ${Words.videos(count)}."

    // ------------------------------------------------------------------ 2. Privacidad

    const val PRIVACY_TITLE = "Lo que ves se queda aquí."

    val PRIVACY_POINTS: List<LabeledText> = listOf(
        LabeledText("SIN CUENTAS", "Ni registro, ni correo, ni contraseña."),
        LabeledText("SIN SERVIDORES", "Todo se guarda en este móvil. No hay copia en ningún otro sitio."),
        LabeledText("SIN ANUNCIOS", "Ni anuncios ni rastreadores. Nadie más mira tus datos."),
        LabeledText("SIN INTERNET", "La app no tiene permiso para conectarse. Aunque quisiera, no podría enviar nada."),
    )

    const val PRIVACY_STAMP = Labels.LOCAL_STAMP

    /** Segunda línea del sello. */
    const val PRIVACY_STAMP_SUBTEXT = "SOLO EN ESTE MÓVIL"

    const val PRIVACY_FOOTNOTE = "Si desinstalas la app, todo se va con ella."

    // ------------------------------------------------------------------ 3. Permiso (guía)
    // La divulgación destacada está en DisclosureCopy y va ANTES de esta guía.

    const val GUIDE_TITLE = "Un interruptor y un «Permitir»."

    val GUIDE_STEPS: List<Step> = listOf(
        Step(1, "Pulsa ABRIR ACCESIBILIDAD: te llevo a los ajustes de Android."),
        Step(
            2,
            "Busca «Reelómetro» en «Aplicaciones descargadas». En algunos móviles está dentro de " +
                "«Aplicaciones instaladas» o «Servicios instalados».",
        ),
        Step(3, "Toca «Reelómetro» y activa «Usar Reelómetro»."),
        Step(
            4,
            "Android preguntará si permites que Reelómetro «pueda controlar totalmente tu dispositivo». " +
                "Es el aviso estándar de cualquier servicio de accesibilidad: Reelómetro mira y cuenta, y solo " +
                "pulsa por ti si usas el envío rápido de Instagram. Toca «Permitir».",
        ),
        Step(5, "Vuelve aquí. Si el marcador dice OK, ya está."),
    )

    const val GUIDE_BUTTON = Buttons.OPEN_ACCESSIBILITY

    /** Etiqueta mientras se espera a que el servicio se conecte. */
    const val GUIDE_WAITING = "ESPERANDO AL SERVICIO"

    const val GUIDE_CONNECTED_LABEL = Labels.CONNECTED

    /** Lo que muestra el marcador al conectar (voltea a «OK» + háptico CONFIRM). */
    const val GUIDE_CONNECTED_COUNTER = "OK"

    /** Al volver con el servicio activo. */
    fun connected(seed: Long): String = pick(
        seed, "onboarding.connected",
        "Conectado. Desde ahora, cada vídeo cuenta.",
        "Listo. A partir de aquí, todo lo que veas queda contado.",
        "Conectado. El marcador ya está mirando.",
    )

    /** Al volver sin haberlo activado. */
    fun notYet(seed: Long): String = pick(
        seed, "onboarding.notYet",
        "Todavía no está activado. Sin prisa: los pasos siguen aquí.",
        "Aún no veo el servicio. Repasa los pasos; suele faltar el último interruptor.",
        "El servicio sigue apagado. Si Android dice «Ajuste restringido», mira justo debajo.",
    )

    /** Bajo el «OK»: qué pasa ahora. */
    const val GUIDE_CONNECTED_BODY =
        "Usa TikTok, Instagram o YouTube como siempre. Yo voy sumando en Hoy."

    /** Con el servicio ya activo, en la pantalla de conectar (fuera del onboarding). */
    const val GUIDE_DONE = Buttons.DONE

    /** Seguir sin activar el servicio (solo en el onboarding: la divulgación ya se ha visto). */
    const val GUIDE_SKIP = Buttons.NEXT

    /** contentDescription del marcador de estado: conectado o esperando. */
    fun guideStatusDescription(connected: Boolean): String =
        if (connected) "Servicio conectado" else "Servicio todavía sin conectar"

    /** Enlace a la ayuda de «Ajuste restringido». */
    const val RESTRICTED_LINK = "¿TE SALE «AJUSTE RESTRINGIDO»?"

    // ------------------------------------------------------------------ 3b. «Ajuste restringido» (Android 13+)

    const val RESTRICTED_TITLE = "¿Android dice «Ajuste restringido»?"

    const val RESTRICTED_BODY =
        "Es una protección de Android para las apps instaladas fuera de Google Play, no un fallo. Se quita así:"

    val RESTRICTED_STEPS: List<Step> = listOf(
        Step(1, "Pulsa ABRIR INFORMACIÓN DE LA APP."),
        Step(2, "En «Información de la aplicación», toca el menú ⋮ de arriba a la derecha."),
        Step(3, "Toca «Permitir ajustes restringidos» y confirma con tu PIN, patrón o huella."),
        Step(4, "Vuelve a Accesibilidad y activa «Usar Reelómetro»."),
    )

    const val RESTRICTED_NOTE =
        "«Permitir ajustes restringidos» solo aparece después de que Android te haya bloqueado una vez. " +
            "Si no lo ves, intenta activar el servicio primero y vuelve aquí."

    const val RESTRICTED_BUTTON = Buttons.OPEN_APP_INFO

    // ------------------------------------------------------------------ 4. Avisos y objetivo

    const val FINAL_TITLE = "Un aviso al día. Y un límite, si quieres."

    const val NOTIFY_DAILY_LABEL = "Aviso diario"

    /** «A las 21:30, tu cuenta del día.». */
    fun notifyDaily(hour: Int, minute: Int): String = "${Format.capitalize(Words.atTime(hour, minute))}, tu cuenta del día."

    const val NOTIFY_MONTHLY_LABEL = "Resumen mensual"

    const val NOTIFY_MONTHLY = "El día 1 de cada mes, el mes anterior, contado."

    /** Antes de pedir POST_NOTIFICATIONS (Android 13+). */
    const val NOTIFY_RATIONALE = "Para avisarte, Android necesita tu permiso. Solo lo uso para esto."

    const val NOTIFY_BUTTON = Buttons.ALLOW_NOTIFICATIONS

    const val NOTIFY_DENIED = "Sin avisos, entonces. Puedes activarlos cuando quieras en Ajustes."

    const val GOAL_LABEL = "OBJETIVO DIARIO · OPCIONAL"

    const val GOAL_BODY =
        "Un máximo de vídeos al día. Con él, el marcador te dice cuánto te queda y lleva la cuenta de tus " +
            "rachas. Sin él, solo cuenta."

    const val GOAL_NONE = "SIN OBJETIVO"

    const val GOAL_OTHER = "OTRO"

    /** Botones del selector «OTRO» (TalkBack). */
    const val GOAL_DECREASE = "Bajar el objetivo"
    const val GOAL_INCREASE = "Subir el objetivo"

    /** Opción numérica: «150». */
    fun goalOption(goal: Int): String = Format.integer(goal)

    /** Ayuda del campo «OTRO»: «Entre 5 y 2.000 vídeos al día.». */
    fun goalRange(min: Int, max: Int): String = "Entre ${Format.integer(min)} y ${Format.integer(max)} vídeos al día."

    /** Confirmación de la elección: «Como mucho, 150 al día.» / «Sin objetivo: solo contar.». */
    fun goalChosen(goal: Int?): String =
        if (goal == null || goal <= 0) "Sin objetivo: solo contar." else "Como mucho, ${Format.integer(goal)} al día."

    const val FINAL_BUTTON = Buttons.START

    /** Cabeceras de los dos bloques del paso 4. */
    const val NOTIFY_SECTION = "AVISOS"

    /** contentDescription del indicador de pasos: «Paso 2 de 4». */
    fun stepDescription(index: Int, total: Int = STEPS): String = "Paso $index de $total"
}
