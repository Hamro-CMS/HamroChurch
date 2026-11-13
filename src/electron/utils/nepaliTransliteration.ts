import { net } from "electron"

// Google Input Tools (Nepali, "ne-t-i0-und") word suggestions for the Google-style Nepali typing.
// Runs in the main process so the renderer is not affected by CORS. Any failure (offline, timeout, API change)
// returns an empty list and the renderer falls back to its offline candidates.

const GOOGLE_INPUT_TOOLS_URL = "https://inputtools.google.com/request"
const TIMEOUT_MS = 2500
const cache = new Map<string, string[]>()
const MAX_CACHE = 2000

export async function fetchNepaliTransliteration(data: { text: string; num?: number }): Promise<string[]> {
    const text = String(data?.text || "").trim()
    if (!text || text.length > 60 || !/^[a-zA-Z]+$/.test(text)) return []

    const num = Math.max(1, Math.min(10, Number(data?.num) || 5))
    const cacheKey = `${text.toLowerCase()}|${num}`
    const cached = cache.get(cacheKey)
    if (cached) return cached

    const params = new URLSearchParams({ text, itc: "ne-t-i0-und", num: String(num), cp: "0", cs: "1", ie: "utf-8", oe: "utf-8" })
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)

    try {
        const response = await net.fetch(`${GOOGLE_INPUT_TOOLS_URL}?${params.toString()}`, { signal: controller.signal })
        if (!response.ok) return []

        const json: any = await response.json()
        if (!Array.isArray(json) || json[0] !== "SUCCESS") return []

        const candidates: string[] = Array.isArray(json[1]?.[0]?.[1]) ? json[1][0][1].map((value: any) => String(value || "").trim()).filter(Boolean) : []
        if (candidates.length) {
            if (cache.size >= MAX_CACHE) cache.delete(cache.keys().next().value as string)
            cache.set(cacheKey, candidates)
        }
        return candidates
    } catch {
        return []
    } finally {
        clearTimeout(timeout)
    }
}
