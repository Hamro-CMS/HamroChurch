package com.hamrochurch.remote.network

import android.os.Build
import com.hamrochurch.remote.model.ChurchPcProfile
import com.hamrochurch.remote.model.ConnectionStatus
import com.hamrochurch.remote.model.LiveState
import com.hamrochurch.remote.model.RemoteParsers
import com.hamrochurch.remote.security.LanSecurityValidator
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HamroRemoteClient(
    private val onLivePush: (LiveState) -> Unit,
    private val onPromotionsPush: (JSONArray) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeHttpClient = OkHttpClient.Builder()
        .connectTimeout(1800, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    private val restHttpClient = OkHttpClient.Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var socket: Socket? = null
    private var activeProfile: ChurchPcProfile? = null
    private var activeIp: String = ""

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

        // Single-IP fast path: open WebSocket & REST verification simultaneously (zero sequential wait)
        if (candidates.size == 1) {
            val singleIp = candidates.first()
            activeProfile = profile.copy(preferredIp = singleIp)
            activeIp = singleIp
            _connectedHost.value = "$singleIp:${profile.wsPort}"

            val wsLatch = CompletableDeferred<Boolean>()
            openSocketIoConnection(singleIp, profile.wsPort, profile.token, wsLatch)

            val probeDeferred = async {
                pingRestOnce(singleIp, profile.restPort, profile.token, System.nanoTime())
            }

            val socketConnected = withTimeoutOrNull(2200L) { wsLatch.await() } ?: false
            if (socketConnected) {
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

            if (probe.verifiedAradhanaServer || probe.statusCode in 200..299) {
                _status.value = ConnectionStatus.CONNECTED
                return@withContext Result.success(singleIp)
            }

            val detail = _lastError.value ?: "Could not reach $singleIp on port ${profile.wsPort}. Check Wi-Fi/Hotspot & Windows Firewall."
            _status.value = ConnectionStatus.ERROR
            _lastError.value = detail
            disconnectInternal(resetStatus = false)
            return@withContext Result.failure(IllegalStateException(detail))
        }

        val probeOutcome = probeReachableHost(candidates, profile.restPort, profile.token)
        if (probeOutcome.authRejected) {
            _status.value = ConnectionStatus.ERROR
            val msg = "Invalid security token (401 Unauthorized). Re-scan QR code on PC."
            _lastError.value = msg
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val chosenIp = probeOutcome.reachableIp ?: candidates.first()
        activeProfile = profile.copy(preferredIp = chosenIp)
        activeIp = chosenIp
        _connectedHost.value = "$chosenIp:${profile.wsPort}"

        val wsLatch = CompletableDeferred<Boolean>()
        openSocketIoConnection(chosenIp, profile.wsPort, profile.token, wsLatch)

        val socketConnected = withTimeoutOrNull(2200L) { wsLatch.await() } ?: false

        if (socketConnected || probeOutcome.reachableIp != null) {
            if (!socketConnected) {
                _status.value = ConnectionStatus.CONNECTED
            }
            Result.success(chosenIp)
        } else {
            val detail = _lastError.value ?: "Could not reach ${candidates.joinToString(", ")} on port ${profile.wsPort}. Check Wi-Fi/Hotspot & Windows Firewall."
            _status.value = ConnectionStatus.ERROR
            _lastError.value = detail
            disconnectInternal(resetStatus = false)
            Result.failure(IllegalStateException(detail))
        }
    }

    private data class ProbeOutcome(
        val reachableIp: String?,
        val authRejected: Boolean
    )

    private data class ProbeResult(
        val ip: String,
        val statusCode: Int,
        val verifiedAradhanaServer: Boolean,
        val verifiedAuthRejection: Boolean,
        val elapsedNanos: Long
    )

    private suspend fun probeReachableHost(
        ips: List<String>,
        restPort: Int,
        token: String
    ): ProbeOutcome = coroutineScope {
        val fastWinner = CompletableDeferred<String>()
        val probeScope = CoroutineScope(coroutineContext + SupervisorJob())

        val jobs = ips.map { ip ->
            probeScope.async {
                val res = pingRestOnce(ip, restPort, token, System.nanoTime())
                if (res.verifiedAradhanaServer) {
                    fastWinner.complete(res.ip)
                }
                res
            }
        }

        // Watchdog: if all probes finish without a verified winner, close fastWinner so we don't wait
        probeScope.launch {
            val all = jobs.awaitAll()
            if (!fastWinner.isCompleted) {
                val fallback2xx = all.filter { it.statusCode in 200..299 }.minByOrNull { it.elapsedNanos }?.ip
                if (fallback2xx != null) {
                    fastWinner.complete(fallback2xx)
                } else {
                    fastWinner.complete("")
                }
            }
        }

        val winnerIp = fastWinner.await()
        if (winnerIp.isNotEmpty()) {
            probeScope.coroutineContext.cancelChildren()
            return@coroutineScope ProbeOutcome(reachableIp = winnerIp, authRejected = false)
        }

        val results = jobs.awaitAll()
        if (results.any { it.verifiedAuthRejection }) {
            return@coroutineScope ProbeOutcome(reachableIp = null, authRejected = true)
        }

        ProbeOutcome(reachableIp = null, authRejected = false)
    }

    private fun pingRestOnce(ip: String, restPort: Int, token: String, startNanos: Long): ProbeResult {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(restPort)) {
            return ProbeResult(ip, -1, verifiedAradhanaServer = false, verifiedAuthRejection = false, elapsedNanos = Long.MAX_VALUE)
        }
        return try {
            val bodyJson = JSONObject().put("action", "get_mobile_ping").toString()
            val req = Request.Builder()
                .url("http://$ip:$restPort/")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Name", headerSafeDeviceName)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            probeHttpClient.newCall(req).execute().use { resp ->
                val elapsed = System.nanoTime() - startNanos
                val bodyText = resp.body?.string().orEmpty()
                val verifiedOk = resp.code in 200..299 && bodyText.contains("\"get_mobile_ping\"")
                val verified401 = resp.code == 401 && bodyText.contains("\"Unauthorized\"")
                ProbeResult(
                    ip = ip,
                    statusCode = resp.code,
                    verifiedAradhanaServer = verifiedOk,
                    verifiedAuthRejection = verified401,
                    elapsedNanos = elapsed
                )
            }
        } catch (_: Exception) {
            ProbeResult(ip, -1, verifiedAradhanaServer = false, verifiedAuthRejection = false, elapsedNanos = Long.MAX_VALUE)
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

        val options = IO.Options.builder()
            .setForceNew(true)
            .setTransports(arrayOf("websocket", "polling"))
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

        val newSocket = IO.socket(URI.create("http://$ip:$wsPort"), options)

        newSocket.on(Socket.EVENT_CONNECT) {
            _status.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            _connectedHost.value = "$ip:$wsPort"
            connectLatch?.complete(true)
        }

        newSocket.on(Socket.EVENT_DISCONNECT) {
            if (activeProfile != null) {
                _status.value = ConnectionStatus.RECONNECTING
            } else {
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

        // Seamless automatic fallback to HTTP REST on port + 1
        sendViaRest(activeIp.ifEmpty { profile.preferredIp }, profile.restPort, profile.token, payload)
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

        return try {
            val request = Request.Builder()
                .url("http://$ip:$restPort/")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Name", headerSafeDeviceName)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            restHttpClient.newCall(request).execute().use { response ->
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    }
}
