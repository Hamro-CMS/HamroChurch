import { app } from "electron"
import axios from "axios"
import fs from "fs"
import path from "path"
import { ToMain } from "../../types/IPC/ToMain"
import type { StutiMediaFeedPayload, StutiMediaSong } from "../../types/Main"
import { sendToMain } from "../IPC/main"
import { syncStutiSongsIntoHymnsFile } from "./stutiHymnSync"

const DEFAULT_STUTI_FEED_URL = "https://hamrocms.com/api/v1/software/stuti-media"
const INITIAL_DELAY_MS = 3500
const POLL_INTERVAL_MS = 30 * 60 * 1000
const VALID_VIDEO_ID = /^[A-Za-z0-9_-]{11}$/

const DEFAULT_CHANNEL = {
    id: "UCV5kuMrCz2CsIochHVXc23A",
    name: "Stuti Media",
    handle: "@stutimedianepal",
    url: "https://www.youtube.com/@stutimedianepal",
    subscribe_url: "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"
}

const VERIFIED_SEED_SONGS: StutiMediaSong[] = [
    {
        video_id: "oY3o5rQwDNw",
        title: "तृषित छ मेरो हृदय (Trishit Cha Mero Hriday) | New Nepali Christian Worship Song | Stuti Media",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/oY3o5rQwDNw/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/oY3o5rQwDNw/hqdefault.jpg",
        duration_text: "2:58",
        duration_seconds: 178,
        published_at: "2026-11-08T12:43:13+00:00",
        watch_url: "https://www.youtube.com/watch?v=oY3o5rQwDNw",
        is_featured: true
    },
    {
        video_id: "n-ucZ9MnBVU",
        title: "जानुछ एकदिन छोडेर सबै प्रभुको घरमा | Heart Touching Nepali Gospel Song (Official)",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/n-ucZ9MnBVU/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/n-ucZ9MnBVU/hqdefault.jpg",
        published_at: "2026-09-28T21:40:47+00:00",
        watch_url: "https://www.youtube.com/watch?v=n-ucZ9MnBVU"
    },
    {
        video_id: "NevtLjPyNas",
        title: "Jaya Hos (जय होस) - स्तुति मिडिया",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/NevtLjPyNas/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/NevtLjPyNas/hqdefault.jpg",
        published_at: "2026-09-07T18:20:44+00:00",
        watch_url: "https://www.youtube.com/watch?v=NevtLjPyNas"
    },
    {
        video_id: "74zKqYpDYzo",
        title: "Yo Kasto Prem (यो कस्तो प्रेम) - स्तुति मिडिया Nepali Christian Worship Song",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/74zKqYpDYzo/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/74zKqYpDYzo/hqdefault.jpg",
        published_at: "2026-09-07T18:10:40+00:00",
        watch_url: "https://www.youtube.com/watch?v=74zKqYpDYzo"
    },
    {
        video_id: "mnB2lZo74hE",
        title: "Asal Mitra (असल मित्र) - स्तुति मिडिया",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/mnB2lZo74hE/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/mnB2lZo74hE/hqdefault.jpg",
        published_at: "2026-09-01T06:34:58+00:00",
        watch_url: "https://www.youtube.com/watch?v=mnB2lZo74hE"
    },
    {
        video_id: "4kbT_Ku_aa8",
        title: "म अघि बढ्छु (Ma Aghi Badhchhu) - Nepali Christian Worship Song",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/4kbT_Ku_aa8/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/4kbT_Ku_aa8/hqdefault.jpg",
        published_at: "2026-08-28T17:14:45+00:00",
        watch_url: "https://www.youtube.com/watch?v=4kbT_Ku_aa8"
    },
    {
        video_id: "SaYYmc2fBA0",
        title: "धन्यवाद छ प्रभु",
        video_type: "video",
        thumbnail_url: "https://i.ytimg.com/vi/SaYYmc2fBA0/hqdefault.jpg",
        thumbnail_hd_url: "https://i.ytimg.com/vi/SaYYmc2fBA0/hqdefault.jpg",
        published_at: "2026-08-27T14:22:26+00:00",
        watch_url: "https://www.youtube.com/watch?v=SaYYmc2fBA0"
    }
]

let initialTimer: NodeJS.Timeout | null = null
let pollTimer: NodeJS.Timeout | null = null
let currentFeed: StutiMediaFeedPayload | null = null

function sanitizePlain(raw: unknown, maxLength: number): string {
    if (typeof raw !== "string") return ""
    return raw
        .replace(/<[^>]*>/g, "")
        .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, "")
        .replace(/[<>]/g, "")
        .trim()
        .slice(0, maxLength)
}

