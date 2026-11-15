import { app, shell } from "electron"
import axios from "axios"
import { ToMain } from "../../types/IPC/ToMain"
import type { SoftwarePromotionPayload } from "../../types/Main"
import { config } from "../data/store"
import { sendToMain } from "../IPC/main"

const PROMOTION_URL = process.env.HAMRO_SOFTWARE_PROMOTION_URL || "https://hamrocms.com/api/v1/software/promotion"
const INITIAL_DELAY_MS = 3500
const POLL_INTERVAL_MS = 45 * 1000
const MAX_MEDIA_BYTES = 4 * 1024 * 1024

let initialTimer: NodeJS.Timeout | null = null
let pollTimer: NodeJS.Timeout | null = null
let currentPromotion: SoftwarePromotionPayload | null = null
let lastEmittedSignature = "__uninitialized__"

const sessionDismissedKeys = new Set<string>()
const recordedImpressions = new Set<string>()
const mediaDataUriCache = new Map<string, string>()

function todayLocalStamp(): string {
    const now = new Date()
    const y = now.getFullYear()
    const m = String(now.getMonth() + 1).padStart(2, "0")
    const d = String(now.getDate()).padStart(2, "0")
    return `${y}-${m}-${d}`
}

function readDismissedMap(): { [key: string]: string } {
    try {
        const raw = config.get("dismissed_promotions")
        if (raw && typeof raw === "object" && !Array.isArray(raw)) {
            return { ...raw }
        }
    } catch {
        // Ignore read errors
    }
    return {}
}

function writeDismissedMap(map: { [key: string]: string }) {
    try {
        const keys = Object.keys(map)
        if (keys.length > 100) {
            const trimmed: { [key: string]: string } = {}
            for (const key of keys.slice(-80)) {
                trimmed[key] = map[key]
            }
            config.set("dismissed_promotions", trimmed)
            return
        }
        config.set("dismissed_promotions", map)
    } catch {
        // Ignore persistence errors
    }
}

function isCampaignDismissed(promo: SoftwarePromotionPayload): boolean {
    const key = promo.campaign_key
    if (!key) return false

    if (sessionDismissedKeys.has(key)) {
        return true
    }

    const stored = readDismissedMap()[key]
    if (!stored) return false

    if (promo.dismiss_mode === "once_per_campaign") {
        return stored === "forever" || stored.length > 0
    }

    if (promo.dismiss_mode === "once_per_day") {
        return stored === todayLocalStamp()
    }

    return false
}

function sanitizePromotion(raw: any): SoftwarePromotionPayload | null {
    if (!raw || typeof raw !== "object") return null

    const id = Number(raw.id)
    const title = typeof raw.title === "string" ? raw.title.trim() : ""
    if (!Number.isFinite(id) || id <= 0 || !title) return null

    const version = Math.max(1, Number(raw.campaign_version) || 1)
    const campaignKey = typeof raw.campaign_key === "string" && raw.campaign_key.trim() ? raw.campaign_key.trim() : `promo-${id}-v${version}`
    const position = raw.position === "bottom_left" ? "bottom_left" : "clock_area"
    const dismissMode = raw.dismiss_mode === "once_per_campaign" || raw.dismiss_mode === "every_launch" ? raw.dismiss_mode : "once_per_day"

    const imageUrl = typeof raw.image_url === "string" && /^https?:\/\//i.test(raw.image_url.trim()) ? raw.image_url.trim() : null
    const ctaUrl = typeof raw.cta_url === "string" && /^https?:\/\//i.test(raw.cta_url.trim()) ? raw.cta_url.trim() : "https://hamrocms.com"
    const eventUrl = typeof raw.event_url === "string" && /^https?:\/\//i.test(raw.event_url.trim()) ? raw.event_url.trim() : null

    return {
        id,
        campaign_key: campaignKey,
        campaign_version: version,
        title,
        body: typeof raw.body === "string" && raw.body.trim() ? raw.body.trim() : null,
        image_url: imageUrl,
        image_mime: typeof raw.image_mime === "string" && raw.image_mime.trim() ? raw.image_mime.trim() : null,
        resolved_image_src: imageUrl,
        cta_label: typeof raw.cta_label === "string" && raw.cta_label.trim() ? raw.cta_label.trim() : null,
        cta_url: ctaUrl,
        position,
        dismiss_mode: dismissMode,
        starts_at: typeof raw.starts_at === "string" ? raw.starts_at : null,
        ends_at: typeof raw.ends_at === "string" ? raw.ends_at : null,
        event_url: eventUrl,
        updated_at: typeof raw.updated_at === "string" ? raw.updated_at : null
    }
}

async function resolveMediaSource(promo: SoftwarePromotionPayload): Promise<string | null> {
    if (!promo.image_url) return null

    const cached = mediaDataUriCache.get(promo.image_url)
    if (cached) return cached

    try {
        const res = await axios.get<ArrayBuffer>(promo.image_url, {
            responseType: "arraybuffer",
            timeout: 10000,
            maxContentLength: MAX_MEDIA_BYTES,
            headers: {
                Accept: "image/gif,image/webp,image/png,image/jpeg,image/*;q=0.8",
                "User-Agent": `HamroChurch/${app.getVersion()}`
            }
        })

        const buf = Buffer.from(res.data)
        if (!buf.length || buf.length > MAX_MEDIA_BYTES) {
            return promo.image_url
        }

        const headerMime = String(res.headers?.["content-type"] || promo.image_mime || "image/png")
            .split(";")[0]
            .trim()
            .toLowerCase()
        const safeMime = ["image/gif", "image/jpeg", "image/png", "image/webp"].includes(headerMime) ? headerMime : "image/png"
        const dataUri = `data:${safeMime};base64,${buf.toString("base64")}`

        if (mediaDataUriCache.size > 8) {
            mediaDataUriCache.clear()
        }
        mediaDataUriCache.set(promo.image_url, dataUri)
        return dataUri
    } catch {
        return promo.image_url
    }
}

