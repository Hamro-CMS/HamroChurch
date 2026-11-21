package com.hamrochurch.remote

import com.hamrochurch.remote.model.ChurchPcProfile
import com.hamrochurch.remote.model.RemoteParsers
import com.hamrochurch.remote.nepali.NepaliPhoneticEngine
import com.hamrochurch.remote.security.LanSecurityValidator
import com.hamrochurch.remote.security.QrPairingParser
import com.hamrochurch.remote.security.QrParseResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndProtocolTest {

    @Test
    fun lanValidator_acceptsPrivateChurchWifiAndHotspotIps() {
        assertTrue(LanSecurityValidator.isAllowedLocalHost("192.168.1.25"))
        assertTrue(LanSecurityValidator.isAllowedLocalHost("192.168.137.1")) // Windows Mobile Hotspot
        assertTrue(LanSecurityValidator.isAllowedLocalHost("10.0.0.12"))
        assertTrue(LanSecurityValidator.isAllowedLocalHost("172.16.0.1"))
        assertTrue(LanSecurityValidator.isAllowedLocalHost("172.31.255.254"))
        assertTrue(LanSecurityValidator.isAllowedLocalHost("100.64.10.5")) // Phone tethering CGNAT
        assertTrue(LanSecurityValidator.isAllowedLocalHost("hamro-church-pc.local"))
        assertTrue(LanSecurityValidator.isAllowedLocalHost("localhost"))
    }

    @Test
    fun lanValidator_blocksPublicInternetAndUrlSpoofing() {
        assertFalse(LanSecurityValidator.isAllowedLocalHost("8.8.8.8"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("1.1.1.1"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("172.15.255.255"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("172.32.0.1"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("192.169.1.1"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("100.128.0.1"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("https://evil.example.com"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("192.168.1.1@evil.com"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("192.168.1.1/api"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost("192.168.1.999"))
        assertFalse(LanSecurityValidator.isAllowedLocalHost(""))
        assertFalse(LanSecurityValidator.isAllowedLocalHost(null))
    }

    @Test
    fun lanValidator_validatesTokensAndPorts() {
        assertFalse(LanSecurityValidator.isValidToken("short-token"))
        assertFalse(LanSecurityValidator.isValidToken("123456789012345")) // 15 chars
        assertTrue(LanSecurityValidator.isValidToken("a1b2c3d4e5f6g7h8i9j0k1l2"))
        assertFalse(LanSecurityValidator.isValidToken("a1b2c3d4e5f6g7h8\u0000bad"))

        assertFalse(LanSecurityValidator.isValidPort(80))
        assertFalse(LanSecurityValidator.isValidPort(70000))
        assertTrue(LanSecurityValidator.isValidPort(5505))
        assertTrue(LanSecurityValidator.isValidPort(5506))
    }

    @Test
    fun qrParser_parsesMultiIpPayloadAndStripsPublicIps() {
        val qrJson = JSONObject().apply {
            put("app", "hamrochurch-remote")
            put("v", 1)
            put("name", "Anugrah Church Laptop")
            put("ips", JSONArray(listOf("203.0.113.55", "192.168.137.1", "192.168.1.44")))
            put("ws", 5505)
            put("rest", 5506)
            put("token", "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c")
        }.toString()

        val result = QrPairingParser.parse(qrJson)
        assertTrue(result is QrParseResult.Success)
        val payload = (result as QrParseResult.Success).payload
        assertEquals("Anugrah Church Laptop", payload.name)
        assertEquals(listOf("192.168.137.1", "192.168.1.44"), payload.ips)
        assertEquals(5505, payload.wsPort)
        assertEquals(5506, payload.restPort)

        val aradhanaQr = JSONObject().apply {
            put("app", "aradhanastudio-remote")
            put("v", 1)
            put("ips", JSONArray(listOf("192.168.1.44")))
            put("ws", 5505)
            put("rest", 5506)
            put("token", "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c")
        }.toString()
        val aradhanaRes = QrPairingParser.parse(aradhanaQr)
        assertTrue(aradhanaRes is QrParseResult.Success)
        assertEquals("Aradhana Studio PC", (aradhanaRes as QrParseResult.Success).payload.name)
    }

    @Test
    fun qrParser_rejectsPublicOnlyOrWeakTokenPayloads() {
        val publicOnlyJson = JSONObject().apply {
            put("app", "hamrochurch-remote")
            put("ips", JSONArray(listOf("8.8.8.8")))
            put("ws", 5505)
            put("token", "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c")
        }.toString()

        assertTrue(QrPairingParser.parse(publicOnlyJson) is QrParseResult.Invalid)

        val uriValid = "hcmsremote://pair?host=192.168.1.10&port=5505&token=abcdef0123456789abcdef01&name=Main+Hall"
        val parsedUri = QrPairingParser.parse(uriValid)
        assertTrue(parsedUri is QrParseResult.Success)
        assertEquals("Main Hall", (parsedUri as QrParseResult.Success).payload.name)

        val compactAradhanaUri = "aradhanaremote://pair?host=192.168.0.20&token=abcdef0123456789abcdef0123456789&alt=192.168.1.5,8.8.8.8"
        val parsedCompact = QrPairingParser.parse(compactAradhanaUri)
        assertTrue(parsedCompact is QrParseResult.Success)
        val compactPayload = (parsedCompact as QrParseResult.Success).payload
        assertEquals(listOf("192.168.0.20", "192.168.1.5"), compactPayload.ips)
        assertEquals(5505, compactPayload.wsPort)
        assertEquals(5506, compactPayload.restPort)

        val pinExchangeJson = JSONObject().apply {
            put("ok", true)
            put("app", "aradhanastudio-remote")
            put("name", "Jedon")
            put("ips", JSONArray(listOf("192.168.0.20")))
            put("ws", 5505)
            put("rest", 5506)
            put("token", "0123456789abcdef0123456789abcdef")
        }.toString()
        val parsedPinRes = QrPairingParser.parse(pinExchangeJson)
        assertTrue(parsedPinRes is QrParseResult.Success)
        assertEquals("Jedon", (parsedPinRes as QrParseResult.Success).payload.name)
    }

    @Test
    fun remoteParsers_parsesNepaliLiveStateAndHymnsCleanly() {
        val liveJson = JSONObject().apply {
            put("isLocked", false)
            put("outputActive", true)
            put("cleared", JSONObject().put("all", false).put("slide", false))
            put("mode", "show")
            put("activeShowId", "hymn_140")
            put("activeShowName", "स्तुतिको योग्य महिमाको परमेश्वर")
            put("currentSlideIndex", 0)
            put("currentGroup", "Chorus")
            put("currentSlideText", "हल्लेलूयाह होसन्ना हल्लेलूयाह")
            put("nextSlideIndex", 1)
            put("nextGroup", "Verse 1")
            put("nextSlideText", "परम पिताको स्तुति गाऔँ")
            put(
                "slides",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put("id", "s1")
                        .put("group", "Chorus")
                        .put("color", "#e6349c")
                        .put("text", "हल्लेलूयाह होसन्ना हल्लेलूयाह")
                )
            )
            put(
                "stageMessage",
                JSONObject()
                    .put("text", "५ मिनेट बाँकी")
                    .put("target", "stage")
                    .put("visible", true)
            )
        }

        val state = RemoteParsers.parseLiveState(liveJson)
        assertFalse(state.cleared.slide)
        assertEquals("स्तुतिको योग्य महिमाको परमेश्वर", state.activeShowName)
        assertEquals(1, state.slides.size)
        assertTrue(state.stageMessage.visible)
        assertEquals("५ मिनेट बाँकी", state.stageMessage.text)

        val hymnDetailJson = JSONObject().apply {
            put("id", "bhajan-1")
            put("number", "1")
            put("title", "हल्लेलूयाह होसन्ना")
            put("verses", JSONArray().put(JSONObject().put("index", 0).put("group", "chorus").put("text", "हल्लेलूयाह")))
        }
        val detail = RemoteParsers.parseHymnDetail(hymnDetailJson)
        assertNotNull(detail)
        assertEquals(1, detail!!.verses.size)

        val profile = ChurchPcProfile(
            id = "p1",
            name = "Pokhara Church PC",
            ips = listOf("192.168.1.50", "192.168.137.1"),
            preferredIp = "192.168.1.50",
            wsPort = 5505,
            restPort = 5506,
            token = "1234567890abcdef1234567890abcdef",
            lastConnectedAt = 1700000000L
        )
        val restored = ChurchPcProfile.fromJson(profile.toJson())
        assertEquals(profile, restored)
    }

    @Test
    fun nepaliPhoneticEngine_generatesAccurateHorizontalCandidatesAndLivePreview() {
        val yeshule = NepaliPhoneticEngine.getCandidatesForWord("yeshule")
        assertTrue("Expected येशूले as primary candidate, got $yeshule", yeshule.firstOrNull() == "येशूले")
        assertTrue(yeshule.contains("येशुले"))
        assertEquals("yeshule", yeshule.last())

        val stutiko = NepaliPhoneticEngine.getCandidatesForWord("stutiko")
        assertEquals("स्तुतिको", stutiko.firstOrNull())
        assertEquals("stutiko", stutiko.last())

        val token = NepaliPhoneticEngine.detectActiveToken("प्रभु yeshule", 13)
        assertNotNull(token)
        assertEquals("yeshule", token!!.roman)
        assertEquals(6, token.start)
        assertEquals(13, token.end)
        assertEquals("येशूले", token.candidates.first())

        assertEquals("येशूले भन्नुभयो", NepaliPhoneticEngine.previewDevanagariQuery("yeshule भन्नुभयो"))
        assertEquals("140", NepaliPhoneticEngine.previewDevanagariQuery("140"))
    }
}
