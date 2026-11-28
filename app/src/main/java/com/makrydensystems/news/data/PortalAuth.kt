package com.makrydensystems.news.data

import android.util.Base64
import com.makrydensystems.news.data.StarBrookApi.PORTAL_SITE
import com.makrydensystems.news.data.StarBrookApi.SUPABASE_ANON_KEY
import com.makrydensystems.news.data.StarBrookApi.SUPABASE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// ---------------------------------------------------------------------------
// Portal auth — Supabase Auth REST, mirroring the web portal's flow
// (~/workspace/portal-news-work/repo_portal_js.json, portal.js) exactly:
//
//   signUp:  POST /auth/v1/signup?redirect_to=<SITE>/account/
//            { email, password }
//   signIn:  POST /auth/v1/token?grant_type=password { email, password }
//   recover: POST /auth/v1/recover?redirect_to=<SITE>/account/reset-password/
//            { email }
//   refresh: POST /auth/v1/token?grant_type=refresh_token { refresh_token }
//   logout:  POST /auth/v1/logout?scope=local {} (Bearer, best effort)
//   user:    GET  /auth/v1/user (Bearer — fallback email, portal news.js)
//
// Every call sends apikey: <anon key> + Content-Type: application/json,
// and authed calls add Authorization: Bearer <access token> — nothing
// else, exactly like portal.js http(). The session is persisted like
// the portal's mk-portal-session (access / refresh / exp / email /
// remember) via SettingsStore; "keep me signed in" only changes whether
// the session survives an app restart — unlike the web there is no
// sessionStorage equivalent on Android, so a non-remembered session is
// still kept for the current process lifetime and cleared on sign-out
// (and on the next cold start it is treated as expired — see
// PortalAuth.restoreSession).
//
// Email confirmation: Supabase sends a confirmation email on sign-up
// (same as the web portal — the redirect_to above is where the link
// lands). When confirmation is required the sign-up response carries a
// user but NO session; [AuthResult.NeedsConfirmation] tells the UI to
// say "check your inbox" instead of pretending the user is signed in.
// A returning user who has not confirmed yet gets
// "email_not_confirmed" on sign-in, mapped to the same plain language.
//
// Friendly errors mirror portal.js friendlyAuthError() — never a raw
// Supabase message, never a status code, in the UI.
// ---------------------------------------------------------------------------

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/** What sign-in / sign-up returned. */
sealed interface AuthResult {
    /** Signed in — session persisted, [email] is the account email. */
    data class SignedIn(val email: String) : AuthResult

    /** Account created, but email confirmation is required first:
     * Supabase sent a confirmation email; there is no session yet. */
    data class NeedsConfirmation(val email: String) : AuthResult

    /** Recovery email sent (or the address is not registered — Supabase
     * does not say which; the UI says the same thing either way). */
    data object RecoverySent : AuthResult
}

