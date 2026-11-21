package com.hamrochurch.remote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.hamrochurch.remote.model.ChurchPcProfile
import com.hamrochurch.remote.model.ConnectionStatus
import com.hamrochurch.remote.model.DiscoveredChurchPc
import com.hamrochurch.remote.model.LiveState
import com.hamrochurch.remote.model.RemoteParsers
import com.hamrochurch.remote.security.LanSecurityValidator
import com.hamrochurch.remote.security.ParsedPairingPayload
import com.hamrochurch.remote.security.QrPairingParser
import com.hamrochurch.remote.security.QrParseResult
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class HamroRemoteClient(
    private val context: Context? = null,
    private val onLivePush: (LiveState) -> Unit,
    private val onPromotionsPush: (JSONArray) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(1400, TimeUnit.MILLISECONDS)
        .readTimeout(2200, TimeUnit.MILLISECONDS)
        .writeTimeout(1800, TimeUnit.MILLISECONDS)
        .build()

    private val discoveryHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(700, TimeUnit.MILLISECONDS)
        .build()

    private val restHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    private val wsBaseHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(4000, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var socket: Socket? = null
    private var activeProfile: ChurchPcProfile? = null
    private var activeIp: String = ""
    private var activeRestPort: Int = 5506

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _connectedHost = MutableStateFlow("")
    val connectedHost: StateFlow<String> = _connectedHost.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val deviceDisplayName: String by lazy {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val model = Build.MODEL ?: "Phone"
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    private val headerSafeDeviceName: String by lazy {
        deviceDisplayName
            .replace(Regex("[^\\x20-\\x7E]"), "")
            .trim()
            .ifEmpty { "Android Remote" }
            .take(64)
    }

    /**
     * Finds the exact Android Wi-Fi / Ethernet [Network] matching [targetIp] so that phones with
     * both Mobile Data (4G/5G) and Wi-Fi active (e.g., Xiaomi WLAN Assistant / Samsung Wi-Fi Assist
     * or offline church routers without internet) route LAN packets over Wi-Fi instead of cellular.
     */
    private fun resolveLanSocketFactory(targetIp: String): SocketFactory? {
        val ctx = context ?: return null
        if (targetIp.startsWith("127.") || targetIp == "localhost") return null
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val targetInt = ipv4ToInt(targetIp)

        var fallbackWifiNetwork: Network? = null

        @Suppress("DEPRECATION")
        val networks = runCatching { cm.allNetworks }.getOrNull() ?: return null
        for (net in networks) {
            val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull() ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val isLanTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!isLanTransport) continue

            val lp = runCatching { cm.getLinkProperties(net) }.getOrNull()
            if (lp != null) {
                var hasIpv4 = false
                for (la in lp.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        hasIpv4 = true
                        val addrInt = ipv4BytesToInt(addr.address)
                        if (targetInt != null && isSameSubnet(addrInt, targetInt, la.prefixLength)) {
                            return net.socketFactory
                        }
                    }
                }
                if (hasIpv4 && fallbackWifiNetwork == null) {
                    fallbackWifiNetwork = net
                }
            }
        }

        // Only use fallback Wi-Fi socketFactory if the phone isn't simultaneously tethering another interface
        if (fallbackWifiNetwork != null && !hasSeparateHotspotInterface()) {
            return fallbackWifiNetwork.socketFactory
        }
        return null
    }

    private fun hasSeparateHotspotInterface(): Boolean {
        val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull() ?: return false
        return ifaces.any { iface ->
            val n = iface.name.lowercase()
            runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) &&
                (n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("wlan1") || n.startsWith("rndis") || n.contains("tether"))
        }
    }

    private fun clientBoundToLan(base: OkHttpClient, targetIp: String): OkHttpClient {
        val sf = resolveLanSocketFactory(targetIp) ?: return base
        return base.newBuilder().socketFactory(sf).build()
    }

    suspend fun connect(profile: ChurchPcProfile): Result<String> = withContext(Dispatchers.IO) {
        disconnectInternal(resetStatus = false)
        _status.value = ConnectionStatus.CONNECTING
        _lastError.value = null

        if (!LanSecurityValidator.isValidToken(profile.token)) {
            _status.value = ConnectionStatus.ERROR
            val msg = "Security token must be at least 16 characters."
            _lastError.value = msg
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val candidates = buildList {
            if (LanSecurityValidator.isAllowedLocalHost(profile.preferredIp)) add(profile.preferredIp)
            profile.ips.forEach { ip ->
                if (LanSecurityValidator.isAllowedLocalHost(ip) && ip !in this) add(ip)
            }
        }

        if (candidates.isEmpty()) {
            _status.value = ConnectionStatus.ERROR
            val msg = "Blocked: Only private church Wi-Fi / Hotspot LAN IPs are allowed."
            _lastError.value = msg
            return@withContext Result.failure(SecurityException(msg))
        }

        // Single-IP ultra-fast race: whichever succeeds FIRST (WebSocket OR verified HTTP REST) wins immediately!
        if (candidates.size == 1) {
            val singleIp = candidates.first()
            activeProfile = profile.copy(preferredIp = singleIp)
            activeIp = singleIp
            activeRestPort = profile.restPort
            _connectedHost.value = "$singleIp:${profile.wsPort}"

            val fastReady = CompletableDeferred<Boolean>()
            val wsLatch = CompletableDeferred<Boolean>()
            var wsConnectedNow = false
            openSocketIoConnection(singleIp, profile.wsPort, profile.token, wsLatch)

            val wsWatcher = launch {
                if (wsLatch.await()) {
                    wsConnectedNow = true
                    fastReady.complete(true)
                }
            }

            val probeDeferred = async {
                val res = probeHostPorts(singleIp, profile.restPort, profile.wsPort, profile.token)
                if (res.verifiedAradhanaServer || res.statusCode in 200..299) {
                    activeRestPort = res.respondedPort
                    fastReady.complete(true)
                } else if (res.verifiedAuthRejection) {
                    fastReady.complete(false)
                }
                res
            }

            val quickWin = withTimeoutOrNull(2400L) { fastReady.await() }
            wsWatcher.cancel()

            if (quickWin == true) {
                _status.value = ConnectionStatus.CONNECTED
                _lastError.value = null
                return@withContext Result.success(singleIp)
            }

            val probe = probeDeferred.await()
            if (probe.verifiedAuthRejection) {
                _status.value = ConnectionStatus.ERROR
                val msg = "Invalid security token (401 Unauthorized). Re-scan QR code on PC."
                _lastError.value = msg
                disconnectInternal(resetStatus = false)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            if (probe.verifiedAradhanaServer || probe.statusCode in 200..299 || wsConnectedNow) {
                activeRestPort = probe.respondedPort
                _status.value = ConnectionStatus.CONNECTED
                _lastError.value = null
                return@withContext Result.success(singleIp)
            }

            val detail = _lastError.value
                ?: "Could not reach $singleIp:${profile.wsPort}. Ensure Phone & PC are on the same Wi-Fi/Hotspot."
            _status.value = ConnectionStatus.ERROR
            _lastError.value = detail
            disconnectInternal(resetStatus = false)
            return@withContext Result.failure(IllegalStateException(detail))
        }

        val probeOutcome = probeReachableHost(candidates, profile.restPort, profile.wsPort, profile.token)
        if (probeOutcome.authRejected) {
            _status.value = ConnectionStatus.ERROR
            val msg = "Invalid security token (401 Unauthorized). Re-scan QR code on PC."
            _lastError.value = msg
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val chosenIp = probeOutcome.reachableIp ?: candidates.first()
        activeProfile = profile.copy(preferredIp = chosenIp)
        activeIp = chosenIp
        activeRestPort = probeOutcome.respondedPort
        _connectedHost.value = "$chosenIp:${profile.wsPort}"

        val wsLatch = CompletableDeferred<Boolean>()
        openSocketIoConnection(chosenIp, profile.wsPort, profile.token, wsLatch)

        if (probeOutcome.reachableIp != null) {
            _status.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            return@withContext Result.success(chosenIp)
        }

        val socketConnected = withTimeoutOrNull(2200L) { wsLatch.await() } ?: false
        if (socketConnected) {
            Result.success(chosenIp)
        } else {
            val detail = _lastError.value
                ?: "Could not reach ${candidates.joinToString(", ")}:${profile.wsPort}. Check Wi-Fi/Hotspot & Windows Firewall."
            _status.value = ConnectionStatus.ERROR
            _lastError.value = detail
            disconnectInternal(resetStatus = false)
            Result.failure(IllegalStateException(detail))
        }
    }

    /**
     * Automatically discovers Aradhana Studio PCs on the active Wi-Fi or Hotspot subnet using
     * both UDP broadcast (instant ~5ms) and parallel Wi-Fi-bound HTTP `/mobile-discover` sweep (~400ms).
     */
    suspend fun discoverChurchPcs(defaultRestPort: Int = 5506): List<DiscoveredChurchPc> = withContext(Dispatchers.IO) {
        val foundByIp = ConcurrentHashMap<String, DiscoveredChurchPc>()

        coroutineScope {
            val udpJob = async {
                discoverViaUdpBroadcast(defaultRestPort).forEach { pc ->
                    foundByIp[pc.ip] = pc
                }
            }

            val httpJob = async {
                val subnets = collectLocalIpv4Prefixes()
                val candidateIps = LinkedHashSet<String>()
                // Common hotspot gateways first
                candidateIps.add("192.168.137.1")
                for ((prefix, selfLastOctet) in subnets) {
                    for (host in 1..254) {
                        if (host != selfLastOctet) {
                            candidateIps.add("$prefix.$host")
                        }
                    }
                }

                val sem = Semaphore(36)
                val tasks = candidateIps.map { ip ->
                    async probeTask@{
                        if (foundByIp.containsKey(ip)) return@probeTask
                        sem.withPermit {
                            if (foundByIp.containsKey(ip)) return@withPermit
                            probeDiscoveryHttp(ip, defaultRestPort)?.let { pc ->
                                foundByIp[pc.ip] = pc
                            }
                        }
                    }
                }
                tasks.awaitAll()
            }

            udpJob.await()
            httpJob.await()
        }

        foundByIp.values.sortedBy { it.name.lowercase() }
    }

    /**
     * Exchanges the 6-digit Pairing PIN shown on the Aradhana Studio PC Connect window
     * for the full pairing profile over private LAN.
     */
    suspend fun pairWithPin(pc: DiscoveredChurchPc, rawPin: String): Result<ParsedPairingPayload> = withContext(Dispatchers.IO) {
        val cleanPin = rawPin.filter(Char::isDigit)
        if (cleanPin.length != 6) {
            return@withContext Result.failure(IllegalArgumentException("Please enter the 6-digit PIN shown on PC."))
        }
        if (!LanSecurityValidator.isAllowedLocalHost(pc.ip)) {
            return@withContext Result.failure(SecurityException("Blocked non-LAN host."))
        }

        val bodyJson = JSONObject().put("pin", cleanPin).toString()
        val portsToTry = listOf(pc.restPort, pc.wsPort).distinct()
        var lastErr = "Could not connect to ${pc.ip}"

        for (port in portsToTry) {
            if (!LanSecurityValidator.isValidPort(port)) continue
            val req = Request.Builder()
                .url("http://${pc.ip}:$port/mobile-pair-pin")
                .header("X-Device-Name", headerSafeDeviceName)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val respResult = executeWithLanFallback(probeHttpClient, pc.ip, req) { resp ->
                val text = resp.body?.string().orEmpty()
                Triple(resp.code, resp.isSuccessful, text)
            }

            val (code, ok, text) = respResult ?: continue
            if (ok && text.isNotBlank()) {
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json != null) {
                    // Ensure scanned/connected IP is first in the list
                    val ipsArr = json.optJSONArray("ips") ?: JSONArray()
                    val mergedIps = JSONArray().apply {
                        put(pc.ip)
                        for (i in 0 until ipsArr.length()) {
                            val item = ipsArr.optString(i, "").trim()
                            if (item.isNotEmpty() && item != pc.ip) put(item)
                        }
                    }
                    json.put("ips", mergedIps)
                    return@withContext when (val parsed = QrPairingParser.parse(json.toString())) {
                        is QrParseResult.Success -> Result.success(parsed.payload)
                        is QrParseResult.Invalid -> Result.failure(IllegalStateException(parsed.reason))
                    }
                }
            } else if (code == 401) {
                return@withContext Result.failure(IllegalArgumentException("Incorrect 6-digit PIN. Check the code on Aradhana Studio PC."))
            } else if (code == 429) {
                return@withContext Result.failure(IllegalStateException("Too many attempts. Wait 60 seconds or scan the QR code."))
            } else {
                lastErr = "HTTP $code"
            }
        }

        Result.failure(IllegalStateException(lastErr))
    }

    private fun discoverViaUdpBroadcast(udpPort: Int): List<DiscoveredChurchPc> {
        val discovered = mutableListOf<DiscoveredChurchPc>()
        val payloadBytes = """{"discover":"aradhanastudio"}""".toByteArray(Charsets.UTF_8)

        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 320

                val targets = LinkedHashSet<String>().apply {
                    add("255.255.255.255")
                    add("192.168.137.255")
                    add("192.168.137.1")
                    collectLocalIpv4Prefixes().forEach { (prefix, _) ->
                        add("$prefix.255")
                    }
                }

                for (addrStr in targets) {
                    runCatching {
                        val addr = InetAddress.getByName(addrStr)
                        sock.send(DatagramPacket(payloadBytes, payloadBytes.size, addr, udpPort))
                    }
                }

                val deadline = System.currentTimeMillis() + 340L
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buf, buf.size)
                    val ok = runCatching {
                        sock.receive(pkt)
                        true
                    }.getOrDefault(false)
                    if (!ok) break

                    val senderIp = pkt.address?.hostAddress?.trim().orEmpty()
                    if (!LanSecurityValidator.isAllowedLocalHost(senderIp)) continue
                    val raw = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    val appTag = obj.optString("app", "")
                    if (appTag != "aradhanastudio" && appTag != "hamrochurch") continue

                    val ipsList = mutableListOf(senderIp)
                    val arr = obj.optJSONArray("ips")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val candidate = arr.optString(i, "").trim()
                            if (LanSecurityValidator.isAllowedLocalHost(candidate) && candidate !in ipsList) {
                                ipsList.add(candidate)
                            }
                        }
                    }

                    discovered.add(
                        DiscoveredChurchPc(
                            name = obj.optString("name", "Aradhana Studio PC").take(64),
                            ip = senderIp,
                            ips = ipsList,
                            wsPort = obj.optInt("wsPort", 5505),
                            restPort = obj.optInt("restPort", udpPort),
                            pinEnabled = obj.optBoolean("pinEnabled", true)
                        )
                    )
                }
            }
        }
        return discovered
    }

    private fun probeDiscoveryHttp(ip: String, restPort: Int): DiscoveredChurchPc? {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(restPort)) return null
        val req = Request.Builder()
            .url("http://$ip:$restPort/mobile-discover")
            .get()
            .build()

        return executeWithLanFallback(discoveryHttpClient, ip, req) { resp ->
            if (!resp.isSuccessful) return@executeWithLanFallback null
            val text = resp.body?.string().orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@executeWithLanFallback null
            val appTag = obj.optString("app", "")
            if (appTag != "aradhanastudio" && appTag != "hamrochurch") return@executeWithLanFallback null
            DiscoveredChurchPc(
                name = obj.optString("name", "Aradhana Studio PC").take(64),
                ip = ip,
                ips = listOf(ip),
                wsPort = obj.optInt("wsPort", 5505),
                restPort = obj.optInt("restPort", restPort),
                pinEnabled = obj.optBoolean("pinEnabled", true)
            )
        }
    }

    private fun collectLocalIpv4Prefixes(): List<Pair<String, Int>> {
        val prefixes = LinkedHashMap<String, Int>()

        // 1. ConnectivityManager Wi-Fi / Ethernet LinkProperties
        context?.let { ctx ->
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            @Suppress("DEPRECATION")
            val nets = runCatching { cm?.allNetworks }.getOrNull().orEmpty()
            for (net in nets) {
                val caps = runCatching { cm?.getNetworkCapabilities(net) }.getOrNull() ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                ) continue
                val lp = runCatching { cm?.getLinkProperties(net) }.getOrNull() ?: continue
                for (la in lp.linkAddresses) {
                    val host = (la.address as? Inet4Address)?.hostAddress ?: continue
                    addPrefixIfPrivate(host, prefixes)
                }
            }
        }

        // 2. NetworkInterface enumeration (catches Android Mobile Hotspot AP interface ap0 / wlan1 / rndis0)
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull().orEmpty()
        for (iface in interfaces) {
            val name = iface.name.lowercase()
            if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("dummy")) {
                continue
            }
            if (!runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)) continue
            val addrs = runCatching { Collections.list(iface.inetAddresses) }.getOrNull().orEmpty()
            for (addr in addrs) {
                val host = (addr as? Inet4Address)?.hostAddress ?: continue
                addPrefixIfPrivate(host, prefixes)
            }
        }

        return prefixes.entries.take(3).map { it.key to it.value }
    }

    private fun addPrefixIfPrivate(ip: String, out: MutableMap<String, Int>) {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || ip.startsWith("127.")) return
        val parts = ip.split('.')
        if (parts.size != 4) return
        val last = parts[3].toIntOrNull() ?: return
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        out.putIfAbsent(prefix, last)
    }

    private data class ProbeOutcome(
        val reachableIp: String?,
        val respondedPort: Int,
        val authRejected: Boolean
    )

    private data class ProbeResult(
        val ip: String,
        val respondedPort: Int,
        val statusCode: Int,
        val verifiedAradhanaServer: Boolean,
        val verifiedAuthRejection: Boolean,
        val elapsedNanos: Long
    )

    private suspend fun probeReachableHost(
        ips: List<String>,
        restPort: Int,
        wsPort: Int,
        token: String
    ): ProbeOutcome = coroutineScope {
        val fastWinner = CompletableDeferred<ProbeResult?>()
        val probeScope = CoroutineScope(coroutineContext + SupervisorJob())

        val jobs = ips.map { ip ->
            probeScope.async {
                val res = probeHostPorts(ip, restPort, wsPort, token)
                if (res.verifiedAradhanaServer) {
                    fastWinner.complete(res)
                }
                res
            }
        }

        probeScope.launch {
            val all = jobs.awaitAll()
            if (!fastWinner.isCompleted) {
                val fallback2xx = all.filter { it.statusCode in 200..299 }.minByOrNull { it.elapsedNanos }
                fastWinner.complete(fallback2xx)
            }
        }

        val winner = fastWinner.await()
        if (winner != null) {
            probeScope.coroutineContext.cancelChildren()
            return@coroutineScope ProbeOutcome(
                reachableIp = winner.ip,
                respondedPort = winner.respondedPort,
                authRejected = false
            )
        }

        val results = jobs.awaitAll()
        if (results.any { it.verifiedAuthRejection }) {
            return@coroutineScope ProbeOutcome(reachableIp = null, respondedPort = restPort, authRejected = true)
        }

        ProbeOutcome(reachableIp = null, respondedPort = restPort, authRejected = false)
    }

    private fun probeHostPorts(ip: String, restPort: Int, wsPort: Int, token: String): ProbeResult {
        val primary = pingRestOnce(ip, restPort, token, System.nanoTime())
        if (primary.verifiedAradhanaServer || primary.verifiedAuthRejection || primary.statusCode in 200..299 || wsPort == restPort) {
            return primary
        }
        // Express REST handler is also mounted on wsPort (5505) — instant fallback if port 5506 is busy
        return pingRestOnce(ip, wsPort, token, System.nanoTime())
    }

    private fun pingRestOnce(ip: String, port: Int, token: String, startNanos: Long): ProbeResult {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(port)) {
            return ProbeResult(ip, port, -1, verifiedAradhanaServer = false, verifiedAuthRejection = false, elapsedNanos = Long.MAX_VALUE)
        }
        val bodyJson = JSONObject().put("action", "get_mobile_ping").toString()
        val req = Request.Builder()
            .url("http://$ip:$port/")
            .header("Authorization", "Bearer $token")
            .header("X-Device-Name", headerSafeDeviceName)
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeWithLanFallback(probeHttpClient, ip, req) { resp ->
            val elapsed = System.nanoTime() - startNanos
            val bodyText = resp.body?.string().orEmpty()
            val verifiedOk = resp.code in 200..299 && bodyText.contains("\"get_mobile_ping\"")
            val verified401 = resp.code == 401 && bodyText.contains("\"Unauthorized\"")
            ProbeResult(
                ip = ip,
                respondedPort = port,
                statusCode = resp.code,
                verifiedAradhanaServer = verifiedOk,
                verifiedAuthRejection = verified401,
                elapsedNanos = elapsed
            )
        } ?: ProbeResult(ip, port, -1, verifiedAradhanaServer = false, verifiedAuthRejection = false, elapsedNanos = Long.MAX_VALUE)
    }

    private fun <T> executeWithLanFallback(
        baseClient: OkHttpClient,
        ip: String,
        request: Request,
        block: (okhttp3.Response) -> T
    ): T? {
        val lanSocketFactory = resolveLanSocketFactory(ip)
        if (lanSocketFactory != null) {
            val boundClient = baseClient.newBuilder().socketFactory(lanSocketFactory).build()
            try {
                return boundClient.newCall(request).execute().use(block)
            } catch (_: Exception) {
                // Fall through to default network socketFactory (e.g. if phone is acting as Hotspot AP)
            }
        }
        return try {
            baseClient.newCall(request).execute().use(block)
        } catch (_: Exception) {
            null
        }
    }

    private fun openSocketIoConnection(
        ip: String,
        wsPort: Int,
        token: String,
        connectLatch: CompletableDeferred<Boolean>? = null
    ) {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(wsPort)) {
            _status.value = ConnectionStatus.ERROR
            _lastError.value = "Refused non-LAN connection target."
            connectLatch?.complete(false)
            return
        }

        val boundWsClient = clientBoundToLan(wsBaseHttpClient, ip)

        val options = IO.Options.builder()
            .setForceNew(true)
            .setTransports(arrayOf("polling", "websocket"))
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(500)
            .setReconnectionDelayMax(3000)
            .setTimeout(3500)
            .setAuth(
                mapOf(
                    "token" to token,
                    "deviceName" to deviceDisplayName
                )
            )
            .setExtraHeaders(
                mapOf(
                    "Authorization" to listOf("Bearer $token"),
                    "X-Device-Name" to listOf(headerSafeDeviceName)
                )
            )
            .build()
            .apply {
                callFactory = boundWsClient
                webSocketFactory = boundWsClient
            }

        val newSocket = IO.socket(URI.create("http://$ip:$wsPort"), options)

        newSocket.on(Socket.EVENT_CONNECT) {
            _status.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            _connectedHost.value = "$ip:$wsPort"
            connectLatch?.complete(true)
        }

        newSocket.on(Socket.EVENT_DISCONNECT) {
            if (activeProfile == null) {
                _status.value = ConnectionStatus.DISCONNECTED
            }
        }

        newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val errMsg = args.firstOrNull()?.toString() ?: "Connection error"
            if (errMsg.contains("Unauthorized", ignoreCase = true)) {
                _lastError.value = "Unauthorized: Security token rejected by Aradhana Studio PC."
                _status.value = ConnectionStatus.ERROR
                newSocket.disconnect()
                connectLatch?.complete(false)
            } else if (_status.value != ConnectionStatus.CONNECTED) {
                _lastError.value = errMsg
            }
        }

        newSocket.on("data") { args ->
            val raw = args.firstOrNull() ?: return@on
            val json = when (raw) {
                is JSONObject -> raw
                is String -> try {
                    JSONObject(raw)
                } catch (_: Exception) {
                    null
                }
                else -> null
            } ?: return@on

            if (json.optBoolean("isBroadcast", false)) {
                val event = json.optString("event", "")
                if (event == "mobile_live_state") {
                    val liveObj = json.optJSONObject("data")
                    if (liveObj != null) {
                        scope.launch {
                            onLivePush(RemoteParsers.parseLiveState(liveObj))
                        }
                    }
                } else if (event == "mobile_promotions") {
                    val dataObj = json.optJSONObject("data")
                    val promosArr = dataObj?.optJSONArray("promotions") ?: json.optJSONArray("data")
                    if (promosArr != null) {
                        scope.launch {
                            onPromotionsPush(promosArr)
                        }
                    }
                }
                return@on
            }

            val reqId = json.optString("requestId", "")
            if (reqId.isNotEmpty()) {
                pendingRequests.remove(reqId)?.complete(json)
            }
        }

        socket = newSocket
        newSocket.connect()
    }

    suspend fun sendAction(
        action: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 3500L
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        val profile = activeProfile
            ?: return@withContext Result.failure(IllegalStateException("Not connected"))

        val requestId = UUID.randomUUID().toString().replace("-", "").take(10)
        val payload = JSONObject(params.toString()).apply {
            put("action", action)
            put("requestId", requestId)
            put("expectReturn", true)
        }

        val currentSocket = socket
        if (currentSocket != null && currentSocket.connected()) {
            val deferred = CompletableDeferred<JSONObject>()
            pendingRequests[requestId] = deferred
            try {
                currentSocket.emit("data", payload.toString())
                val wsReply = withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (wsReply != null) {
                    return@withContext unwrapDataEnvelope(wsReply)
                }
            } finally {
                pendingRequests.remove(requestId)
            }
        }

        // Seamless automatic fallback to HTTP REST ( bound to active Wi-Fi LAN interface )
        val targetHost = activeIp.ifEmpty { profile.preferredIp }
        val primaryRes = sendViaRest(targetHost, activeRestPort, profile.token, payload)
        if (primaryRes.isSuccess || activeRestPort == profile.wsPort) {
            return@withContext primaryRes
        }
        sendViaRest(targetHost, profile.wsPort, profile.token, payload)
    }

    private fun sendViaRest(
        ip: String,
        restPort: Int,
        token: String,
        payload: JSONObject
    ): Result<JSONObject> {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(restPort)) {
            return Result.failure(SecurityException("Refused non-LAN HTTP request"))
        }

        val request = Request.Builder()
            .url("http://$ip:$restPort/")
            .header("Authorization", "Bearer $token")
            .header("X-Device-Name", headerSafeDeviceName)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeWithLanFallback(restHttpClient, ip, request) { response ->
            when {
                response.code == 204 -> Result.success(JSONObject())
                response.code == 401 -> {
                    _status.value = ConnectionStatus.ERROR
                    _lastError.value = "Session unauthorized — token changed on PC."
                    Result.failure(IllegalStateException("Unauthorized"))
                }
                !response.isSuccessful -> Result.failure(IllegalStateException("HTTP ${response.code}"))
                else -> {
                    val text = response.body?.string().orEmpty()
                    val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
                    unwrapDataEnvelope(json)
                }
            }
        } ?: Result.failure(IllegalStateException("Could not reach $ip:$restPort"))
    }

    private fun unwrapDataEnvelope(envelope: JSONObject): Result<JSONObject> {
        val err = envelope.optString("error", "")
        if (err.isNotEmpty()) {
            return Result.failure(IllegalStateException(err))
        }
        val inner = envelope.optJSONObject("data")
        return Result.success(inner ?: envelope)
    }

    fun disconnect() {
        disconnectInternal(resetStatus = true)
    }

    private fun disconnectInternal(resetStatus: Boolean) {
        activeProfile = null
        activeIp = ""
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()

        socket?.let { s ->
            try {
                s.off()
                s.disconnect()
                s.close()
            } catch (_: Exception) {
            }
        }
        socket = null

        if (resetStatus) {
            _status.value = ConnectionStatus.DISCONNECTED
            _connectedHost.value = ""
            _lastError.value = null
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun ipv4ToInt(ip: String): Int? {
            val parts = ip.split('.')
            if (parts.size != 4) return null
            var res = 0
            for (p in parts) {
                val octet = p.toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                res = (res shl 8) or octet
            }
            return res
        }

        private fun ipv4BytesToInt(bytes: ByteArray): Int {
            if (bytes.size != 4) return 0
            return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        }

        private fun isSameSubnet(a: Int, b: Int, prefixLen: Int): Boolean {
            if (prefixLen <= 0 || prefixLen > 32) return false
            val mask = if (prefixLen == 32) -1 else (-1 shl (32 - prefixLen))
            return (a and mask) == (b and mask)
        }
    }
}
