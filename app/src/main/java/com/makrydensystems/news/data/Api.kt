package com.makrydensystems.news.data

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
}