class PortalAuth(
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    // Token responses are parsed via JSONObject (below) so an unexpected
    // extra field never breaks sign-in.

    /** False when no anon key was supplied at build time — sign-in is
     * unavailable in this build and the Hub must say so. */
    val isConfigured: Boolean get() = SUPABASE_ANON_KEY.isNotBlank()

    /** The currently signed-in email, or "" when signed out. */
    suspend fun sessionEmail(): String =
        settings.portalSessionFlow.first()?.email.orEmpty()

    /**
     * Restore the persisted session on cold start. Returns the email
     * when a usable session exists, "" otherwise. A stored session whose
     * refresh token is rejected (400/401) is cleared, exactly like
     * portal.js token() — the user is simply signed out, not errored.
     * A non-remembered session does not survive a cold start (web
     * sessionStorage parity): it is cleared here.
     *
     * The session is validated (and refreshed when stale) via
     * [validAccessToken], but the token itself is never returned — the
     * caller displays this value as the account email, so returning
     * the raw token here put it on screen as the user's identity.
     */
    suspend fun restoreSession(): String = withContext(Dispatchers.IO) {
        val session = settings.portalSessionFlow.first() ?: return@withContext ""
        if (!session.keepSignedIn) {
            settings.clearPortalSession()
            return@withContext ""
        }
        return@withContext try {
            validAccessToken() ?: return@withContext ""
            // Re-read the stored session: a refresh rewrites it via
            // takeTokens with session.email preserved, so this is the
            // account email, not the token.
            settings.portalSessionFlow.first()?.email.orEmpty()
                .ifBlank { fetchAccountEmail() }
        } catch (e: IOException) {
            // Offline on cold start: keep the stored session (the Hub
            // shows signed-in from the cache; the next authed call
            // refreshes or signs out). Only 400/401 clears it.
            session.email
        }
    }

    suspend fun signIn(email: String, password: String, keepSignedIn: Boolean): AuthResult =
        withContext(Dispatchers.IO) {
            val data = postAuth(
                "/auth/v1/token?grant_type=password",
                """{"email":${jsonString(email)},"password":${jsonString(password)}}""",
                token = null,
            )
            takeTokens(data, email, keepSignedIn)
            AuthResult.SignedIn(data.userEmail() ?: email)
        }

    suspend fun signUp(email: String, password: String, keepSignedIn: Boolean): AuthResult =
        withContext(Dispatchers.IO) {
            val data = postAuth(
                "/auth/v1/signup?redirect_to=" +
                    java.net.URLEncoder.encode("$PORTAL_SITE/account/", "UTF-8"),
                """{"email":${jsonString(email)},"password":${jsonString(password)}}""",
                token = null,
            )
            val access = data.optString("access_token")
            if (access.isNullOrBlank()) {
                // No session in the response = email confirmation required
                // (portal.js signUp has the same shape). Nothing persisted.
                return@withContext AuthResult.NeedsConfirmation(email)
            }
            takeTokens(data, email, keepSignedIn)
            AuthResult.SignedIn(data.userEmail() ?: email)
        }

    suspend fun sendRecoveryEmail(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            postAuth(
                "/auth/v1/recover?redirect_to=" +
                    java.net.URLEncoder.encode("$PORTAL_SITE/account/reset-password/", "UTF-8"),
                """{"email":${jsonString(email)}}""",
                token = null,
            )
            AuthResult.RecoverySent
        }

    /** Best-effort server logout, then the local session is cleared no
     * matter what — sign-out never fails on a network error. */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            val session = settings.portalSessionFlow.first()
            if (session != null) {
                runCatching {
                    postAuth("/auth/v1/logout?scope=local", "{}", token = session.accessToken)
                }
            }
            settings.clearPortalSession()
        }
    }

    /**
     * A valid access token, refreshing it first when it is stale —
     * portal.js token(): fresh while exp is > 60s away, else
     * grant_type=refresh_token; a 400/401 on refresh clears the session
     * and returns null. Throws IOException only when offline / the
     * refresh itself fails for a non-auth reason (caller decides).
     */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val session = settings.portalSessionFlow.first() ?: return@withContext null
        if (!session.isExpired) return@withContext session.accessToken
        try {
            val data = postAuth(
                "/auth/v1/token?grant_type=refresh_token",
                """{"refresh_token":${jsonString(session.refreshToken)}}""",
                token = null,
            )
            takeTokens(data, session.email, session.keepSignedIn)
            data.optString("access_token")
        } catch (e: PortalApiException) {
            if (e.status == 400 || e.status == 401) {
                settings.clearPortalSession()
                null
            } else {
                throw IOException(e.friendlyMessage, e)
            }
        }
    }

    /** The signed-in user's id (JWT sub claim), or "" when signed out —
     * portal.js sessionUserId(). Used for the news_subs / shelf_items
     * user_id=eq. filters. */
    suspend fun userId(): String {
        val token = settings.portalSessionFlow.first()?.accessToken.orEmpty()
        return userIdFromToken(token)
    }

    /** Fallback email from GET /auth/v1/user (portal news.js
     * accountEmail) when the stored session has no email on it. */
    suspend fun fetchAccountEmail(): String = withContext(Dispatchers.IO) {
        val token = validAccessToken() ?: return@withContext ""
        val request = authedRequest("GET", "/auth/v1/user", null, token)
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext ""
            JSONObject(body).optString("email", "")
        }
    }

    // --- internals ---

    private fun jsonString(value: String): String =
        JSONObject.quote(value)

    /** Persist a token response like portal.js takeTokens(). */
    private suspend fun takeTokens(data: JSONObject, fallbackEmail: String, keepSignedIn: Boolean) {
        val access = data.optString("access_token")
        val refresh = data.optString("refresh_token")
        val expiresIn = data.optLong("expires_in", 3600L)
        settings.savePortalSession(
            PortalSession(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
                email = data.userEmail() ?: fallbackEmail,
                keepSignedIn = keepSignedIn,
            ),
        )
    }

    private fun JSONObject.userEmail(): String? {
        val user = optJSONObject("user") ?: return null
        return user.optString("email").takeIf { it.isNotBlank() }
    }

    private fun authedRequest(method: String, path: String, bodyJson: String?, token: String?): Request {
        val builder = Request.Builder()
            .url(SUPABASE_URL + path)
            .header("apikey", SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val body = bodyJson?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PUT" -> builder.put(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            else -> builder.method(method, body)
        }
        return builder.build()
    }

    /**
     * One Auth REST call. Throws [PortalApiException] with a friendly
     * message (portal.js friendlyAuthError mapping) on any non-2xx, and
     * IOException("offline…") when the request never reaches the server.
     */
    private fun postAuth(path: String, bodyJson: String, token: String?): JSONObject {
        if (!isConfigured) throw PortalApiException(
            "Sign-in isn't set up in this build yet.",
            status = 0,
            code = "not_configured",
        )
        val request = authedRequest("POST", path, bodyJson, token)
        val text: String
        val code: Int
        try {
            client.newCall(request).execute().use { response ->
                code = response.code
                text = response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw IOException(
                "You seem to be offline. Check your connection and try again.",
                e,
            )
        }
        val data = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
        if (code !in 200..299) {
            throw PortalApiException(
                friendlyAuthError(data, code, context = null),
                status = code,
                code = data.optString("error_code")
                    .ifBlank { data.optString("code") }.ifBlank { null },
            )
        }
        return data
    }

    companion object {
        /** JWT sub claim without any JWT library — same split + base64url
         * decode portal.js sessionUserId() does. */
        fun userIdFromToken(token: String): String {
            val parts = token.split(".")
            if (parts.size < 2) return ""
            return try {
                val bytes = Base64.decode(parts[1], Base64.URL_SAFE)
                JSONObject(String(bytes, Charsets.UTF_8)).optString("sub", "")
            } catch (e: Exception) {
                ""
            }
        }
    }
}

