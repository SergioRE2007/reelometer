package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import java.util.Locale

/**
 * Quita nombres de personas y grupos del registro de Calibración antes de que salga del móvil (Copiar y Compartir).
 * Hoy el registro no lleva nombres de contactos (solo cuántos: «enviado a 2», «destino 2»), pero esto es la red por
 * si un cambio futuro los dejara pasar o un creador se llamara igual que un contacto.
 *
 * Se aplica solo a los campos libres de cada evento (nota y regla, ver [CalibrationLog.exportText]), nunca a la
 * cabecera ni a las claves fijas («clase=», «idx=»…): un contacto que se llame «Clase» o «Al» no debe destrozar el
 * registro.
 *
 * Reglas: palabras enteras (antes y después, nada que sea letra, cifra o «_»), sin distinguir mayúsculas, el nombre
 * más largo antes («Ana María» antes que «Ana») y en una sola pasada (lo que se pone no se vuelve a cambiar). Se
 * descartan los nombres de menos de [MIN_LENGTH] caracteres, los que no llevan ninguna letra («10», «...») y las
 * marcas de las apps y «Reelometer».
 *
 * Aparte, [redactPackages] tapa los nombres de paquete de otras apps en las descripciones de los cierres que pone el
 * sistema (`com.miui.securitycenter`, `com.android.systemui`…): dicen qué apps hay en el móvil. Los de Reelometer
 * ([OWN_PACKAGE] y lo que cuelga de él: `.qa`, `.debug`, sus clases) sí pueden salir.
 */
object CalibrationRedactor {

    private const val MIN_LENGTH = 3

    /** El paquete de Reelometer; sus copias (`.qa`, `.debug`) y sus clases cuelgan de él. */
    const val OWN_PACKAGE = "com.reelometro.app"

    /**
     * Un nombre de paquete (o de proceso o de clase): dos o más trozos separados por puntos que empiezan por letra
     * (grupo 1). No empieza detrás de una letra, cifra o «_» (no coge el final de otro nombre); detrás de un punto
     * suelto («...com.miui.home») sí se tapa. No acaba delante de una letra o cifra. Un punto final
     * («…com.miui.home.») no entra. Si detrás va una clase relativa («/.launcher.recents.RecentsView», con todos sus
     * trozos), entra en la misma coincidencia (grupo 2) y [redactPackages] la deja tal cual: es una clase del paquete de
     * delante, que ya se tapa. Un «/.» sin paquete delante no protege nada: «x/.com.otra.app» sale «x/.[otra app 1]».
     */
    private val PACKAGE =
        Regex("""(?<![\p{L}\p{N}_])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)(?![\p{L}\p{N}_])(/\.[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*)?""")

    /** Es de Reelometer: [OWN_PACKAGE] o algo que cuelga de él (`com.reelometro.app.qa`); `com.reelometro.application` no. */
    fun isOwnPackage(name: String): Boolean = name == OWN_PACKAGE || name.startsWith("$OWN_PACKAGE.")

    /**
     * Cambia en [text] cada nombre de paquete que no sea de Reelometer ([isOwnPackage]) por `token(n)`, con n = 1, 2…
     * por orden de aparición (el mismo paquete, el mismo número), en una sola pasada. Con el mismo [numbers] en varias
     * llamadas (las líneas de los cierres) la numeración se comparte entre todos esos textos.
     */
    fun redactPackages(text: String, token: (Int) -> String, numbers: MutableMap<String, Int> = HashMap()): String {
        if (text.isEmpty()) return text
        return PACKAGE.replace(text) { match ->
            val name = match.groupValues[1]
            val relative = match.groupValues[2] // "" si no lleva «/.clase»
            (if (isOwnPackage(name)) name else token(numbers.getOrPut(name) { numbers.size + 1 })) + relative
        }
    }

    private val KEEP: Set<String> =
        (Platform.entries.flatMap { listOf(it.shortName, it.displayName, it.appName) } + "Reelometer")
            .map { it.lowercase(Locale.ROOT) }
            .toSet()

    /**
     * Cambia en [text] cada aparición de los [names] por `token(n)`, con n = 1, 2… según el orden de [names] (sin
     * contar los descartados: vacíos, cortos, sin letras, repetidos o marcas).
     */
    fun redact(text: String, names: Collection<String>, token: (Int) -> String): String =
        compile(names, token)?.invoke(text) ?: text

    /**
     * Prepara la sustitución una vez para muchos textos (cada evento del registro); null si no hay nada que quitar.
     * Los números de cada nombre son los mismos en todos los textos.
     */
    fun compile(names: Collection<String>, token: (Int) -> String): ((String) -> String)? {
        val numbers = LinkedHashMap<String, Int>()
        for (raw in names) {
            val name = raw.trim()
            val key = name.lowercase(Locale.ROOT)
            if (name.length < MIN_LENGTH || name.none(Char::isLetter) || key in KEEP || key in numbers) continue
            numbers[key] = numbers.size + 1
        }
        if (numbers.isEmpty()) return null
        val alternatives = numbers.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val pattern = Regex("(?<![\\p{L}\\p{N}_])(?:$alternatives)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        return { text ->
            if (text.isEmpty()) {
                text
            } else {
                pattern.replace(text) { match ->
                    val n = numbers[match.value.lowercase(Locale.ROOT)]
                        ?: numbers.entries.firstOrNull { it.key.equals(match.value, ignoreCase = true) }?.value
                        ?: numbers.values.first() // Mayúsculas raras (ß, İ…): mejor un token cualquiera que dejar el nombre.
                    token(n)
                }
            }
        }
    }
}
