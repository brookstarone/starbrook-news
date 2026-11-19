package com.makrydensystems.news.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.data.model.SavedStoryEntity
import com.makrydensystems.news.data.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * Room database "starbrook-news.db" — the offline cache behind the INSTANT
 * STANDARD (render cached data immediately, refresh quietly behind it).
 * Everything cached here is refetchable public data, so v1 uses
 * fallbackToDestructiveMigration (set in AppContainer) instead of
 * hand-written migrations.
 */
@Database(
    entities = [
        Story::class, // cached_stories (one row per story per edition)
        Breaking::class, // breaking (0–1 rows)
        LiveItem::class, // live_items
        BriefingVideo::class, // videos
        SavedStoryEntity::class, // saved_stories (Shelf, local only in v1)
    ],
    // v2 (Wave 2): Story gains the outletCount column (edition JSON "n").
    // Destructive migration wipes the refetchable cache on upgrade.
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedStoryDao(): CachedStoryDao
    abstract fun breakingDao(): BreakingDao
    abstract fun liveItemDao(): LiveItemDao
    abstract fun videoDao(): VideoDao
    abstract fun savedStoryDao(): SavedStoryDao
}

@Dao
interface CachedStoryDao {
    @Query("SELECT * FROM cached_stories WHERE edition = :edition ORDER BY published DESC")
    suspend fun storiesForEdition(edition: String): List<Story>

    /**
     * Search across the cached editions. [pattern] must already be wrapped
     * in %...% with %, _ and \ escaped (the repository does that); LIKE
     * against a NULL excerpt simply does not match.
     */
    @Query(
        "SELECT * FROM cached_stories WHERE " +
            "title LIKE :pattern ESCAPE '\\' OR " +
            "excerpt LIKE :pattern ESCAPE '\\' OR " +
            "source LIKE :pattern ESCAPE '\\' " +
            "ORDER BY published DESC",
    )
    fun searchStories(pattern: String): Flow<List<Story>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stories: List<Story>)

    @Query("DELETE FROM cached_stories WHERE edition = :edition")
    suspend fun clearEdition(edition: String)

    @Query("DELETE FROM cached_stories")
    suspend fun clearAll()
}

@Dao
interface BreakingDao {
    @Query("SELECT * FROM breaking LIMIT 1")
    suspend fun getBreaking(): Breaking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(breaking: Breaking)

    @Query("DELETE FROM breaking")
    suspend fun clear()
}

@Dao
interface LiveItemDao {
    @Query("SELECT * FROM live_items ORDER BY published DESC")
    suspend fun getAll(): List<LiveItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LiveItem>)

    @Query("DELETE FROM live_items")
    suspend fun clearAll()
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY date DESC")
    suspend fun getAll(): List<BriefingVideo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<BriefingVideo>)

    @Query("DELETE FROM videos")
    suspend fun clearAll()
}

@Dao
interface SavedStoryDao {
    @Query("SELECT * FROM saved_stories ORDER BY savedAt DESC")
    fun savedStories(): Flow<List<SavedStoryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_stories WHERE link = :link)")
    fun isSaved(link: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(story: SavedStoryEntity)

    @Query("DELETE FROM saved_stories WHERE link = :link")
    suspend fun deleteByLink(link: String)
}
