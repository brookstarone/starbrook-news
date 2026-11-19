package com.makrydensystems.news.data

import com.makrydensystems.news.data.StarBrookApi.SUPABASE_ANON_KEY
import com.makrydensystems.news.data.StarBrookApi.SUPABASE_URL
import com.makrydensystems.news.data.local.AppDatabase
import com.makrydensystems.news.data.model.SavedStoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

// ---------------------------------------------------------------------------
// Portal data — Newsroom prefs, Shelf sync, Master Room. All three ride
// the same authed() pattern as the web portal (portal.js): apikey +
// Bearer access token, RLS limiting every row to the signed-in user.
// Every call here is best-effort from the UI's point of view: a failure
// never wipes local data and never crashes — local Room / DataStore is
// always the source of truth on screen, the server is the sync target.
//
//   news_subs (portal news.js — the live Newsroom page):
//     GET  /rest/v1/news_subs?select=morning,evening,subscribed,breaking,
//          sections&user_id=eq.<uid>
//     PATCH same URL, body { subscribed, morning, evening, breaking,
//          sections }   (sections = JSON array, or null = all sections)
//     POST /rest/v1/news_subs, body { user_id, email, morning, evening,
//          subscribed, breaking, sections: null }  (first subscribe)
//     The row holds: subscribed (master on/off), morning, evening,
//     breaking, sections (array of ids, or NULL for all). Portal
//     section ids: national, local, tech, business, sports, faith,
//     culture. The app only offers national/local/tech/business/culture
//     (SettingsStore.ALL_SECTIONS); sports/faith rows from the web are
//     preserved untouched on save (read-modify-write keeps them).
//
//   shelf_items (migration 20261021000000_shelf_items.sql):
//     id = client UUID, url_hash = lowercase hex SHA-1 of the canonical
//     URL (tracking params stripped), partial unique index
//     (user_id, url_hash) WHERE deleted=false, deleted=true rows are
//     tombstones. Upsert mirrors portal.js upsertRules():
//     POST /rest/v1/shelf_items?on_conflict=user_id,url_hash with
//     Prefer: resolution=merge-duplicates,return=minimal.
//     Deletes are tombstones, never hard deletes:
//     PATCH /rest/v1/shelf_items?user_id=eq.<uid>&url_hash=eq.<hash>
//     body { deleted: true }.
//
//   master-room (CONTRACT.md): POST /functions/v1/master-room with
//     { action: "status" } / { action: "chat", message }, Bearer token,
//     verify_jwt=false by design, in-function callerEmail() operator
//     gate. WARNING — the function's exact response shape was NOT
//     verifiable from any local copy (no master-room source in the
//     workdir); the parsing below is deliberately lenient (it accepts
//     "cards" / "status" / "reply" / "message" / "text" in any nesting)
//     and the Hub shows "couldn't load" instead of crashing when the
//     shape differs. Verify against the live function before relying
//     on the chat path.
//
// "How Saved works" policy (enforced here, surfaced in the Hub):
//   guest saves stay local-only (Room saved_stories, this device);
//   signed-in saves sync to shelf_items. The card bookmark toggle
//   keeps reading Room isSavedFlow — there is exactly one saved-state
//   source; this class only converges Room <-> server, never a
//   parallel store.
// ---------------------------------------------------------------------------

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/** One Master Room status card (operator overview). */
data class MasterRoomStatus(
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val statusIsAlert: Boolean = false,
)

/** The signed-in user's Newsroom prefs as stored in news_subs. */
data class NewsroomPrefs(
    val subscribed: Boolean = true,
    val morning: Boolean = true,
    val evening: Boolean = true,
    val breaking: Boolean = true,
    /** null = all sections (server meaning). App sections are a subset;
     * ids outside the app's five are preserved on save. */
    val sections: List<String>? = null,
)

