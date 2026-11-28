import path from "path"
import type { StutiMediaSong } from "../../types/Main"
import { createFolder, getDataFolderRoot, readFile, writeFile } from "./files"

const DEVANAGARI_REGEX = /[\u0900-\u097F]/
const TRAILING_NOISE_LINE = /^(?:#\S+|(?:[A-Za-z0-9_]+\s+)?#[A-Za-z0-9_]+|━+|─+|-+|═+|©|copyright\b|subscribe\b|like\b.*share|follow\s+us|watch\s+more|credits?\b|song\s+credits?\b|प्रस्तुति\s*:|विधा\s*:|गीतको\s+विवरण)/i

function normalizeText(raw: string): string {
    return String(raw || "")
        .replace(/\u00a0/g, " ")
        .replace(/\r\n/g, "\n")
        .replace(/\r/g, "\n")
        .trim()
}

export function normalizeHymnTitleKey(title: string): string {
    return normalizeText(title)
        .toLowerCase()
        .replace(/[()[\]|।,.\-_:;!?"“”‘’/'\u200c\u200d]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
}

export function extractStutiTitleParts(rawTitle: string): { title: string; titleEn: string } {
    let base = normalizeText(rawTitle)
    // Strip pipe-separated channel/SEO suffixes: "Title | Official Lyrical Music Video..."
    if (base.includes("|")) {
        base = base.split("|")[0].trim()
    }
    // Strip dash-separated suffixes like "- स्तुति मिडिया", "- Nepali Christian Worship Song", "- By Stuti Media"
    base = base
        .replace(/\s*[-–—]\s*(?:स्तुति\s*मिडिया|stuti\s*media|by\s+stuti\s*media|new\s+nepali|nepali\s+christian|official|heart\s+touching).*$/i, "")
        .trim()

    // Check "English (नेपाली)" or "नेपाली (English)"
    const parenMatch = base.match(/^(.+?)\s*\(([^()]+)\)\s*$/)
    if (parenMatch) {
        const left = parenMatch[1].trim()
        const right = parenMatch[2].trim()
        const leftHasDeva = DEVANAGARI_REGEX.test(left)
        const rightHasDeva = DEVANAGARI_REGEX.test(right)
        if (!leftHasDeva && rightHasDeva) {
            return { title: right, titleEn: left }
        }
        if (leftHasDeva && !rightHasDeva) {
            return { title: left, titleEn: right }
        }
    }

    return { title: base, titleEn: "" }
}

export function extractStutiLyrics(description: string | null | undefined): { lyrics: string; slides: string[] } | null {
    const text = normalizeText(description || "")
    if (!text) return null

    const lines = text.split("\n")

    // 1. Prefer explicit "Lyrics:" / "FULL LYRICS" / "शब्द / गीत" heading
    let startIndex = -1
    for (let i = 0; i < lines.length; i++) {
        const trimmed = lines[i].trim()
        if (/(?:^|\s|[^\w])(?:full\s+)?lyrics\b|शब्द\s*\/\s*गीत/i.test(trimmed) && trimmed.length <= 70) {
            startIndex = i + 1
            break
        }
    }

    // 2. Fallback: divider line (e.g. "-----------------") followed by Devanagari lines
    if (startIndex < 0) {
        for (let i = 0; i < lines.length; i++) {
            const trimmed = lines[i].trim()
            if (/^[-━─═]{5,}$/.test(trimmed)) {
                const remaining = lines.slice(i + 1).join("\n")
                if ((remaining.match(/[\u0900-\u097F]/g) || []).length >= 40) {
                    startIndex = i + 1
                    break
                }
            }
        }
    }

    if (startIndex < 0) return null

    // Skip leading separator or blank lines right after the heading
    while (startIndex < lines.length) {
        const t = lines[startIndex].trim()
        if (!t || /^[-━─═]{4,}$/.test(t)) {
            startIndex++
        } else {
            break
        }
    }

    const cleanedLines: string[] = []
    for (let i = startIndex; i < lines.length; i++) {
        const rawLine = lines[i].replace(/\s+$/g, "")
        const trimmed = rawLine.trim()

        if (!trimmed) {
            cleanedLines.push("")
            continue
        }

        // Stop when hashtags or footer metadata start
        if (TRAILING_NOISE_LINE.test(trimmed)) {
            break
        }
        // Skip inline hashtag-only clusters
        if ((trimmed.match(/#[A-Za-z0-9_\u0900-\u097F]+/g) || []).length >= 2) {
            break
        }

        cleanedLines.push(trimmed)
    }

    const lyrics = cleanedLines
        .join("\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim()

    if (!lyrics) return null

    const devaChars = (lyrics.match(/[\u0900-\u097F]/g) || []).length
    const nonEmptyLineCount = lyrics.split("\n").filter((l) => l.trim().length > 0).length
    if (devaChars < 40 && nonEmptyLineCount < 4) return null

    const slides = lyrics
        .split(/\n\s*\n/)
        .map((block) => block.trim())
        .filter(Boolean)

    if (!slides.length) return null
    return { lyrics, slides }
}

export function buildStutiHymnRecord(song: StutiMediaSong) {
    const videoId = String(song?.video_id || "").trim()
    if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return null

    const extracted = extractStutiLyrics(song.description)
    if (!extracted) return null

    const { title, titleEn } = extractStutiTitleParts(song.title || "")
    if (!title) return null

    const publishedTs = song.published_at ? Date.parse(song.published_at) : 0
    return {
        sourceKey: `stuti-${videoId}`,
        sourcePage: `https://www.youtube.com/watch?v=${videoId}`,
        title,
        titleEn: titleEn || null,
        category: "New Songs",
        language: "ne",
        sourceRefs: [] as string[],
        lyrics: extracted.lyrics,
        slides: extracted.slides,
        authors: "Stuti Media",
        hasLyrics: true,
        updatedAt: Number.isFinite(publishedTs) && publishedTs > 0 ? publishedTs : Date.now(),
        rawDetails: "Stuti Media (YouTube)"
    }
}

export function syncStutiSongsIntoHymnsFile(songs: StutiMediaSong[]): { added: number; updated: number } {
    if (!Array.isArray(songs) || !songs.length) return { added: 0, updated: 0 }

    try {
        const dataFolder = createFolder(path.join(getDataFolderRoot(), "data"))
        const targetPath = path.join(dataFolder, "nepali_hymns.json")
        const existingContent = readFile(targetPath)
        if (!existingContent) return { added: 0, updated: 0 }

        const payload = JSON.parse(existingContent)
        if (!Array.isArray(payload?.categories)) return { added: 0, updated: 0 }

        let newSongsCategory = payload.categories.find((entry: any) => String(entry?.name || "").toLowerCase() === "new songs")
        if (!newSongsCategory) {
            newSongsCategory = { name: "New Songs", songs: [] }
            payload.categories.push(newSongsCategory)
        }
        if (!Array.isArray(newSongsCategory.songs)) newSongsCategory.songs = []

        const bySourceKey = new Map<string, { category: any; index: number; song: any }>()
        const existingTitleKeys = new Set<string>()
        for (const cat of payload.categories) {
            if (!Array.isArray(cat?.songs)) continue
            cat.songs.forEach((s: any, idx: number) => {
                const sk = String(s?.sourceKey || "").trim()
                if (sk) bySourceKey.set(sk, { category: cat, index: idx, song: s })
                const tk = normalizeHymnTitleKey(String(s?.title || ""))
                if (tk) existingTitleKeys.add(tk)
            })
        }

        let added = 0
        let updated = 0

        for (const song of songs) {
            const record = buildStutiHymnRecord(song)
            if (!record) continue

            const existingEntry = bySourceKey.get(record.sourceKey)
            if (existingEntry) {
                const rawDetails = String(existingEntry.song?.rawDetails || "")
                if (rawDetails.startsWith("Edited")) continue
                const prevLyrics = normalizeText(String(existingEntry.song?.lyrics || ""))
                if (prevLyrics !== record.lyrics || existingEntry.song?.title !== record.title) {
                    existingEntry.category.songs[existingEntry.index] = {
                        ...existingEntry.song,
                        title: record.title,
                        titleEn: record.titleEn || existingEntry.song?.titleEn || null,
                        lyrics: record.lyrics,
                        slides: record.slides,
                        authors: existingEntry.song?.authors || record.authors,
                        updatedAt: Math.max(Number(existingEntry.song?.updatedAt || 0), record.updatedAt)
                    }
                    updated += 1
                }
                continue
            }

            // Avoid duplicating a song the user already added manually with the same Nepali title
            const titleKey = normalizeHymnTitleKey(record.title)
            if (titleKey && existingTitleKeys.has(titleKey)) continue

            newSongsCategory.songs.push(record)
            if (titleKey) existingTitleKeys.add(titleKey)
            bySourceKey.set(record.sourceKey, {
                category: newSongsCategory,
                index: newSongsCategory.songs.length - 1,
                song: record
            })
            added += 1
        }

        if (added > 0 || updated > 0) {
            writeFile(targetPath, JSON.stringify(payload, null, 2))
        }

        return { added, updated }
    } catch (err) {
        console.warn("Could not sync Stuti Media songs into nepali_hymns.json:", err)
        return { added: 0, updated: 0 }
    }
}
