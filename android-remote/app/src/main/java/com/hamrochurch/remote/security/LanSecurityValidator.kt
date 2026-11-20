package com.hamrochurch.remote.security

object LanSecurityValidator {
    const val MIN_TOKEN_LENGTH = 16
    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val MDNS_HOST_REGEX = Regex("""^[a-zA-Z0-9][a-zA-Z0-9\-]{0,62}\.local$""")

    /**
     * Strictly permits only private/local LAN IPv4 addresses or .local mDNS hostnames
     * so that church security tokens can never be exfiltrated to public internet servers.
     */
    fun isAllowedLocalHost(rawHost: String?): Boolean {
        val host = rawHost?.trim()?.lowercase() ?: return false
        if (host.isEmpty() || host.length > 253) return false
        if (host.contains('/') || host.contains('@') || host.contains(':') || host.contains(' ')) {
            return false
        }

        if (host == "localhost" || MDNS_HOST_REGEX.matches(host)) {
            return true
        }

        val match = IPV4_REGEX.matchEntire(host) ?: return false
        val octets = match.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val a = octets[0]
        val b = octets[1]

        return when {
            // 10.0.0.0/8 (Private LAN + Android Emulator 10.0.2.2)
            a == 10 -> true
            // 172.16.0.0/12 (Private LAN 172.16.x.x - 172.31.x.x)
            a == 172 && b in 16..31 -> true
            // 192.168.0.0/16 (Home/Church Wi-Fi & Windows Mobile Hotspot 192.168.137.x)
            a == 192 && b == 168 -> true
            // 100.64.0.0/10 (Phone Hotspot / CGNAT local tethering)
            a == 100 && b in 64..127 -> true
            // 127.0.0.0/8 (Loopback)
            a == 127 -> true
            else -> false
        }
    }

    fun isValidPort(port: Int): Boolean = port in 1025..65535

    fun isValidToken(token: String?): Boolean {
        val trimmed = token?.trim() ?: return false
        return trimmed.length in MIN_TOKEN_LENGTH..256 && trimmed.none { it.isISOControl() }
    }
}
