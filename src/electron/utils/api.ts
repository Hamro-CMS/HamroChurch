import crypto from "crypto"
import dgram from "dgram"
import { ipcMain } from "electron"
import express, { type NextFunction, type Request, type Response } from "express"
import http from "http"
import os from "os"
import OSC from "osc-js"
import { Server, type Socket } from "socket.io"
import { uid } from "uid"
import { ToMain } from "../../types/IPC/ToMain"
import { getLocalIPs } from "../data/bonjour"
import { requestToMain, sendToMain } from "../IPC/main"
import { waitUntilValueIsDefined } from "./helpers"

export interface CompanionApiConfig {
    port?: number
    allowLan?: boolean
    token?: string
}

export interface ConnectedMobileClient {
    id: string
    deviceName: string
    ip: string
    connectedAt: number
    lastSeenAt: number
}

const MIN_LAN_TOKEN_LENGTH = 16

const app = express()
app.disable("x-powered-by")

const servers: { [key: string]: http.Server | OSC } = {}
let ioServer: Server | null = null
let discoveryUdpSocket: dgram.Socket | null = null
const connectedClients = new Map<string, ConnectedMobileClient & { socket: Socket }>()
const restClients = new Map<string, ConnectedMobileClient>()
const REST_CLIENT_TTL_MS = 25000
const PIN_WINDOW_MS = 60000
const PIN_MAX_FAILURES = 5
const pinFailuresByIp = new Map<string, number[]>()
let globalPinFailures: number[] = []
const DEFAULT_PORTS = { WebSocket: 5505, REST: 5506 }

let currentConfig: { port?: number; allowLan: boolean; token: string } = {
    port: undefined,
    allowLan: false,
    token: ""
}
let activeListenState: { wsPort: number; restPort: number; host: string } | null = null

export function generateMobileRemoteToken(): string {
    return crypto.randomBytes(16).toString("hex")
}

export function deriveMobilePairingPin(rawToken: string): string {
    const clean = typeof rawToken === "string" ? rawToken.trim() : ""
    if (clean.length < MIN_LAN_TOKEN_LENGTH) return ""
    const digest = crypto.createHmac("sha256", "aradhana-studio-mobile-pin-v1").update(clean, "utf8").digest()
    const code = (digest.readUInt32BE(0) % 900000) + 100000
    return String(code)
}

export function getMobilePairingPin(tokenOverride?: string): string {
    const source = typeof tokenOverride === "string" && tokenOverride.trim().length >= MIN_LAN_TOKEN_LENGTH ? tokenOverride : currentConfig.token
    return deriveMobilePairingPin(source)
}

function isPrivateLanIp(rawIp: string): boolean {
    const ip = normalizeRemoteIp(rawIp)
    if (ip === "::1" || ip === "localhost") return true
    const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(ip)
    if (!m) return false
    const octets = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4])]
    if (octets.some((n) => Number.isNaN(n) || n < 0 || n > 255)) return false
    const [a, b] = octets
    return a === 127 || a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) || (a === 100 && b >= 64 && b <= 127)
}

function pruneStaleRestClients(now = Date.now()): boolean {
    let changed = false
    restClients.forEach((entry, key) => {
        if (now - entry.lastSeenAt > REST_CLIENT_TTL_MS) {
            restClients.delete(key)
            changed = true
        }
    })
    return changed
}

function trackRestMobileClient(ip: string, rawDeviceName: unknown) {
    const now = Date.now()
    pruneStaleRestClients(now)
    const deviceName = sanitizeClientLabel(rawDeviceName)
    const key = `rest:${ip}:${deviceName}`
    const existing = restClients.get(key)
    if (existing) {
        existing.lastSeenAt = now
        return
    }
    restClients.set(key, {
        id: key,
        deviceName,
        ip,
        connectedAt: now,
        lastSeenAt: now
    })
    notifyClientsUpdated()
}

function sanitizeClientLabel(raw: unknown): string {
    if (typeof raw !== "string") return "Mobile Remote"
    const cleaned = raw.replace(/[\u0000-\u001F\u007F<>]/g, "").trim().slice(0, 64)
    return cleaned || "Mobile Remote"
}

function normalizeRemoteIp(raw: unknown): string {
    if (typeof raw !== "string" || !raw) return "unknown"
    return raw.replace(/^::ffff:/i, "").trim().slice(0, 64)
}

