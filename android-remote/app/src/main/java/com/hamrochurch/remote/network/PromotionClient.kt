package com.hamrochurch.remote.network

import com.hamrochurch.remote.BuildConfig
import com.hamrochurch.remote.model.MobilePromotion
import com.hamrochurch.remote.model.PromotionSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/**
 * Strictly isolated, read-only public HTTPS promotion client for Hamro Church Remote.
 *
 * Security invariants:
 * - Connects ONLY to https:// URLs (never cleartext http://).
 * - NEVER accesses or transmits ChurchPcProfile, LAN IP addresses, or bearer tokens.
 * - Verifies raster binary magic bytes (PNG/JPEG/GIF/WebP) before exposing image bytes to UI.
 */
class PromotionClient(
    private val endpointUrl: String = DEFAULT_MOBILE_PROMOTION_URL
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(5000, TimeUnit.MILLISECONDS)
        .build()

    private val imageCache: MutableMap<String, ByteArray> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
                return size > 12
            }
        }
    )

    private val userAgent: String = "HamroChurchRemote/${BuildConfig.VERSION_NAME} (Android)"

    suspend fun fetchActivePromotions(): Result<List<MobilePromotion>> = withContext(Dispatchers.IO) {
        val safeEndpoint = PromotionSanitizer.sanitizeHttpsUrl(endpointUrl, null)
            ?: return@withContext Result.failure(SecurityException("Insecure promotion endpoint blocked"))

        try {
            val request = Request.Builder()
                .url(safeEndpoint)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", userAgent)
                .get()
                .build()

            val bodyText = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                response.body?.string().orEmpty()
            }

            if (bodyText.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            val parsedList = PromotionSanitizer.parsePromotionsResponse(JSONObject(bodyText))
            val latestFeedSongs = fetchLatestStutiSongs()
            val combined = PromotionSanitizer.mergePromotionsWithStutiSongs(parsedList, latestFeedSongs)
            val enriched = attachVerifiedImages(combined)
            Result.success(enriched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchLatestStutiSongs(): List<MobilePromotion> {
        val safeFeedUrl = PromotionSanitizer.sanitizeHttpsUrl(DEFAULT_STUTI_MEDIA_URL, null) ?: return emptyList()
        return try {
            val req = Request.Builder()
                .url(safeFeedUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", userAgent)
                .get()
                .build()

            val bodyText = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string().orEmpty()
            }
            if (bodyText.isBlank()) return emptyList()

            val json = JSONObject(bodyText)
            val videosArr = json.optJSONArray("videos") ?: return emptyList()
            val out = mutableListOf<MobilePromotion>()
            val limit = minOf(5, videosArr.length())
            for (i in 0 until limit) {
                val parsed = PromotionSanitizer.parseStutiSongItem(videosArr.optJSONObject(i)) ?: continue
                out.add(parsed)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun enrichRelayedPromotions(promos: List<MobilePromotion>): List<MobilePromotion> =
        withContext(Dispatchers.IO) {
            attachVerifiedImages(promos)
        }

    private suspend fun attachVerifiedImages(promos: List<MobilePromotion>): List<MobilePromotion> =
        coroutineScope {
            promos.map { promo ->
                async {
                    if (promo.imageBytes != null) {
                        promo
                    } else if (!promo.imageUrl.isNullOrBlank()) {
                        val bytes = downloadVerifiedRasterImage(promo.imageUrl)
                        if (bytes != null) {
                            promo.copy(
                                imageBytes = bytes,
                                imageMime = PromotionSanitizer.detectSafeRasterImageMime(bytes) ?: promo.imageMime
                            )
                        } else {
                            promo
                        }
                    } else {
                        promo
                    }
                }
            }.awaitAll()
        }

    private fun downloadVerifiedRasterImage(rawUrl: String): ByteArray? {
        val safeUrl = PromotionSanitizer.sanitizeHttpsUrl(rawUrl, null) ?: return null
        imageCache[safeUrl]?.let { return it }

        return try {
            val req = Request.Builder()
                .url(safeUrl)
                .header("Accept", "image/gif,image/webp,image/png,image/jpeg")
                .header("User-Agent", userAgent)
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                if (body.contentLength() > PromotionSanitizer.MAX_MEDIA_BYTES) return null

                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > PromotionSanitizer.MAX_MEDIA_BYTES) return null
                if (PromotionSanitizer.detectSafeRasterImageMime(bytes) == null) return null

                imageCache[safeUrl] = bytes
                bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun recordEvent(
        promoId: Int,
        event: String,
        explicitEventUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (promoId <= 0 || (event != "impression" && event != "click")) {
            return@withContext false
        }

        val fallbackUrl = "$DEFAULT_PROMOTION_BASE/promotions/$promoId/event"
        val targetUrl = PromotionSanitizer.sanitizeHttpsUrl(explicitEventUrl, fallbackUrl) ?: fallbackUrl

        try {
            val payload = JSONObject().put("event", event).toString()
            val req = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val DEFAULT_PROMOTION_BASE = "https://hamrocms.com/api/v1/software"
        const val DEFAULT_MOBILE_PROMOTION_URL = "$DEFAULT_PROMOTION_BASE/promotion?target=mobile"
        const val DEFAULT_STUTI_MEDIA_URL = "$DEFAULT_PROMOTION_BASE/stuti-media?refresh=1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
