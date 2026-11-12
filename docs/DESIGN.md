# Reelometer · Sistema de diseño

> Concepto: **el marcador**. Un contador implacable y elegante, entre panel de aeropuerto
> (split-flap), ticket de compra y revista editorial. Los números son los protagonistas.
> Quien abra la app tiene que pensar «esto lo ha hecho un estudio».

Este documento es normativo. Si algo no está aquí, decide con este criterio: **menos adorno,
más tipografía, números enormes, líneas finas, un solo acento**.

---

## 0. Prohibido (si lo ves, quítalo)

- Degradados (de ningún tipo, salvo el sombreado sutil de las fichas split-flap y el reflejo y la sombra de las
  burbujas de cristal del envío rápido, que van sobre Instagram y TikTok: ver la excepción de la 1.4.2 en §3).
- Morado, rosa o cian como color de interfaz. Solo existen tinta, hueso, grises cálidos y naranja señal.
- Tarjetas redondeadas apiladas (`Card`, `ElevatedCard`, contenedores con radio ≥ 8 dp y sombra).
- Sombras/elevación. La jerarquía se hace con tipografía, filetes y espacio. (Única excepción: la sombra suave de
  las burbujas de cristal, §3.)
- Emojis en la interfaz (solo se permiten dentro del texto de la imagen compartible).
- Iconos de Material (`Icons.*`). Todos los iconos salen de `ui/icons/ReelIcons.kt`.
- Componentes Material sin personalizar a la vista (`Button`, `FilterChip`, `NavigationBar`,
  `Switch`, `AlertDialog`, `LinearProgressIndicator`, `Slider`…). Material3 solo como base
  invisible (p. ej. `MaterialTheme` para valores por defecto de texto/ripple), nunca con su aspecto.
- Textos de relleno o eufóricos: «¡Genial!», «¡Enhorabuena!», «Increíble», «🎉», signos de exclamación.
- Duraciones lineales en animaciones (`tween` con `LinearEasing`). Se anima con **springs**.
- Colores, tamaños de letra o espaciados escritos a mano en las pantallas: todo sale de los tokens.

---

## 1. Color

Tokens en `ui/theme/Colors.kt` (`ReelTheme.colors.*`). Dos esquemas: oscuro (por defecto) y claro
(papel). Por defecto se sigue al sistema; en Ajustes se puede forzar.

| Token          | Oscuro    | Claro     | Uso |
|----------------|-----------|-----------|-----|
| `bg`           | `#11100E` | `#F3EEE5` | Fondo de todas las pantallas (negro cálido / papel). |
| `bgRaised`     | `#1A1815` | `#EAE4D8` | Superficies puntuales: tooltip, hoja de diálogo, banda de aviso. |
| `bgSunken`     | `#1F1D1A` | `#E3DCCF` | Fichas split-flap, pistas de barras y de interruptores, celdas vacías del mapa de calor. |
| `text`         | `#EDE8DF` | `#151412` | Texto principal y cifras (hueso / tinta). |
| `textMuted`    | `#A39E94` | `#5F5A52` | Texto secundario. Contraste ≥ 4,5:1 sobre `bg`. |
| `textFaint`    | `#6F6A62` | `#8C857A` | Solo decoración o texto ≥ 18 sp (≥ 3:1). Nunca para información esencial pequeña. |
| `line`         | `#2E2B27` | `#D6CFC2` | Filetes, separadores, rejilla de gráficas. |
| `lineStrong`   | `#4A463F` | `#B5AD9F` | Bordes de botón secundario, ejes, pistas activas. |
| `signal`       | `#FF5A1F` | `#FF5A1F` | **Único acento.** Rellenos (botón primario, banda a sangre, barra destacada). |
| `onSignal`     | `#11100E` | `#11100E` | Texto/iconos sobre `signal` (6:1). |
| `signalText`   | `#FF5A1F` | `#C23A06` | Acento usado como texto o trazo fino sobre `bg` (AA en ambos temas). |
| `signalDisplay`| `#FF5A1F` | `#D9480F` | Cifras grandes (≥ 24 sp) en acento sobre `bg` (≥ 3:1). |