export function getConnectedMobileClients(): ConnectedMobileClient[] {
    pruneStaleRestClients()
    const wsList = Array.from(connectedClients.values()).map(({ id, deviceName, ip, connectedAt, lastSeenAt }) => ({
        id,
        deviceName,
        ip,
        connectedAt,
        lastSeenAt
    }))
    const wsPeers = new Set(wsList.map((c) => `${c.ip}|${c.deviceName}`))
    const restOnly = Array.from(restClients.values()).filter((c) => !wsPeers.has(`${c.ip}|${c.deviceName}`))
    return [...wsList, ...restOnly]
}

function notifyClientsUpdated() {
    sendToMain(ToMain.MOBILE_CLIENTS_UPDATED, getConnectedMobileClients())
}

export function disconnectMobileClient(id: string): boolean {
    if (id === "all") {
        connectedClients.forEach((entry) => {
            try {
                entry.socket.disconnect(true)
            } catch {
                // ignore
            }
        })
        connectedClients.clear()
        restClients.clear()
        notifyClientsUpdated()
        return true
    }

    if (restClients.has(id)) {
        restClients.delete(id)
        notifyClientsUpdated()
        return true
    }

    const client = connectedClients.get(id)
    if (!client) return false

    try {
        client.socket.disconnect(true)
    } catch {
        // ignore
    }
    connectedClients.delete(id)
    notifyClientsUpdated()
    return true
}

export function broadcastMobileData(payload: { event: string; data: any }) {
    if (!connectedClients.size) return
    const message = { isBroadcast: true, event: payload.event, data: payload.data, timestamp: Date.now() }
    connectedClients.forEach((entry) => {
        try {
            if ((entry.socket as any).connected !== false) {
                entry.socket.emit("data", message)
            }
        } catch {
            // ignore per-socket emit error
        }
    })
}

function verifyApiToken(provided: unknown): boolean {
    if (!currentConfig.token) return true
    if (typeof provided !== "string" || !provided) return false
    const expectedBuf = Buffer.from(currentConfig.token, "utf8")
    const providedBuf = Buffer.from(provided, "utf8")
    if (expectedBuf.length !== providedBuf.length) return false
    return crypto.timingSafeEqual(expectedBuf, providedBuf)
}

function extractHttpToken(headers: http.IncomingHttpHeaders): string {
    const authHeader = headers.authorization
    if (typeof authHeader === "string" && /^bearer\s+/i.test(authHeader)) {
        return authHeader.replace(/^bearer\s+/i, "").trim()
    }
    const apiTokenHeader = headers["x-api-token"]
    if (typeof apiTokenHeader === "string") {
        return apiTokenHeader.trim()
    }
    return ""
}

// WebSocket on 5505, REST on +1 (5506), bound to 127.0.0.1 by default unless allowLan + strong token (>= 16 chars) are configured
export function startWebSocketAndRest(config?: number | CompanionApiConfig) {
    const nextConfig =
        typeof config === "number" || config === undefined
            ? { port: config, allowLan: false, token: "" }
            : {
                  port: config.port,
                  allowLan: config.allowLan === true,
                  token: typeof config.token === "string" ? config.token.trim() : ""
              }

    const bindHost = nextConfig.allowLan && nextConfig.token.length >= MIN_LAN_TOKEN_LENGTH ? "0.0.0.0" : "127.0.0.1"
    const wsPort = nextConfig.port || DEFAULT_PORTS.WebSocket
    const restPort = nextConfig.port ? nextConfig.port + 1 : DEFAULT_PORTS.REST

    const previousToken = currentConfig.token
    currentConfig = nextConfig

    // Fast in-place token update if WebSocket & REST servers are already listening on the exact same host & ports
    const wsListening = !!(servers.WebSocket && (servers.WebSocket as http.Server).listening)
    const restListening = !!(servers.REST && (servers.REST as http.Server).listening)
    if (activeListenState && wsListening && restListening && activeListenState.wsPort === wsPort && activeListenState.restPort === restPort && activeListenState.host === bindHost) {
        if (previousToken && previousToken !== currentConfig.token) {
            disconnectMobileClient("all")
        }
        if (bindHost === "0.0.0.0" && !discoveryUdpSocket) {
            startMobileDiscoveryUdp(restPort, wsPort)
        }
        return
    }

    stopApiListener()
    activeListenState = { wsPort, restPort, host: bindHost }

    startRestListener(restPort, bindHost)
    startWebSocket(wsPort, bindHost)
    startOSC(wsPort, bindHost)
    if (bindHost === "0.0.0.0") {
        startMobileDiscoveryUdp(restPort, wsPort)
    }
}

