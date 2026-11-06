package com.reelometro.app.copy

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Entrada del comprobador de traducciones y guardián de la batería normal: todas las carpetas `values-xx` que
 * haya tienen que pasar [I18nCheck] sin errores.
 *
 * Variables de entorno: `I18N_LANG` (calificadores separados por comas: `en`,
 * `pt-rBR`; vacío = todos) e `I18N_MODE` (`check` o `status`). El informe queda en
 * `app/build/i18n/report.txt`.
 */
class I18nCheckTest {

    @Test
    fun `las traducciones pasan el check`() {
        val wanted = System.getenv("I18N_LANG").orEmpty().split(',').map { it.trim().removePrefix("values-") }.filter { it.isNotEmpty() }
        val status = System.getenv("I18N_MODE") == "status"
        val existing = I18nCheck.qualifiers()
        val qualifiers = wanted.ifEmpty { existing }
        val base = I18nCheck.load(File(I18nCheck.resDir(), "values"))
        val total = (base.keys - base.untranslatable).size

        val out = StringBuilder()
        var failed = false
        if (qualifiers.isEmpty()) {
            out.appendLine("Sin traducciones todavía: solo values/ (español, $total claves por traducir en cada idioma).")
        }
        for (q in qualifiers) {
            if (q !in existing) {
                out.appendLine("values-$q: no existe la carpeta app/src/main/res/values-$q")
                failed = true
                continue
            }
            val report = I18nCheck.check(q)
            if (report.errors.isNotEmpty()) failed = true
            if (status) {
                val pct = if (report.total == 0) 100 else report.translated * 100 / report.total
                out.appendLine(
                    "values-$q: ${report.translated}/${report.total} ($pct %) · faltan ${report.total - report.translated} · " +
                        "${report.errors.size} errores · ${report.warnings.size} avisos",
                )
            } else {
                out.append(I18nCheck.format(report))
            }
        }
        val dir = if (File("src/main/res").isDirectory) File("build/i18n") else File("app/build/i18n")
        dir.mkdirs()
        File(dir, "report.txt").writeText(out.toString())
        if (failed && !status) fail(out.toString())
    }
}