- **Escala del mapa de calor** (5 pasos, de nada a mucho): `bgSunken` → mezcla de `signal` al 25 %, 45 %, 70 % sobre `bg` → `signal`.
- **Marcas de plataforma** (solo puntos de 6–8 dp, filetes de 2 dp o segmentos de la barra de reparto; nunca fondos):

  | Plataforma | Oscuro    | Claro     |
  |------------|-----------|-----------|
  | TikTok     | `#3FE0E5` | `#0B8A8F` |
  | Reels      | `#E0559A` | `#B8306F` |
  | Shorts     | `#FF3347` | `#D41B30` |

  Acceso: `ReelTheme.colors.platform(platform)`. La etiqueta de texto siempre acompaña al color
  (el color nunca es el único portador de información).
- **Semántica**: más vídeos = peor → se marca en `signalText`. Menos vídeos = mejor → `text` (sin verde).
- La **imagen compartible** y el **Wrapped** usan siempre la paleta oscura (son «pantallas de marcador»).

## 2. Tipografía

Fuentes reales con licencia OFL en `res/font/` (licencias en `assets/licenses/`):

| Familia | Fichero | Papel |
|---|---|---|
| **Bricolage Grotesque** (variable: `opsz` 12–96, `wdth` 75–100, `wght` 200–800) | `bricolage_grotesque.ttf` | Display y texto. En cifras grandes: condensada (`wdth` 75) y pesada (800). |
| **JetBrains Mono** (variable `wght`) | `jetbrains_mono.ttf` | Datos, etiquetas, botones, ejes. Cifras tabulares por naturaleza. |
| **Instrument Serif** Italic | `instrument_serif_italic.ttf` | Apartes editoriales: la frase irónica, las equivalencias. Con moderación (1–2 por pantalla). |

Escala (`ReelTheme.type.*`). Tamaños en sp; interlineado entre paréntesis; tracking en em.

| Token        | Familia / ajustes                         | Tamaño        | Uso |
|--------------|-------------------------------------------|---------------|-----|
| `counter`    | Bricolage 800 · wdth 75 · opsz 96         | 132 (1,0) · −0,02 | Dígitos del marcador split-flap. Se reduce para caber (autoajuste). |
| `displayXL`  | Bricolage 800 · wdth 75 · opsz 96         | 96 (0,95) · −0,02 | Cifra protagonista en Wrapped / Estadísticas. |
| `displayL`   | Bricolage 800 · wdth 80 · opsz 72         | 64 (1,0) · −0,01 | Cifras de sección. |
| `displayM`   | Bricolage 700 · wdth 85 · opsz 48         | 44 (1,05)     | Titulares de pantalla («Estadísticas»). |
| `headline`   | Bricolage 700 · wdth 90 · opsz 32         | 30 (1,1)      | Titulares de bloque, diapositivas. |
| `title`      | Bricolage 600 · wdth 100 · opsz 20        | 20 (1,25)     | Títulos pequeños, nombres de creador. |
| `body`       | Bricolage 400 · opsz 14                   | 16 (1,45)     | Texto corrido. |
| `bodySmall`  | Bricolage 400 · opsz 12                   | 14 (1,4)      | Texto secundario. |
| `quote`      | Instrument Serif Italic                   | 28 (1,1)      | La frase irónica bajo el marcador. |
| `quoteSmall` | Instrument Serif Italic                   | 20 (1,2)      | Equivalencias en lista, apartes. |
| `dataL`      | JetBrains Mono 500                        | 22 (1,25)     | Cifras secundarias («47 MIN»). |
| `data`       | JetBrains Mono 500                        | 15 (1,35)     | Valores de líneas de ticket. |
| `dataS`      | JetBrains Mono 400                        | 13 (1,35)     | Ejes, notas, fuentes de cálculo. |
| `label`      | JetBrains Mono 600 · MAYÚSCULAS · +0,12em | 11 (1,3)      | Etiquetas pequeñas («VÍDEOS», «01 — PLATAFORMAS»). |
| `labelL`     | JetBrains Mono 600 · MAYÚSCULAS · +0,10em | 13 (1,3)      | Pestañas, cabeceras. |
| `button`     | JetBrains Mono 700 · MAYÚSCULAS · +0,08em | 14 (1,15)     | Botones. |

Reglas: jerarquía **muy** marcada (cifra de 132 sp junto a etiqueta de 11 sp). Nunca más de 3
tamaños por bloque. Las mayúsculas siempre con tracking. Números siempre formateados con
`stats/Format.kt` (1.234 · 3,2 h · 34 %).

## 3. Retícula, espaciado y composición