// WEBSOCKET

function startWebSocket(PORT: number, host: string) {
    const server = (servers.WebSocket = http.createServer(app))

    if (!PORT) PORT = DEFAULT_PORTS.WebSocket
    server.listen(PORT, host, () => {
        console.info(`WebSocket: Starting server at ${host}:${PORT.toString()}`)
    })

    server.once("error", (err) => {
        if ((err as any).code === "EADDRINUSE") server.close()
    })

    const io = (ioServer = new Server(server, {
        pingInterval: 15000,
        pingTimeout: 15000,
        maxHttpBufferSize: 1e6
    }))
    io.use((socket, next) => {
        // Prevent browser Cross-Site WebSocket Hijacking (CSWSH)
        if (socket.handshake.headers.origin !== undefined) {
            return next(new Error("Cross-origin WebSocket connections are not permitted"))
        }

        if (currentConfig.token) {
            const authToken = typeof socket.handshake.auth?.token === "string" ? socket.handshake.auth.token.trim() : extractHttpToken(socket.handshake.headers)
            if (!verifyApiToken(authToken)) {
                return next(new Error("Unauthorized"))
            }
        }

        next()
    })
    io.on("connection", connected)
}

function connected(socket: Socket) {
    const rawName = socket.handshake.auth?.deviceName ?? socket.handshake.headers["x-device-name"]
    const clientInfo: ConnectedMobileClient & { socket: Socket } = {
        id: socket.id,
        deviceName: sanitizeClientLabel(rawName),
        ip: normalizeRemoteIp(socket.handshake.address),
        connectedAt: Date.now(),
        lastSeenAt: Date.now(),
        socket
    }
    connectedClients.set(socket.id, clientInfo)
    notifyClientsUpdated()

    log(`Client connected (${clientInfo.deviceName} @ ${clientInfo.ip}).`)
    sendToMain(ToMain.WEBSOCKET, "connected")

    socket.on("data", async (data: string | Record<string, any>) => {
        const tracked = connectedClients.get(socket.id)
        if (tracked) tracked.lastSeenAt = Date.now()

        let parsedData: any
        try {
            if (typeof data === "string") {
                parsedData = data ? JSON.parse(data) : {}
            } else if (data && typeof data === "object" && !Array.isArray(data)) {
                parsedData = data
            } else {
                parsedData = {}
            }
        } catch (err) {
            console.error("Could not parse socket data\n", err)
            return
        }

        if (!parsedData || typeof parsedData !== "object" || Array.isArray(parsedData)) return

        let returnData
        if (parsedData.isVariable) {
            returnData = { isVariable: true, values: (await requestToMain(ToMain.GET_DYNAMIC_VALUES, parsedData.keys || [])) || {} }
        } else {
            returnData = await receivedData(parsedData, log)
        }
        if (!returnData) return

        safeEmit("data", returnData)
    })

    const apiDataHandler = (_e: any, msg: any) => safeEmit("data", msg)

    ipcMain.on("API_DATA", apiDataHandler)

    socket.on("disconnect", () => {
        log(`Client disconnected (${clientInfo.deviceName}).`)
        connectedClients.delete(socket.id)
        notifyClientsUpdated()

        try {
            ipcMain.removeListener("API_DATA", apiDataHandler)
        } catch (err) {
            // ignore
        }
    })

    function safeEmit(event: string, payload: any) {
        try {
            if (socket && (socket as any).connected !== false) socket.emit(event, payload)
        } catch (err) {
            console.error(`Error emitting ${event} to socket:`, err)
        }
    }

    function log(msg: string, isError = false) {
        console.info(`WebSocket: ${msg}`)
        if (isError) safeEmit("error", msg)
    }
}

// REST

