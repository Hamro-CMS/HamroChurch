import { app, shell } from "electron"
import axios from "axios"
import { ToMain } from "../../types/IPC/ToMain"
import type { SoftwarePromotionPayload, SoftwarePromotionTransitionStyle } from "../../types/Main"
import { config } from "../data/store"
import { sendToMain } from "../IPC/main"

const DEFAULT_PROMOTION_URL = "https://hamrocms.com/api/v1/software/promotion"
const INITIAL_DELAY_MS = 2800
const POLL_INTERVAL_MS = 45 * 1000
const MAX_MEDIA_BYTES = 4 * 1024 * 1024

const VALID_TRANSITION_STYLES: SoftwarePromotionTransitionStyle[] = ["auto_mix", "flip_y", "flip_x", "flip_diagonal", "swing_3d"]

function resolvePromotionEndpointUrl(): string {
    if (app.isPackaged) {
        return DEFAULT_PROMOTION_URL
    }

    const custom = String(process.env.HAMRO_SOFTWARE_PROMOTION_URL || "").trim()
    return custom || DEFAULT_PROMOTION_URL
}

function sanitizeSafePromotionUrl(raw: unknown, fallback: string | null = null): string | null {
    if (typeof raw !== "string" || !raw.trim()) return fallback

    try {
        const parsed = new URL(raw.trim())
        if (parsed.protocol === "https:") {
            return parsed.toString()
        }
        if (!app.isPackaged && parsed.protocol === "http:") {
            return parsed.toString()
        }
    } catch {
        // Ignore malformed URLs
    }

    return fallback
}