- **Retícula de 4 dp.** Tokens `Space` (`ui/theme/Space.kt`): `x1=4, x2=8, x3=12, x4=16, x5=20, x6=24, x8=32, x10=40, x12=48, x16=64, x20=80`.
- Margen lateral de pantalla: **20 dp**. Separación entre secciones: **48 dp**. Dentro de sección: 12–16 dp.
- Formas: bloques con **esquinas rectas (0 dp)**; fichas split-flap y botones **2 dp**; puntos circulares. Nada más.
- **Excepción deliberada (1.4.0): avatares circulares.** Las burbujas del envío rápido (sobre los vídeos de
  Instagram y, desde la 1.10.0, de TikTok, `detection/QuickSendOverlay.kt`) y su vista previa en Ajustes son
  círculos, como la burbuja de Instagram cuando un amigo da «me gusta» a un reel, con una **foto de perfil** (o
  las iniciales en Bricolage; si el nombre es solo emojis, como los hay en TikTok, su primer emoji entero). Es un avatar, no una tarjeta: no vale como precedente para contenedores
  redondeados. Sin texto visible (el nombre va a TalkBack).
- **Excepción deliberada (1.4.2): burbujas de cristal.** Esas mismas burbujas ya no llevan aro de `signal` ni
  insignia (fuera el avión de papel): son de **cristal**, como las del sistema del iPhone actual, porque van
  **encima de Instagram y TikTok**, no dentro de la app. Un único dibujo, `detection/GlassBubbleArt.kt`, para
  las de verdad, la vista previa de Ajustes y la galería (sección 16).
  - **Medidas:** círculo de **42 dp** en una zona táctil de **48 dp** (`Space.x12`, accesibilidad), **4 dp**
    (`Space.x1`) entre burbujas.
  - **Cristal:** la foto recortada en círculo (o las iniciales en `text` sobre `bg` al 62 %); un **anillo** de
    `text` (hueso, casi blanco) con alfa baja (42 %, ≈ 1,5 dp); un **reflejo** arriba, una elipse de `text` que
    se apaga hacia abajo; y una **sombra** suave alrededor (`bg` al 32 % que se apaga). Reflejo y sombra son
    **los únicos degradados permitidos fuera de las fichas split-flap**, y solo aquí. Por fuera, nada opaco.
  - **Sin desenfoque real** de lo de detrás: en una ventana añadida desde un servicio no hay `Window` con
    `setBackgroundBlurRadius`, y `FLAG_BLUR_BEHIND`/`blurBehindRadius` desenfoca **toda la pantalla** detrás de
    la ventana (el reel entero), además de costar GPU en cada fotograma del vídeo. El cristal se simula con el
    anillo, el reflejo y la sombra.
  - **Estados, sin insignia**, pequeños y encima de la foto: enviando, un brillo de `text` que gira por el anillo;
    enviado, la foto se oscurece un poco y sale un círculo pequeño (≈ 18 dp) de `signal` con la marca de visto en
    `onSignal` (el único acento, hasta pasar de reel); fallo, un círculo pequeño de `text` con «!» en `bg` y una
    sacudida; la despegada (mantener pulsada) crece ×1,15 y su anillo se aclara; la de debajo crece ×1,08, anillo
    más claro y el doble de grueso y un «+» de `text`. Sin naranja salvo el visto.
  - **Movimiento:** muelles para hundirse al tocar, despegarse, ir a su sitio al soltar (se queda donde se suelta,
    sin imán al borde), girar de columna a fila (en la franja de abajo, encima del nombre del reel) y rebotar al
    enviar.
- **Composición editorial y asimétrica**: cifras alineadas a la izquierda, etiquetas en columna estrecha;
  alterna bloques a sangre (de borde a borde) con bloques con margen; deja aire (espacio negativo) generoso.
- **No todo va en contenedores.** La mayoría del contenido va directamente sobre `bg`, separado por filetes.
- Elementos de ticket (componentes en `ui/components/Ticket.kt`):
  - `TicketDivider(Dashed)`: línea discontinua 1 dp (trazo 4 dp, hueco 4 dp) en `line`.
  - `TicketDivider(Perforated)`: discontinua + muescas semicirculares de 8 dp a ambos lados (recorte del papel).
  - `JaggedEdge`: borde dentado (dientes de 8 dp) para cerrar un bloque tipo ticket.
  - `ReceiptLine`: etiqueta a la izquierda · puntos guía · valor a la derecha en mono.