let restHandlersInitialized = false
function startRestListener(PORT: number, host: string) {
    const server = (servers.REST = app.listen(PORT, host, () => {
        console.info(`REST: Listening for data at ${host}:${PORT.toString()}`)
    }))

    server.once("error", (err: any) => {
        if (err.code === "EADDRINUSE") server.close()
    })

    if (restHandlersInitialized) return
    restHandlersInitialized = true

    const jsonBodyParser = express.json({ limit: "1mb" })

    // Reject browser CSRF drive-by requests before body parsing
    app.use((req: Request, res: Response, next: NextFunction) => {
        if (req.headers.origin !== undefined) {
            res.status(403).json({ error: "Cross-origin browser requests are not permitted" })
            return
        }
        next()
    })

    // Zero-token LAN discovery probe (returns non-sensitive host metadata only for private LAN peers)
    app.get("/mobile-discover", (req: Request, res: Response) => {
        const remoteIp = normalizeRemoteIp(req.socket.remoteAddress)
        if (!isPrivateLanIp(remoteIp)) {
            res.status(403).json({ error: "LAN only" })
            return
        }
        if (!currentConfig.allowLan || currentConfig.token.length < MIN_LAN_TOKEN_LENGTH) {
            res.status(404).json({ ok: false })
            return
        }
        const wsPort = activeListenState?.wsPort || DEFAULT_PORTS.WebSocket
        const restPort = activeListenState?.restPort || DEFAULT_PORTS.REST
        res.json({
            ok: true,
            app: "aradhanastudio",
            name: os.hostname() || "Aradhana Studio PC",
            wsPort,
            restPort,
            pinEnabled: true
        })
    })

    // Rate-limited 6-digit PIN pairing exchange (private LAN only, non-browser JSON POST only)
    app.post("/mobile-pair-pin", jsonBodyParser, (req: Request, res: Response) => {
        const remoteIp = normalizeRemoteIp(req.socket.remoteAddress)
        if (!isPrivateLanIp(remoteIp)) {
            res.status(403).json({ error: "LAN only" })
            return
        }
        if (!req.is("application/json")) {
            res.status(415).json({ error: "Content-Type must be application/json" })
            return
        }
        if (!currentConfig.allowLan || currentConfig.token.length < MIN_LAN_TOKEN_LENGTH) {
            res.status(403).json({ error: "Mobile Remote is disabled on PC" })
            return
        }

        const now = Date.now()
        globalPinFailures = globalPinFailures.filter((ts) => now - ts < PIN_WINDOW_MS)
        const ipList = (pinFailuresByIp.get(remoteIp) || []).filter((ts) => now - ts < PIN_WINDOW_MS)
        if (ipList.length >= PIN_MAX_FAILURES || globalPinFailures.length >= PIN_MAX_FAILURES * 2) {
            pinFailuresByIp.set(remoteIp, ipList)
            res.status(429).json({ error: "Too many PIN attempts. Wait 60 seconds or scan QR code." })
            return
        }

        const providedPin = typeof req.body?.pin === "string" ? req.body.pin.replace(/\s+/g, "").trim() : ""
        const expectedPin = deriveMobilePairingPin(currentConfig.token)
        const isPinValid =
            /^\d{6}$/.test(providedPin) &&
            expectedPin.length === 6 &&
            crypto.timingSafeEqual(Buffer.from(providedPin, "utf8"), Buffer.from(expectedPin, "utf8"))

        if (!isPinValid) {
            ipList.push(now)
            pinFailuresByIp.set(remoteIp, ipList)
            globalPinFailures.push(now)
            res.status(401).json({ error: "Invalid 6-digit pairing code" })
            return
        }

        pinFailuresByIp.delete(remoteIp)
        const wsPort = activeListenState?.wsPort || DEFAULT_PORTS.WebSocket
        const restPort = activeListenState?.restPort || DEFAULT_PORTS.REST
        const lanIps = getLocalIPs().filter((addr) => addr && addr !== "localhost" && addr !== "127.0.0.1")

        res.json({
            ok: true,
            app: "aradhanastudio-remote",
            name: os.hostname() || "Aradhana Studio PC",
            ips: lanIps,
            ws: wsPort,
            rest: restPort,
            token: currentConfig.token
        })
    })

    app.use((req: Request, res: Response, next: NextFunction) => {
        if (req.method === "POST" && !req.is("application/json")) {
            res.status(415).json({ error: "Content-Type must be application/json" })
            return
        }

        if (currentConfig.token && !verifyApiToken(extractHttpToken(req.headers))) {
            res.status(401).json({ error: "Unauthorized" })
            return
        }

        next()
    })

    app.use(jsonBodyParser)

    // Handle malformed JSON request bodies cleanly
    app.use((err: any, _req: Request, res: Response, next: NextFunction) => {
        if (err instanceof SyntaxError && "body" in err) {
            res.status(400).json({ error: "Invalid JSON request body" })
            return
        }
        next(err)
    })

    app.post("/", async (req: Request, res: Response) => {
        // {action: ACTION_ID, ...{}}
        let data: any = req.body && typeof req.body === "object" && !Array.isArray(req.body) ? req.body : {}

        // ?action=ACTION_ID&data={} (only allowed when Content-Type: application/json is verified above)
        if (!data.action && typeof req.query.action === "string" && req.query.action) {
            let parsedQueryData: any = {}
            if (req.query.data !== undefined) {
                if (typeof req.query.data !== "string") {
                    res.status(400).json({ error: "Invalid query data parameter" })
                    return
                }
                try {
                    parsedQueryData = JSON.parse(req.query.data || "{}")
                } catch {
                    res.status(400).json({ error: "Invalid JSON in query data parameter" })
                    return
                }
                if (!parsedQueryData || typeof parsedQueryData !== "object" || Array.isArray(parsedQueryData)) {
                    res.status(400).json({ error: "Query data parameter must be a JSON object" })
                    return
                }
            }
            data = { action: req.query.action, ...parsedQueryData }
        }

        if (typeof data?.action === "string" && (data.action.startsWith("get_mobile_") || data.action.startsWith("mobile_"))) {
            trackRestMobileClient(normalizeRemoteIp(req.socket.remoteAddress), req.headers["x-device-name"])
        }

        const returnData = await receivedData(data, (msg: string) => console.info(`REST: ${msg}`))
        if (!returnData) {
            res.status(204).send()
            return
        }

        res.json(returnData)
    })
}

