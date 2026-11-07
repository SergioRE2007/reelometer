package com.reelometro.app.detection

import com.reelometro.app.data.Platform

/**
 * Lo que cambia del **envío rápido** y de **«A quién envías»** de una app a otra (1.10.0: Instagram y TikTok). Un solo
 * [QuickSender] y un solo [RecipientDetector] para las dos; las diferencias viven aquí, como datos.
 *
 * Los ids van **cortos** (lo que sigue a «:id/», en minúsculas, como [shortViewId]): TikTok tiene dos paquetes
 * ([Platform.packages]) y el id completo se forma con el del nodo ([fullIds]). Son atajos: lo que devuelven pasa las
 * mismas reglas que lo que sale recorriendo el árbol, y si la app los cambia se recorre como siempre. Las etiquetas,
 * normalizadas ([RecipientDetector.normalize]: sin tildes ni mayúsculas).
 *
 * **TikTok 47.7.3** (árbol real del 7 nov 2026, `src/test/resources/calibration/tiktok-47.7-*.xml`, anonimizado):
 *  - ofusca casi todos los ids (`g_d`, `v8u`, `w1p`…: cambian con cada versión). Estables, por ahora: `viewpager`,
 *    `tv_title`, `tv_send`, `touch_outside`, `title`, `desc`, `user_avatar`;
 *  - «Compartir» del visor: `Button` con la descripción «Compartir el vídeo. 122,7 mil veces compartido» (el mismo id
 *    ofuscado que «Me gusta»: **nunca aprender ni usar el id de compartir en TikTok**, [learnShareId] = false);
 *  - la hoja va en **su propia ventana** (un diálogo `TYPE_APPLICATION` encima del feed), contenedor `touch_outside` +
 *    descrito «Módulo inferior». Cabecera: lupa (`ImageView` «Buscar»), título `tv_title` «Enviar a», «Cerrar»;
 *  - fila de contactos: un `RecyclerView` horizontal; cada celda es un `Button` **descrito con el nombre entero** y,
 *    dentro, la foto (`ImageView`) y el nombre como `Button` con texto que puede ir **cortado** («MARÍA FERNÁNDEZ…») y
 *    con marcas de dirección. Se ven ~6 celdas; las demás no están en el árbol (hay que buscar);
 *  - debajo, separadas por una línea, dos filas de opciones que **no son contactos**: «Compartir» (¡es *repostear*!),
 *    WhatsApp, Copiar enlace, Status, Facebook, Telegram… y Denunciar, No me interesa, Descargar, Añadir a Story,
 *    Promocionar, Dúo…;
 *  - al marcar un contacto: la celda lleva un `CheckBox` marcado (`checked`) y abajo salen «Escribe un mensaje…», una
 *    fila de **emojis de reacción** (`GridView` de celdas descritas «🥰», «👍»…: **nunca se pulsan**, mandarían el
 *    vídeo con ese emoji) y «Enviar» (`Button`, id ofuscado; en el buscador, `tv_send`). El atributo `selected` **no
 *    vale**: las celdas recicladas lo arrastran (visto en el móvil: una celda sin marcar con `selected=true`);
 *  - el buscador no está a la vista: la lupa abre otra pantalla (en otra ventana: abajo) con un `EditText` («Buscar»),
 *    «Chats recientes» y filas (`ViewGroup` pulsable) con foto, nombre, «· Amigos», el usuario (o «3 miembros» en un
 *    grupo) y un `CheckBox`. Al escribir, salen también los grupos de los que es miembro (nombre «Ana, Bea, Carlos»);
 *  - tras «Enviar», la hoja **se cierra al momento** y abajo sale «Enviado a …» (fuera de la hoja): confirmar = la hoja
 *    se ha cerrado.
 *
 *  - el buscador va en **otra ventana** (otro diálogo) que la hoja de contactos: en los volcados, la ventana de la hoja tiene
 *    un `FrameLayout` más entre `content` y el contenedor (`f3f`) que la del buscador, y su hoja (`g4v`) se mide distinta. El
 *    móvil lo confirma de lado: tras la lupa, el atajo (que miraba solo la ventana de la hoja) no veía el campo durante ~1 s y
 *    el recorrido (que mira todas) sí;
 *  - la fila de contactos puede abrirse **desplazada** (visto: «idx 2→8 de 61»): el fijado no sale aunque esté entre los
 *    primeros.
 *
 * El [QuickSender] puede añadir campos aquí; [RecipientDetector] y el servicio solo leen los que ya están.
 *
 * Cómo lo recorre el [QuickSender] en TikTok (1.10.0; 1.10.1, cualquier idioma): «Compartir» del visor por sus palabras → la
 * hoja por `tv_title` (fuera de la ventana del visor) → la celda de la fila de contactos cuya descripción es el nombre (debajo
 * de la cabecera y en la primera fila; nunca en la cuadrícula de emojis ni una opción) → si no está y la fila puede ir hacia
 * atrás (abierta desplazada), se vuelve al principio una vez y se mira otra vez → si no, la lupa de la cabecera **por su
 * sitio** (el primer botón pulsable junto a `tv_title`, en el lado donde empieza la lectura; las palabras de
 * [searchButtonWords], de respaldo) → el campo del buscador (el `EditText` junto a la lupa, en la ventana del buscador, que se
 * vuelve a buscar entre las ventanas de la app que no son el visor y que son una hoja de abajo, con `touch_outside`; su id
 * ofuscado se aprende) → «Chats recientes» antes de escribir → la fila (pulsable, de ancho entero y con `CheckBox`) cuyo
 * nombre visible (su primer texto; nunca el usuario) es exactamente ese → «Enviar» (`tv_send`, las palabras de [sendWords]
 * o, en cualquier idioma, **por su sitio**: el único botón pulsable, ancho y abajo, debajo del campo del mensaje, con alguien
 * marcado) → confirmado cuando la ventana de la hoja desaparece.
 *
 * Las palabras van en dos formas: tal cual salen en pantalla (`…Words`, lo que se pide al atajo de texto, que distingue
 * tildes) y normalizadas (`…Labels`, para comparar). Las de otros idiomas que no son el español y el inglés salen de memoria,
 * sin fuente comprobada: por eso son solo el respaldo del sitio, y al atajo solo se le piden el español, el inglés y las que
 * cuadran con lo que ya se ve (el título de la hoja, la etiqueta de la lupa pulsada).
 */
