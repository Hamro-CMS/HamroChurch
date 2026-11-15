import crypto from "crypto"
import { ipcMain } from "electron"
import express, { type NextFunction, type Request, type Response } from "express"
import http from "http"
import OSC from "osc-js"
import { Server, type Socket } from "socket.io"
import { uid } from "uid"
import { ToMain } from "../../types/IPC/ToMain"
import { requestToMain, sendToMain } from "../IPC/main"
import { waitUntilValueIsDefined } from "./helpers"

export interface CompanionApiConfig {
    port?: number
    allowLan?: boolean
    token?: string
}

const app = express()
app.disable("x-powered-by")

const servers: { [key: string]: http.Server | OSC } = {}
const DEFAULT_PORTS = { WebSocket: 5505, REST: 5506 }

let currentConfig: { port?: number; allowLan: boolean; token: string } = {
    port: undefined,
    allowLan: false,
    token: ""
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

// WebSocket on 5505, REST on +1 (5506), bound to 127.0.0.1 by default unless allowLan + non-empty token are configured
export function startWebSocketAndRest(config?: number | CompanionApiConfig) {
    stopApiListener()

    if (typeof config === "number" || config === undefined) {
        currentConfig = { port: config, allowLan: false, token: "" }
    } else {
        currentConfig = {
            port: config.port,
            allowLan: config.allowLan === true,
            token: typeof config.token === "string" ? config.token.trim() : ""
        }
    }

    const bindHost = currentConfig.allowLan && currentConfig.token.length > 0 ? "0.0.0.0" : "127.0.0.1"
    const wsPort = currentConfig.port || DEFAULT_PORTS.WebSocket
    const restPort = currentConfig.port ? currentConfig.port + 1 : DEFAULT_PORTS.REST

    startRestListener(restPort, bindHost)
    startWebSocket(wsPort, bindHost)
    startOSC(wsPort, bindHost)
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

    const io = new Server(server)
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
    log("Client connected.")
    sendToMain(ToMain.WEBSOCKET, "connected") // TODO: respond with API_DATA

    socket.on("data", async (data: string) => {
        let parsedData
        try {
            parsedData = data && typeof data === "string" ? JSON.parse(data) : {}
        } catch (err) {
            console.error("Could not parse socket data\n", err)
            return
        }

        if (!parsedData || typeof parsedData !== "object") return

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
        log("Client disconnected.")

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

    // Reject browser CSRF drive-by requests before body parsing
    app.use((req: Request, res: Response, next: NextFunction) => {
        if (req.headers.origin !== undefined) {
            res.status(403).json({ error: "Cross-origin browser requests are not permitted" })
            return
        }

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

    app.use(express.json({ limit: "1mb" }))

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

        const returnData = await receivedData(data, (msg: string) => console.info(`REST: ${msg}`))
        if (!returnData) {
            res.status(204).send()
            return
        }

        res.json(returnData)
    })
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

    const get = data.action.startsWith("get_")
    if (get) data.returnId = uid(5)

    sendToMain(ToMain.API_TRIGGER2, data)

    if (!data.returnId) return

    const returnData = await waitUntilValueIsDefined(() => returnDataObj[data.returnId], 50, 10000)
    delete returnDataObj[data.returnId]
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

    if (specificId) {
        stop(specificId)
    } else {
        Object.keys(servers).forEach(stop)
    }

    function stop(id: string) {
        console.info(`${id}: Stopping server.`)

        try {
            if (servers[id]) {
                servers[id].close()
                delete servers[id]
            }
        } catch (err) {
            console.error(`${id}: Error closing server:`, err)
        }
    }
}
