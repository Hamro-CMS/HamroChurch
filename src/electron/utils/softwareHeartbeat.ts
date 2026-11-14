import { randomUUID } from "crypto"
import fs from "fs"
import path from "path"
import { app } from "electron"
import axios from "axios"
import type { DeleteAnalyticsResult, PrivacySettings } from "../../types/Main"
import { config } from "../data/store"

const HEARTBEAT_URL = process.env.HAMRO_SOFTWARE_HEARTBEAT_URL || "https://hamrocms.com/api/v1/software/heartbeat"
const DELETE_DEVICE_BASE_URL = process.env.HAMRO_ANALYTICS_DELETE_BASE_URL || "https://hamrocms.com/api/analytics/device"
const HEARTBEAT_INTERVAL_MS = 60 * 1000
const INITIAL_START_DELAY_MS = 2500
const LEGACY_MACHINE_ID_FILE = ".hamro-machine-id"
const UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

let initialStartTimer: NodeJS.Timeout | null = null
let heartbeatTimer: NodeJS.Timeout | null = null
let anonymousDeviceIdCache = ""
let hasSentStart = false
let stopped = false

let sessionStartedAt = Date.now()
let sessionStartedIso = new Date(sessionStartedAt).toISOString()
let sessionId: string = randomUUID()

function removeLegacyMachineIdFile() {
    try {
        const legacyPath = path.join(app.getPath("userData"), LEGACY_MACHINE_ID_FILE)
        if (fs.existsSync(legacyPath)) {
            fs.unlinkSync(legacyPath)
        }
    } catch {
        // Ignore cleanup errors
    }
}

function resolveOsPlatform(): "Windows" | "macOS" | "Linux" {
    if (process.platform === "win32") return "Windows"
    if (process.platform === "darwin") return "macOS"
    return "Linux"
}

function resolveArch(): string {
    if (process.arch === "x64" || process.arch === "arm64") return process.arch
    return process.arch || "x64"
}

export function getAnalyticsConsent(): boolean | null {
    try {
        const stored = config.get("analytics_consent")
        if (stored === true) return true
        if (stored === false) return false
        return null
    } catch {
        return null
    }
}

export function getAnonymousDeviceId(): string {
    if (anonymousDeviceIdCache && UUID_V4_REGEX.test(anonymousDeviceIdCache)) {
        return anonymousDeviceIdCache
    }

    try {
        const stored = config.get("anonymous_device_id")
        if (typeof stored === "string" && UUID_V4_REGEX.test(stored.trim())) {
            anonymousDeviceIdCache = stored.trim()
            return anonymousDeviceIdCache
        }
    } catch {
        // Fall through to generate a fresh UUID v4
    }

    const generated = randomUUID()
    anonymousDeviceIdCache = generated
    try {
        config.set("anonymous_device_id", generated)
        config.set("machineId", generated)
    } catch (err) {
        console.warn("Failed to persist anonymous_device_id:", err)
    }

    removeLegacyMachineIdFile()
    return anonymousDeviceIdCache
}

export function getPrivacySettings(): PrivacySettings {
    let consentUpdatedAt: string | null = null
    try {
        const raw = config.get("analytics_consent_updated_at")
        consentUpdatedAt = typeof raw === "string" ? raw : null
    } catch {
        consentUpdatedAt = null
    }

    return {
        consent: getAnalyticsConsent(),
        anonymousDeviceId: getAnonymousDeviceId(),
        consentUpdatedAt
    }
}

type HeartbeatEvent = "start" | "heartbeat" | "stop"

/**
 * Strictly anonymous, Zero-PII payload:
 * Contains ONLY random UUID v4, OS platform, CPU architecture, app version, and session duration.
 * Never includes computer hostnames, usernames, IP addresses, locale, timezone, or church data.
 */
export function buildAnonymousTelemetryPayload(event: HeartbeatEvent) {
    const elapsedSeconds = Math.floor((Date.now() - sessionStartedAt) / 1000)

    return {
        anonymous_device_id: getAnonymousDeviceId(),
        os_platform: resolveOsPlatform(),
        arch: resolveArch(),
        app_version: app.getVersion(),
        session_id: sessionId,
        session_started_at: sessionStartedIso,
        session_duration: Math.max(2, elapsedSeconds),
        event
    }
}