function startMobileDiscoveryUdp(udpPort: number, wsPort: number) {
    stopMobileDiscoveryUdp()
    try {
        const sock = dgram.createSocket({ type: "udp4", reuseAddr: true })
        sock.on("error", (err) => {
            console.warn("Discovery UDP error:", err.message)
            stopMobileDiscoveryUdp()
        })
        sock.on("message", (msg, rinfo) => {
            if (!currentConfig.allowLan || currentConfig.token.length < MIN_LAN_TOKEN_LENGTH) return
            if (!isPrivateLanIp(rinfo.address)) return
            const text = msg.toString("utf8").trim()
            if (!text.includes("aradhanastudio") && !text.includes("hamrochurch")) return

            const lanIps = getLocalIPs().filter((addr) => addr && addr !== "localhost" && addr !== "127.0.0.1")
            const reply = JSON.stringify({
                ok: true,
                app: "aradhanastudio",
                name: os.hostname() || "Aradhana Studio PC",
                ips: lanIps,
                wsPort,
                restPort: udpPort,
                pinEnabled: true
            })
            sock.send(Buffer.from(reply, "utf8"), rinfo.port, rinfo.address)
        })
        sock.bind(udpPort, "0.0.0.0")
        discoveryUdpSocket = sock
    } catch (err) {
        console.warn("Discovery UDP failed to bind:", err instanceof Error ? err.message : String(err))
    }
}

function stopMobileDiscoveryUdp() {
    if (!discoveryUdpSocket) return
    try {
        discoveryUdpSocket.close()
    } catch {
        // ignore
    }
    discoveryUdpSocket = null
}

// Open Sound Control

function startOSC(PORT: number, host: string) {
    const osc = (servers.OSC = new OSC({ plugin: new OSC.DatagramPlugin() })) // UDP

    osc.on("/freeshow/*", async (msg: OSC.Message) => {
        let args: any = {}
        try {
            const data = msg.args[0]
            args = data && typeof data === "string" ? JSON.parse(data) : {}
        } catch (err) {
            console.error("OSC: Could not parse JSON!\n", err)
            return
        }

        if (!args || typeof args !== "object" || Array.isArray(args)) {
            args = {}
        }

        if (currentConfig.token) {
            if (!verifyApiToken(args.token)) {
                console.warn("OSC: Rejected unauthorized message")
                return
            }
            delete args.token
        }

        const action = msg.address.replace("/freeshow", "")
        const returnData = await receivedData({ action, ...args }, (a: string) => console.info(`OSC: ${a}`))
        if (!returnData) return

        const message = new OSC.Message(msg.address, returnData)
        osc.send(message)
    })

    osc.on("open", () => {
        console.info(`OSC: Listening for data at ${host}:${PORT.toString()}`)
    })
    osc.on("error", (err: any) => {
        console.info(`OSC: Error. ${JSON.stringify(err)}`)
    })

    osc.open({ port: PORT, host })
}