/**
 * An API failure carrying the plain-language message the UI shows.
 * [status] is the HTTP status (0 = never reached the server);
 * [code] is the Supabase error_code when one was returned.
 */
class PortalApiException(
    val friendlyMessage: String,
    val status: Int,
    val code: String? = null,
) : IOException(friendlyMessage)

/**
 * portal.js friendlyAuthError(), ported: the Supabase error_code (or
 * message text) maps to one plain sentence. Unknown failures get the
 * generic line — never a raw message, never a code.
 */
fun friendlyAuthError(data: JSONObject, status: Int, context: String?): String {
    val code = data.optString("error_code").ifBlank { data.optString("code") }
    val message = (
        data.optString("msg")
            .ifBlank { data.optString("error_description") }
            .ifBlank { data.optString("message") }
            .ifBlank { data.optString("error") }
        ).lowercase()
    if (status == 0) return "You seem to be offline. Check your connection and try again."
    if (code == "email_not_confirmed" || message.contains("not confirmed")) {
        return "Please confirm your email first — the link is in your inbox (check spam too)."
    }
    if (code == "invalid_credentials" || message.contains("invalid login")) {
        return "That email and password didn't match. Try again — or reset your password below."
    }
    if (code == "weak_password" || message.contains("password should")) {
        return "That password is too easy to guess. Use at least 12 characters — a phrase you can remember works best."
    }
    if (code == "over_email_send_rate_limit" || code == "over_request_rate_limit" || status == 429) {
        return "Too many tries in a short time. Wait a few minutes and try again."
    }
    if (code == "same_password") return "That's the password you already have. Choose a new one."
    if (code == "user_already_exists" || message.contains("already registered")) {
        return "That email is already registered — try signing in instead."
    }
    if (context == "signup" && code == "email_address_invalid") {
        return "That email address doesn't look right."
    }
    return "Something went wrong on our side. Try again in a minute."
}
