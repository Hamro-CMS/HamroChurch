import { createHash, randomUUID } from "crypto"
import fs from "fs"
import os from "os"
import path from "path"
import { app } from "electron"
import axios from "axios"

const HEARTBEAT_URL = process.env.HAMRO_SOFTWARE_HEARTBEAT_URL || "https://hamrocms.com/api/v1/software/heartbeat"
const HEARTBEAT_INTERVAL_MS = 60 * 1000
const MACHINE_ID_FILE = ".hamro-machine-id"

let heartbeatTimer: NodeJS.Timeout | null = null
let machineIdCache = ""

function getMachineIdFilePath() {
    return path.join(app.getPath("userData"), MACHINE_ID_FILE)
}

function normalizePlatform() {
    if (process.platform === "win32") return "windows"
    if (process.platform === "darwin") return "mac"
    return "linux"
}

function persistMachineId(machineId: string) {
    try {
        fs.mkdirSync(app.getPath("userData"), { recursive: true })
        fs.writeFileSync(getMachineIdFilePath(), machineId, "utf8")
    } catch (err) {
        console.warn("Failed to persist machine id:", err)
    }
}

function resolveMachineId() {
    if (machineIdCache) return machineIdCache

    try {
        const filePath = getMachineIdFilePath()

        if (fs.existsSync(filePath)) {
            const stored = fs.readFileSync(filePath, "utf8").trim()
            if (stored.length >= 8) {
                machineIdCache = stored
                return machineIdCache
            }
        }
    } catch (err) {
        console.warn("Failed reading machine id:", err)
    }

    const generated = typeof randomUUID === "function"
        ? randomUUID()
        : createHash("sha256")
              .update(`${os.hostname()}|${process.platform}|${process.arch}|${Date.now().toString()}`)
              .digest("hex")

    machineIdCache = generated
    persistMachineId(generated)

    return machineIdCache
}

function timezoneName() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || null
    } catch {
        return null
    }
}

function buildPayload() {
    return {
        machine_id: resolveMachineId(),
        platform: normalizePlatform(),
        app_version: app.getVersion(),
        device_name: os.hostname(),
        locale: app.getLocale(),
        timezone: timezoneName(),
    }
}

async function sendHeartbeat() {
    try {
        await axios.post(HEARTBEAT_URL, buildPayload(), {
            timeout: 8000,
            headers: {
                Accept: "application/json",
                "User-Agent": `HamroChurch/${app.getVersion()}`,
            },
        })
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err)
        console.warn(`Software heartbeat failed: ${message}`)
    }
}

export function startSoftwareHeartbeat() {
    if (heartbeatTimer) return

    void sendHeartbeat()

    heartbeatTimer = setInterval(() => {
        void sendHeartbeat()
    }, HEARTBEAT_INTERVAL_MS)
}

export function stopSoftwareHeartbeat() {
    if (!heartbeatTimer) return

    clearInterval(heartbeatTimer)
    heartbeatTimer = null
}
