package com.reelometro.app.game

/**
 * Flap en pixel art: una ficha split-flap con ojos (rejilla de [SIZE]×[SIZE]). Son datos puros
 * para que lo pinten igual la pantalla (Compose `Canvas`) y el widget (bitmap).
 *
 * Cada fila es una cadena de [SIZE] caracteres; cada carácter, un píxel:
 *  - `.` vacío (transparente)
 *  - `X` tinta (`text`: hueso en oscuro, tinta en claro)
 *  - `s` ficha (`bgSunken`)
 *  - `h` bisagra (`bg`)
 *  - `m` gris (`textMuted`): párpados, sombras
 *  - `o` acento (`signal`): solo detalles
 *
 * La animación (respirar, saltar, temblar, cambiar de estado) se hace con transformaciones sobre
 * el dibujo; el parpadeo cambia de [FlapFrame.OPEN] a [FlapFrame.BLINK]. Cada estado se distingue
 * también sin color (solo con la tinta), del mejor al peor:
 *  - EN FORMA: brazos arriba, sonrisa ancha y mofletes.
 *  - NORMAL: ojos y boca rectos, sin nada más.
 *  - CANSADO: párpados caídos, boca hacia abajo y una gota encima.
 *  - ENFERMO: ojos entornados (párpado arriba y ojera abajo), termómetro en la boca que sale por
 *    la derecha con la punta en acento, gota de sudor a la izquierda y bufanda gris.
 *  - KO: ficha aplastada contra el suelo (más ancha y baja, sin patas), ojos en X y la lengua
 *    fuera en acento. No parpadea: su [FlapFrame.BLINK] es igual que [FlapFrame.OPEN].
 * Los párpados del parpadeo ocupan las filas 2–5 (ojos); nada más cambia entre fotogramas.
 *
 * Para pintarlo con los tokens del tema, usa [pixel] (pantalla y widget, el mismo mapeo).
 */
object FlapSprite {
    const val SIZE = 16

    /** Caracteres válidos en las rejillas. */
    const val PIXELS = ".Xshmo"

    /** Papel de un carácter de la rejilla ([FlapPixel.EMPTY] para `.` y cualquier otro). */
    fun pixel(c: Char): FlapPixel = when (c) {
        'X' -> FlapPixel.INK
        's' -> FlapPixel.TILE
        'h' -> FlapPixel.HINGE
        'm' -> FlapPixel.MUTED
        'o' -> FlapPixel.ACCENT
        else -> FlapPixel.EMPTY
    }

    fun frame(mood: FlapMood, frame: FlapFrame = FlapFrame.OPEN): List<String> =
        FRAMES.getValue(mood to frame)

