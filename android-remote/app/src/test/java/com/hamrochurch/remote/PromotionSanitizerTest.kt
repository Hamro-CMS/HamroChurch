package com.hamrochurch.remote

import com.hamrochurch.remote.model.PromotionSanitizer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionSanitizerTest {

    @Test
    fun stripsHtmlAndJavascriptFromPlainText() {
        val dirty = "HamroCMS <script>alert(1)</script> <b>Free</b> javascript:evil()"
        val clean = PromotionSanitizer.sanitizePlainText(dirty, 180)
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains("script"))
        assertFalse(clean.contains("javascript:"))
        assertTrue(clean.contains("HamroCMS"))
        assertTrue(clean.contains("Free"))
    }

    @Test
    fun enforcesHttpsOnlyUrls() {
        assertEquals(
            "https://hamrocms.com/register-church",
            PromotionSanitizer.sanitizeHttpsUrl("https://hamrocms.com/register-church", null)
        )
        assertNull(PromotionSanitizer.sanitizeHttpsUrl("http://hamrocms.com/insecure", null))
        assertNull(PromotionSanitizer.sanitizeHttpsUrl("javascript:alert(1)", null))
        assertNull(PromotionSanitizer.sanitizeHttpsUrl("file:///etc/passwd", null))
    }

    @Test
    fun verifiesRasterImageMagicBytesAndDecodesDataUri() {
        // Minimal valid 1x1 GIF89a (42 bytes)
        val gifB64 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
        val dataUri = "data:image/gif;base64,$gifB64"
        val decoded = PromotionSanitizer.decodeSafeDataUriBytes(dataUri)
        assertNotNull(decoded)
        assertEquals("image/gif", PromotionSanitizer.detectSafeRasterImageMime(decoded))

        // Disguised SVG inside PNG data URI is blocked
        val svgBytes = "<svg onload=\"alert(1)\"></svg>".toByteArray()
        assertNull(PromotionSanitizer.detectSafeRasterImageMime(svgBytes))
    }

    @Test
    fun filtersOutDesktopOnlyPromotionsAndKeepsAllAndMobileTargets() {
        val arr = JSONArray()
            .put(
                JSONObject()
                    .put("id", 10)
                    .put("title", "Shared Campaign")
                    .put("target_platform", "all")
                    .put("delay_seconds", 15)
                    .put("rotate_interval_seconds", 6)
            )
            .put(
                JSONObject()
                    .put("id", 11)
                    .put("title", "Desktop Only Campaign")
                    .put("target_platform", "desktop")
            )
            .put(
                JSONObject()
                    .put("id", 12)
                    .put("title", "Mobile Only Campaign")
                    .put("target_platform", "mobile")
                    .put("dismiss_mode", "once_per_campaign")
            )

        val root = JSONObject().put("promotions", arr)
        val parsed = PromotionSanitizer.parsePromotionsResponse(root)

        assertEquals(2, parsed.size)
        assertEquals("Shared Campaign", parsed[0].title)
        assertEquals(15, parsed[0].delaySeconds)
        assertEquals(6, parsed[0].rotateIntervalSeconds)
        assertEquals("Mobile Only Campaign", parsed[1].title)
    }

    @Test
    fun enforcesDismissModesCorrectly() {
        val dailyPromo = PromotionSanitizer.parsePromotion(
            JSONObject()
                .put("id", 5)
                .put("campaign_key", "promo-5-v1")
                .put("title", "Daily Ad")
                .put("dismiss_mode", "once_per_day")
        )!!

        assertTrue(
            PromotionSanitizer.isDismissed(
                promo = dailyPromo,
                sessionDismissedKeys = emptySet(),
                persistedMap = mapOf("promo-5-v1" to "2026-11-20"),
                todayStamp = "2026-11-20"
            )
        )
        assertFalse(
            PromotionSanitizer.isDismissed(
                promo = dailyPromo,
                sessionDismissedKeys = emptySet(),
                persistedMap = mapOf("promo-5-v1" to "2026-11-19"),
                todayStamp = "2026-11-20"
            )
        )
    }
}
