package com.makrydensystems.news.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.makrydensystems.news.BuildConfig
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.StarBrookApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

// In-app updater (2026-11-23): the app is sideloaded — no Play Store —
// so the app checks for its own updates. One small public JSON fetch
// (news-videos/app-update.json, same bucket as the video manifest);
// when the manifest's versionCode is newer than this build's, the app
// shows an Update prompt with a real Update button. Tapping it
// downloads the APK in-app and opens the system installer.
//
// HONEST LIMIT (say this in every user-facing word about updates):
// Android does not let a sideloaded app replace itself silently — the
// user always taps Install once on the system installer screen. The
// check, the prompt, and the download are automatic; the last tap is
// the user's, and the copy below never promises otherwise.
//
// SCOPE: app builds ONLY. This reads one version manifest and compares
// one versionCode — content updates (editions, flashes) never trigger
// it, and a content fetch never touches this state.
private const val UPDATE_MANIFEST_URL_SUFFIX = "news-videos/app-update.json"
private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000 // once a day
private const val APK_FILE_NAME = "starbrook-news-update.apk"

/**
 * The server-side update manifest (public JSON in the news-videos
 * bucket — see APP_UPDATE_RELEASE.md for the shape + release step).
 * Defaults keep a half-written manifest from killing the decode.
 * [sha256] is the lowercase hex SHA-256 of the exact APK bytes the
 * release step uploaded (R-23): the downloader verifies the file
 * against it before the installer opens, and fails closed on a
 * mismatch. Empty = no hash published (old manifests) — the download
 * is then checked by length only, as before.
 */
@Serializable
data class AppUpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val sha256: String = "",
)

sealed interface AppUpdateState {
    data object None : AppUpdateState
    data class Available(val manifest: AppUpdateManifest) : AppUpdateState
    data class Downloading(val progress: Float) : AppUpdateState
    data class Downloaded(val manifest: AppUpdateManifest, val file: File) : AppUpdateState
    data object NeedsPermission : AppUpdateState
    data object Failed : AppUpdateState
}

/**
 * The outcome of a manual "Check for update" (Settings > App updates).
 * A return value, not an AppUpdateState: the automatic loop and the
 * dialog only ever deal in AppUpdateState, and "you're up to date" /
 * "couldn't check" are transient answers to the user who just asked —
 * they must not become a persistent prompt state. Only Available
 * touches the shared state (it sets AppUpdateState.Available, so the
 * existing dialog appears); UpToDate and Failed leave it alone.
 */
sealed interface ManualCheckResult {
    data object Available : ManualCheckResult
    data object UpToDate : ManualCheckResult
    data object Failed : ManualCheckResult
}