    private val FRAMES: Map<Pair<FlapMood, FlapFrame>, List<String>> = mapOf(
        (FlapMood.EN_FORMA to FlapFrame.OPEN) to listOf(
            "X..............X",
            "X.XXXXXXXXXXXX.X",
            "XXssssssssssssXX",
            ".XssXXssssXXssX.",
            ".XssXXssssXXssX.",
            ".XssssssssssssX.",
            ".XsoossssssoosX.",
            ".XhhhhhhhhhhhhX.",
            ".XssXssssssXssX.",
            ".XsssXXXXXXsssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        (FlapMood.EN_FORMA to FlapFrame.BLINK) to listOf(
            "X..............X",
            "X.XXXXXXXXXXXX.X",
            "XXssssssssssssXX",
            ".XssssssssssssX.",
            ".XsXXXssssXXXsX.",
            ".XssssssssssssX.",
            ".XsoossssssoosX.",
            ".XhhhhhhhhhhhhX.",
            ".XssXssssssXssX.",
            ".XsssXXXXXXsssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        (FlapMood.NORMAL to FlapFrame.OPEN) to listOf(
            "................",
            "..XXXXXXXXXXXX..",
            ".XssssssssssssX.",
            ".XssXXssssXXssX.",
            ".XssXXssssXXssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XssssssssssssX.",
            ".XssssXXXXssssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        (FlapMood.NORMAL to FlapFrame.BLINK) to listOf(
            "................",
            "..XXXXXXXXXXXX..",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XsXXXssssXXXsX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XssssssssssssX.",
            ".XssssXXXXssssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        (FlapMood.CANSADO to FlapFrame.OPEN) to listOf(
            "..............o.",
            "..XXXXXXXXXXXX..",
            ".XssssssssssssX.",
            ".XssmmssssmmssX.",
            ".XssXXssssXXssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XsssXXXXXXsssX.",
            ".XssXssssssXssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "....XX....XX....",
            "................",
        ),
        (FlapMood.CANSADO to FlapFrame.BLINK) to listOf(
            "..............o.",
            "..XXXXXXXXXXXX..",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XsXXXssssXXXsX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XsssXXXXXXsssX.",
            ".XssXssssssXssX.",
            ".XssssssssssssX.",
            ".XssssssssssssX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "....XX....XX....",
            "................",
        ),
        (FlapMood.ENFERMO to FlapFrame.OPEN) to listOf(
            "................",
            "..XXXXXXXXXXXX..",
            "mXssssssssssssX.",
            "mXssmmssssmmssX.",
            ".XssXXssssXXssX.",
            ".XssmmssssmmssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XssssssssssssX.",
            ".XsssXXXXXXXXXXo",
            ".XsssXXsssssssX.",
            ".XmmmmmmmmmmmmX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        (FlapMood.ENFERMO to FlapFrame.BLINK) to listOf(
            "................",
            "..XXXXXXXXXXXX..",
            "mXssssssssssssX.",
            "mXssssssssssssX.",
            ".XsXXXssssXXXsX.",
            ".XssmmssssmmssX.",
            ".XssssssssssssX.",
            ".XhhhhhhhhhhhhX.",
            ".XssssssssssssX.",
            ".XsssXXXXXXXXXXo",
            ".XsssXXsssssssX.",
            ".XmmmmmmmmmmmmX.",
            "..XXXXXXXXXXXX..",
            "....X......X....",
            "...XX......XX...",
            "................",
        ),
        // Ojos en X: no parpadea (el fotograma BLINK es el mismo).
        (FlapMood.KO to FlapFrame.OPEN) to listOf(
            "................",
            "................",
            "................",
            "................",
            "................",
            ".XXXXXXXXXXXXXX.",
            "XssssssssssssssX",
            "XsXsXssssssXsXsX",
            "XssXssssssssXssX",
            "XsXsXssssssXsXsX",
            "XhhhhhhhhhhhhhhX",
            "XssssssssssssssX",
            "XssssXXXXXXssssX",
            "XssssssssoossssX",
            ".XXXXXXXXXXXXXX.",
            "................",
        ),
        (FlapMood.KO to FlapFrame.BLINK) to listOf(
            "................",
            "................",
            "................",
            "................",
            "................",
            ".XXXXXXXXXXXXXX.",
            "XssssssssssssssX",
            "XsXsXssssssXsXsX",
            "XssXssssssssXssX",
            "XsXsXssssssXsXsX",
            "XhhhhhhhhhhhhhhX",
            "XssssssssssssssX",
            "XssssXXXXXXssssX",
            "XssssssssoossssX",
            ".XXXXXXXXXXXXXX.",
            "................",
        ),
    )
}

/** Fotogramas de Flap. */
enum class FlapFrame { OPEN, BLINK }

/**
 * Papel de cada píxel de Flap, con su token de color: [INK] `text` (hueso en oscuro, tinta en
 * claro), [TILE] `bgSunken`, [HINGE] `bg`, [MUTED] `textMuted`, [ACCENT] `signal` y [EMPTY] nada.
 */
enum class FlapPixel { EMPTY, INK, TILE, HINGE, MUTED, ACCENT }
