package com.makrydensystems.news.data

import com.makrydensystems.news.BuildConfig

/**
 * Public base URLs — single source of truth. No credentials anywhere in
 * this file (v1 fetches public URLs only; anything needing a key lives in
 * TODO.md and is NOT built here).
 */
object StarBrookApi {
    const val STORAGE_BASE = "https://bamrgsjgvakyucyqtwlg.supabase.co/storage/v1/object/public/"
    const val RSS_URL = "https://bamrgsjgvakyucyqtwlg.supabase.co/functions/v1/news-rss"

    /** Full URL for a news-videos bucket file, e.g. "2026-11-19-morning.mp4". */
    fun videoUrl(file: String): String = STORAGE_BASE + "news-videos/" + file

    /** Full URL for a news-videos poster file. */
    fun posterUrl(poster: String?): String? =
        poster?.takeIf { it.isNotBlank() }?.let { STORAGE_BASE + "news-videos/" + it }

    // --- Makryden portal (Supabase project "makryden", same as the web
    // portal at makryden-systems.com/account/). Mirrors
    // ~/workspace/portal-news-work/repo_portal_js.json (portal.js) call
    // for call: same project URL, same anon key, same Auth REST paths,
    // same /rest/v1 table paths. The anon key is PUBLIC in the portal's
    // own JS, but it is NEVER committed here: it arrives via the
    // BuildConfig field fed from env / gradle property (see
    // app/build.gradle.kts). An empty key means "sign-in unavailable in
    // this build" — the Hub says so instead of failing mysteriously.
    const val SUPABASE_URL = "https://bamrgsjgvakyucyqtwlg.supabase.co"
    const val PORTAL_SITE = "https://makrydensystems.com"
    val SUPABASE_ANON_KEY: String get() = BuildConfig.SUPABASE_ANON_KEY

    /** Operator emails — the Master Room gate (CONTRACT.md). The server
     * function re-checks via its own callerEmail(); this only hides the
     * section from everyone else. */
    val OPERATOR_EMAILS: Set<String> = setOf(
        "brookstarone@gmail.com",
        "mattdouglas9216@gmail.com",
    )

    fun isOperator(email: String?): Boolean =
        email != null && OPERATOR_EMAILS.contains(email.trim().lowercase())
}