// let OSC_SENDER: null | OSC = null // must work with different ip:port
export function emitOSC(msg: { signal: any; data: string }) {
    if (typeof msg.data !== "string") return

    const port: number = msg.signal?.port || 8080
    const host: string = msg.signal?.host || "127.0.0.1"

    // @ts-ignore
    const OSC_SENDER = new OSC({ plugin: new OSC.DatagramPlugin({ send: { host, port } }) })

    OSC_SENDER.on("open", sendMessage)
    OSC_SENDER.open()

    OSC_SENDER.on("error", (err: any) => {
        console.error("OSC EMIT ERROR:", err)
        OSC_SENDER.close()
    })

    function sendMessage() {
        if (!OSC_SENDER) return

        const data = msg.data.split(" ")
        const address = data.shift() || ""
        const message = new OSC.Message(address, ...data.map(convertToType))
        console.info(`Emitting OSC at ${host}:${String(port)}:`, message.address)

        OSC_SENDER.send(message)
        // ensure message is sent
        setTimeout(() => {
            try {
                OSC_SENDER.close()
            } catch (err) {
                // already closed
            }
        }, 100)
    }
}

function convertToType(value: string) {
    if (value === "true") return true
    if (value === "false") return false
    if (value === "") return value
    const num = Number(value)
    if (!isNaN(num)) return num
    return value
}

// DATA

async function receivedData(data: any = {}, log: (...msg: any[]) => void): Promise<any> {
    if (!data?.action || typeof data.action !== "string") return log("Received message from client, but missing 'action' key.", true)
    log(`Received action ${String(data.action)}`)

    const clientRequestId = typeof data.requestId === "string" ? data.requestId.slice(0, 64) : undefined
    const expectsReturn = data.action.startsWith("get_") || data.action.startsWith("mobile_") || data.expectReturn === true
    if (expectsReturn) data.returnId = uid(6)

    sendToMain(ToMain.API_TRIGGER2, data)

    if (!data.returnId) return

    const returnId = data.returnId
    const returnData = await waitUntilValueIsDefined(() => returnDataObj[returnId], 25, 10000)
    delete returnDataObj[returnId]

    if (returnData === undefined) {
        return { action: data.action, ...(clientRequestId ? { requestId: clientRequestId } : {}), error: "Request timed out" }
    }

    if (clientRequestId && returnData && typeof returnData === "object" && !Array.isArray(returnData)) {
        ;(returnData as Record<string, any>).requestId = clientRequestId
    }

    log(`Sending data ${String(data.action)}`)
    return returnData
}

const returnDataObj: { [key: string]: any } = {}
export function apiReturnData(data: any) {
    const id = data.returnId
    if (!id) return

    delete data.returnId
    returnDataObj[id] = data
}

// CLOSE

export function stopApiListener(specificId = "") {
    try {
        ipcMain.removeAllListeners("API_DATA")
    } catch (err) {
        // ignore
    }

    if (!specificId || specificId === "WebSocket") {
        stopMobileDiscoveryUdp()
        connectedClients.clear()
        restClients.clear()
        notifyClientsUpdated()

        if (ioServer) {
            try {
                ioServer.disconnectSockets(true)
                ioServer.close()
            } catch {
                // ignore
            }
            ioServer = null
        }
    }

    if (!specificId) {
        activeListenState = null
    }

    if (specificId) {
        stop(specificId)
    } else {
        Object.keys(servers).forEach(stop)
    }

    function stop(id: string) {
        console.info(`${id}: Stopping server.`)

        try {
            if (servers[id]) {
                ;(servers[id] as any).closeAllConnections?.()
                servers[id].close()
                delete servers[id]
            }
        } catch (err) {
            console.error(`${id}: Error closing server:`, err)
        }
    }
}
