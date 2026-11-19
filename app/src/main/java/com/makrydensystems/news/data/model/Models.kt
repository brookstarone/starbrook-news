package com.makrydensystems.news.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * News models — field names are FIXED by DESIGN.md §7 (the components read
 * these exact fields). JSON shapes were verified live 2026-11-19 against the
 * public Supabase storage bucket (see NewsRepository for the URLs).
 *
 * Story / Breaking / LiveItem / BriefingVideo are both @Serializable (network)
 * and Room @Entity (offline cache). Saved stories live in a separate table
 * ([SavedStoryEntity]) because one entity class maps to exactly one table.
 */

// ---------------------------------------------------------------------------
// Story — edition JSON: {"title","source","excerpt","link","published":ms,
// "section","image","n"} — "n" is the outlet count (trust mark; web
// fallback is 1 when missing). isOriginal / beat are NOT in the edition
// JSON; they default off and are filled by the Originals pipeline
// (TODO.md) when it lands.
// DEFECT 1 (2026-11-19): the live bucket JSON OMITS "excerpt"/"image" on
// some stories (morning 2026-11-19 story 13 "Gaten Matarazzo…" has no
// "image" key at all; evening has two such stories). kotlinx-serialization
// treats a nullable property WITHOUT a default as required — an absent key
// throws MissingFieldException and kills the WHOLE edition decode. The
// "= null" defaults on excerpt/image below make an absent key decode to
// null instead. "title"/"source"/"link"/"published"/"section" stay required:
// every observed story carries them, and a story without them is unusable.
// ---------------------------------------------------------------------------
@Serializable
@Entity(tableName = "cached_stories", primaryKeys = ["link", "edition"])
data class Story(
    val title: String,
    val source: String,
    val excerpt: String? = null,
    val link: String,
    val published: Long, // ms epoch
    val section: String,
    val image: String? = null,
    val isOriginal: Boolean = false,
    val beat: String? = null,
    // Outlet count from the edition JSON's "n" key (trust mark — the
    // card reads "Confirmed by N outlets"). Room column too: the DB
    // version bump in AppDatabase covers the added column; the cache is
    // refetchable, so the destructive migration on upgrade is fine.
    @SerialName("n") val outletCount: Int = 1,
    // Room-only: which edition cache this row belongs to ("morning" /
    // "evening"). NOT part of the network JSON (kotlinx @Transient) and
    // never read by the UI. It is a composite-PK column so the same story
    // can sit in both edition caches.
    @Transient val edition: String = "",
)

// ---------------------------------------------------------------------------
// Breaking — breaking.json: {"breaking": null | {"t","s","a","n","u"},
// "detectedAt": ISO-8601}. Live file was null when verified (2026-11-19
// ~12:55 CST); the compact keys are confirmed from the live web page JS
// (news_mix.html renderBreaking/fetchLiveBreaking: b.t title, b.s source,
// b.a age string, b.n outlet count, b.u read-page link) plus the 6-hour
// freshness rule on detectedAt, which the repository mirrors.
// ---------------------------------------------------------------------------
@Serializable
@Entity(tableName = "breaking")
data class Breaking(
    @SerialName("t") val title: String,
    @SerialName("s") val source: String,
    @SerialName("a") val age: String? = null, // display string, e.g. "25 min ago"
    @SerialName("n") val outletCount: Int = 1, // trust mark
    @PrimaryKey @SerialName("u") val url: String, // read-page link
)

// ---------------------------------------------------------------------------
// LiveItem — latest.json: {"stories":[{"t","s","a","u","published","label"}]}
// Verified live 2026-11-19 (8 stories, label "live", builtAt present).
// ---------------------------------------------------------------------------
@Serializable
@Entity(tableName = "live_items")
data class LiveItem(
    @SerialName("t") val title: String,
    @SerialName("s") val source: String,
    @SerialName("a") val age: String? = null,
    @PrimaryKey @SerialName("u") val url: String,
    val published: Long,
    val label: String = "live",
)

// ---------------------------------------------------------------------------
// BriefingVideo — news-briefing-manifest.json:
// {"latest","videos":[{"id","date","edition","breaking","title",
// "duration":"m:ss","file","poster"}]}.
// Verified live 2026-11-19: breaking flashes carry explicit "file" +
// "poster" (bare filenames in the news-videos bucket). Daily morning /
// evening entries carry NEITHER — the repository derives them by edition:
// morning file = "news-briefing-<date>.mp4", evening file =
// "news-briefing-<date>-evening.mp4" (posters likewise ".jpg"); both
// verified to exist live (morning 2026-11-19, evening 2026-11-18).
// "file" stays a bare bucket filename; the player builds the full URL as
// storage base + "news-videos/" + file. posterUrl is a full URL.
// ---------------------------------------------------------------------------
@Serializable
@Entity(tableName = "videos")
data class BriefingVideo(
    @PrimaryKey val id: String,
    val date: String,
    val edition: String,
    @SerialName("breaking") val isBreaking: Boolean = false,
    val title: String,
    val duration: String, // "m:ss"
    val file: String,
    @SerialName("poster") val posterUrl: String?,
)

// ---------------------------------------------------------------------------
// SavedStoryEntity — the Shelf (local Room only in v1, no login). Same
// fields as Story plus when it was saved; the cached-edition row and the
// saved row are independent (unsaving never evicts the edition cache).
// ---------------------------------------------------------------------------
@Entity(tableName = "saved_stories")
data class SavedStoryEntity(
    @PrimaryKey val link: String,
    val title: String,
    val source: String,
    val excerpt: String?,
    val published: Long,
    val section: String,
    val image: String?,
    val isOriginal: Boolean = false,
    val beat: String? = null,
    val savedAt: Long = 0L,
) {
    fun toStory(): Story = Story(
        title = title,
        source = source,
        excerpt = excerpt,
        link = link,
        published = published,
        section = section,
        image = image,
        isOriginal = isOriginal,
        beat = beat,
    )

    companion object {
        fun fromStory(story: Story, savedAt: Long = System.currentTimeMillis()): SavedStoryEntity =
            SavedStoryEntity(
                link = story.link,
                title = story.title,
                source = story.source,
                excerpt = story.excerpt,
                published = story.published,
                section = story.section,
                image = story.image,
                isOriginal = story.isOriginal,
                beat = story.beat,
                savedAt = savedAt,
            )
    }
}
