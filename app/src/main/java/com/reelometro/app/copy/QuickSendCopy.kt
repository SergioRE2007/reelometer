package com.reelometro.app.copy

/**
 * Textos del **envío rápido** que pone el servicio sobre los reels de Instagram (ventanas propias, con Views).
 * Las burbujas no llevan texto visible: lo que sigue es para TalkBack y para la pregunta de apagarlo.
 */
object QuickSendCopy {

    /** Para TalkBack, en cada burbuja: «Enviar este reel a Los del piso». */
    fun bubbleDescription(name: String): String = "Enviar este reel a $name"

    fun sendingDescription(name: String): String = "Enviando a $name"

    fun sentDescription(name: String): String = "Enviado a $name"

    /** No ha encontrado algo: si la hoja se abrió, se queda abierta para terminar a mano. */
    fun failedDescription(name: String): String = "No he podido enviarlo a $name"

    /** Acción de TalkBack en cada burbuja, una por cada otro fijado: «Enviar a Ana y a Los del piso». */
    fun pairAction(name: String, other: String): String = "Enviar a $name y a $other"

    /** Acción de TalkBack en cada burbuja (equivale a soltarla en la «X»). */
    const val HIDE_ACTION = "Esconder hasta el siguiente reel"

    /** Nombre de la ventana de las burbujas (TalkBack la anuncia así). */
    const val WINDOW_TITLE = "Envío rápido de Reelómetro"

    /** La zona «X» que sale abajo mientras arrastras las burbujas. */
    const val ZONE_TITLE = "Suelta aquí para esconder"

    /** Tras esconder las burbujas tres veces seguidas, una sola vez. */
    const val ASK_TITLE = "¿Apago el envío rápido?"

    const val ASK_BODY =
        "Has escondido las burbujas tres veces seguidas. Si lo apagas, lo vuelves a encender en Ajustes, " +
            "debajo de «A quién envías»."

    const val ASK_TURN_OFF = "APAGAR"
    const val ASK_KEEP = "SEGUIR"
}
