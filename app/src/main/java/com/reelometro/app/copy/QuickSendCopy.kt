package com.reelometro.app.copy

import com.reelometro.app.R

/**
 * Textos del **envío rápido** que pone el servicio sobre los reels de Instagram (ventanas propias, con Views),
 * en `res/values/copy_quicksend.xml`. Las burbujas no llevan texto visible: lo que sigue es para TalkBack y
 * para la pregunta de apagarlo.
 */
object QuickSendCopy {

    /** Para TalkBack, en cada burbuja: «Enviar este reel a Los del piso». */
    fun bubbleDescription(name: String): String = str(R.string.quicksend_bubble_description, name)

    fun sendingDescription(name: String): String = str(R.string.quicksend_sending_description, name)

    fun sentDescription(name: String): String = str(R.string.quicksend_sent_description, name)

    /** No ha encontrado algo: si la hoja se abrió, se queda abierta para terminar a mano. */
    fun failedDescription(name: String): String = str(R.string.quicksend_failed_description, name)

    /** Acción de TalkBack en cada burbuja, una por cada otro fijado: «Enviar a Ana y a Los del piso». */
    fun pairAction(name: String, other: String): String = str(R.string.quicksend_pair_action, name, other)

    /** Acción de TalkBack en cada burbuja (equivale a soltarla en la «X»). */
    val HIDE_ACTION: String get() = str(R.string.quicksend_hide_action)

    /** Nombre de la ventana de las burbujas (TalkBack la anuncia así). */
    val WINDOW_TITLE: String get() = str(R.string.quicksend_window_title)

    /** La zona «X» que sale abajo mientras arrastras las burbujas. */
    val ZONE_TITLE: String get() = str(R.string.quicksend_zone_title)

    /** Tras esconder las burbujas tres veces seguidas, una sola vez. */
    val ASK_TITLE: String get() = str(R.string.quicksend_ask_title)

    val ASK_BODY: String get() = str(R.string.quicksend_ask_body)

    val ASK_TURN_OFF: String get() = str(R.string.quicksend_ask_turn_off)
    val ASK_KEEP: String get() = str(R.string.quicksend_ask_keep)
}
