package com.reelometro.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reelometro.app.MainActivity
import com.reelometro.app.R
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.TitledText
import com.reelometro.app.ui.theme.LightReelColors
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Canales y publicación de los avisos. Sin permiso (`POST_NOTIFICATIONS` en Android 13+) o con las
 * notificaciones de la app apagadas no se publica nada.
 */
object Notifications {

    /** Tipo de aviso: canal propio (el usuario puede silenciar uno sin el otro) e id fijo (uno de cada). */
    enum class Kind(val channel: String, val id: Int) {
        DAILY("summary_daily", 1),
        MONTHLY("summary_monthly", 2),

        /** Aviso en directo al pasar el objetivo (una vez al día). */
        GOAL_LIVE("goal_live", 3),

        /**
         * El servicio está activado pero no funciona ([com.reelometro.app.detection.ServiceHealth.STALLED]). Canal propio,
         * fuera del grupo «Resúmenes»: no es un resumen y no debería silenciarse con ellos. Se crea al primer aviso.
         */
        SERVICE("service_health", 5),
    }

    /** Grupo «Resúmenes» (`notification_channel_name`) que reúne los dos canales en los ajustes del sistema. */
    private const val GROUP = "summaries_group"

    /** Canal provisional de una versión antigua: se borra al arrancar. */
    private const val LEGACY_CHANNEL = "summaries"

    /**
     * Canal e id del aviso de nivel nuevo del antiguo Pasaporte (hasta 1.11.x; Flap no manda avisos).
     * El canal se borra al arrancar y, si su aviso seguía en la cortina, se quita. No se reutilizan.
     */
    private const val LEGACY_LEVEL_CHANNEL = "passport_level"
    private const val LEGACY_LEVEL_ID = 4

    /**
     * Crea (o actualiza) los canales del grupo «Resúmenes» y borra los antiguos (el provisional y el del
     * aviso de nivel, con su aviso y lo que recordaba). Idempotente.
     */
    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        manager.cancel(LEGACY_LEVEL_ID)
        manager.deleteNotificationChannel(LEGACY_LEVEL_CHANNEL)
        AlertStore(context).dropLegacyLevel()
        manager.createNotificationChannelGroup(
            NotificationChannelGroupCompat.Builder(GROUP)
                .setName(context.getString(R.string.notification_channel_name))
                .setDescription(context.getString(R.string.notification_channel_description))
                .build(),
        )
        manager.createNotificationChannelsCompat(
            listOf(
                channel(context, Kind.DAILY, R.string.notification_channel_daily_name, R.string.notification_channel_daily_description),
                channel(context, Kind.MONTHLY, R.string.notification_channel_monthly_name, R.string.notification_channel_monthly_description),
                channel(Kind.GOAL_LIVE, NotificationCopy.CHANNEL_GOAL_NAME, NotificationCopy.CHANNEL_GOAL_DESCRIPTION),
            ),
        )
    }

    /** Canal del aviso del servicio (sin grupo); solo cuando hace falta. Idempotente. */
    private fun createServiceChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(Kind.SERVICE.channel, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(NotificationCopy.CHANNEL_SERVICE_NAME)
                .setDescription(NotificationCopy.CHANNEL_SERVICE_DESCRIPTION)
                .setShowBadge(false)
                .build(),
        )
    }

    /** Quita el aviso [kind] de la cortina si sigue ahí. */
    fun cancel(context: Context, kind: Kind) {
        NotificationManagerCompat.from(context).cancel(kind.id)
    }

    /** ¿Se puede notificar? Permiso concedido (Android 13+) y notificaciones de la app encendidas. */
    fun canNotify(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Publica (o sustituye) el aviso [kind]: [copy] en la cortina, [public] en la pantalla de bloqueo
     * con «ocultar contenido sensible», toque → [route] de MainActivity, y desaparece solo en [expiresAt]
     * (sus textos dicen «hoy» o «el mes pasado»). Devuelve `false` si no se puede notificar.
     */
    @SuppressLint("MissingPermission") // canNotify() comprueba el permiso justo antes.
    fun show(
        context: Context,
        kind: Kind,
        copy: TitledText,
        public: TitledText,
        route: String,
        now: ZonedDateTime,
        expiresAt: ZonedDateTime,
    ): Boolean {
        if (!canNotify(context)) return false
        createChannels(context)
        if (kind == Kind.SERVICE) createServiceChannel(context)
        val tap = PendingIntent.getActivity(
            context,
            kind.id,
            MainActivity.intent(context, route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = base(context, kind)
            .setContentTitle(public.title)
            .setContentText(public.text)
            .setContentIntent(tap)
            .build()
        val notification = base(context, kind)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.text))
            .setContentIntent(tap)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setWhen(now.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(Duration.between(now, expiresAt).toMillis().coerceAtLeast(MIN_TIMEOUT_MS))
            .build()
        NotificationManagerCompat.from(context).notify(kind.id, notification)
        return true
    }

    private fun base(context: Context, kind: Kind): NotificationCompat.Builder =
        NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_notification)
            // El acento es el mismo en los dos temas.
            .setColor(LightReelColors.signal.toArgb())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    private fun channel(context: Context, kind: Kind, name: Int, description: Int): NotificationChannelCompat =
        channel(kind, context.getString(name), context.getString(description))

    private fun channel(kind: Kind, name: String, description: String): NotificationChannelCompat =
        NotificationChannelCompat.Builder(kind.channel, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(name)
            .setDescription(description)
            .setGroup(GROUP)
            .setShowBadge(false)
            .build()

    private const val MIN_TIMEOUT_MS = 60_000L
}