- **Cabecera de sección**: índice + título + filete hasta el borde: `01  PLATAFORMAS ─────────── 3`.
- **Banda a sangre** (`Band`): bloque `signal` de borde a borde con texto `onSignal`; máximo una por pantalla.

## 4. Componentes (`ui/components/`)

Nombres orientativos; la implementación real manda (ver la galería debug).

- **`SplitFlapCounter`** — el marcador. Cada dígito en su ficha (`bgSunken`, esquinas 2 dp, bisagra
  horizontal de 1–2 dp en `bg` a media altura, leve sombreado superior/inferior). Al cambiar de valor
  y al entrar en pantalla, cada columna **voltea** (rotationX de la mitad superior, revelando el
  siguiente dígito) pasando por los dígitos intermedios, con **escalonado** de izquierda a derecha y
  spring al asentarse. Separador de millar como punto pequeño entre fichas. Un tic háptico al asentarse.
  Con «reducir animaciones», cambio instantáneo. Semántica: `contentDescription = "212 vídeos"`.
- **`RollingNumber`** — cifra inline (mono o display) cuyos dígitos ruedan en vertical (odómetro). Para cifras secundarias.
- **Flap** (1.12.0, `ui/passport/`) — la mascota de la pestaña Flap. Cinco estados (EN_FORMA, NORMAL, CANSADO, ENFERMO, KO) que se
  distinguen también **sin color** (forma de ojos, boca, postura, termómetro, gota). Barra de vida por segmentos rectos al estilo marcador
  (`signal` solo cuando la vida está en peligro o en KO), sin tarjetas redondeadas, degradados ni emojis. Cambio de estado con el volteo
  split-flap; con «reducir animaciones», sin movimiento (Flap quieto).
- **`SectionHeader`**, **`ReceiptLine`**, **`TicketDivider`**, **`JaggedEdge`**, **`Band`**, **`Stamp`** (sello rotado con borde, p. ej. «100 % LOCAL»).
- **`ReelButton`** (`Primary`: relleno `signal` + texto `onSignal`; `Secondary`: borde 1 dp `lineStrong`; `Ghost`: texto + flecha →).
  Altura mínima 48 dp. Todos usan **`Modifier.pressable`**: se hunden al pulsar (escala 0,96 y +1 dp hacia abajo, spring `snappy`) + háptico.
- **`ReelSwitch`** — interruptor rectangular (pista `bgSunken`, mando que se desliza; «SÍ/NO» en mono). Háptico on/off.
- **`Stepper`** — `−  150  +` con cifra `RollingNumber`; pulsación larga repite.
- **`SegmentedTabs`** — opciones en mono mayúsculas con subrayado `signal` que **se desliza** (spring) a la opción activa.
- **`PeriodNavigator`** — `‹  27 OCT – 2 NOV  ›`, chevrons de 48 dp, desactivados en `textFaint`.
- **`ReelTabBar`** — barra inferior propia: filete superior, hasta 4 destinos (Hoy · Estadísticas · Resumen · Flap;
  3 con Flap apagado y, con el descubrimiento progresivo, solo los ya descubiertos) con icono propio + etiqueta
  `labelL`, todas al mismo tamaño (el mayor con el que caben todas);
  el activo en `text` con un indicador `signal` de 2 dp que se desliza; inactivos en `textMuted`.
- **Gráficas** (`ui/components/charts/`), todas dibujadas en `Canvas`, animadas al aparecer y táctiles:
  - `DailyBars` — barras que **crecen escalonadas** (stagger ~24 ms, spring `gentle`); media como línea discontinua;
    barra máxima en `signal`, resto en `text` al 85 % / `lineStrong`. **Mantener el dedo** muestra un tooltip
    (fondo `text`, texto `bg`, mono) con valor y fecha; al arrastrar, salta de barra en barra con **tic háptico**.
  - `HourLine` — línea 0–24 h que **se traza** al aparecer (PathMeasure), área muy tenue, marcador de la hora actual; arrastre con tooltip + tic.
  - `CalendarHeatmap` — semanas en columnas × 7 filas (L…D), celdas cuadradas con 2–3 dp de hueco, escala de 5 pasos;
    modos semana / mes (calendario) / año (estilo contribuciones de GitHub, con meses arriba). Tocar celda: tooltip + háptico (+ abrir el día).
  - `RankBars` — barras horizontales finas (6 dp) con etiqueta y valor; para categorías.
  - `PlatformSplitBar` — una única barra de 4 dp partida en segmentos por plataforma + leyenda en `ReceiptLine`.
