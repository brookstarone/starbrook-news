package com.makrydensystems.news.data.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.makrydensystems.news.MainActivity
import com.makrydensystems.news.R
import com.makrydensystems.news.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * FCM push receiver (Build #24). The server sends DATA-ONLY messages
 * (see _shared/fcm.ts) — no "notification" key — so this service
 * always builds the notification itself, foreground and background
 * alike, and the tap routing is identical in both cases.
 *
 * Payload data (all strings): title, body, kind
 * (breaking|morning|evening|weather), route (the in-app route the tap
 * opens: "news", "weather", or "story?link=..&title=.."), link (the
 * story URL for a breaking push). The channel + notification id come
 * from the kind (NotificationChannels), so each kind replaces its own
 * previous notification instead of stacking.
 *
 * onNewToken: Firebase rotated the token — re-register it with the
 * server immediately, with the current ZIP + toggle kinds. The
 * registrar here is built from the application context (the service
 * is created by the system, not by MainActivity, so it cannot reach
 * AppContainer); it reads the same DataStore the rest of the app
 * uses, so the ZIP + toggles it sends are the live ones.
 */
class StarBrookMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "StarBrookMsgService"
        /** Intent extra MainActivity reads for the tap destination. */
        const val EXTRA_ROUTE = "starbrook_route"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = data["kind"] ?: "breaking"
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "StarBrook News"
        val body = data["body"].orEmpty()
        val route = validRoute(data["route"])
        // R-43 (2026-11-25): the receive path used to notify for
        // whatever kind arrived, ignoring the local toggles — a user
        // who turned Morning off still got the morning push. Check the
        // matching toggle and drop the notification when that kind is
        // off. The DataStore read runs on the service scope; a read
        // failure fails OPEN (show the notification) and never crashes
        // the receive path. The server-side unregister stays the
        // primary path — this is the local backstop for a stale row.
        scope.launch {
            val allowed = try {
                val settings = SettingsStore(applicationContext)
                shouldShowNotification(
                    kind,
                    breaking = settings.notifyBreakingFlow.first(),
                    morning = settings.notifyMorningFlow.first(),
                    evening = settings.notifyEveningFlow.first(),
                    weather = settings.notifyWeatherFlow.first(),
                )
            } catch (e: Exception) {
                Log.w(TAG, "toggle read failed; showing notification (fail open)", e)
                true
            }
            if (allowed) showNotification(kind, title, body, route)
        }
    }

    override fun onNewToken(token: String) {
        // Token rotated: re-register with the current ZIP + kinds.
        // Never throws — a failure retries on the next cold start.
        scope.launch {
            try {
                val settings = SettingsStore(applicationContext)
                val registrar = FcmRegistrar(OkHttpClient(), settings)
                val kinds = buildList {
                    if (settings.notifyBreakingFlow.first()) add("breaking")
                    if (settings.notifyMorningFlow.first()) add("morning")
                    if (settings.notifyEveningFlow.first()) add("evening")
                    if (settings.notifyWeatherFlow.first()) add("weather")
                }
                if (kinds.isEmpty()) {
                    registrar.unregisterStored()
                } else {
                    registrar.register(token, settings.zipFlow.first(), kinds)
                }
            } catch (e: Exception) {
                Log.w(TAG, "onNewToken re-register failed", e)
            }
        }
    }

    private fun showNotification(kind: String, title: String, body: String, route: String) {
        NotificationChannels.createChannels(this)
        val channelId = NotificationChannels.channelForKind(kind)
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NotificationChannels.notificationIdForKind(kind),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (kind == "breaking" || kind == "weather") {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationChannels.notificationIdForKind(kind), notification)
    }

    /** Only routes the app knows how to open — anything else (or a
     * missing route) opens the News tab. The server only ever sends
     * our own routes, but a notification tap must never crash the app
     * on a bad value. */
    private fun validRoute(route: String?): String {
        val r = route?.trim().orEmpty()
        if (r.isEmpty()) return "news"
        return when {
            r == "news" || r == "weather" || r == "portal" || r == "search" -> r
            r.startsWith("story?link=") -> r
            r.startsWith("play?url=") -> r
            else -> "news"
        }
    }
}

/**
 * R-43 (2026-11-25): whether a push of [kind] may notify, given the
 * four local toggles. Unknown kinds fall under breaking (the default
 * kind). Pure (no Android) so unit tests can cover it directly.
 */
internal fun shouldShowNotification(
    kind: String,
    breaking: Boolean,
    morning: Boolean,
    evening: Boolean,
    weather: Boolean,
): Boolean = when (kind) {
    "morning" -> morning
    "evening" -> evening
    "weather" -> weather
    else -> breaking
}