async function recordPromotionMetric(promoId: number, event: "impression" | "click", explicitEventUrl?: string | null) {
    const fallbackBase = PROMOTION_URL.replace(/\/promotion(?:s\/active)?\/?$/i, "")
    const targetUrl = explicitEventUrl && /^https?:\/\//i.test(explicitEventUrl) ? explicitEventUrl : `${fallbackBase}/promotions/${encodeURIComponent(String(promoId))}/event`

    try {
        await axios.post(
            targetUrl,
            { event },
            {
                timeout: 6000,
                headers: {
                    Accept: "application/json",
                    "Content-Type": "application/json",
                    "User-Agent": `HamroChurch/${app.getVersion()}`
                }
            }
        )
    } catch {
        // Silent when offline
    }
}

function computeSignature(promo: SoftwarePromotionPayload | null): string {
    if (!promo) return "none"
    return [promo.id, promo.campaign_key, promo.title, promo.body || "", promo.image_url || "", promo.cta_label || "", promo.cta_url || "", promo.position, promo.dismiss_mode, promo.updated_at || ""].join("|")
}

function emitPromotionToMainRenderer(promo: SoftwarePromotionPayload | null, force = false) {
    const signature = computeSignature(promo)
    if (!force && signature === lastEmittedSignature) return
    lastEmittedSignature = signature
    sendToMain(ToMain.SOFTWARE_PROMOTION, promo)
}

export async function ingestPromotionFromServer(rawPromotion: any, forceEmit = false): Promise<SoftwarePromotionPayload | null> {
    const parsed = sanitizePromotion(rawPromotion)

    if (!parsed || isCampaignDismissed(parsed)) {
        currentPromotion = null
        emitPromotionToMainRenderer(null, forceEmit)
        return null
    }

    parsed.resolved_image_src = await resolveMediaSource(parsed)
    currentPromotion = parsed
    emitPromotionToMainRenderer(parsed, forceEmit)

    if (!recordedImpressions.has(parsed.campaign_key)) {
        recordedImpressions.add(parsed.campaign_key)
        void recordPromotionMetric(parsed.id, "impression", parsed.event_url)
    }

    return parsed
}

export async function fetchActiveSoftwarePromotion(forceEmit = false): Promise<SoftwarePromotionPayload | null> {
    try {
        const response = await axios.get(PROMOTION_URL, {
            timeout: 8000,
            headers: {
                Accept: "application/json",
                "Cache-Control": "no-cache",
                "User-Agent": `HamroChurch/${app.getVersion()}`
            }
        })

        return await ingestPromotionFromServer(response.data?.promotion ?? null, forceEmit)
    } catch {
        // Remain silent when user has no internet connection during worship service
        if (forceEmit) {
            emitPromotionToMainRenderer(currentPromotion, true)
        }
        return currentPromotion
    }
}

export async function getActiveSoftwarePromotion(): Promise<SoftwarePromotionPayload | null> {
    if (currentPromotion && !isCampaignDismissed(currentPromotion)) {
        return currentPromotion
    }
    return await fetchActiveSoftwarePromotion(true)
}

export function dismissSoftwarePromotion(data: { campaignKey: string; dismissMode: "once_per_campaign" | "once_per_day" | "every_launch" }): boolean {
    const key = String(data?.campaignKey || "").trim()
    if (!key) return false

    sessionDismissedKeys.add(key)

    if (data.dismissMode === "once_per_campaign" || data.dismissMode === "once_per_day") {
        const map = readDismissedMap()
        map[key] = data.dismissMode === "once_per_campaign" ? "forever" : todayLocalStamp()
        writeDismissedMap(map)
    }

    if (currentPromotion?.campaign_key === key) {
        currentPromotion = null
        emitPromotionToMainRenderer(null, true)
    }

    return true
}

export async function openSoftwarePromotionLink(data: { id: number; url: string; eventUrl?: string | null }): Promise<boolean> {
    const rawUrl = String(data?.url || "").trim()
    if (!/^https?:\/\//i.test(rawUrl)) return false

    if (Number.isFinite(Number(data?.id)) && Number(data.id) > 0) {
        void recordPromotionMetric(Number(data.id), "click", data.eventUrl)
    }

    try {
        await shell.openExternal(rawUrl)
        return true
    } catch {
        return false
    }
}

export function startSoftwarePromotions() {
    if (initialTimer || pollTimer) return

    initialTimer = setTimeout(() => {
        initialTimer = null
        void fetchActiveSoftwarePromotion(true)
    }, INITIAL_DELAY_MS)

    pollTimer = setInterval(() => {
        void fetchActiveSoftwarePromotion(false)
    }, POLL_INTERVAL_MS)
}

export function stopSoftwarePromotions() {
    if (initialTimer) {
        clearTimeout(initialTimer)
        initialTimer = null
    }
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
}