- **Estados**: `FlapLoader` (cargando: fichas que voltean en bucle con «– – –»), `EmptyState` (marcador a «0» o «—» + frase),
  `ErrorState` (frase + `ReelButton` «REINTENTAR»), `Notice` (aviso integrado: filete `signal` a la izquierda + texto + acción; p. ej. servicio desactivado).
- **`ReelDialog`** — hoja propia tipo ticket (fondo `bgRaised`, borde dentado inferior), botones `ReelButton`.
- **`Tooltip`**, **`PlatformMark`**, **`Label`** (texto `label`), **`Barcode`** (código de barras decorativo hecho con datos reales: una barra por día).

## 5. Iconografía (`ui/icons/ReelIcons.kt`)

- `ImageVector` propios, rejilla de 24 dp, **trazo uniforme de 1,75**, remates y uniones **rectos**
  (`StrokeCap.Square`/`Butt`, `StrokeJoin.Miter`), sin rellenos salvo puntos de 2 dp. Geometría limpia, sin «gotas».
- Conjunto mínimo: Today (ficha con bisagra), Stats (columnas), Wrapped (fichas apiladas / play), Settings (deslizadores, **no** engranaje),
  Share, Back, Close, ChevronLeft/Right/Down, Play, Pause, Check, Bell, Clock, Target, Calendar, Download, Trash,
  Shield, Eye, Info, Plus, Minus, ArrowUp, ArrowDown, ArrowRight, External, Sun, Moon, Auto, Apps, Pulse, Lock, Copy, Bug, Widget.
- Iconos con significado llevan `contentDescription`; los decorativos, `null`.

## 6. Movimiento y tacto

- Tokens en `ui/theme/Motion.kt`:
  - `snappy` = spring(dampingRatio 0,7, stiffness 900) — pulsaciones, interruptores.
  - `standard` = spring(0,85, 380) — indicadores, cambios de layout, transiciones.
  - `gentle` = spring(0,9, 160) — cifras, gráficas que crecen.
  - `bouncy` = spring(0,55, 500) — Flap que revive y otros momentos de recompensa (con moderación).
  - Escalonados: 24 ms por barra, 40 ms por columna de dígitos, 30 ms por línea de lista.
- **Reducir animaciones**: `LocalReducedMotion` (true si la escala de animación del sistema es 0 o
  el usuario activó «quitar animaciones»). Con true: sin volteos, sin escalonados, gráficas completas al instante, transiciones por fundido corto.
- Transiciones: entre pestañas, fundido + desplazamiento corto (16 dp) con spring; de una cifra a su
  detalle, **shared element** (`SharedTransitionLayout`). Gesto de **atrás predictivo** soportado (NavHost).
  Cambio de periodo: `AnimatedContent` (la cifra sale hacia arriba y entra la nueva; dirección según avance/retroceso).
- **Hápticos** (`ui/theme/Haptics.kt`, vía `View.performHapticFeedback`): pulsación de botón → `VIRTUAL_KEY`;
  recorrer barras/celdas → `CLOCK_TICK` (o `SEGMENT_FREQUENT_TICK` en API 34+); activar pulsación larga → `LONG_PRESS`;
  interruptor → `TOGGLE_ON/OFF` (API 34+, si no `CONTEXT_CLICK`); éxito (servicio conectado, exportado) → `CONFIRM`;
  borrar datos → `REJECT`; el marcador al asentarse → un `CLOCK_TICK`.
- **Rendimiento**: 60/120 fps. Lecturas de estado de animación dentro de `graphicsLayer {}`/`drawBehind {}`
  (fase de dibujo, no de composición); `drawWithCache` para trazados; `remember` de `TextMeasurer`/layouts;
  `derivedStateOf` cuando proceda; `key` estable en listas; nada de cálculos pesados en composición
  (las agregaciones llegan hechas desde la capa de datos, fuera del hilo principal).

## 7. Accesibilidad

- `contentDescription` en todo lo que transmite información (iconos de acción, gráficas con un resumen
  hablado: «Martes: 212 vídeos, la barra más alta»), `mergeDescendants` en líneas de ticket.
