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

        openSocketIoConnection(chosenIp, profile.wsPort, profile.token)

        // Wait up to 3.5s for Socket.IO connect OR verify REST reachability
        val socketConnected = withTimeoutOrNull(3500L) {
            while (_status.value == ConnectionStatus.CONNECTING) {
                kotlinx.coroutines.delay(60)
            }
            _status.value == ConnectionStatus.CONNECTED
        } ?: false

        if (socketConnected || probeOutcome.reachableIp != null) {
            if (!socketConnected) {
                // REST works even if WS is still handshaking
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

    private suspend fun probeReachableHost(
        ips: List<String>,
        restPort: Int,
        token: String
    ): ProbeOutcome = coroutineScope {
        val results = ips.map { ip ->
            async {
                val start = System.nanoTime()
                val code = pingRestOnce(ip, restPort, token)
                Triple(ip, code, System.nanoTime() - start)
            }
        }.awaitAll()

        if (results.any { it.second == 401 || it.second == 403 }) {
            return@coroutineScope ProbeOutcome(reachableIp = null, authRejected = true)
        }

        val fastestOk = results
            .filter { it.second in 200..299 }
            .minByOrNull { it.third }
            ?.first

        ProbeOutcome(reachableIp = fastestOk, authRejected = false)
    }

    private fun pingRestOnce(ip: String, restPort: Int, token: String): Int {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(restPort)) {
            return -1
        }
        return try {
            val bodyJson = JSONObject().put("action", "get_mobile_ping").toString()
            val req = Request.Builder()
                .url("http://$ip:$restPort/")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Name", deviceDisplayName)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            probeHttpClient.newCall(req).execute().use { resp -> resp.code }
        } catch (_: Exception) {
            -1
        }
    }

    private fun openSocketIoConnection(ip: String, wsPort: Int, token: String) {
        if (!LanSecurityValidator.isAllowedLocalHost(ip) || !LanSecurityValidator.isValidPort(wsPort)) {
            _status.value = ConnectionStatus.ERROR
            _lastError.value = "Refused non-LAN connection target."
            return
        }

        val options = IO.Options.builder()
            .setForceNew(true)
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(800)
            .setReconnectionDelayMax(4000)
            .setTimeout(5000)
            .setAuth(
                mapOf(
                    "token" to token,
                    "deviceName" to deviceDisplayName
                )
            )
            .setExtraHeaders(
                mapOf(
                    "Authorization" to listOf("Bearer $token"),
                    "X-Device-Name" to listOf(deviceDisplayName)
                )
            )
            .build()

        val newSocket = IO.socket(URI.create("http://$ip:$wsPort"), options)

        newSocket.on(Socket.EVENT_CONNECT) {
            _status.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            _connectedHost.value = "$ip:$wsPort"
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
                .header("X-Device-Name", deviceDisplayName)
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