class PortalRepository(
    private val client: OkHttpClient,
    private val auth: PortalAuth,
    private val settings: SettingsStore,
    private val db: AppDatabase,
) {

    // --- Newsroom prefs (news_subs) ---

    /** Load the stored row; null when the user has never subscribed
     * (the Hub then shows the Subscribe button, portal parity). */
    suspend fun loadNewsroomPrefs(): NewsroomPrefs? = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext null
        val uid = auth.userId()
        if (uid.isBlank()) return@withContext null
        val request = restRequest(
            "GET",
            "/rest/v1/news_subs?select=morning,evening,subscribed,breaking,sections" +
                "&user_id=eq.$uid",
            null, token,
        )
        val rows = JSONArray(execute(request))
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        NewsroomPrefs(
            subscribed = row.optBoolean("subscribed", true),
            morning = row.optBoolean("morning", true),
            evening = row.optBoolean("evening", true),
            breaking = row.optBoolean("breaking", true),
            sections = row.optJSONArray("sections")?.let { arr ->
                List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
            },
        )
    }

    /** Save prefs: PATCH the existing row, or POST a first row when the
     * user has never subscribed (portal news.js subscribe flow). Also
     * mirrors the values into local SettingsStore so the Hub and the
     * Settings screen agree even offline. */
    suspend fun saveNewsroomPrefs(prefs: NewsroomPrefs): Unit = withContext(Dispatchers.IO) {
        applyPrefsLocally(prefs)
        val token = auth.validAccessToken() ?: return@withContext
        val uid = auth.userId()
        if (uid.isBlank()) return@withContext
        val email = auth.sessionEmail().ifBlank { auth.fetchAccountEmail() }
        val body = JSONObject().apply {
            put("subscribed", prefs.subscribed)
            put("morning", prefs.morning)
            put("evening", prefs.evening)
            put("breaking", prefs.breaking)
            if (prefs.sections == null) put("sections", JSONObject.NULL)
            else put("sections", JSONArray(prefs.sections))
        }.toString()
        val patch = restRequest(
            "PATCH",
            "/rest/v1/news_subs?user_id=eq.$uid",
            body, token,
        )
        val patchResult = runCatching { execute(patch) }
        if (patchResult.isSuccess) return@withContext
        // No row to patch (or the PATCH failed) — try the first-subscribe
        // POST, exactly like portal news.js #btn-subscribe.
        val postBody = JSONObject().apply {
            put("user_id", uid)
            put("email", email)
            put("morning", prefs.morning)
            put("evening", prefs.evening)
            put("subscribed", prefs.subscribed)
            put("breaking", prefs.breaking)
            if (prefs.sections == null) put("sections", JSONObject.NULL)
            else put("sections", JSONArray(prefs.sections))
        }.toString()
        runCatching {
            execute(restRequest("POST", "/rest/v1/news_subs", postBody, token))
        }
    }

    suspend fun applyPrefsLocally(prefs: NewsroomPrefs) {
        settings.setNewsSubscribed(prefs.subscribed)
        settings.setPrefMorning(prefs.morning)
        settings.setPrefEvening(prefs.evening)
        settings.setPrefBreaking(prefs.breaking)
        val appSections = prefs.sections
            ?.filter { it in SettingsStore.ALL_SECTIONS }?.toSet()
            ?: SettingsStore.ALL_SECTIONS
        // Sections are stored locally as the enabled set; a null (all)
        // server value enables every app section.
        for (section in SettingsStore.ALL_SECTIONS) {
            settings.setSectionEnabled(section, section in appSections)
        }
    }

    /** The prefs the Hub edits, from local state (instant, offline-safe).
     * The web-only section ids (sports/faith) are NOT in the local set;
     * saveNewsroomPrefs merges them back in via [loadNewsroomPrefs] —
     * callers that save should load first when signed in. */
    suspend fun localNewsroomPrefs(): NewsroomPrefs {
        val subscribed = settings.newsSubscribedFlow.first()
        val morning = settings.prefMorningFlow.first()
        val evening = settings.prefEveningFlow.first()
        val breaking = settings.prefBreakingFlow.first()
        val sections = settings.sectionsFlow.first()
        return NewsroomPrefs(
            subscribed = subscribed,
            morning = morning,
            evening = evening,
            breaking = breaking,
            sections = if (sections.size == SettingsStore.ALL_SECTIONS.size) null
            else sections.toList(),
        )
    }

    // --- Shelf sync (Room saved_stories <-> shelf_items) ---

    /**
     * Converge local Room and server Shelf, both ways. Pull first
     * (remote rows not in Room are inserted locally), then push (local
     * rows missing or newer remotely are upserted). Tombstones
     * (deleted=true) remove the local row. Best-effort: any failure
     * returns false and local data is untouched. Call on sign-in, on
     * Hub open, and from the Hub's Sync button. Returns true when the
     * round trip completed.
     *
     * NOTE for the coordinator: the card bookmark toggle writes Room
     * only (NewsRepository.saveStory/unsaveStory). A save made while
     * signed in syncs on the NEXT syncShelf() call, not instantly —
     * wiring an instant push needs a hook in NewsRepository, which
     * this task did not own. Deletes made on another device converge
     * here via tombstones.
     */
    suspend fun syncShelf(): Boolean = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext false
        val uid = auth.userId()
        if (uid.isBlank()) return@withContext false
        try {
            val deviceId = settings.deviceId()
            // --- Pull ---
            val remote = fetchRemoteShelf(token, uid)
            val local = db.savedStoryDao().savedStories().first()
            val localByHash = local.associateBy { urlHash(it.link) }
            for (item in remote) {
                if (item.deleted) {
                    localByHash[item.urlHash]?.let { db.savedStoryDao().deleteByLink(it.link) }
                } else if (!localByHash.containsKey(item.urlHash)) {
                    db.savedStoryDao().insert(item.toEntity())
                }
            }
            // --- Push: every local row upserts (merge-duplicates on
            // (user_id, url_hash) converges re-saves to one row). ---
            val refreshedLocal = db.savedStoryDao().savedStories().first()
            for (row in refreshedLocal) {
                val body = shelfUpsertBody(uid, deviceId, row)
                val request = restRequest(
                    "POST",
                    "/rest/v1/shelf_items?on_conflict=user_id,url_hash",
                    body, token,
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
                execute(request)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Push one tombstone for a locally-unsaved story (best-effort). */
    suspend fun pushShelfDelete(link: String): Boolean = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext false
        val uid = auth.userId()
        if (uid.isBlank()) return@withContext false
        runCatching {
            val body = JSONObject().apply { put("deleted", true) }.toString()
            execute(
                restRequest(
                    "PATCH",
                    "/rest/v1/shelf_items?user_id=eq.$uid&url_hash=eq.${urlHash(link)}",
                    body, token,
                ),
            )
        }.isSuccess
    }

    private suspend fun fetchRemoteShelf(token: String, uid: String): List<RemoteShelfItem> {
        val request = restRequest(
            "GET",
            "/rest/v1/shelf_items?select=id,url,url_hash,title,source_name,snippet," +
                "published_ms,saved_at_ms,deleted,archived" +
                "&user_id=eq.$uid&deleted=eq.false&order=saved_at_ms.desc&limit=500",
            null, token,
        )
        // Tombstones matter too (a delete on another device must remove
        // the local row), so fetch them in a second pass.
        val live = JSONArray(execute(request))
        val tombRequest = restRequest(
            "GET",
            "/rest/v1/shelf_items?select=url_hash,deleted" +
                "&user_id=eq.$uid&deleted=eq.true&order=saved_at_ms.desc&limit=500",
            null, token,
        )
        val tombs = runCatching { JSONArray(execute(tombRequest)) }.getOrDefault(JSONArray())
        val items = mutableListOf<RemoteShelfItem>()
        for (i in 0 until live.length()) {
            val o = live.getJSONObject(i)
            items += RemoteShelfItem(
                urlHash = o.optString("url_hash"),
                url = o.optString("url"),
                title = o.optString("title"),
                source = o.optString("source_name"),
                snippet = o.optString("snippet").takeIf { it.isNotBlank() },
                publishedMs = o.optLong("published_ms", 0L),
                savedAtMs = o.optLong("saved_at_ms", System.currentTimeMillis()),
                deleted = false,
            )
        }
        for (i in 0 until tombs.length()) {
            val o = tombs.getJSONObject(i)
            items += RemoteShelfItem(
                urlHash = o.optString("url_hash"),
                url = "", title = "", source = null, snippet = null,
                publishedMs = 0L, savedAtMs = 0L, deleted = true,
            )
        }
        return items
    }

    private fun shelfUpsertBody(uid: String, deviceId: String, row: SavedStoryEntity): String =
        JSONObject().apply {
            // Deterministic id: the same story always upserts to the same
            // row (a fresh random UUID on every push would churn the PK
            // under merge-duplicates). nameUUIDFromBytes is v3, stable
            // per URL — the server only needs it unique + stable.
            put(
                "id",
                java.util.UUID.nameUUIDFromBytes(
                    ("shelf:${row.link}").toByteArray(Charsets.UTF_8),
                ).toString(),
            )
            put("user_id", uid)
            put("device_id", deviceId)
            put("url", row.link)
            put("url_hash", urlHash(row.link))
            put("title", row.title)
            put("source_name", row.source)
            put("snippet", row.excerpt ?: JSONObject.NULL)
            put("published_ms", row.published)
            put("saved_at_ms", row.savedAt)
            put("deleted", false)
            put("archived", false)
            put("version", 1)
        }.toString()

    // --- Master Room (operators only; data only) ---

    /** Operator status cards. Lenient parse — see the file header. */
    suspend fun masterRoomStatus(): List<MasterRoomStatus> = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext emptyList()
        val email = auth.sessionEmail()
        if (!StarBrookApi.isOperator(email)) return@withContext emptyList()
        val request = functionRequest(token, """{"action":"status"}""")
        val data = runCatching { JSONObject(execute(request)) }.getOrNull()
            ?: return@withContext emptyList()
        parseStatusCards(data)
    }

    /** One CADE chat round-trip. Returns CADE's reply text, or null when
     * the call failed / the shape was unrecognised (Hub shows a retry). */
    suspend fun sendCadeMessage(message: String): String? = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext null
        val email = auth.sessionEmail()
        if (!StarBrookApi.isOperator(email)) return@withContext null
        val body = JSONObject().apply {
            put("action", "chat")
            put("message", message)
        }.toString()
        val data = runCatching { JSONObject(execute(functionRequest(token, body))) }.getOrNull()
            ?: return@withContext null
        parseChatReply(data)
    }

    // --- shared HTTP ---

    private fun restRequest(
        method: String,
        path: String,
        bodyJson: String?,
        token: String,
        prefer: String? = null,
    ): Request {
        val builder = Request.Builder()
            .url(SUPABASE_URL + path)
            .header("apikey", SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        val body = bodyJson?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            else -> builder.method(method, body)
        }
        return builder.build()
    }

    private fun functionRequest(token: String, bodyJson: String): Request =
        Request.Builder()
            .url(SUPABASE_URL + "/functions/v1/master-room")
            .header("apikey", SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    /** Executes a request, returning the body text. Throws IOException
     * on transport failure and PortalApiException on non-2xx (friendly
     * message; a 401 here means the session died — the caller treats it
     * as signed-out). */
    private fun execute(request: Request): String {
        val code: Int
        val text: String
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
        if (code !in 200..299) {
            val data = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
            throw PortalApiException(
                friendlyAuthError(data, code, context = null),
                status = code,
                code = data.optString("error_code")
                    .ifBlank { data.optString("code") }.ifBlank { null },
            )
        }
        return text
    }

    companion object {
        /** Canonical URL for url_hash: tracking params stripped
         * (utm_*, fbclid, gclid, mc_*, _ga), fragment dropped. */
        fun canonicalUrl(url: String): String {
            val noFragment = url.substringBefore("#")
            val base = noFragment.substringBefore("?")
            val query = noFragment.substringAfter("?", "")
            if (query.isBlank()) return base
            val kept = query.split("&").filter { param ->
                val key = param.substringBefore("=").lowercase()
                !(key.startsWith("utm_") || key == "fbclid" || key == "gclid" ||
                    key.startsWith("mc_") || key == "_ga" || key == "_gl")
            }
            return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
        }

        /** Lowercase hex SHA-1 of the canonical URL (migration spec). */
        fun urlHash(url: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            val bytes = digest.digest(canonicalUrl(url).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** Lenient status parse: accepts {"cards":[{title,subtitle,
         * status,statusIsAlert|alert}]} or {"status": "..."} or a bare
         * {"message": ...}. Unknown shapes yield one honest card. */
        fun parseStatusCards(data: JSONObject): List<MasterRoomStatus> {
            val cards = data.optJSONArray("cards")
            if (cards != null && cards.length() > 0) {
                return List(cards.length()) { i ->
                    val o = cards.optJSONObject(i) ?: JSONObject()
                    MasterRoomStatus(
                        title = o.optString("title").ifBlank { "Master Room" },
                        subtitle = o.optString("subtitle")
                            .ifBlank { o.optString("detail") },
                        statusLabel = o.optString("status")
                            .ifBlank { o.optString("statusLabel") }
                            .ifBlank { "OK" },
                        statusIsAlert = o.optBoolean("statusIsAlert", false) ||
                            o.optBoolean("alert", false) ||
                            o.optString("status").equals("alert", ignoreCase = true),
                    )
                }
            }
            val text = data.optString("status")
                .ifBlank { data.optString("message") }
                .ifBlank { data.optString("text") }
            if (text.isNotBlank()) {
                return listOf(
                    MasterRoomStatus(
                        title = "Master Room",
                        subtitle = text,
                        statusLabel = "Live",
                    ),
                )
            }
            return emptyList()
        }

        /** Lenient chat-reply parse: "reply" / "message" / "text", at
         * the top level or nested under "data" / "result". */
        fun parseChatReply(data: JSONObject): String? {
            for (key in listOf("reply", "message", "text", "answer")) {
                val value = data.optString(key)
                if (value.isNotBlank()) return value
            }
            for (nest in listOf("data", "result")) {
                val inner = data.optJSONObject(nest) ?: continue
                for (key in listOf("reply", "message", "text", "answer")) {
                    val value = inner.optString(key)
                    if (value.isNotBlank()) return value
                }
            }
            return null
        }
    }
}

/** One remote shelf row, as pulled from shelf_items. */
private data class RemoteShelfItem(
    val urlHash: String,
    val url: String,
    val title: String,
    val source: String?,
    val snippet: String?,
    val publishedMs: Long,
    val savedAtMs: Long,
    val deleted: Boolean,
) {
    fun toEntity(): SavedStoryEntity = SavedStoryEntity(
        link = url,
        title = title.ifBlank { url },
        source = source.orEmpty(),
        excerpt = snippet,
        published = publishedMs,
        section = "national",
        image = null,
        savedAt = savedAtMs,
    )
}