- Texto escalable (sp). Probar con **fuente al 130 %**: nada se corta ni se solapa; las cifras gigantes
  se autoajustan al ancho (`TextAutoSize`/medición) en lugar de partirse.
- Contraste AA con los tokens anteriores. Objetivos táctiles **≥ 48 dp** (`minimumInteractiveComponentSize`).
- El orden de lectura de TalkBack sigue el orden visual; los botones dicen qué hacen («Compartir resumen»).

## 8. Voz y textos

Español de España, **tú**, frases cortas, ironía seca, **sin sermones ni culpa**. El marcador informa;
el usuario saca sus conclusiones. Referencia: _«Hoy llevas 212. Eso son 47 minutos que nadie te va a devolver.»_

- Nunca: «¡Genial!», «¡Enhorabuena!», «Increíble», «deberías», «es importante que», «tu salud mental»,
  «usuario», «usted», exclamaciones, emojis (salvo en la imagen compartible), puntos suspensivos de relleno.
- Sí: datos concretos, comparaciones inesperadas, humor seco, verbos directos («Activa», «Comparte»).
- **Variantes**: cada situación tiene 3–6 frases; se elige de forma determinista por día (no cambia al
  recomponer, pero no se repite cada día). Ver `copy/`.
- Cifras siempre con `Format` (1.234 · 3,2 h · 34 % · +12 %). Tildes y signos de apertura revisados
  (vídeo, también, todavía, ¿…?).
- Las equivalencias llevan su **base de cálculo** en una nota discreta (`dataS`, `textMuted`):
  «Base: 240 ECTS × 25 h (Plan Bolonia)».
- Etiquetas en mayúsculas cortas: «VÍDEOS», «TIEMPO», «HORA PUNTA», «DÍA DÉBIL», «RACHA».
- **Idiomas**: los textos viven en `res/values/` (español de España, el idioma por defecto) y cada traducción en su
  `values-xx/`; las variantes son `<string-array>` (cada idioma, las suyas). En otro idioma, la misma voz en su
  lengua: registro informal, frases cortas, ironía seca, sin exclamaciones ni emojis.

## 9. Pantallas (maquetas de referencia)

Ancho de referencia 412 dp. `│` = margen de 20 dp.

### Hoy
```
│ HOY · MARTES 3 NOV                                [ajustes] │   label + icono 48 dp
│─────────────────────────────────────────────────────────────│   filete
│ VÍDEOS                                                      │   label
│ ┌───┐┌───┐┌───┐                                             │   SplitFlapCounter enorme,
│ │ 2 ││ 1 ││ 2 │                                 OBJETIVO 150 │   alineado a la izquierda;
│ └───┘└───┘└───┘                                 +62 PASADO  │   columna estrecha a la derecha
│ 47 MIN DE PANTALLA · 4 COMPARTIDOS                          │   dataL / data
│                                                             │
│ «Eso son 47 minutos que nadie te va a devolver.»            │   quote (serif itálica)
│ ↑ +34 % QUE TU MARTES MEDIO A ESTA HORA                     │   data en signalText
│ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - │   TicketDivider(Perforated)
│ 01  PLATAFORMAS ─────────────────────────────────────── 3   │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ (PlatformSplitBar)           │
│ ● TikTok ............................. 120 · 26 MIN         │   ReceiptLine
│ 02  TU DÍA, HORA A HORA ────────────────────────────────    │
│ [HourLine con marcador de la hora actual]                   │
│ HORA PUNTA 23:00 · 41 VÍDEOS                                │
│ 03  LO QUE MÁS VES ──────── / 04  QUIÉN TE LO PONE ──────── │   RankBars / ranking
███ BANDA A SANGRE (signal): «DESDE EL 3 MAR: 12.408 VÍDEOS · 61 H» ███
```
- Tocar el marcador → **Detalle del día** con shared element de la cifra.
- **Servicio desactivado**: el marcador muestra fichas en blanco «– – –» y la etiqueta «SIN SEÑAL»;
  debajo un `Notice` con una frase («Sin el permiso de accesibilidad no veo nada. Que, por otra parte,
  es lo que esperas de una app así.») y `ReelButton` «CONECTAR». Integrado en el diseño, no una tarjeta de error.
- Cero vídeos hoy: marcador a 000 y frase de la familia «De momento, ganas tú.»