data class PlatformProfile(
    val platform: Platform,
    /** Ids del visor de vídeos (con `contains`): ahí dentro solo se pulsa «Compartir», y un clic ahí no es de la hoja. */
    val feedIds: List<String>,
    /** La hoja de compartir va en su propia ventana encima del visor (TikTok); en Instagram, en la misma. */
    val sheetOwnWindow: Boolean,
    /** Ids de la hoja de compartir (contenedor, cuadrícula, título): «la hoja ya está». */
    val sheetIds: List<String>,
    /** Ids de cualquier hoja de abajo abierta: «¿ya hay una hoja abierta?» antes de pulsar «Compartir». */
    val openSheetIds: List<String>,
    /** Al subir por los antepasados de un nodo del atajo, al llegar a uno de estos ya se sabe que no está en el visor. */
    val sheetRootIds: List<String>,
    /** Ids del buscador de la hoja (un `EditText`), si los hay estables. */
    val searchFieldIds: List<String>,
    /** Ids de una celda de la cuadrícula o fila de contactos: «ya ha cargado». */
    val gridCellIds: List<String>,
    /**
     * El buscador no está a la vista y hay que abrirlo pulsando un botón de la cabecera de la hoja (TikTok: la lupa
     * «Buscar»), tal cual sale en pantalla (el español y el inglés primero). Vacío = el campo ya está a la vista (Instagram).
     * Solo dentro de la ventana de la hoja: el feed de TikTok tiene otra lupa «Buscar» arriba a la derecha. 1.10.1: en TikTok
     * la lupa se busca antes por su sitio; estas palabras son el respaldo.
     */
    val searchButtonWords: List<String>,
    /** Ids del «Enviar» de abajo (cualquier idioma). */
    val sendIds: List<String>,
    /** Opciones que solo salen en la hoja de compartir (normalizadas): marcan que la hoja está abierta. */
    val sheetMarkers: Set<String>,
    /** Lo que se pide con el atajo de texto para ver si la hoja está abierta. */
    val markerQueries: List<String>,
    /**
     * Opciones de la hoja que nunca son un contacto aunque alguien se llame igual (normalizadas): en TikTok, las dos filas
     * de debajo de los contactos. Respaldo de la regla por posición (los contactos van entre la cabecera y la primera
     * línea divisoria).
     */
    val sheetOptions: Set<String>,
    /** Vale un nombre hecho solo de emojis («💿»): `RecipientDetector.clean(symbolNames = true)`. */
    val symbolNames: Boolean,
    /** Un contacto marcado se sabe por un `CheckBox` marcado en su celda o fila; el atributo `selected` no vale. */
    val selectedByCheckbox: Boolean,
    /** Se puede aprender y usar el id del botón compartir (en TikTok no: es el mismo que el de «Me gusta»). */
    val learnShareId: Boolean,
    /**
     * El «Enviar» de abajo cuando la hoja va en su propia ventana, tal cual sale en pantalla (el español y el inglés
     * primero): solo ese botón, nunca «Enviar a» (el título). Vacío = las palabras de siempre ([RecipientDetector.SEND],
     * Instagram). 1.10.1: en TikTok el botón también se encuentra por su sitio; estas palabras son el respaldo.
     */
    val sendWords: List<String> = emptyList(),
    /**
     * Título de la hoja, tal cual sale en pantalla («Enviar a»): marca la cabecera si su id cambia. Nunca uno que sea igual
     * que «Enviar» en su idioma (turco, ruso, ucraniano, coreano, hindi): sería también el botón.
     */
    val titleWords: List<String> = emptyList(),
    /**
     * 1.10.1: el botón de cerrar la hoja, tal cual sale en pantalla («Cerrar»): nunca es la lupa aunque esté donde ella. De
     * memoria fuera del español y el inglés.
     */
    val closeWords: List<String> = emptyList(),
) {
    /** [searchButtonWords] normalizadas (para comparar etiquetas). */
    val searchButtonLabels: Set<String> = searchButtonWords.mapTo(LinkedHashSet()) { label(it) }

    /** [sendWords] normalizadas: las etiquetas exactas del «Enviar» de abajo. */
    val sendLabels: Set<String> = sendWords.mapTo(LinkedHashSet()) { label(it) }

    /** [titleWords] normalizadas («enviar a»). */
    val titleLabels: Set<String> = titleWords.mapTo(LinkedHashSet()) { label(it) }

    /** [closeWords] normalizadas. */
    val closeLabels: Set<String> = closeWords.mapTo(LinkedHashSet()) { label(it) }

    /** Los ids completos de [shortId] en los paquetes de la app (`com.zhiliaoapp.musically:id/tv_send`…). */
    fun fullIds(shortId: String): List<String> = platform.packages.map { "$it:id/$shortId" }

    companion object {
        /** Una etiqueta normalizada como las compara el [QuickSender]: sin tildes ni mayúsculas, sin puntuación al final. */
        fun label(text: String): String = RecipientDetector.normalize(text).trimEnd('.', '…', ':', ' ')

        val INSTAGRAM = PlatformProfile(
            platform = Platform.INSTAGRAM,
            feedIds = RecipientDetector.FEED_IDS,
            sheetOwnWindow = false,
            sheetIds = listOf("direct_private_share_container_view", "direct_private_share_recipients_recycler_view"),
            openSheetIds = listOf("direct_private_share_container_view", "layout_container_bottom_sheet"),
            sheetRootIds = listOf(
                "direct_private_share_container_view", "direct_private_share_recipients_recycler_view",
                "layout_container_bottom_sheet", "bottom_sheet_container",
            ),
            searchFieldIds = listOf("search_edit_text"),
            gridCellIds = listOf("direct_share_sheet_grid_view_pog"),
            searchButtonWords = emptyList(),
            sendIds = listOf("direct_send_button_multi_select"),
            sheetMarkers = setOf(
                "copiar enlace", "copy link", "anadir a la historia", "anadir a historia", "add to story",
                "add to your story", "escribe un mensaje", "write a message",
            ),
            markerQueries = listOf("Copiar enlace", "Copy link", "Añadir a", "Add to"),
            sheetOptions = emptySet(),
            symbolNames = false,
            selectedByCheckbox = false,
            learnShareId = true,
        )

        /** TikTok 47.7.3 (ver el KDoc de la clase). Español e inglés, como en Instagram. */
        val TIKTOK = PlatformProfile(
            platform = Platform.TIKTOK,
            feedIds = listOf("viewpager"),
            sheetOwnWindow = true,
            sheetIds = listOf("tv_title"),
            openSheetIds = listOf("touch_outside"),
            sheetRootIds = listOf("touch_outside"),
            searchFieldIds = emptyList(),
            gridCellIds = emptyList(),
            // Español primero (el móvil de las pruebas): en él no hay consultas de más. Las de otros idiomas, de memoria y sin
            // comprobar en un móvil (solo el español y el inglés son seguros): respaldo del sitio de la lupa.
            searchButtonWords = listOf(
                "Buscar", "Search", "Pesquisar", "Rechercher", "Recherche", "Suchen", "Suche", "Cerca", "Zoeken", "Szukaj",
                "Ara", "Arama", "Поиск", "Пошук", "بحث", "Cari", "Telusuri", "Tìm kiếm", "ค้นหา", "検索", "검색", "搜索", "搜尋",
                "खोजें", "Hanapin", "Maghanap", "Caută",
            ),
            sendIds = listOf("tv_send"),
            sheetMarkers = setOf("enviar a", "send to", "copiar enlace", "copy link"),
            markerQueries = listOf("Enviar a", "Send to", "Copiar enlace", "Copy link"),
            sheetOptions = setOf(
                "compartir", "repost", "whatsapp", "copiar enlace", "copy link", "status", "estado", "facebook", "telegram",
                "messenger", "instagram", "sms", "correo", "email", "mas", "more", "denunciar", "report", "no me interesa",
                "not interested", "descargar", "download", "save video", "guardar video", "anadir a story", "add to story",
                "promocionar", "promote", "duo", "duet", "pegar", "stitch", "crear sticker", "create sticker",
                "fondo de pantalla dinamico", "live photo", "compartir como gif", "share as gif", "buscar", "search",
                "cerrar", "close", "enviar", "send", "enviar a", "send to", "escribe un mensaje", "write a message",
            ),
            symbolNames = true,
            selectedByCheckbox = true,
            learnShareId = false,
            // Como la lupa: el español y el inglés seguros; los demás, de memoria (respaldo del sitio del botón).
            sendWords = listOf(
                "Enviar", "Send", "Envoyer", "Senden", "Invia", "Verzenden", "Versturen", "Wyślij", "Gönder", "Отправить",
                "Надіслати", "Відправити", "إرسال", "Kirim", "Hantar", "Gửi", "ส่ง", "送信", "보내기", "전송", "发送", "傳送", "發送",
                "भेजें", "Ipadala", "Trimite",
            ),
            // Sin el turco, el ruso, el ucraniano, el coreano ni el hindi: su título sería igual que «Enviar».
            titleWords = listOf(
                "Enviar a", "Send to", "Enviar para", "Envoyer à", "Senden an", "Invia a", "Verzenden naar", "Versturen naar",
                "Wyślij do", "إرسال إلى", "Kirim ke", "Hantar kepada", "Hantar ke", "Gửi đến", "Gửi tới", "ส่งถึง", "ส่งไปยัง",
                "送信先", "发送给", "傳送給", "傳送至", "Ipadala sa", "Trimite către",
            ),
            closeWords = listOf(
                "Cerrar", "Close", "Fechar", "Fermer", "Schließen", "Chiudi", "Sluiten", "Zamknij", "Kapat", "Закрыть", "Закрити",
                "إغلاق", "Tutup", "Đóng", "ปิด", "閉じる", "닫기", "关闭", "關閉", "बंद करें", "Isara", "Închide",
            ),
        )

        /** El perfil de [platform], o `null` si esa app no tiene envío rápido (YouTube). */
        fun of(platform: Platform): PlatformProfile? = when (platform) {
            Platform.INSTAGRAM -> INSTAGRAM
            Platform.TIKTOK -> TIKTOK
            else -> null
        }
    }
}
