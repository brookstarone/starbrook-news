package com.makrydensystems.news.data.push

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.StarBrookApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

/**
 * FCM token registration (Build #24): keeps the app's Firebase token
 * on the server (news_fcm_tokens, via the news-fcm-subscribe edge
 * function) so breaking + editions + weather alerts can reach this
 * phone as real push notifications — not just while the app is open.
 *
 * When it runs:
 * - Cold start (MainActivity): [ensureRegistered] — registers the
 *   current token with the current ZIP + toggle kinds, or unregisters
 *   when every toggle is off.
 * - Toggle change / ZIP change (SettingsScreen): [ensureRegistered]
 *   again — the server row is upserted, so the new kinds/ZIP take
 *   effect without a re-subscribe.
 * - Token refresh (StarBrookMessagingService.onNewToken): [register]
 *   with the fresh token.
 *
 * Safety:
 * - Never throws: every failure returns false and is logged; the
 *   next cold start retries. A failed registration never breaks the
 *   app — the in-app polling (PushModule) keeps working.
 * - Firebase is guarded: [firebaseAvailable] returns false when
 *   FirebaseApp is not initialized (no google-services.json yet, or
 *   a broken Firebase setup) and nothing Firebase is touched. The
 *   token is never logged.
 * - The ZIP sent is the DEVICE zip (SettingsStore — the same ZIP the
 *   app's Weather screen uses). When the device has no ZIP, the zip
 *   key is omitted from the POST and the server preserves the stored
 *   value (the 48235 lesson: a missing ZIP must never wipe the one
 *   on file).
 * - The token is only ever sent to Supabase, never logged, and the
 *   last registered token is kept in DataStore (app-private) so an
 *   unregister can target exactly the token the server has.
 */
class FcmRegistrar(
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {

    companion object {
        private const val TAG = "FcmRegistrar"
        private const val FCM_SUBSCRIBE_URL =
            StarBrookApi.SUPABASE_URL + "/functions/v1/news-fcm-subscribe"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    /** False when Firebase is not initialized — nothing FCM is safe to
     * call. True once google-services.json + the plugin are in place. */
    fun firebaseAvailable(): Boolean = try {
        FirebaseApp.getInstance()
        true
    } catch (e: IllegalStateException) {
        false
    }

    /** The current FCM registration token, or null when Firebase is
     * unavailable or the token fetch fails. */
    suspend fun currentToken(): String? {
        if (!firebaseAvailable()) return null
        return try {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            cont.resume(task.result)
                        } else {
                            cont.resume(null)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "couldn't get FCM token", e)
            null
        }
    }

    /**
     * Make sure the server has this device's current token + ZIP +
     * kinds. When every notification toggle is off, the stored token
     * is unregistered server-side instead. Returns true when the
     * server state now matches the device state.
     */
    suspend fun ensureRegistered(): Boolean = withContext(Dispatchers.IO) {
        try {
            val breaking = settings.notifyBreakingFlow.first()
            val morning = settings.notifyMorningFlow.first()
            val evening = settings.notifyEveningFlow.first()
            val weather = settings.notifyWeatherFlow.first()
            if (!breaking && !morning && !evening && !weather) {
                return@withContext unregisterStored()
            }
            val kinds = buildList {
                if (breaking) add("breaking")
                if (morning) add("morning")
                if (evening) add("evening")
                if (weather) add("weather")
            }
            val token = currentToken() ?: return@withContext false
            val zip = settings.zipFlow.first()
            register(token, zip, kinds)
        } catch (e: Exception) {
            Log.w(TAG, "ensureRegistered failed", e)
            false
        }
    }

    /**
     * Register one token with the server. [zip] is the device ZIP
     * (null = omit the key, server preserves the stored ZIP).
     * [kinds] is the current toggle state, 1:1 with the app's
     * Settings toggles.
     */
    suspend fun register(token: String, zip: String?, kinds: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (!StarBrookApi.SUPABASE_ANON_KEY.isNotBlank()) {
                Log.w(TAG, "no anon key — FCM registration unavailable in this build")
                return@withContext false
            }
            val body = JSONObject().apply {
                put("token", token)
                if (!zip.isNullOrBlank()) put("zip", zip.trim())
                put("kinds", JSONArray(kinds))
                put("platform", "android")
            }
            val request = Request.Builder()
                .url(FCM_SUBSCRIBE_URL)
                .header("apikey", StarBrookApi.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val ok = response.isSuccessful &&
                        runCatching {
                            JSONObject(response.body?.string().orEmpty())
                                .optBoolean("ok", false)
                        }.getOrDefault(false)
                    if (ok) settings.setFcmToken(token)
                    ok
                }
            } catch (e: IOException) {
                Log.w(TAG, "FCM register failed (offline?)", e)
                false
            }
        }

    /** Unregister the stored token server-side (all toggles off, or
     * sign-out cleanup). Returns true when there is nothing left to
     * unregister, or the server confirmed the removal. */
    suspend fun unregisterStored(): Boolean = withContext(Dispatchers.IO) {
        val token = settings.fcmTokenFlow.first().orEmpty()
        if (token.isBlank()) return@withContext true
        if (!StarBrookApi.SUPABASE_ANON_KEY.isNotBlank()) return@withContext false
        val body = JSONObject().apply {
            put("token", token)
            put("action", "unregister")
        }
        val request = Request.Builder()
            .url(FCM_SUBSCRIBE_URL)
            .header("apikey", StarBrookApi.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) settings.clearFcmToken()
                response.isSuccessful
            }
        } catch (e: IOException) {
            Log.w(TAG, "FCM unregister failed (offline?)", e)
            false
        }
    }
}
