package com.makrydensystems.news.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels for FCM push (Build #24). One channel per
 * Settings toggle, 1:1 with the FCM kinds in news_fcm_tokens.kinds —
 * so a user can silence one kind in system settings without losing
 * the others, and the server's channel_id (see _shared/fcm.ts
 * FCM_CHANNELS) always matches an existing channel.
 *
 * Idempotent: creating a channel that already exists is a no-op, so
 * this runs on every cold start (MainActivity) and in the messaging
 * service's onCreate — whichever runs first wins, and a user who
 * changed a channel's importance in system settings keeps their
 * choice (createNotificationChannel never overwrites an existing
 * channel's settings).
 */
object NotificationChannels {

    const val CHANNEL_BREAKING = "starbrook_breaking"
    const val CHANNEL_MORNING = "starbrook_morning"
    const val CHANNEL_EVENING = "starbrook_evening"
    const val CHANNEL_WEATHER = "starbrook_weather"

    /** FCM kind -> channel id. Unknown kinds fall back to breaking. */
    fun channelForKind(kind: String?): String = when (kind) {
        "morning" -> CHANNEL_MORNING
        "evening" -> CHANNEL_EVENING
        "weather" -> CHANNEL_WEATHER
        else -> CHANNEL_BREAKING
    }

    /** Notification id per kind, so a new push of one kind replaces
     * the previous notification of that kind instead of stacking. */
    fun notificationIdForKind(kind: String?): Int = when (kind) {
        "morning" -> 1002
        "evening" -> 1003
        "weather" -> 1004
        else -> 1001
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                CHANNEL_BREAKING,
                "Breaking news",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Breaking news as it happens."
            },
            NotificationChannel(
                CHANNEL_MORNING,
                "Morning edition",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "The morning edition, when it is ready."
            },
            NotificationChannel(
                CHANNEL_EVENING,
                "Evening edition",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "The evening edition, when it is ready."
            },
            NotificationChannel(
                CHANNEL_WEATHER,
                "Weather alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Severe weather warnings and rain starting soon."
            },
        )
        for (channel in channels) manager.createNotificationChannel(channel)
    }
}
