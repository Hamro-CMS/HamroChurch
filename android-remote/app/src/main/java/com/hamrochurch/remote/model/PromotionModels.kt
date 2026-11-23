package com.hamrochurch.remote.model

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MobilePromotion(
    val id: Int,
    val campaignKey: String,
    val campaignVersion: Int = 1,
    val title: String,
    val body: String? = null,
    val imageUrl: String? = null,
    val imageMime: String? = null,
    val imageBytes: ByteArray? = null,
    val ctaLabel: String? = null,
    val ctaUrl: String = "https://hamrocms.com",
    val targetPlatform: String = "all",
    val position: String = "clock_area",
    val dismissMode: String = "once_per_day",
    val delaySeconds: Int = 0,
    val rotateIntervalSeconds: Int = 8,
    val transitionStyle: String = "auto_mix",
    val sortOrder: Int = 0,
    val eventUrl: String? = null,
    val updatedAt: String? = null,
    val isStutiSong: Boolean = false
)

object PromotionSanitizer {
    const val MAX_MEDIA_BYTES = 4 * 1024 * 1024

    private val VALID_TRANSITIONS = setOf("auto_mix", "flip_y", "flip_x", "flip_diagonal", "swing_3d")
    private val VALID_TARGETS = setOf("all", "desktop", "mobile")
    private val DANGEROUS_BLOCK_REGEX = Regex(
        """<\s*(script|style|iframe|object|embed|svg|math)[^>]*>[\s\S]*?<\s*/\s*\1\s*>""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val ANY_TAG_REGEX = Regex("""<[^>]*>""")
    private val DANGEROUS_SCHEME_REGEX = Regex(
        """(?:javascript|vbscript|data\s*:\s*text/html)\s*:""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val SAFE_CAMPAIGN_KEY_CHARS = Regex("""[^a-zA-Z0-9_-]""")
    private val DATA_URI_REGEX = Regex(
        """^data:(image/(?:png|jpeg|gif|webp));base64,([A-Za-z0-9+/=\s]+)$""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun sanitizePlainText(raw: String?, maxLength: Int): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw
            .replace(DANGEROUS_BLOCK_REGEX, " ")
            .replace(ANY_TAG_REGEX, "")
            .replace(DANGEROUS_SCHEME_REGEX, "")
            .filter { ch ->
                ch == '\n' || ch == '\t' || (ch.code >= 0x20 && ch.code != 0x7F)
            }
            .replace("<", "")
            .replace(">", "")
            .trim()
        return if (cleaned.length > maxLength) cleaned.take(maxLength) else cleaned
    }

    fun sanitizeHttpsUrl(raw: String?, fallback: String? = null): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        return try {
            val uri = java.net.URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US)
            val host = uri.host?.trim().orEmpty()
            if (scheme == "https" && host.isNotEmpty() && !host.contains(' ')) {
                uri.toString()
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun detectSafeRasterImageMime(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < 12 || bytes.size > MAX_MEDIA_BYTES) return null

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }

        // JPEG: FF D8 FF
        if (
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        // GIF87a or GIF89a
        val header6 = String(bytes, 0, 6, StandardCharsets.US_ASCII)
        if (header6 == "GIF87a" || header6 == "GIF89a") {
            return "image/gif"
        }

        // WebP: RIFF....WEBP
        val riff = String(bytes, 0, 4, StandardCharsets.US_ASCII)
        val webp = String(bytes, 8, 4, StandardCharsets.US_ASCII)
        if (riff == "RIFF" && webp == "WEBP") {
            return "image/webp"
        }

        return null
    }

    private val BASE64_LUT = IntArray(128) { -1 }.apply {
        for (i in 'A'..'Z') this[i.code] = i - 'A'
        for (i in 'a'..'z') this[i.code] = i - 'a' + 26
        for (i in '0'..'9') this[i.code] = i - '0' + 52
        this['+'.code] = 62
        this['/'.code] = 63
    }

    internal fun decodeBase64Pure(input: String): ByteArray? {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.isEmpty() || clean.length % 4 != 0) return null
        val pad = when {
            clean.endsWith("==") -> 2
            clean.endsWith("=") -> 1
            else -> 0
        }
        val outLen = (clean.length / 4) * 3 - pad
        if (outLen <= 0 || outLen > MAX_MEDIA_BYTES) return null

        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = clean[i].code
            val c1 = clean[i + 1].code
            val c2 = clean[i + 2].code
            val c3 = clean[i + 3].code

            val b0 = if (c0 < 128) BASE64_LUT[c0] else -1
            val b1 = if (c1 < 128) BASE64_LUT[c1] else -1
            val b2 = if (clean[i + 2] == '=') 0 else if (c2 < 128) BASE64_LUT[c2] else -1
            val b3 = if (clean[i + 3] == '=') 0 else if (c3 < 128) BASE64_LUT[c3] else -1
            if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return null

            val triple = (b0 shl 18) or (b1 shl 12) or (b2 shl 6) or b3
            out[outIdx++] = ((triple shr 16) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = ((triple shr 8) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        return out
    }

    fun decodeSafeDataUriBytes(dataUri: String?): ByteArray? {
        val raw = dataUri?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > MAX_MEDIA_BYTES * 2) return null
        val match = DATA_URI_REGEX.matchEntire(raw) ?: return null
        val decoded = decodeBase64Pure(match.groupValues[2]) ?: return null
        return if (detectSafeRasterImageMime(decoded) != null) decoded else null
    }

    private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractYouTubeVideoId(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return try {
            val uri = java.net.URI(rawUrl.trim())
            val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
            val pathSegments = uri.path?.split("/")?.filter { it.isNotBlank() }.orEmpty()
            val candidate = when {
                host == "youtu.be" -> pathSegments.firstOrNull()
                host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com" -> {
                    val queryVid = uri.rawQuery
                        ?.split("&")
                        ?.firstOrNull { it.startsWith("v=") }
                        ?.substringAfter("v=")
                    if (!queryVid.isNullOrBlank()) {
                        queryVid
                    } else if (pathSegments.size >= 2 && pathSegments[0] in setOf("shorts", "live", "embed", "v")) {
                        pathSegments[1]
                    } else {
                        null
                    }
                }
                else -> null
            }?.trim()
            if (candidate != null && YOUTUBE_ID_REGEX.matches(candidate)) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    fun parseStutiSongItem(obj: JSONObject?): MobilePromotion? {
        if (obj == null) return null
        val videoId = obj.optString("video_id", "").trim()
        if (!YOUTUBE_ID_REGEX.matches(videoId)) return null

        val title = sanitizePlainText(
            if (obj.isNull("title")) "" else obj.optString("title", ""),
            180
        )
        if (title.isEmpty()) return null

        val defaultThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val rawHd = if (obj.isNull("thumbnail_hd_url")) null else obj.optString("thumbnail_hd_url", "")
        val rawStd = if (obj.isNull("thumbnail_url")) null else obj.optString("thumbnail_url", "")
        val imageUrl = sanitizeHttpsUrl(rawHd, null) ?: sanitizeHttpsUrl(rawStd, defaultThumb) ?: defaultThumb

        val relayedDataUri = if (obj.isNull("resolved_image_src")) null else obj.optString("resolved_image_src", "")
        val inlineBytes = decodeSafeDataUriBytes(relayedDataUri)
        val verifiedMime = if (inlineBytes != null) detectSafeRasterImageMime(inlineBytes) else "image/jpeg"

        val publishedAt = sanitizePlainText(
            if (obj.isNull("published_at")) "" else obj.optString("published_at", ""),
            40
        ).ifEmpty { null }

        return MobilePromotion(
            id = 0,
            campaignKey = "stuti-$videoId",
            campaignVersion = 1,
            title = title,
            body = null,
            imageUrl = imageUrl,
            imageMime = verifiedMime,
            imageBytes = inlineBytes,
            ctaLabel = null,
            ctaUrl = "https://www.youtube.com/watch?v=$videoId",
            targetPlatform = "all",
            position = "clock_area",
            dismissMode = "every_launch",
            delaySeconds = 0,
            rotateIntervalSeconds = 8,
            transitionStyle = "auto_mix",
            sortOrder = 0,
            eventUrl = null,
            updatedAt = publishedAt,
            isStutiSong = true
        )
    }

    fun parsePromotion(obj: JSONObject?): MobilePromotion? {
        if (obj == null) return null
        if (!obj.isNull("video_id") && obj.optString("video_id", "").isNotBlank()) {
            return parseStutiSongItem(obj)
        }
        val id = obj.optInt("id", 0)
        val rawKeyPreview = if (obj.isNull("campaign_key")) "" else obj.optString("campaign_key", "").trim()
        val isRelayedStuti = obj.optBoolean("is_stuti_song", false) || rawKeyPreview.startsWith("stuti-")
        val title = sanitizePlainText(
            if (obj.isNull("title")) "" else obj.optString("title", ""),
            180
        )
        if ((!isRelayedStuti && id <= 0) || title.isEmpty()) return null

        val rawTarget = obj.optString("target_platform", "all").trim().lowercase(Locale.US)
        val targetPlatform = if (rawTarget in VALID_TARGETS) rawTarget else "all"
        if (targetPlatform == "desktop") {
            return null
        }

        val bodyText = sanitizePlainText(
            if (obj.isNull("body")) "" else obj.optString("body", ""),
            1200
        ).ifEmpty { null }

        val ctaLabel = sanitizePlainText(
            if (obj.isNull("cta_label")) "" else obj.optString("cta_label", ""),
            80
        ).ifEmpty { null }

        val version = obj.optInt("campaign_version", 1).coerceAtLeast(1)
        val rawKey = sanitizePlainText(
            if (obj.isNull("campaign_key")) "" else obj.optString("campaign_key", ""),
            80
        ).replace(SAFE_CAMPAIGN_KEY_CHARS, "")
        val campaignKey = rawKey.ifEmpty { "promo-$id-v$version" }

        val position = if (obj.optString("position") == "bottom_left") "bottom_left" else "clock_area"
        val rawDismiss = obj.optString("dismiss_mode", "once_per_day")
        val dismissMode = if (rawDismiss == "once_per_campaign" || rawDismiss == "every_launch") {
            rawDismiss
        } else {
            "once_per_day"
        }

        val delaySeconds = obj.optInt("delay_seconds", 0).coerceIn(0, 86400)
        val rotateIntervalSeconds = obj.optInt("rotate_interval_seconds", 8).let {
            if (it < 3) 8 else it.coerceAtMost(300)
        }

        val rawTransition = obj.optString("transition_style", "auto_mix")
        val transitionStyle = if (rawTransition in VALID_TRANSITIONS) rawTransition else "auto_mix"
        val sortOrder = obj.optInt("sort_order", 0)

        val imageUrl = sanitizeHttpsUrl(
            if (obj.isNull("image_url")) null else obj.optString("image_url", ""),
            null
        )
        val ctaUrl = sanitizeHttpsUrl(
            if (obj.isNull("cta_url")) null else obj.optString("cta_url", ""),
            "https://hamrocms.com"
        ) ?: "https://hamrocms.com"
        val eventUrl = sanitizeHttpsUrl(
            if (obj.isNull("event_url")) null else obj.optString("event_url", ""),
            null
        )

        val relayedDataUri = if (obj.isNull("resolved_image_src")) null else obj.optString("resolved_image_src", "")
        val inlineBytes = decodeSafeDataUriBytes(relayedDataUri)
        val verifiedMime = if (inlineBytes != null) {
            detectSafeRasterImageMime(inlineBytes)
        } else {
            val m = obj.optString("image_mime", "").trim().lowercase(Locale.US)
            if (m in setOf("image/png", "image/jpeg", "image/gif", "image/webp")) m else null
        }

        val updatedAt = sanitizePlainText(
            if (obj.isNull("updated_at")) "" else obj.optString("updated_at", ""),
            40
        ).ifEmpty { null }

        return MobilePromotion(
            id = id,
            campaignKey = campaignKey,
            campaignVersion = version,
            title = title,
            body = bodyText,
            imageUrl = imageUrl,
            imageMime = verifiedMime,
            imageBytes = inlineBytes,
            ctaLabel = ctaLabel,
            ctaUrl = ctaUrl,
            targetPlatform = targetPlatform,
            position = position,
            dismissMode = dismissMode,
            delaySeconds = delaySeconds,
            rotateIntervalSeconds = rotateIntervalSeconds,
            transitionStyle = transitionStyle,
            sortOrder = sortOrder,
            eventUrl = eventUrl,
            updatedAt = updatedAt,
            isStutiSong = isRelayedStuti
        )
    }

    fun mergePromotionsWithStutiSongs(
        basePromos: List<MobilePromotion>,
        extraStutiSongs: List<MobilePromotion>
    ): List<MobilePromotion> {
        val adminPromos = mutableListOf<MobilePromotion>()
        val stutiSongs = mutableListOf<MobilePromotion>()
        val seenCampaignKeys = mutableSetOf<String>()
        val seenVideoIds = mutableSetOf<String>()

        for (p in basePromos) {
            if (p.isStutiSong || p.campaignKey.startsWith("stuti-")) {
                stutiSongs.add(p)
            } else if (seenCampaignKeys.add(p.campaignKey)) {
                adminPromos.add(p)
                extractYouTubeVideoId(p.ctaUrl)?.let { seenVideoIds.add(it) }
            }
        }

        val uniqueStuti = mutableListOf<MobilePromotion>()
        for (s in extraStutiSongs + stutiSongs) {
            val vid = extractYouTubeVideoId(s.ctaUrl) ?: s.campaignKey.removePrefix("stuti-")
            if (vid.isNotEmpty() && !seenVideoIds.add(vid)) continue
            if (!seenCampaignKeys.add(s.campaignKey)) continue
            uniqueStuti.add(s)
        }

        val interleaved = mutableListOf<MobilePromotion>()
        val maxLen = maxOf(adminPromos.size, uniqueStuti.size)
        for (i in 0 until maxLen) {
            if (i < uniqueStuti.size) {
                interleaved.add(uniqueStuti[i])
            }
            if (i < adminPromos.size) {
                interleaved.add(adminPromos[i])
            }
        }

        return interleaved.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
    }

    fun parsePromotionsResponse(raw: Any?): List<MobilePromotion> {
        val rawArray: JSONArray = when (raw) {
            is JSONArray -> raw
            is JSONObject -> when {
                raw.optJSONArray("promotions") != null -> raw.optJSONArray("promotions")!!
                raw.optJSONArray("mobile_promotions") != null -> raw.optJSONArray("mobile_promotions")!!
                raw.optJSONObject("promotion") != null -> JSONArray().put(raw.optJSONObject("promotion"))
                raw.has("id") && raw.has("title") -> JSONArray().put(raw)
                else -> JSONArray()
            }
            else -> JSONArray()
        }

        val seenKeys = mutableSetOf<String>()
        val list = mutableListOf<MobilePromotion>()
        for (i in 0 until rawArray.length()) {
            val parsed = parsePromotion(rawArray.optJSONObject(i)) ?: continue
            if (seenKeys.add(parsed.campaignKey)) {
                list.add(parsed)
            }
        }

        val inlineStutiSongs = mutableListOf<MobilePromotion>()
        if (raw is JSONObject) {
            val stutiArr = raw.optJSONArray("stuti_media_latest") ?: raw.optJSONArray("videos")
            if (stutiArr != null) {
                for (i in 0 until stutiArr.length()) {
                    val parsedSong = parseStutiSongItem(stutiArr.optJSONObject(i)) ?: continue
                    inlineStutiSongs.add(parsedSong)
                }
            }
        }

        return if (inlineStutiSongs.isNotEmpty() || list.any { it.isStutiSong }) {
            mergePromotionsWithStutiSongs(list, inlineStutiSongs)
        } else {
            list
        }
    }

    fun todayLocalStamp(now: Date = Date()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    }

    fun isDismissed(
        promo: MobilePromotion,
        sessionDismissedKeys: Set<String>,
        persistedMap: Map<String, String>,
        todayStamp: String = todayLocalStamp()
    ): Boolean {
        val key = promo.campaignKey
        if (key.isEmpty()) return false
        if (key in sessionDismissedKeys) return true

        val stored = persistedMap[key] ?: return false
        return when (promo.dismissMode) {
            "once_per_campaign" -> stored == "forever" || stored.isNotEmpty()
            "once_per_day" -> stored == todayStamp
            else -> false
        }
    }
}
