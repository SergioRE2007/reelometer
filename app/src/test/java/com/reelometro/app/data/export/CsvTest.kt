package com.reelometro.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `entrecomilla solo cuando hace falta`() {
        assertEquals("simple", Csv.escape("simple"))
        assertEquals("", Csv.escape(""))
        assertEquals("12,3", Csv.escape("12,3")) // la coma decimal no es el separador
        assertEquals("\"a;b\"", Csv.escape("a;b"))
        assertEquals("\"di \"\"hola\"\"\"", Csv.escape("di \"hola\""))
        assertEquals("\"línea\nnueva\"", Csv.escape("línea\nnueva"))
        assertEquals("\"retorno\r\"", Csv.escape("retorno\r"))
        assertEquals("\" borde \"", Csv.escape(" borde "))
        assertEquals("a;\"b;c\";\r\n", Csv.row(listOf("a", "b;c", "")))
    }

    @Test
    fun `el texto libre no se puede ejecutar como fórmula`() {
        assertEquals("", Csv.text(null))
        assertEquals("", Csv.text(""))
        assertEquals("normal", Csv.text("normal"))
        assertEquals("'=SUMA(A1:A9)", Csv.text("=SUMA(A1:A9)"))
        assertEquals("'+18 años", Csv.text("+18 años"))
        assertEquals("'-50 % hoy", Csv.text("-50 % hoy"))
        assertEquals("'@alguien", Csv.text("@alguien"))
        assertEquals("con @ dentro", Csv.text("con @ dentro"))
    }

    @Test
    fun `segundos con coma decimal y sin separador de millar`() {
        assertEquals("0,0", Csv.seconds(0))
        assertEquals("0,0", Csv.seconds(-5))
        assertEquals("12,3", Csv.seconds(12_345))
        assertEquals("12,4", Csv.seconds(12_350))
        assertEquals("60,0", Csv.seconds(59_960))
        assertEquals("1234,6", Csv.seconds(1_234_567))
    }
}