async function sendHeartbeat(event: HeartbeatEvent = "heartbeat", timeout = 8000) {
    // Strict Consent Gate: NEVER send any network ping unless analytics_consent === true
    if (getAnalyticsConsent() !== true) return

    try {
        if (event === "start") hasSentStart = true

        await axios.post(HEARTBEAT_URL, buildAnonymousTelemetryPayload(event), {
            timeout,
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                "User-Agent": `HamroChurch/${app.getVersion()}`
            }
        })
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err)
        console.warn(`Anonymous usage ping skipped: ${message}`)
    }
}

function clearTelemetryTimers() {
    if (initialStartTimer) {
        clearTimeout(initialStartTimer)
        initialStartTimer = null
    }
    if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
    }
}

export function initSoftwareTelemetry() {
    // Ensure local random UUID v4 exists (strictly local — zero network calls)
    getAnonymousDeviceId()

    // Only start background pings if user has explicitly opted in
    if (getAnalyticsConsent() === true) {
        startSoftwareHeartbeat()
    }
}

export function startSoftwareHeartbeat() {
    if (getAnalyticsConsent() !== true) return
    if (heartbeatTimer || initialStartTimer) return

    stopped = false

    // Delay initial ping slightly so active session duration is >= 2s (> 1s bot filter)
    initialStartTimer = setTimeout(() => {
        initialStartTimer = null
        if (getAnalyticsConsent() !== true) return
        void sendHeartbeat("start")
    }, INITIAL_START_DELAY_MS)

    heartbeatTimer = setInterval(() => {
        if (getAnalyticsConsent() !== true) {
            clearTelemetryTimers()
            return
        }
        void sendHeartbeat("heartbeat")
    }, HEARTBEAT_INTERVAL_MS)
}

export function setAnalyticsConsent(consent: boolean): PrivacySettings {
    const normalized = consent === true
    const nowIso = new Date().toISOString()

    try {
        config.set("analytics_consent", normalized)
        config.set("analytics_consent_updated_at", nowIso)
    } catch (err) {
        console.warn("Failed to persist analytics_consent:", err)
    }

    if (normalized) {
        // Ensure elapsed duration is at least 2s if the user just accepted on launch
        if (Date.now() - sessionStartedAt < 2000) {
            sessionStartedAt = Date.now() - 2000
            sessionStartedIso = new Date(sessionStartedAt).toISOString()
        }
        startSoftwareHeartbeat()
    } else {
        // Immediately halt all background pings without sending any network request
        clearTelemetryTimers()
        hasSentStart = false
    }

    return getPrivacySettings()
}

export async function deleteAnalyticsServerData(): Promise<DeleteAnalyticsResult> {
    const currentId = getAnonymousDeviceId()
    const deleteUrl = `${DELETE_DEVICE_BASE_URL.replace(/\/+$/, "")}/${encodeURIComponent(currentId)}`

    try {
        const response = await axios.delete(deleteUrl, {
            timeout: 10000,
            headers: {
                Accept: "application/json",
                "User-Agent": `HamroChurch/${app.getVersion()}`
            }
        })

        // Rotate to a fresh random UUID v4 so future opt-ins cannot correlate with deleted logs
        const rotatedId = randomUUID()
        anonymousDeviceIdCache = rotatedId
        sessionId = randomUUID()
        sessionStartedAt = Date.now()
        sessionStartedIso = new Date(sessionStartedAt).toISOString()
        hasSentStart = false

        try {
            config.set("anonymous_device_id", rotatedId)
            config.set("machineId", rotatedId)
        } catch (err) {
            console.warn("Failed to persist rotated anonymous_device_id:", err)
        }

        return {
            success: true,
            anonymousDeviceId: rotatedId,
            deleted: typeof response.data?.deleted === "number" ? response.data.deleted : 0
        }
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err)
        return {
            success: false,
            anonymousDeviceId: currentId,
            error: message
        }
    }
}

export function stopSoftwareHeartbeat() {
    clearTelemetryTimers()

    if (!stopped) {
        stopped = true
        if (getAnalyticsConsent() === true && hasSentStart) {
            void sendHeartbeat("stop", 2500)
        }
    }
}