### Estadísticas
```
│ ESTADÍSTICAS                                       [ajustes] │   displayM
│ SEMANA   MES   AÑO   SIEMPRE                                │   SegmentedTabs (subrayado deslizante)
│ ‹            27 OCT – 2 NOV 2026                          › │   PeriodNavigator
│ 1.204                                                       │   displayXL (AnimatedContent al cambiar)
│ VÍDEOS · 9 H 12 MIN · 172/DÍA · 27 S POR VÍDEO              │
│ ↑ +12 % QUE LA SEMANA ANTERIOR                              │
│ 01 CADA DÍA        [DailyBars + media discontinua]          │
│ 02 CALENDARIO      [CalendarHeatmap según periodo]          │
│ 03 PATRONES        ReceiptLines: HORA PUNTA · DÍA DÉBIL ·   │
│                    RACHA ACTUAL · MEJOR RACHA · POR VÍDEO · │
│                    COMPARTIDOS                              │
│ 04 PLATAFORMAS · 05 CATEGORÍAS · 06 CREADORES (top 10)      │
```
- Racha sin objetivo: línea «RACHA — Ponte un objetivo y empezamos a contar» que lleva a Ajustes.

### Resumen (hub) y Wrapped
- Hub: titular editorial («Tu octubre, contado.») y una lista de periodos disponibles como tiras de
  ticket a sangre (MES PASADO destacado en `signal`, ESTE MES, ESTA SEMANA, ESTE AÑO).
- Story a pantalla completa (siempre paleta oscura): barra de progreso segmentada arriba, cerrar ×.
  Avance automático (~6 s), toque derecha/izquierda = siguiente/anterior, mantener = pausa.
  Diapositivas: portada → total (marcador) → tiempo → equivalencias → creador nº 1 → categoría →
  peor día → hora punta / plataformas → **ticket final** (vista previa de la imagen 1080×1920 + COMPARTIR / GUARDAR).
- **Imagen compartible** (1080×1920, `android.graphics`): «ticket de tu tiempo» — papel hueso sobre
  negro cálido, bordes dentados, cabecera REELOMETER, periodo, cifra enorme, tiempo, equivalencias
  (con emoji permitido), creador nº 1, categoría, peor día, código de barras con un trazo por día,
  sello rotado en `signal` («PAGADO CON TU TIEMPO») y pie con la marca.

### Ajustes
Lista editorial de secciones numeradas con `ReceiptLine`/`ReelSwitch`/`Stepper`:
01 OBJETIVO · 02 AVISOS (diario + hora, mensual) · 03 QUÉ APPS CUENTO · 04 TEMA (SISTEMA/OSCURO/CLARO) ·
05 TUS DATOS (exportar CSV, borrar con `ReelDialog`) · 06 SERVICIO (estado + abrir ajustes) ·
07 PRIVACIDAD (explicación) · 08 ACERCA DE (versión: 7 toques desbloquean Calibración, con cuenta atrás discreta; licencias de fuentes).

### Calibración (oculta)
Consola en mono sobre `bg`: estado del servicio, interruptor CAPTURAR, filtros (plataforma, tipo),
lista en vivo (más reciente arriba): hora, paquete, tipo, viewId, clase, from→to, items, dy, alto/alto pantalla,
decisión FEED ✓/✗; vídeos detectados resaltados con filete `signal`. Botones COPIAR / COMPARTIR LOG / LIMPIAR.

### Onboarding (primera vez, 4 pasos a pantalla completa)
1. Qué hace — marcador que cuenta solo de 0 a 212 + «Cuenta lo que no cuentas.»
2. Privacidad — «Lo que ves se queda aquí.» Sin cuentas · sin servidores · sin anuncios. Sello «100 % LOCAL».
3. Permiso de accesibilidad — **divulgación destacada** (qué lee, para qué, que nada sale del móvil) con
   aceptación explícita; guía paso a paso; detección de «Ajuste restringido» (Android 13+) con sus pasos;
   al volver con el servicio activo: el marcador voltea a «OK» + háptico `CONFIRM`.
4. Avisos + objetivo diario opcional (sin objetivo / 30 / 60 / 100 / 150 / 200 / otro) → EMPEZAR.

### Widget (Glance)
Ficha oscura (o papel en claro): «HOY» en label, dígitos split-flap renderizados como imagen con las
fuentes de la app, «47 MIN» debajo. Toque → abre Hoy.
