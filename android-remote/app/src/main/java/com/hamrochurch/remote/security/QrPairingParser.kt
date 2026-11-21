package com.hamrochurch.remote.security

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class ParsedPairingPayload(
    val name: String,
    val ips: List<String>,
    val wsPort: Int,
    val restPort: Int,
    val token: String
)

sealed class QrParseResult {
    data class Success(val payload: ParsedPairingPayload) : QrParseResult()
    data class Invalid(val reason: String) : QrParseResult()
}

object QrPairingParser {

    fun parse(rawContent: String?): QrParseResult {
        val text = rawContent?.trim() ?: return QrParseResult.Invalid("Empty QR code")
        if (text.isEmpty() || text.length > 4096) {
            return QrParseResult.Invalid("Invalid QR code length")
        }

        return when {
            text.startsWith("{") -> parseJsonPayload(text)
            text.startsWith("aradhanaremote://", ignoreCase = true) ||
                text.startsWith("hcmsremote://", ignoreCase = true) -> parseUriPayload(text)
            else -> QrParseResult.Invalid("Not an Aradhana Studio pairing QR code")
        }
    }

    private fun parseJsonPayload(jsonText: String): QrParseResult {
        val obj = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return QrParseResult.Invalid("Malformed QR JSON")
        }

        val appTag = obj.optString("app", "")
        val validTags = setOf("aradhanastudio-remote", "aradhanastudio", "hamrochurch-remote", "hamrochurch")
        if (appTag !in validTags) {
            return QrParseResult.Invalid("Unrecognized QR application tag")
        }

        val name = obj.optString("name", "Aradhana Studio PC")
            .replace(Regex("[\\u0000-\\u001F<>]"), "")
            .trim()
            .ifEmpty { "Aradhana Studio PC" }
            .take(64)

        val wsPort = obj.optInt("ws", obj.optInt("wsPort", 5505))
        val restPort = obj.optInt("rest", obj.optInt("restPort", wsPort + 1))
        if (!LanSecurityValidator.isValidPort(wsPort) || !LanSecurityValidator.isValidPort(restPort)) {
            return QrParseResult.Invalid("Invalid port in QR code")
        }

        val token = obj.optString("token", "").trim()
        if (!LanSecurityValidator.isValidToken(token)) {
            return QrParseResult.Invalid("Missing or weak security token in QR code")
        }

        val candidateIps = mutableListOf<String>()
        val ipsArray = obj.optJSONArray("ips")
        if (ipsArray != null) {
            for (i in 0 until ipsArray.length()) {
                val ip = ipsArray.optString(i, "").trim()
                if (LanSecurityValidator.isAllowedLocalHost(ip) && ip !in candidateIps) {
                    candidateIps.add(ip)
                }
            }
        }
        val singleHost = obj.optString("ip", obj.optString("host", "")).trim()
        if (LanSecurityValidator.isAllowedLocalHost(singleHost) && singleHost !in candidateIps) {
            candidateIps.add(singleHost)
        }

        if (candidateIps.isEmpty()) {
            return QrParseResult.Invalid("QR code contains no valid private LAN IP addresses")
        }

        return QrParseResult.Success(
            ParsedPairingPayload(
                name = name,
                ips = candidateIps,
                wsPort = wsPort,
                restPort = restPort,
                token = token
            )
        )
    }

    private fun parseUriPayload(uriText: String): QrParseResult {
        val uri = try {
            URI(uriText)
        } catch (_: Exception) {
            return QrParseResult.Invalid("Malformed pairing URI")
        }

        val params = mutableMapOf<String, String>()
        uri.rawQuery?.split("&")?.forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                params[key] = value
            }
        }

        val host = params["host"]?.trim() ?: ""
        if (!LanSecurityValidator.isAllowedLocalHost(host)) {
            return QrParseResult.Invalid("Host is not a valid local LAN address")
        }

        val wsPort = params["port"]?.toIntOrNull() ?: 5505
        val restPort = params["restPort"]?.toIntOrNull() ?: (wsPort + 1)
        if (!LanSecurityValidator.isValidPort(wsPort) || !LanSecurityValidator.isValidPort(restPort)) {
            return QrParseResult.Invalid("Invalid port number")
        }

        val token = params["token"]?.trim() ?: ""
        if (!LanSecurityValidator.isValidToken(token)) {
            return QrParseResult.Invalid("Invalid security token")
        }

        val candidateIps = mutableListOf(host)
        params["alt"]?.split(",")?.forEach { extra ->
            val clean = extra.trim()
            if (LanSecurityValidator.isAllowedLocalHost(clean) && clean !in candidateIps) {
                candidateIps.add(clean)
            }
        }

        val name = (params["name"] ?: "Aradhana Studio PC").trim().ifEmpty { "Aradhana Studio PC" }.take(64)

        return QrParseResult.Success(
            ParsedPairingPayload(
                name = name,
                ips = candidateIps,
                wsPort = wsPort,
                restPort = restPort,
                token = token
            )
        )
    }
}