function sanitizeHttpsUrl(raw: unknown, fallback: string): string {
    if (typeof raw !== "string" || !raw.trim()) return fallback
    try {
        const parsed = new URL(raw.trim())
        if (parsed.protocol === "https:") return parsed.toString()
        if (!app.isPackaged && parsed.protocol === "http:") return parsed.toString()
    } catch {
        // ignore invalid url
    }
    return fallback
}

function resolveFeedEndpointUrl(forceFresh = false): string {
    const base = app.isPackaged ? DEFAULT_STUTI_FEED_URL : String(process.env.HAMRO_STUTI_MEDIA_URL || "").trim() || DEFAULT_STUTI_FEED_URL
    return forceFresh ? `${base}${base.includes("?") ? "&" : "?"}refresh=1` : base
}

function getCacheFilePath(): string {
    return path.join(app.getPath("userData"), "stuti-media-cache.json")
}

function readDiskCache(): StutiMediaFeedPayload | null {
    try {
        const filePath = getCacheFilePath()
        if (!fs.existsSync(filePath)) return null
        const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"))
        return sanitizeFeedPayload(parsed)
    } catch {
        return null
    }
}

function writeDiskCache(feed: StutiMediaFeedPayload): void {
    try {
        fs.writeFileSync(getCacheFilePath(), JSON.stringify(feed), "utf8")
    } catch {
        // non-fatal disk cache write failure
    }
}

function buildSeedFeed(): StutiMediaFeedPayload {
    return {
        channel: { ...DEFAULT_CHANNEL },
        videos: VERIFIED_SEED_SONGS,
        shorts: [],
        updated_at: new Date().toISOString()
    }
}

function sanitizeSongList(rawList: unknown, fallbackType: "video" | "short"): StutiMediaSong[] {
    if (!Array.isArray(rawList)) return []
    const out: StutiMediaSong[] = []
    const seen = new Set<string>()

    for (const raw of rawList) {
        if (!raw || typeof raw !== "object") continue
        const videoId = String((raw as any).video_id || "").trim()
        const title = sanitizePlain((raw as any).title, 300)
        if (!VALID_VIDEO_ID.test(videoId) || !title || seen.has(videoId)) continue
        seen.add(videoId)

        const defaultThumb = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`
        const rawType = (raw as any).video_type === "short" ? "short" : (raw as any).video_type === "video" ? "video" : fallbackType

        out.push({
            video_id: videoId,
            title,
            description: sanitizePlain((raw as any).description, 8000) || null,
            video_type: rawType,
            thumbnail_url: sanitizeHttpsUrl((raw as any).thumbnail_url, defaultThumb),
            thumbnail_hd_url: sanitizeHttpsUrl((raw as any).thumbnail_hd_url, defaultThumb),
            duration_text: sanitizePlain((raw as any).duration_text, 24) || null,
            duration_seconds: Number.isFinite(Number((raw as any).duration_seconds)) ? Math.max(0, Math.floor(Number((raw as any).duration_seconds))) : null,
            view_count_text: sanitizePlain((raw as any).view_count_text, 48) || null,
            published_label: sanitizePlain((raw as any).published_label, 48) || null,
            published_at: sanitizePlain((raw as any).published_at, 40) || null,
            watch_url: `https://www.youtube.com/watch?v=${videoId}`,
            is_featured: Boolean((raw as any).is_featured)
        })
    }

    return out
}

function sanitizeFeedPayload(raw: any): StutiMediaFeedPayload | null {
    if (!raw || typeof raw !== "object") return null
    const videos = sanitizeSongList(raw.videos, "video")
    const shorts = sanitizeSongList(raw.shorts, "short")
    if (!videos.length && !shorts.length) return null

    const ch = raw.channel && typeof raw.channel === "object" ? raw.channel : {}
    return {
        channel: {
            id: sanitizePlain(ch.id, 40) || DEFAULT_CHANNEL.id,
            name: sanitizePlain(ch.name, 80) || DEFAULT_CHANNEL.name,
            handle: sanitizePlain(ch.handle, 80) || DEFAULT_CHANNEL.handle,
            url: sanitizeHttpsUrl(ch.url, DEFAULT_CHANNEL.url),
            subscribe_url: sanitizeHttpsUrl(ch.subscribe_url, DEFAULT_CHANNEL.subscribe_url)
        },
        videos,
        shorts,
        updated_at: sanitizePlain(raw.updated_at, 40) || new Date().toISOString()
    }
}

function ensureCurrentFeed(): StutiMediaFeedPayload {
    if (currentFeed) return currentFeed
    currentFeed = readDiskCache() || buildSeedFeed()
    return currentFeed
}

