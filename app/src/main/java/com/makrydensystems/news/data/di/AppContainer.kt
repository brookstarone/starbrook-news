package com.makrydensystems.news.data.di

import android.content.Context
import androidx.room.Room
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.NewsRepositoryImpl
import com.makrydensystems.news.data.PortalAuth
import com.makrydensystems.news.data.PortalRepository
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.StarBrookApi
import com.makrydensystems.news.data.WeatherRepository
import com.makrydensystems.news.data.WeatherRepositoryImpl
import com.makrydensystems.news.data.local.AppDatabase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual DI — no Hilt. One shared OkHttpClient, one Room database, one
 * SettingsStore; both repositories are built from the shared client + db.
 * The client adds the generic app User-Agent only when a request does not
 * already carry one (NWS calls set their own contact UA per-request).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val withUa = if (request.header("User-Agent") == null) {
                request.newBuilder()
                    .header("User-Agent", "StarBrookNews/1.0 (Android)")
                    .build()
            } else {
                request
            }
            chain.proceed(withUa)
        }
        .build()

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "starbrook-news.db",
    )
        // v1: the cache is refetchable public data — destructive migration
        // instead of hand-written migrations.
        .fallbackToDestructiveMigration()
        .build()

    val settingsStore: SettingsStore = SettingsStore(appContext)

    val portalAuth: PortalAuth = PortalAuth(okHttpClient, settingsStore)

    val portalRepository: PortalRepository =
        PortalRepository(okHttpClient, portalAuth, settingsStore, database)

    // Merge hookups (Wave 2): the repositories take the SettingsStore so
    // the notification toggles gate real in-app polling (breaking 5m,
    // editions 15m, weather alerts 10m) instead of the PushModule
    // in-memory fallback; and the Supabase anon key (BuildConfig from
    // env — blank in a build without the CI secret) so the Originals
    // PostgREST fetch + short-code resolution work. Blank key -> null,
    // so Originals stay hidden rather than failing.
    val newsRepository: NewsRepository = NewsRepositoryImpl(
        okHttpClient,
        database,
        settingsStore,
        StarBrookApi.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() },
    )

    val weatherRepository: WeatherRepository =
        WeatherRepositoryImpl(okHttpClient, settingsStore)
}