let initialTimer: NodeJS.Timeout | null = null
let pollTimer: NodeJS.Timeout | null = null
let currentPromotions: SoftwarePromotionPayload[] = []
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
        if (keys.length > 120) {
            const trimmed: { [key: string]: string } = {}
            for (const key of keys.slice(-90)) {
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

function isCampaignDismissed(promo: SoftwarePromotionPayload, storedMap?: { [key: string]: string }): boolean {
    const key = promo.campaign_key
    if (!key) return false

    if (sessionDismissedKeys.has(key)) {
        return true
    }

    const map = storedMap || readDismissedMap()
    const stored = map[key]
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

    const rawDelay = Number(raw.delay_seconds)
    const delaySeconds = Number.isFinite(rawDelay) && rawDelay >= 0 ? Math.min(86400, Math.floor(rawDelay)) : 0

    const rawRotate = Number(raw.rotate_interval_seconds)
    const rotateIntervalSeconds = Number.isFinite(rawRotate) && rawRotate >= 3 ? Math.min(300, Math.floor(rawRotate)) : 8

    const transitionStyle: SoftwarePromotionTransitionStyle = VALID_TRANSITION_STYLES.includes(raw.transition_style) ? raw.transition_style : "auto_mix"

    const sortOrder = Number.isFinite(Number(raw.sort_order)) ? Math.floor(Number(raw.sort_order)) : 0

    const imageUrl = sanitizeSafePromotionUrl(raw.image_url, null)
    const ctaUrl = sanitizeSafePromotionUrl(raw.cta_url, "https://hamrocms.com") || "https://hamrocms.com"
    const eventUrl = sanitizeSafePromotionUrl(raw.event_url, null)

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
        delay_seconds: delaySeconds,
        rotate_interval_seconds: rotateIntervalSeconds,
        transition_style: transitionStyle,
        sort_order: sortOrder,
        draggable: raw.draggable !== false,
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

        if (mediaDataUriCache.size > 16) {
            mediaDataUriCache.clear()
        }
        mediaDataUriCache.set(promo.image_url, dataUri)
        return dataUri
    } catch {
        return promo.image_url
    }
}

async function recordPromotionMetric(promoId: number, event: "impression" | "click", explicitEventUrl?: string | null) {
    const promotionUrl = resolvePromotionEndpointUrl()
    const fallbackBase = promotionUrl.replace(/\/promotion(?:s\/active)?\/?$/i, "")
    const safeExplicit = sanitizeSafePromotionUrl(explicitEventUrl, null)
    const targetUrl = !app.isPackaged && safeExplicit ? safeExplicit : `${fallbackBase}/promotions/${encodeURIComponent(String(promoId))}/event`

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

function computeListSignature(promos: SoftwarePromotionPayload[]): string {
    if (!promos.length) return "none"
    return promos.map((p) => [p.id, p.campaign_key, p.title, p.body || "", p.image_url || "", p.cta_label || "", p.cta_url || "", p.position, p.dismiss_mode, p.delay_seconds, p.rotate_interval_seconds, p.transition_style, p.sort_order, p.updated_at || ""].join("|")).join("||")
}

function emitPromotionsToMainRenderer(promos: SoftwarePromotionPayload[], force = false) {
    const signature = computeListSignature(promos)
    if (!force && signature === lastEmittedSignature) return
    lastEmittedSignature = signature
    sendToMain(ToMain.SOFTWARE_PROMOTION, promos)
}

export async function ingestPromotionsFromServer(rawInput: any, forceEmit = false): Promise<SoftwarePromotionPayload[]> {
    let rawList: any[] = []
    if (Array.isArray(rawInput)) {
        rawList = rawInput
    } else if (rawInput && typeof rawInput === "object") {
        if (Array.isArray(rawInput.promotions)) {
            rawList = rawInput.promotions
        } else if (rawInput.promotion && typeof rawInput.promotion === "object") {
            rawList = [rawInput.promotion]
        } else if ("id" in rawInput && "title" in rawInput) {
            rawList = [rawInput]
        }
    }

    const storedDismissed = readDismissedMap()
    const seenKeys = new Set<string>()
    const activeList: SoftwarePromotionPayload[] = []

    for (const item of rawList) {
        const parsed = sanitizePromotion(item)
        if (!parsed) continue
        if (seenKeys.has(parsed.campaign_key)) continue
        if (isCampaignDismissed(parsed, storedDismissed)) continue
        seenKeys.add(parsed.campaign_key)
        activeList.push(parsed)
    }

    await Promise.all(
        activeList.map(async (promo) => {
            promo.resolved_image_src = await resolveMediaSource(promo)
        })
    )

    currentPromotions = activeList
    emitPromotionsToMainRenderer(activeList, forceEmit)

    return activeList
}

export async function fetchActiveSoftwarePromotions(forceEmit = false): Promise<SoftwarePromotionPayload[]> {
    try {
        const response = await axios.get(resolvePromotionEndpointUrl(), {
            timeout: 8000,
            headers: {
                Accept: "application/json",
                "Cache-Control": "no-cache",
                "User-Agent": `HamroChurch/${app.getVersion()}`
            }
        })

        return await ingestPromotionsFromServer(response.data, forceEmit)
    } catch {
        // Remain silent when user has no internet connection during worship service
        if (forceEmit) {
            emitPromotionsToMainRenderer(currentPromotions, true)
        }
        return currentPromotions
    }
}

export async function getActiveSoftwarePromotions(): Promise<SoftwarePromotionPayload[]> {
    const storedDismissed = readDismissedMap()
    const filtered = currentPromotions.filter((p) => !isCampaignDismissed(p, storedDismissed))
    if (filtered.length > 0) {
        currentPromotions = filtered
        return filtered
    }
    return await fetchActiveSoftwarePromotions(true)
}

export function recordSoftwarePromotionImpression(data: { id: number; campaignKey: string; eventUrl?: string | null }): boolean {
    const key = String(data?.campaignKey || "").trim()
    const id = Number(data?.id)
    if (!key || !Number.isFinite(id) || id <= 0) return false
    if (recordedImpressions.has(key)) return false

    recordedImpressions.add(key)
    void recordPromotionMetric(id, "impression", data?.eventUrl)
    return true
}

export function dismissSoftwarePromotion(data: {
    campaignKey?: string
    dismissMode?: "once_per_campaign" | "once_per_day" | "every_launch"
    items?: {
        campaignKey: string
        dismissMode: "once_per_campaign" | "once_per_day" | "every_launch"
    }[]
}): boolean {
    const targets: {
        campaignKey: string
        dismissMode: "once_per_campaign" | "once_per_day" | "every_launch"
    }[] = []

    if (Array.isArray(data?.items) && data.items.length > 0) {
        for (const item of data.items) {
            const key = String(item?.campaignKey || "").trim()
            if (key) {
                targets.push({
                    campaignKey: key,
                    dismissMode: item.dismissMode || "once_per_day"
                })
            }
        }
    } else if (data?.campaignKey) {
        const key = String(data.campaignKey).trim()
        if (key) {
            targets.push({
                campaignKey: key,
                dismissMode: data.dismissMode || "once_per_day"
            })
        }
    }

    if (!targets.length) return false

    const map = readDismissedMap()
    let shouldPersist = false
    const dismissedSet = new Set<string>()

    for (const target of targets) {
        sessionDismissedKeys.add(target.campaignKey)
        dismissedSet.add(target.campaignKey)

        if (target.dismissMode === "once_per_campaign" || target.dismissMode === "once_per_day") {
            map[target.campaignKey] = target.dismissMode === "once_per_campaign" ? "forever" : todayLocalStamp()
            shouldPersist = true
        }
    }

    if (shouldPersist) {
        writeDismissedMap(map)
    }

    currentPromotions = currentPromotions.filter((p) => !dismissedSet.has(p.campaign_key))
    emitPromotionsToMainRenderer(currentPromotions, true)

    return true
}

export async function openSoftwarePromotionLink(data: { id: number; url: string; eventUrl?: string | null }): Promise<boolean> {
    const safeUrl = sanitizeSafePromotionUrl(data?.url, null)
    if (!safeUrl) return false

    if (Number.isFinite(Number(data?.id)) && Number(data.id) > 0) {
        void recordPromotionMetric(Number(data.id), "click", data.eventUrl)
    }

    try {
        await shell.openExternal(safeUrl)
        return true
    } catch {
        return false
    }
}

export function startSoftwarePromotions() {
    if (initialTimer || pollTimer) return

    initialTimer = setTimeout(() => {
        initialTimer = null
        void fetchActiveSoftwarePromotions(true)
    }, INITIAL_DELAY_MS)

    pollTimer = setInterval(() => {
        void fetchActiveSoftwarePromotions(false)
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
