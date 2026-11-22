package com.hamrochurch.remote.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hamrochurch.remote.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class MobileUpdatePhase {
    IDLE,
    CHECKING,
    DOWNLOADING,
    READY_TO_INSTALL
}

data class MobileAppUpdateState(
    val phase: MobileUpdatePhase = MobileUpdatePhase.IDLE,
    val latestVersion: String = "",
    val progressPercent: Int = 0,
    val apkFilePath: String? = null,
    val autoPromptedVersion: String = ""
)

data class RemoteReleaseMetadata(
    val version: String,
    val apkDownloadUrl: String
)

class MobileAppUpdater(context: Context) {
    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val userAgent = "AradhanaStudioRemote/${BuildConfig.VERSION_NAME} (Android)"

    suspend fun checkLatestRelease(pcHintVersion: String? = null): RemoteReleaseMetadata? =
        withContext(Dispatchers.IO) {
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)

            // 1. Primary: GitHub Releases API (inspects published APK assets directly)
            val apiRelease = fetchFromGitHubApi()
            if (apiRelease != null && compareSemver(apiRelease.version, currentVersion) > 0) {
                return@withContext apiRelease
            }

            // 2. Rate-limit-free fallback: GitHub /releases/latest 302 Location redirect
            val redirectRelease = fetchFromLatestRedirect()
            if (redirectRelease != null && compareSemver(redirectRelease.version, currentVersion) > 0) {
                return@withContext redirectRelease
            }

            // 3. Connected Church PC version hint fallback
            val normalizedHint = normalizeVersion(pcHintVersion.orEmpty())
            if (normalizedHint.isNotEmpty() && compareSemver(normalizedHint, currentVersion) > 0) {
                return@withContext RemoteReleaseMetadata(
                    version = normalizedHint,
                    apkDownloadUrl = buildStandardApkUrl(normalizedHint)
                )
            }

            null
        }

    suspend fun downloadUpdateApk(
        release: RemoteReleaseMetadata,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val finalApk = File(updatesDir, "AradhanaStudio-Remote-v${release.version}.apk")

        // Reuse already downloaded & verified APK if present
        if (isValidOwnApk(finalApk)) {
            cleanupOtherApks(updatesDir, finalApk.name)
            onProgress(100)
            return@withContext finalApk
        }

        cleanupOtherApks(updatesDir, finalApk.name)
        val partFile = File(updatesDir, "${finalApk.name}.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        try {
            val req = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                .header("User-Agent", userAgent)
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastReportedPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (!isValidOwnApk(partFile)) {
                partFile.delete()
                return@withContext null
            }

            if (finalApk.exists()) {
                finalApk.delete()
            }
            if (!partFile.renameTo(finalApk)) {
                partFile.copyTo(finalApk, overwrite = true)
                partFile.delete()
            }

            onProgress(100)
            finalApk
        } catch (_: Exception) {
            partFile.delete()
            null
        }
    }

    private fun isValidOwnApk(file: File): Boolean {
        if (!file.exists() || file.length() < 100_000L) return false
        return try {
            // Verify ZIP/APK PK\x03\x04 magic header
            val header = ByteArray(4)
            val readCount = file.inputStream().use { it.read(header) }
            if (readCount < 4 ||
                header[0] != 0x50.toByte() ||
                header[1] != 0x4B.toByte() ||
                header[2] != 0x03.toByte() ||
                header[3] != 0x04.toByte()
            ) {
                return false
            }

            @Suppress("DEPRECATION")
            val archiveInfo = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            archiveInfo?.packageName == appContext.packageName
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanupOtherApks(dir: File, keepName: String) {
        dir.listFiles()?.forEach { f ->
            if (f.name != keepName) {
                runCatching { f.delete() }
            }
        }
    }

    private fun fetchFromGitHubApi(): RemoteReleaseMetadata? {
        return try {
            val req = Request.Builder()
                .url(GITHUB_LATEST_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", userAgent)
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                    return null
                }
                val version = normalizeVersion(json.optString("tag_name", ""))
                if (version.isEmpty()) return null

                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) &&
                            downloadUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                if (apkUrl.isEmpty()) {
                    apkUrl = buildStandardApkUrl(version)
                }

                RemoteReleaseMetadata(version = version, apkDownloadUrl = apkUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromLatestRedirect(): RemoteReleaseMetadata? {
        return try {
            val req = Request.Builder()
                .url(GITHUB_LATEST_HTML_URL)
                .header("User-Agent", userAgent)
                .head()
                .build()

            noRedirectClient.newCall(req).execute().use { resp ->
                val location = resp.header("Location").orEmpty()
                val tag = location.substringAfterLast("/tag/", "").substringBefore("?").trim()
                val version = normalizeVersion(tag)
                if (version.isEmpty()) return null
                RemoteReleaseMetadata(
                    version = version,
                    apkDownloadUrl = buildStandardApkUrl(version)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val GITHUB_REPO = "Hamro-CMS/HamroChurch"
        private const val GITHUB_LATEST_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val GITHUB_LATEST_HTML_URL = "https://github.com/$GITHUB_REPO/releases/latest"

        fun buildStandardApkUrl(version: String): String =
            "https://github.com/$GITHUB_REPO/releases/download/v$version/AradhanaStudio-Remote-v$version.apk"

        fun normalizeVersion(raw: String): String =
            raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
                .trim()

        fun compareSemver(aRaw: String, bRaw: String): Int {
            val aParts = normalizeVersion(aRaw).split(".").map { it.toIntOrNull() ?: 0 }
            val bParts = normalizeVersion(bRaw).split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(aParts.size, bParts.size)
            for (i in 0 until maxLen) {
                val a = aParts.getOrElse(i) { 0 }
                val b = bParts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        fun canInstallPackages(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
        }

        fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
        }

        fun launchPackageInstaller(activity: Activity, apkPath: String): Boolean {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return false
            return try {
                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                activity.startActivity(installIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