class AppUpdateChecker(
    private val client: OkHttpClient,
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    // The manifest behind the current prompt (kept so a retry after the
    // permission screen, or after a failed download, does not re-fetch).
    private var currentManifest: AppUpdateManifest? = null

    /**
     * Launch check + daily re-check while the app stays open. Call once
     * from MainActivity (LaunchedEffect) — the loop cancels itself when
     * the composition leaves. A failed fetch is silent: no error
     * surface, and the last-check stamp is left alone so the next
     * launch retries.
     */
    suspend fun monitorUpdates() {
        while (currentCoroutineContext().isActive) {
            checkOnce()
            delay(CHECK_INTERVAL_MS)
        }
    }

    /** One due-gated check: at most once every 24h per install. */
    suspend fun checkOnce() {
        val lastCheck = settings.lastAppUpdateCheckMs()
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        val manifest = try {
            fetchManifest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // silent: no error surface for a failed check
        }
        settings.setLastAppUpdateCheckMs(System.currentTimeMillis())
        val dismissed = settings.dismissedAppUpdateVersionCode()
        if (manifest.versionCode > BuildConfig.VERSION_CODE &&
            manifest.apkUrl.isNotBlank() &&
            manifest.versionCode != dismissed
        ) {
            currentManifest = manifest
            _state.value = AppUpdateState.Available(manifest)
        }
    }

    /**
     * Manual "Check for update" (Settings > App updates). Fetches the
     * manifest regardless of the 24h last-check gate and reports the
     * outcome to the caller:
     * - Available: a newer build with a download URL exists. Sets
     *   AppUpdateState.Available so the existing Update dialog appears.
     *   The dismissed-version suppression is BYPASSED — the user asked,
     *   so a version they tapped "Later" on is offered again.
     * - UpToDate: this build is current (no newer build in the
     *   manifest). The shared state is untouched.
     * - Failed: the manifest could not be fetched, or it names a newer
     *   build with no download URL. The shared state is untouched, and
     *   the last-check stamp is left alone so the next launch retries.
     * A successful check stamps the last-check time, so the automatic
     * loop does not immediately re-check. Never stomps an in-flight
     * download or a downloaded update.
     */
    suspend fun checkNow(): ManualCheckResult {
        val manifest = try {
            fetchManifest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ManualCheckResult.Failed
        }
        settings.setLastAppUpdateCheckMs(System.currentTimeMillis())
        val newer = manifest.versionCode > BuildConfig.VERSION_CODE
        if (newer && manifest.apkUrl.isBlank()) return ManualCheckResult.Failed
        if (!newer) return ManualCheckResult.UpToDate
        if (_state.value !is AppUpdateState.Downloading &&
            _state.value !is AppUpdateState.Downloaded
        ) {
            currentManifest = manifest
            _state.value = AppUpdateState.Available(manifest)
        }
        return ManualCheckResult.Available
    }

    /**
     * The Update button: download the APK in-app, then open the system
     * installer. If the install-unknown-apps permission is not granted
     * yet, the state goes to NeedsPermission instead — the dialog then
     * offers the settings screen, and tapping Update again retries.
     * A downloaded file is reused: tapping Update/Install again opens
     * the installer without re-downloading.
     */
    suspend fun startUpdate() {
        val manifest = currentManifest ?: return
        if (_state.value is AppUpdateState.Downloaded) {
            installDownloaded()
            return
        }
        if (!canInstall()) {
            _state.value = AppUpdateState.NeedsPermission
            return
        }
        _state.value = AppUpdateState.Downloading(0f)
        try {
            val file = withContext(Dispatchers.IO) { downloadApk(manifest) }
            _state.value = AppUpdateState.Downloaded(manifest, file)
            openInstaller(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = AppUpdateState.Failed
        }
    }

    /** The Install button on a downloaded update — no re-download. */
    fun installDownloaded() {
        val downloaded = _state.value as? AppUpdateState.Downloaded ?: return
        openInstaller(downloaded.file)
    }

    /** "Later" on a normal (non-mandatory) build: hide, and never
     * re-prompt for THIS version. A newer version still prompts. */
    suspend fun dismissUpdate() {
        currentManifest?.let { settings.setDismissedAppUpdateVersionCode(it.versionCode) }
        _state.value = AppUpdateState.None
    }

    /** Hide the prompt for this session only (a failed download's
     * "Later", a dismissed dialog): the next check re-prompts. */
    fun hidePrompt() {
        _state.value = AppUpdateState.None
    }

    /** The "Open settings" button: Android's per-app unknown-sources
     * screen for this app, with plain wording in the dialog. */
    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun canInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    private fun openInstaller(file: File) {
        if (!canInstall()) {
            _state.value = AppUpdateState.NeedsPermission
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun fetchManifest(): AppUpdateManifest {
        val request = Request.Builder()
            .url(StarBrookApi.STORAGE_BASE + UPDATE_MANIFEST_URL_SUFFIX)
            .header("User-Agent", "StarBrookNews/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val text = response.body?.string() ?: throw IOException("Empty response")
            return json.decodeFromString<AppUpdateManifest>(text)
        }
    }

    /** Streams the APK to app-private cache storage with progress.
     * A truncated file fails here — a half-downloaded APK would fail
     * the installer with a useless error instead. R-23: when the
     * manifest publishes a SHA-256, the downloaded bytes are verified
     * against it before the file is returned — a mismatch deletes the
     * file and fails closed (the caller reports Failed, never Install).
     * The debug signing key is public by design, so this hash is the
     * only check that the bytes are the ones the release step shipped.
     */
    private fun downloadApk(manifest: AppUpdateManifest): File {
        val request = Request.Builder()
            .url(manifest.apkUrl)
            .header("User-Agent", "StarBrookNews/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            val file = File(context.cacheDir, APK_FILE_NAME)
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _state.value =
                                AppUpdateState.Downloading(downloaded.toFloat() / total)
                        }
                    }
                    output.flush()
                }
            }
            if (total > 0 && file.length() != total) throw IOException("Truncated download")
            if (!verifyApkSha256(file, manifest.sha256)) {
                file.delete()
                throw IOException("Downloaded APK failed the SHA-256 check")
            }
            return file
        }
    }
}

/**
 * R-23 (2026-11-25): SHA-256 helpers for the updater. The file is
 * streamed, never read whole into memory. Pure (no Android) except
 * for the File read itself, so unit tests cover the byte-level logic
 * via [sha256Hex] / [sha256Matches] directly.
 */

/** Lowercase hex SHA-256 of [bytes]. */
internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/**
 * Whether [bytes] match the manifest's published hash. Empty means
 * no hash was published (old manifests) — nothing to check. A
 * malformed value never matches: fail closed.
 */
internal fun sha256Matches(bytes: ByteArray, expectedHex: String): Boolean {
    val expected = expectedHex.trim().lowercase()
    if (expected.isEmpty()) return true
    if (!expected.matches(Regex("[0-9a-f]{64}"))) return false
    return sha256Hex(bytes) == expected
}

/** Streaming SHA-256 check of a downloaded APK file against the manifest hash. */
internal fun verifyApkSha256(file: File, expectedHex: String): Boolean {
    val expected = expectedHex.trim().lowercase()
    if (expected.isEmpty()) return true
    if (!expected.matches(Regex("[0-9a-f]{64}"))) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return actual == expected
}