async function fetchFullYouTubeDescription(videoId: string): Promise<string | null> {
    if (!VALID_VIDEO_ID.test(videoId)) return null
    try {
        const res = await axios.get<string>(`https://www.youtube.com/watch?v=${videoId}`, {
            timeout: 7000,
            responseType: "text",
            headers: {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                "Accept-Language": "en-US,en;q=0.9"
            }
        })
        const html = typeof res.data === "string" ? res.data : ""
        const match = html.match(/"shortDescription":"((?:\\.|[^"\\])*)"/)
        if (!match?.[1]) return null
        const decoded = JSON.parse(`"${match[1]}"`)
        const cleaned = sanitizePlain(decoded, 8000)
        return cleaned || null
    } catch {
        return null
    }
}

async function enrichStutiDescriptions(songs: StutiMediaSong[], baseline: StutiMediaFeedPayload): Promise<StutiMediaSong[]> {
    const cachedById = new Map((baseline.videos || []).map((v) => [v.video_id, v]))
    return await Promise.all(
        songs.map(async (song) => {
            const existingDesc = cachedById.get(song.video_id)?.description || ""
            const currentDesc = song.description || ""
            if (existingDesc.length > currentDesc.length && existingDesc.length > 1200) {
                return { ...song, description: existingDesc }
            }
            if (currentDesc.length >= 1150) {
                const fullDesc = await fetchFullYouTubeDescription(song.video_id)
                if (fullDesc && fullDesc.length > currentDesc.length) {
                    return { ...song, description: fullDesc }
                }
            }
            return song
        })
    )
}

export async function fetchStutiMediaFeed(forceFresh = false, emit = true): Promise<StutiMediaFeedPayload> {
    const baseline = ensureCurrentFeed()

    try {
        const res = await axios.get(resolveFeedEndpointUrl(forceFresh), {
            timeout: 10000,
            headers: {
                Accept: "application/json",
                "Cache-Control": forceFresh ? "no-cache" : "max-age=60",
                "User-Agent": `AradhanaStudio/${app.getVersion()}`
            }
        })

        const parsed = sanitizeFeedPayload(res.data)
        if (parsed) {
            parsed.videos = await enrichStutiDescriptions(parsed.videos, baseline)
            currentFeed = parsed
            writeDiskCache(parsed)
            syncStutiSongsIntoHymnsFile(parsed.videos)
            if (emit) sendToMain(ToMain.STUTI_MEDIA_SONGS, parsed)
            return parsed
        }
    } catch {
        // Keep cached / seed feed silently when offline
    }

    syncStutiSongsIntoHymnsFile(baseline.videos)
    if (emit) sendToMain(ToMain.STUTI_MEDIA_SONGS, baseline)
    return baseline
}

export async function getStutiMediaFeed(forceFresh = false): Promise<StutiMediaFeedPayload> {
    const cached = ensureCurrentFeed()
    if (!forceFresh) {
        sendToMain(ToMain.STUTI_MEDIA_SONGS, cached)
    }
    return await fetchStutiMediaFeed(forceFresh, true)
}

export function getCurrentStutiSongs(limit = 5): StutiMediaSong[] {
    return (ensureCurrentFeed().videos || []).slice(0, Math.max(0, limit))
}

export function ingestLatestStutiSongsPreview(rawList: unknown): void {
    const incoming = sanitizeSongList(rawList, "video")
    if (!incoming.length) return

    const baseline = ensureCurrentFeed()
    const existingById = new Map((baseline.videos || []).map((v) => [v.video_id, v]))
    let hasNewOrUpdated = false

    const merged: StutiMediaSong[] = []
    const seen = new Set<string>()
    for (const song of incoming) {
        if (seen.has(song.video_id)) continue
        seen.add(song.video_id)
        if (!existingById.has(song.video_id)) hasNewOrUpdated = true
        merged.push(song)
    }
    for (const oldSong of baseline.videos || []) {
        if (seen.has(oldSong.video_id)) continue
        seen.add(oldSong.video_id)
        merged.push(oldSong)
    }

    if (!hasNewOrUpdated && merged[0]?.video_id === baseline.videos?.[0]?.video_id) return

    currentFeed = {
        ...baseline,
        videos: merged,
        updated_at: new Date().toISOString()
    }
    writeDiskCache(currentFeed)
    sendToMain(ToMain.STUTI_MEDIA_SONGS, currentFeed)
}

export function startStutiMediaSync() {
    if (initialTimer || pollTimer) return

    initialTimer = setTimeout(() => {
        initialTimer = null
        void fetchStutiMediaFeed(true, true)
    }, INITIAL_DELAY_MS)

    pollTimer = setInterval(() => {
        void fetchStutiMediaFeed(true, true)
    }, POLL_INTERVAL_MS)
}

export function stopStutiMediaSync() {
    if (initialTimer) {
        clearTimeout(initialTimer)
        initialTimer = null
    }
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
}
