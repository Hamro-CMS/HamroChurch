import { get } from "svelte/store"
import { uid } from "uid"
import { fromNepaliDigits } from "../../../common/nepali"
import { generateLocalCandidates } from "../../../lib/googleNepaliTyping"
import { handleKeyPress } from "../../../lib/nepaliTyping"
import { Main } from "../../../types/IPC/Main"
import type { Item } from "../../../types/Show"
import { clearAudio } from "../../audio/audioFading"
import { AudioPlayer } from "../../audio/audioPlayer"
import { defaultBibleBookNames } from "../../converters/bebliaBible"
import { requestMain, sendMain } from "../../IPC/main"
import {
    activeProject,
    activeScripture,
    activeShow,
    activeTimers,
    customScriptureBooks,
    draw,
    drawerTabsData,
    drawSettings,
    drawTool,
    groups,
    language,
    media,
    mobileRemoteClients,
    mobileSoftwarePromotions,
    mobileStageAlert,
    os,
    outLocked,
    outputDisplay,
    outputs,
    overlays,
    projects,
    scriptures,
    shows,
    showsCache,
    styles,
    timers,
    version
} from "../../stores"
import { createScriptureShow, formatBibleText, loadJsonBible, moveSelection, outputIsScripture, playScripture } from "../drawer/bible/scripture"
import { applyHymnToExistingShow, buildHymnShow, getCategoryCounts, getFilteredHymns, hymnItems, inferSlideGroup, loadHymns, type HymnCategoryId, type HymnRecord } from "../drawer/hymns/hymns"
import { pauseAllTimers } from "../drawer/timers/timers"
import { getTextLines } from "../edit/scripts/textStyle"
import { keysToID, removeDeleted, sortByName } from "../helpers/array"
import { history } from "../helpers/history"
import { getMediaStyle } from "../helpers/media"
import { getFirstActiveOutput, getOutputResolution, setOutput, toggleOutputs } from "../helpers/output"
import { OutputHelper } from "../helpers/OutputHelper"
import { loadShows } from "../helpers/setShow"
import { getLayoutRef, openShow } from "../helpers/show"
import { selectProjectShow, updateOut } from "../helpers/showActions"
import { _show } from "../helpers/shows"
import { startTimerById, stopTimers } from "../helpers/timerTick"
import { clearAll, clearBackground, clearDrawing, clearOverlay, clearOverlays, clearSlide, clearTimers, restoreOutput } from "../output/clear"
import { getClearedState, getTimersDetailed, pauseTimerById, stopTimerById, timerSeekTo } from "./apiHelper"

const MOBILE_ALERT_OVERLAY_ID = "hamro_mobile_alert"

let hymnsLoadPromise: Promise<void> | null = null
async function ensureHymnsLoaded(): Promise<HymnRecord[]> {
    const existing = get(hymnItems)
    if (existing.length > 0) return existing

    if (!hymnsLoadPromise) {
        hymnsLoadPromise = loadHymns().finally(() => {
            hymnsLoadPromise = null
        })
    }
    await hymnsLoadPromise
    return get(hymnItems)
}

function transliterateRomanToDevanagariOffline(input: string): string {
    if (!input) return ""
    let value = ""
    let cursor = 0
    for (const ch of input) {
        const nextVal = value.slice(0, cursor) + ch + value.slice(cursor)
        const nextCur = cursor + ch.length
        const res = handleKeyPress({ inputType: "insertText", data: ch }, nextVal, nextCur)
        if (res) {
            value = res.newValue
            cursor = res.newCursor
        } else {
            value = nextVal
            cursor = nextCur
        }
    }
    // Strip trailing halant if user ended on consonant (e.g. "parmeshwar" -> "परमेश्वर्" vs "परमेश्वर")
    return value.replace(/\u094D$/g, "").trim()
}

function normalizePhoneticRoman(value: string): string {
    return fromNepaliDigits(String(value || ""))
        .toLowerCase()
        .replace(/aa/g, "a")
        .replace(/ee|ii/g, "i")
        .replace(/oo|uu/g, "u")
        .replace(/w/g, "v")
        .replace(/shh/g, "sh")
        .replace(/[^a-z0-9\u0900-\u097f\s]/g, " ")
        .replace(/\s+/g, " ")
        .trim()
}

/**
 * Normalizes common Devanagari vowel-length & sibilant/nasal variations
 * (e.g. येशुले <-> येशूले <-> येसुले, स्तुती <-> स्तुति, विश्वास <-> बिश्वास, तपाईं <-> तपाइँ)
 * so phonetic or typed Nepali queries always match Bible & Bhajan text.
 */
function normalizeDevanagariLoose(value: string): string {
    return fromNepaliDigits(String(value || ""))
        .toLowerCase()
        .replace(/ी/g, "ि")
        .replace(/ई/g, "इ")
        .replace(/ू/g, "ु")
        .replace(/ऊ/g, "उ")
        .replace(/[शष]/g, "स")
        .replace(/ँ/g, "ं")
        .replace(/व/g, "ब")
        .replace(/ण/g, "न")
        .replace(/\u200c|\u200d/g, "")
        .replace(/\s+/g, " ")
        .trim()
}

function buildDevanagariQueryCandidates(rawQuery: string): string[] {
    const trimmed = String(rawQuery || "").trim()
    if (!trimmed) return []
    const out = new Set<string>()
    out.add(fromNepaliDigits(trimmed.toLowerCase()))

    if (/[a-zA-Z]/.test(trimmed)) {
        // Convert each Roman token individually so multi-word queries like "yeshule bhanuhunchha" also work
        const words = trimmed.split(/\s+/).filter(Boolean)
        const directConverted = words.map((w) => (/[a-zA-Z]/.test(w) ? transliterateRomanToDevanagariOffline(w) : w)).join(" ").toLowerCase()
        if (directConverted) out.add(directConverted)

        if (words.length === 1) {
            for (const cand of generateLocalCandidates(words[0], 6)) {
                out.add(cand.toLowerCase())
            }
        } else {
            const topWordVariants = words.map((w) => (/[a-zA-Z]/.test(w) ? generateLocalCandidates(w, 2)[0] || transliterateRomanToDevanagariOffline(w) : w))
            out.add(topWordVariants.join(" ").toLowerCase())
        }
    }

    return Array.from(out).filter((c) => c.length >= 1)
}

function extractItemsPlainText(items: Item[] | undefined): string {
    if (!Array.isArray(items) || !items.length) return ""
    const lines: string[] = []
    for (const item of items) {
        if (!item?.lines) continue
        for (const line of item.lines) {
            const text = (line.text || [])
                .map((part) => String(part?.value || ""))
                .join("")
                .replace(/<[^>]+>/g, "")
                .trim()
            if (text) lines.push(text)
        }
    }
    return lines.join("\n").trim()
}

function resolveSlideGroupInfo(slide: any): { group: string; color: string } {
    const globalGroupId = slide?.globalGroup
    const globalGroup = globalGroupId ? get(groups)[globalGroupId] : null
    const groupLabel = String(slide?.group || globalGroup?.name || "").trim()
    const color = String(slide?.color || globalGroup?.color || "").trim()
    return { group: groupLabel, color }
}

export function getMobileLiveState() {
    const firstOutput = getFirstActiveOutput()
    const out = firstOutput?.out || {}
    const outSlide = out.slide || null
    const cleared = getClearedState()
    const isScriptureOut = outputIsScripture()

    let mode: "show" | "scripture" | "pdf" | "none" = "none"
    let activeShowId = ""
    let activeShowName = ""
    let activeLayoutId = ""
    let currentSlideIndex = -1
    let nextSlideIndex = -1
    let currentSlideText = ""
    let currentGroup = ""
    let currentSlideNotes = ""
    let nextSlideText = ""
    let nextGroup = ""

    const slidesPayload: {
        index: number
        id: string
        group: string
        color: string
        text: string
        notes: string
        disabled: boolean
    }[] = []

    if (isScriptureOut && outSlide) {
        mode = "scripture"
        const dynRef = String(outSlide.customDynamicValues?.scripture_reference || outSlide.customDynamicValues?.scripture1_reference || "").trim()
        const rawText = extractItemsPlainText(outSlide.tempItems)
        const resolvedText = rawText
            .replace(/\{scripture1?_text\}/g, "")
            .replace(/\{scripture1?_reference\}/g, dynRef)
            .trim()

        // Prefer activeScripture cached text when dynamic placeholders are used
        const activeRef = get(activeScripture)
        activeShowId = "temp"
        activeShowName = dynRef || "Scripture"
        currentSlideIndex = 0
        currentGroup = dynRef || "Scripture"
        currentSlideText = resolvedText || dynRef

        if (Array.isArray(outSlide.nextSlides) && outSlide.nextSlides[0]) {
            nextSlideIndex = 1
            nextSlideText = extractItemsPlainText(outSlide.nextSlides[0])
            nextGroup = "Next Verse"
        }

        slidesPayload.push({
            index: 0,
            id: "temp_current",
            group: currentGroup,
            color: "#e6349c",
            text: currentSlideText,
            notes: activeRef?.id ? String(get(scriptures)[activeRef.id]?.name || "") : "",
            disabled: false
        })
    } else if (outSlide?.type === "pdf") {
        mode = "pdf"
        activeShowId = outSlide.id || ""
        activeShowName = outSlide.name || "PDF"
        currentSlideIndex = typeof outSlide.page === "number" ? outSlide.page : 0
        const pages = typeof outSlide.pages === "number" ? outSlide.pages : 1
        currentGroup = `Page ${currentSlideIndex + 1} / ${pages}`
        currentSlideText = `${activeShowName}\n(${currentGroup})`
        if (currentSlideIndex + 1 < pages) {
            nextSlideIndex = currentSlideIndex + 1
            nextGroup = `Page ${nextSlideIndex + 1} / ${pages}`
            nextSlideText = `${activeShowName} — ${nextGroup}`
        }
    } else {
        // Check either live outputted show OR currently opened show in center view
        const liveShowId = outSlide?.id && outSlide.id !== "temp" ? outSlide.id : ""
        const openShowRef = get(activeShow)
        const targetShowId = liveShowId || ((openShowRef?.type || "show") === "show" ? openShowRef?.id || "" : "")
        const cachedShow = targetShowId ? get(showsCache)[targetShowId] : null

        if (cachedShow) {
            mode = "show"
            activeShowId = targetShowId
            activeShowName = cachedShow.name || get(shows)[targetShowId]?.name || ""
            activeLayoutId = (liveShowId ? outSlide?.layout : "") || cachedShow.settings?.activeLayout || Object.keys(cachedShow.layouts || {})[0] || ""

            const layoutRef = getLayoutRef(targetShowId) || []
            const showSlides = cachedShow.slides || {}

            layoutRef.forEach((refItem, idx) => {
                const slideObj = showSlides[refItem.id]
                const text = slideObj ? getTextLines(slideObj).join("\n").trim() : ""
                const { group, color } = resolveSlideGroupInfo(slideObj)
                slidesPayload.push({
                    index: idx,
                    id: refItem.id,
                    group: refItem.type === "child" ? "" : group || `Slide ${idx + 1}`,
                    color,
                    text,
                    notes: String(slideObj?.notes || "").trim(),
                    disabled: !!refItem.data?.disabled
                })
            })

            if (liveShowId && !cleared.slide && typeof outSlide?.index === "number" && outSlide.index >= 0) {
                currentSlideIndex = outSlide.index
                const currentItem = slidesPayload[currentSlideIndex]
                if (currentItem) {
                    currentSlideText = currentItem.text
                    currentGroup = currentItem.group
                    currentSlideNotes = currentItem.notes
                }

                for (let n = currentSlideIndex + 1; n < slidesPayload.length; n++) {
                    if (!slidesPayload[n].disabled) {
                        nextSlideIndex = n
                        nextSlideText = slidesPayload[n].text
                        nextGroup = slidesPayload[n].group
                        break
                    }
                }
            } else if (slidesPayload.length > 0) {
                const firstEnabled = slidesPayload.findIndex((s) => !s.disabled)
                if (firstEnabled >= 0) {
                    nextSlideIndex = firstEnabled
                    nextSlideText = slidesPayload[firstEnabled].text
                    nextGroup = slidesPayload[firstEnabled].group
                }
            }
        }
    }

    const activeScriptureState = get(activeScripture)
    const scriptureSummary = activeScriptureState?.reference
        ? {
              bibleId: activeScriptureState.id || get(drawerTabsData).scripture?.activeSubTab || "",
              book: activeScriptureState.reference.book,
              chapter: activeScriptureState.reference.chapters?.[0] ?? 1,
              verses: activeScriptureState.reference.verses?.[0] || []
          }
        : null

    const currentDraw = get(draw)
    const currentTool = get(drawTool)
    const resolution = getOutputResolution(firstOutput?.id || "", get(outputs), true) || { width: 1920, height: 1080 }

    return {
        isLocked: !!get(outLocked),
        outputActive: !!get(outputDisplay),
        cleared,
        mode,
        activeShowId,
        activeShowName,
        activeLayoutId,
        currentSlideIndex,
        totalSlides: slidesPayload.length,
        currentSlideText,
        currentGroup,
        currentSlideNotes,
        nextSlideIndex,
        nextSlideText,
        nextGroup,
        slides: slidesPayload,
        scripture: scriptureSummary,
        timers: getTimersDetailed().slice(0, 20),
        stageMessage: get(mobileStageAlert),
        pointer: {
            active: currentDraw !== null,
            tool: currentDraw !== null ? currentTool : "off",
            x: currentDraw ? Math.max(0, Math.min(1, currentDraw.x / (resolution.width || 1920))) : 0.5,
            y: currentDraw ? Math.max(0, Math.min(1, currentDraw.y / (resolution.height || 1080))) : 0.5,
            size: get(drawSettings)?.[currentTool]?.size || 150
        },
        timestamp: Date.now()
    }
}

export async function getMobileBootstrap() {
    const items = await ensureHymnsLoaded()
    const hymnCounts = getCategoryCounts(items)
    const biblesList = await getMobileBibles()
    const projectsData = getMobileProjects()

    return {
        app: "Aradhana Studio",
        protocolVersion: 1,
        version: get(version) || "1.7.32",
        hostName: get(os)?.name || "Church-PC",
        language: get(language) || "ne",
        hymnCounts,
        bibles: biblesList.bibles,
        activeBibleId: biblesList.activeBibleId,
        activeProjectId: projectsData.activeProjectId,
        projects: projectsData.projects,
        activeProjectItems: projectsData.activeItems,
        promotions: get(mobileSoftwarePromotions),
        live: getMobileLiveState()
    }
}

export function getMobilePromotions() {
    return {
        promotions: get(mobileSoftwarePromotions)
    }
}

export function mobilePromotionEvent(data: { id?: number; campaignKey?: string; event?: "impression" | "click"; eventUrl?: string | null }) {
    const id = Number(data?.id || 0)
    const campaignKey = String(data?.campaignKey || "").trim()
    if (!Number.isFinite(id) || id <= 0 || !campaignKey) return { ok: false }

    sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
        id,
        campaignKey,
        eventUrl: data?.eventUrl || null,
        event: data?.event === "click" ? "click" : "impression",
        source: "mobile"
    })
    return { ok: true }
}

export async function mobileNextSlide() {
    if (get(outLocked)) return getMobileLiveState()

    if (outputIsScripture()) {
        return await mobileScriptureStep({ direction: "next" })
    }

    OutputHelper.advanceOutputs("next")
    return getMobileLiveState()
}

export async function mobilePrevSlide() {
    if (get(outLocked)) return getMobileLiveState()

    if (outputIsScripture()) {
        return await mobileScriptureStep({ direction: "previous" })
    }

    OutputHelper.advanceOutputs("previous")
    return getMobileLiveState()
}

export async function mobileSelectSlide(data: { showId?: string; layoutId?: string; slideIndex: number }) {
    if (get(outLocked)) return getMobileLiveState()

    const index = Number(data?.slideIndex ?? 0)
    if (!Number.isFinite(index) || index < 0) return getMobileLiveState()

    const targetShowId = data?.showId || (get(activeShow)?.id && (get(activeShow)?.type || "show") === "show" ? get(activeShow)!.id : getFirstActiveOutput()?.out?.slide?.id || "")
    if (!targetShowId || targetShowId === "temp") return getMobileLiveState()

    await loadShows([targetShowId])
    const cached = get(showsCache)[targetShowId]
    if (!cached) return getMobileLiveState()

    const layoutId = data?.layoutId || cached.settings?.activeLayout || Object.keys(cached.layouts || {})[0] || ""
    const showRef = _show(targetShowId).layouts([layoutId]).ref()[0] || []
    if (!showRef[index]) return getMobileLiveState()

    if (get(activeShow)?.id !== targetShowId) {
        activeShow.set({ id: targetShowId, type: "show" })
    }

    updateOut(targetShowId, index, showRef)
    setOutput("slide", { id: targetShowId, layout: layoutId, index, line: 0 })
    return getMobileLiveState()
}

export function mobileClear(data: { layer: string }) {
    const layer = String(data?.layer || "all").toLowerCase()
    switch (layer) {
        case "restore":
            restoreOutput()
            break
        case "slide":
            clearSlide()
            break
        case "background":
            clearBackground()
            break
        case "overlays":
            clearOverlays()
            break
        case "audio":
            clearAudio("", { clearPlaylist: true, commonClear: true })
            break
        case "timers":
            clearTimers()
            break
        case "drawing":
            clearDrawing()
            draw.set(null)
            break
        case "all":
        default:
            clearAll(true)
            draw.set(null)
            break
    }
    return getMobileLiveState()
}

export function mobileToggleLock(data: { locked?: boolean } = {}) {
    const next = typeof data?.locked === "boolean" ? data.locked : !get(outLocked)
    outLocked.set(next)
    return getMobileLiveState()
}

export function mobileToggleOutputWindow(data: { enabled?: boolean } = {}) {
    toggleOutputs(null, typeof data?.enabled === "boolean" ? { state: data.enabled } : {})
    return getMobileLiveState()
}

// --- HYMNS (BHAJAN) ---

export async function getMobileHymns(data: { query?: string; category?: string; offset?: number; limit?: number } = {}) {
    const allItems = await ensureHymnsLoaded()
    const category = (["all", "bhajan", "chorus", "children", "new"].includes(String(data?.category)) ? data.category : "all") as HymnCategoryId
    const rawQuery = String(data?.query || "").trim()
    const limit = Math.min(200, Math.max(1, Number(data?.limit || 80)))
    const offset = Math.max(0, Number(data?.offset || 0))

    let matched: HymnRecord[]
    if (!rawQuery) {
        matched = getFilteredHymns("", [category], false, allItems, [], { field: "number", direction: "asc" })
    } else {
        const directMatches = getFilteredHymns(rawQuery, [category], false, allItems, [], { field: null, direction: "asc" })
        const seen = new Set(directMatches.map((item) => item.id))
        const merged = [...directMatches]

        const candidates = buildDevanagariQueryCandidates(rawQuery)
        for (const cand of candidates) {
            if (cand === rawQuery.toLowerCase()) continue
            for (const item of getFilteredHymns(cand, [category], false, allItems, [], { field: null, direction: "asc" })) {
                if (!seen.has(item.id)) {
                    seen.add(item.id)
                    merged.push(item)
                }
            }
        }

        // Loose Devanagari vowel-length / sibilant fallback (e.g. स्तुती <-> स्तुति, येशु <-> येशू)
        const looseCandidates = candidates.map((c) => normalizeDevanagariLoose(c)).filter((c) => c.length >= 2)
        if (looseCandidates.length > 0) {
            for (const item of allItems) {
                if (seen.has(item.id)) continue
                if (category !== "all" && item.categoryId !== category) continue
                const looseHaystack = normalizeDevanagariLoose(`${item.title} ${item.firstLine} ${item.searchLyrics || ""}`)
                if (looseCandidates.some((lc) => looseHaystack.includes(lc))) {
                    seen.add(item.id)
                    merged.push(item)
                }
            }
        }

        if (/[a-zA-Z]/.test(rawQuery)) {
            const phoneticQuery = normalizePhoneticRoman(rawQuery)
            if (phoneticQuery.length >= 2) {
                for (const item of allItems) {
                    if (seen.has(item.id)) continue
                    if (category !== "all" && item.categoryId !== category) continue
                    const phoneticTitle = normalizePhoneticRoman(item.transliteration)
                    if (phoneticTitle.includes(phoneticQuery)) {
                        seen.add(item.id)
                        merged.push(item)
                    }
                }
            }
        }
        matched = merged
    }

    const slice = matched.slice(offset, offset + limit).map((item) => ({
        id: item.id,
        number: item.number,
        title: item.title,
        titleEn: item.titleEn,
        transliteration: item.transliteration,
        categoryId: item.categoryId,
        slidesCount: item.slidesCount,
        firstLine: item.firstLine
    }))

    return {
        total: matched.length,
        offset,
        limit,
        categoryCounts: getCategoryCounts(allItems),
        items: slice
    }
}

function findHymnByIdOrNumber(allItems: HymnRecord[], data: { id?: string; number?: string; category?: string }): HymnRecord | null {
    if (data?.id) {
        const byId = allItems.find((h) => h.id === data.id || h.sourceKey === data.id)
        if (byId) return byId
    }

    if (data?.number !== undefined && String(data.number).trim() !== "") {
        const normalizedNum = String(Number.parseInt(fromNepaliDigits(String(data.number).trim()), 10))
        if (normalizedNum && normalizedNum !== "NaN") {
            const preferredCat = data?.category && data.category !== "all" ? data.category : "bhajan"
            return (
                allItems.find((h) => h.number === normalizedNum && h.categoryId === preferredCat) ||
                allItems.find((h) => h.number === normalizedNum) ||
                null
            )
        }
    }

    return null
}

export async function getMobileHymnDetail(data: { id?: string; number?: string; category?: string }) {
    const allItems = await ensureHymnsLoaded()
    const hymn = findHymnByIdOrNumber(allItems, data)
    if (!hymn) return { found: false, hymn: null }

    return {
        found: true,
        hymn: {
            id: hymn.id,
            number: hymn.number,
            title: hymn.title,
            titleEn: hymn.titleEn,
            transliteration: hymn.transliteration,
            categoryId: hymn.categoryId,
            authors: hymn.authors,
            verses: hymn.verses.map((text, index) => ({
                index,
                group: inferSlideGroup(text),
                text
            }))
        }
    }
}

export async function mobileProjectHymn(data: { id?: string; number?: string; category?: string; slideIndex?: number; addToProject?: boolean; goLive?: boolean }) {
    const allItems = await ensureHymnsLoaded()
    const hymn = findHymnByIdOrNumber(allItems, data)
    if (!hymn) {
        return { ok: false, error: "Hymn not found", live: getMobileLiveState() }
    }

    // Reuse existing show if this hymn was already converted earlier, avoiding duplicate clutter
    let existingShowId = Object.entries(get(shows)).find(([, s]: any) => {
        const metaKey = s?.quickAccess?.metadata?.hymnSourceKey
        return metaKey && hymn.sourceKey && metaKey === hymn.sourceKey
    })?.[0]

    if (existingShowId) {
        await loadShows([existingShowId])
        const cached = get(showsCache)[existingShowId]
        if (!cached) {
            existingShowId = undefined
        } else {
            const cachedSyncedAt = Number(cached?.quickAccess?.metadata?.hymnSyncedAt || 0)
            if (!cachedSyncedAt || (hymn.updatedAt && hymn.updatedAt > cachedSyncedAt)) {
                const now = Math.max(Date.now(), hymn.updatedAt || 0)
                const refreshed = applyHymnToExistingShow(cached, hymn, now)
                showsCache.update((c) => ({ ...c, [existingShowId!]: refreshed }))
            }
        }
    }

    let showId = existingShowId || ""
    if (!showId) {
        const newShow = buildHymnShow(hymn)
        showId = uid()
        const remember = data.addToProject && get(activeProject) ? { project: get(activeProject) } : {}
        history({
            id: "UPDATE",
            newData: { data: newShow, remember },
            oldData: { id: showId },
            location: { page: "show", id: "show" }
        })
    } else if (data.addToProject && get(activeProject)) {
        const projId = get(activeProject)!
        projects.update((all) => {
            if (!all[projId]?.shows) return all
            if (!all[projId].shows.some((item) => item.id === showId)) {
                all[projId].shows.push({ id: showId, type: "show" })
                all[projId].modified = Date.now()
            }
            return all
        })
    }

    await loadShows([showId])
    activeShow.set({ id: showId, type: "show" })

    if (data.goLive !== false && !get(outLocked)) {
        const cached = get(showsCache)[showId]
        const layoutId = cached?.settings?.activeLayout || Object.keys(cached?.layouts || {})[0] || ""
        const showRef = _show(showId).layouts([layoutId]).ref()[0] || []
        const targetIndex = Math.max(0, Math.min(showRef.length - 1, Number(data.slideIndex ?? 0) || 0))
        if (showRef[targetIndex]) {
            updateOut(showId, targetIndex, showRef)
            setOutput("slide", { id: showId, layout: layoutId, index: targetIndex, line: 0 })
        }
    }

    return { ok: true, showId, title: hymn.title, number: hymn.number, live: getMobileLiveState() }
}

// --- SCRIPTURE (BIBLE) ---

async function ensureLocalBiblesRegistered() {
    const current = get(scriptures)
    if (Object.keys(current).length > 0) return current

    const localBibles = (await requestMain(Main.READ_BIBLES_FOLDER)) || []
    for (const b of localBibles) {
        const exists = Object.values(get(scriptures)).some((entry: any) => entry?.name?.replace(/\.fsb$/i, "") === b.name)
        if (!exists) {
            const id = uid()
            scriptures.update((s) => ({ ...s, [id]: { name: b.name, id } }))
        }
    }
    return get(scriptures)
}

function resolveDefaultBibleId(preferredId?: string): string {
    const all = get(scriptures)
    if (preferredId && all[preferredId]) return preferredId

    const drawerSubTab = get(drawerTabsData).scripture?.activeSubTab
    if (drawerSubTab && all[drawerSubTab]) return drawerSubTab

    const nepaliEntry = Object.entries(all).find(([, v]: any) => String(v?.name || "").toLowerCase().includes("nepali"))
    if (nepaliEntry) return nepaliEntry[0]

    return Object.keys(all)[0] || ""
}

export async function getMobileBibles() {
    const all = await ensureLocalBiblesRegistered()
    const activeBibleId = resolveDefaultBibleId()
    const bibles = Object.entries(all).map(([id, entry]: [string, any]) => ({
        id,
        name: String(entry?.customName || entry?.name || id).replace(/\.fsb$/i, ""),
        isApi: !!entry?.api,
        isCollection: !!entry?.collection?.versions?.length
    }))

    return { activeBibleId, bibles }
}

function resolvePrimaryBibleForCollection(bibleId: string): string {
    const entry = get(scriptures)[bibleId]
    if (entry?.collection?.versions?.length) {
        return entry.collection.versions[0] || bibleId
    }
    return bibleId
}

export async function getMobileBibleBooks(data: { bibleId?: string } = {}) {
    await ensureLocalBiblesRegistered()
    const selectedId = resolveDefaultBibleId(data?.bibleId)
    const loadId = resolvePrimaryBibleForCollection(selectedId)
    if (!loadId) return { bibleId: "", bibleName: "", books: [] }

    const bibleInstance = await loadJsonBible(loadId)
    if (!bibleInstance?.data?.books) return { bibleId: selectedId, bibleName: "", books: [] }

    const scriptureMeta = get(scriptures)[selectedId]
    const bibleName = String(scriptureMeta?.customName || scriptureMeta?.name || "").replace(/\.fsb$/i, "")
    const customNames = get(customScriptureBooks)[loadId] || []

    const books = (bibleInstance.data.books as any[]).map((b, idx) => {
        const num = Number(b?.number) || idx + 1
        const englishName = defaultBibleBookNames[num] || String(b?.name || `Book ${num}`)
        const localName = String(customNames[idx] || b?.customName || b?.name || englishName)
        const chaptersCount = Array.isArray(b?.chapters) ? b.chapters.length : Number(b?.chaptersCount || 1)
        return {
            number: num,
            name: localName,
            englishName,
            chaptersCount
        }
    })

    return { bibleId: selectedId, bibleName, books }
}

export async function getMobileBibleChapter(data: { bibleId?: string; book: number; chapter: number }) {
    await ensureLocalBiblesRegistered()
    const selectedId = resolveDefaultBibleId(data?.bibleId)
    const loadId = resolvePrimaryBibleForCollection(selectedId)
    const bookNum = Math.max(1, Number(data?.book || 1))
    const chapterNum = Math.max(1, Number(data?.chapter || 1))

    const bibleInstance = loadId ? await loadJsonBible(loadId) : null
    if (!bibleInstance) return { bibleId: selectedId, book: bookNum, bookName: "", chapter: chapterNum, chaptersCount: 0, verses: [] }

    try {
        const bookObj = await bibleInstance.getBook(bookNum)
        const chaptersCount = Array.isArray(bookObj?.data?.chapters) ? bookObj.data.chapters.length : 1
        const safeChapter = Math.min(Math.max(1, chapterNum), Math.max(1, chaptersCount))
        const chapterObj = await bookObj.getChapter(safeChapter)
        const rawVerses = chapterObj?.data?.verses || []

        const verses = rawVerses.map((v: any) => {
            const vNum = v.number
            let rawHtml = ""
            try {
                rawHtml = chapterObj.getVerse(vNum).getHTML() || v.text || ""
            } catch {
                rawHtml = v.text || ""
            }
            const cleanText = formatBibleText(rawHtml).replace(/<[^>]+>/g, "").replace(/!\{(.*?)\}!/g, "$1").trim()
            return {
                number: vNum,
                text: cleanText
            }
        })

        return {
            bibleId: selectedId,
            book: bookNum,
            bookName: String(bookObj?.name || defaultBibleBookNames[bookNum] || `Book ${bookNum}`),
            chapter: safeChapter,
            chaptersCount,
            verses
        }
    } catch (err) {
        console.error("Mobile Bible chapter load failed:", err)
        return { bibleId: selectedId, book: bookNum, bookName: "", chapter: chapterNum, chaptersCount: 0, verses: [] }
    }
}

export async function getMobileBibleSearch(data: { bibleId?: string; query: string; limit?: number }) {
    await ensureLocalBiblesRegistered()
    const selectedId = resolveDefaultBibleId(data?.bibleId)
    const loadId = resolvePrimaryBibleForCollection(selectedId)
    const rawQuery = String(data?.query || "").trim()
    const limit = Math.min(60, Math.max(1, Number(data?.limit || 30)))

    if (!loadId || rawQuery.length < 2) return { bibleId: selectedId, results: [] }

    const bibleInstance = await loadJsonBible(loadId)
    if (!bibleInstance?.data?.books) return { bibleId: selectedId, results: [] }

    const exactCandidates = buildDevanagariQueryCandidates(rawQuery).filter((c) => c.length >= 2)
    const looseCandidates = Array.from(new Set(exactCandidates.map((c) => normalizeDevanagariLoose(c)).filter((c) => c.length >= 2)))

    const exactResults: { book: number; bookName: string; chapter: number; verse: number; text: string }[] = []
    const looseResults: { book: number; bookName: string; chapter: number; verse: number; text: string }[] = []
    const books = bibleInstance.data.books as any[]

    for (let bIdx = 0; bIdx < books.length && exactResults.length < limit; bIdx++) {
        const book = books[bIdx]
        const bookNum = Number(book?.number) || bIdx + 1
        const bookName = String(book?.customName || book?.name || defaultBibleBookNames[bookNum] || `Book ${bookNum}`)
        const chapters = book?.chapters || []

        for (let cIdx = 0; cIdx < chapters.length && exactResults.length < limit; cIdx++) {
            const chapter = chapters[cIdx]
            const chapNum = Number(chapter?.number) || cIdx + 1
            const verses = chapter?.verses || []

            for (let vIdx = 0; vIdx < verses.length && exactResults.length < limit; vIdx++) {
                const verse = verses[vIdx]
                const rawText = String(verse?.text || "").replace(/<[^>]+>/g, "").trim()
                if (!rawText) continue
                const normalized = fromNepaliDigits(rawText.toLowerCase())
                if (exactCandidates.some((cand) => normalized.includes(cand))) {
                    exactResults.push({
                        book: bookNum,
                        bookName,
                        chapter: chapNum,
                        verse: Number(verse?.number) || vIdx + 1,
                        text: formatBibleText(rawText).replace(/<[^>]+>/g, "").trim()
                    })
                } else if (looseResults.length < limit && looseCandidates.length > 0) {
                    const looseNorm = normalizeDevanagariLoose(rawText)
                    if (looseCandidates.some((lc) => looseNorm.includes(lc))) {
                        looseResults.push({
                            book: bookNum,
                            bookName,
                            chapter: chapNum,
                            verse: Number(verse?.number) || vIdx + 1,
                            text: formatBibleText(rawText).replace(/<[^>]+>/g, "").trim()
                        })
                    }
                }
            }
        }
    }

    const results = [...exactResults, ...looseResults].slice(0, limit)
    return { bibleId: selectedId, results }
}

export async function mobileProjectScripture(data: { bibleId?: string; book: number; chapter: number; verses: (number | string)[]; addToProject?: boolean }) {
    await ensureLocalBiblesRegistered()
    const selectedId = resolveDefaultBibleId(data?.bibleId)
    if (!selectedId) return { ok: false, error: "No Bible available", live: getMobileLiveState() }

    const book = Math.max(1, Number(data?.book || 1))
    const chapter = Math.max(1, Number(data?.chapter || 1))
    const versesList = Array.isArray(data?.verses) && data.verses.length ? data.verses : [1]

    drawerTabsData.update((tabs) => {
        tabs.scripture = { ...(tabs.scripture || { enabled: true }), activeSubTab: selectedId }
        return tabs
    })

    activeScripture.set({
        id: resolvePrimaryBibleForCollection(selectedId),
        reference: {
            book,
            chapters: [chapter],
            verses: [versesList]
        }
    })

    if (data?.addToProject) {
        await createScriptureShow()
    }

    if (!get(outLocked)) {
        await playScripture()
    }

    return { ok: true, live: getMobileLiveState() }
}

export async function mobileScriptureStep(data: { direction: "next" | "previous" }) {
    if (get(outLocked)) return getMobileLiveState()

    await ensureLocalBiblesRegistered()
    const active = get(activeScripture)
    const selectedId = resolveDefaultBibleId(active?.id)
    const loadId = resolvePrimaryBibleForCollection(selectedId)
    if (!loadId || !active?.reference) return getMobileLiveState()

    const bibleInstance = await loadJsonBible(loadId)
    if (!bibleInstance?.data?.books) return getMobileLiveState()

    const books = bibleInstance.data.books as any[]
    const currentBook = Number(active.reference.book || 1)
    const currentChapter = Number(active.reference.chapters?.[0] || 1)
    const currentVerses = active.reference.verses?.[0] || [1]

    const bookObj = await bibleInstance.getBook(currentBook)
    const maxChapters = Array.isArray(bookObj?.data?.chapters) ? bookObj.data.chapters.length : 1
    const chapterObj = await bookObj.getChapter(currentChapter)
    const maxVerses = Array.isArray(chapterObj?.data?.verses) ? chapterObj.data.verses.length : 1

    const moved = moveSelection(
        { book: books.length, chapters: maxChapters, verses: maxVerses },
        { book: currentBook, chapters: [currentChapter], verses: currentVerses },
        data?.direction === "previous"
    )

    // If chapter changed when moving backward, clamp to actual verse count of target chapter
    let nextVerses = moved.verses
    if (data?.direction === "previous" && (moved.book !== currentBook || moved.chapters[0] !== currentChapter)) {
        try {
            const prevBookObj = await bibleInstance.getBook(moved.book)
            const targetChap = Math.min(moved.chapters[0], prevBookObj?.data?.chapters?.length || 1)
            const prevChapObj = await prevBookObj.getChapter(targetChap)
            const prevMaxVerses = prevChapObj?.data?.verses?.length || 1
            moved.chapters = [targetChap]
            nextVerses = [prevMaxVerses]
        } catch {
            // keep moved defaults
        }
    }

    drawerTabsData.update((tabs) => {
        tabs.scripture = { ...(tabs.scripture || { enabled: true }), activeSubTab: selectedId }
        return tabs
    })

    activeScripture.set({
        id: loadId,
        reference: {
            book: moved.book,
            chapters: moved.chapters,
            verses: [nextVerses]
        }
    })

    await playScripture()
    return getMobileLiveState()
}

// --- PROJECTS & SHOWS ---

export function getMobileProjects() {
    const allProjects = sortByName(removeDeleted(keysToID(get(projects))))
    const currentProjectId = get(activeProject) || allProjects[0]?.id || ""
    const currentItems = (get(projects)[currentProjectId]?.shows || []).map((item: any, index: number) => {
        const type = item.type || "show"
        const resolvedName = item.name || (type === "show" ? get(shows)[item.id]?.name : "") || item.id || `Item ${index + 1}`
        return {
            index,
            id: item.id || "",
            type,
            name: resolvedName,
            notes: String(item.notes || ""),
            played: !!item.played
        }
    })

    return {
        activeProjectId: currentProjectId,
        projects: allProjects.map((p: any) => ({
            id: p.id,
            name: p.name || "Untitled Project",
            modified: p.modified || 0,
            itemsCount: Array.isArray(p.shows) ? p.shows.length : 0
        })),
        activeItems: currentItems
    }
}

export function mobileSelectProject(data: { projectId: string }) {
    if (data?.projectId && get(projects)[data.projectId]) {
        activeProject.set(data.projectId)
    }
    return {
        ...getMobileProjects(),
        live: getMobileLiveState()
    }
}

export async function mobileOpenProjectItem(data: { index: number; goLive?: boolean }) {
    const currentProjectId = get(activeProject) || ""
    const items = get(projects)[currentProjectId]?.shows || []
    const idx = Number(data?.index ?? -1)
    const item = items[idx]
    if (!item) return { ok: false, live: getMobileLiveState() }

    const type = item.type || "show"
    if (type === "show") {
        await loadShows([item.id])
        selectProjectShow(idx)
        if (data?.goLive !== false && !get(outLocked)) {
            const cached = get(showsCache)[item.id]
            const layoutId = item.layout || cached?.settings?.activeLayout || Object.keys(cached?.layouts || {})[0] || ""
            const layoutRef = _show(item.id).layouts([layoutId]).ref()[0] || []
            const firstEnabled = Math.max(0, layoutRef.findIndex((s) => !s.data?.disabled))
            if (layoutRef[firstEnabled]) {
                updateOut(item.id, firstEnabled, layoutRef)
                setOutput("slide", { id: item.id, layout: layoutId, index: firstEnabled, line: 0 })
            }
        }
    } else if (type === "audio" && data?.goLive !== false && !get(outLocked)) {
        AudioPlayer.start(item.id, { name: item.name || "" })
    } else if ((type === "image" || type === "video") && data?.goLive !== false && !get(outLocked)) {
        const firstOutput = getFirstActiveOutput()
        const outputStyle = get(styles)[firstOutput?.style || ""]
        const mediaStyle = getMediaStyle(get(media)[item.id] || {}, outputStyle)
        setOutput("background", {
            path: item.id,
            type,
            loop: item.loop ?? true,
            muted: item.muted ?? false,
            startAt: 0,
            ...mediaStyle
        })
    } else if (type === "overlay" && data?.goLive !== false && !get(outLocked)) {
        setOutput("overlays", item.id, false, "", true)
    } else {
        selectProjectShow(idx)
    }

    return { ok: true, live: getMobileLiveState() }
}

export function getMobileShows(data: { query?: string; limit?: number } = {}) {
    const rawInput = String(data?.query || "").trim()
    const rawQuery = fromNepaliDigits(rawInput.toLowerCase())
    const exactCandidates = buildDevanagariQueryCandidates(rawInput)
    const looseCandidates = exactCandidates.map((c) => normalizeDevanagariLoose(c)).filter((c) => c.length >= 2)
    const limit = Math.min(150, Math.max(1, Number(data?.limit || 60)))

    const allShows = Object.entries(get(shows) || {})
        .filter(([, s]: [string, any]) => s && !s.private)
        .map(([id, s]: [string, any]) => ({
            id,
            name: String(s.name || "Untitled"),
            category: String(s.category || ""),
            number: String(s.quickAccess?.number || ""),
            modified: Number(s.timestamps?.modified || 0)
        }))

    const filtered = !rawQuery
        ? allShows.sort((a, b) => b.modified - a.modified)
        : allShows.filter((s) => {
              const nameNorm = fromNepaliDigits(s.name.toLowerCase())
              const numNorm = fromNepaliDigits(s.number.toLowerCase())
              if (numNorm === rawQuery || exactCandidates.some((cand) => nameNorm.includes(cand))) return true
              const nameLoose = normalizeDevanagariLoose(s.name)
              return looseCandidates.some((lc) => nameLoose.includes(lc))
          })

    return {
        total: filtered.length,
        items: filtered.slice(0, limit)
    }
}

export async function mobileOpenShow(data: { showId: string; goLive?: boolean; slideIndex?: number }) {
    const showId = String(data?.showId || "").trim()
    if (!showId || !get(shows)[showId]) return { ok: false, live: getMobileLiveState() }

    await loadShows([showId])
    openShow(showId)

    if (data?.goLive !== false && !get(outLocked)) {
        const cached = get(showsCache)[showId]
        const layoutId = cached?.settings?.activeLayout || Object.keys(cached?.layouts || {})[0] || ""
        const layoutRef = _show(showId).layouts([layoutId]).ref()[0] || []
        const idx = Math.max(0, Math.min(layoutRef.length - 1, Number(data?.slideIndex ?? 0) || 0))
        if (layoutRef[idx]) {
            updateOut(showId, idx, layoutRef)
            setOutput("slide", { id: showId, layout: layoutId, index: idx, line: 0 })
        }
    }

    return { ok: true, live: getMobileLiveState() }
}

// --- STAGE PROMPTER, ALERTS, TIMERS & POINTER ---

export function mobileStageMessage(data: { text?: string; target?: "stage" | "output" | "both"; visible?: boolean }) {
    const cleanText = String(data?.text || "")
        .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "")
        .trim()
        .slice(0, 240)
    const target: "stage" | "output" | "both" = data?.target === "output" || data?.target === "both" ? data.target : "stage"
    const visible = Boolean(data?.visible && cleanText.length > 0)

    mobileStageAlert.set({
        text: visible ? cleanText : "",
        target,
        visible,
        updatedAt: Date.now()
    })

    // Show or clear dedicated lower-third alert overlay on main output when target includes "output"
    if (visible && (target === "output" || target === "both") && !get(outLocked)) {
        overlays.update((all) => ({
            ...all,
            [MOBILE_ALERT_OVERLAY_ID]: {
                name: "Mobile Alert",
                color: "#e6349c",
                category: null,
                items: [
                    {
                        type: "text",
                        style: "top:900px;left:80px;width:1760px;height:130px;background-color:rgba(15,18,28,0.92);border-radius:16px;border:2px solid #e6349c;padding:16px;",
                        align: "align-items:center;",
                        lines: [{ align: "text-align:center;", text: [{ value: cleanText, style: "font-size:52px;font-weight:bold;color:#ffffff;font-family:NotoSansDevanagari;" }] }]
                    }
                ]
            }
        }))

        const activeOverlayIds: string[] = getFirstActiveOutput()?.out?.overlays || []
        if (!activeOverlayIds.includes(MOBILE_ALERT_OVERLAY_ID)) {
            setOutput("overlays", MOBILE_ALERT_OVERLAY_ID, true)
        }
    } else {
        clearOverlay(MOBILE_ALERT_OVERLAY_ID)
    }

    return getMobileLiveState()
}

export function mobileTimerAction(data: { action: "start" | "pause" | "stop" | "seek" | "quick_countdown"; timerId?: string; seconds?: number; name?: string }) {
    const act = data?.action
    if (act === "quick_countdown") {
        const durationSec = Math.max(10, Math.min(86400, Number(data?.seconds || 300)))
        const quickId = "hamro_mobile_countdown"
        const timerLabel = String(data?.name || "Stage Countdown").slice(0, 50)
        timers.update((all) => ({
            ...all,
            [quickId]: {
                name: timerLabel,
                type: "counter",
                start: durationSec,
                end: 0
            }
        }))
        startTimerById(quickId)
        return getMobileLiveState()
    }

    if (act === "start" && data?.timerId) {
        startTimerById(data.timerId)
    } else if (act === "pause") {
        if (data?.timerId) pauseTimerById(data.timerId)
        else pauseAllTimers()
    } else if (act === "stop") {
        if (data?.timerId) stopTimerById(data.timerId)
        else stopTimers()
    } else if (act === "seek" && typeof data?.seconds === "number") {
        timerSeekTo({ id: data.timerId, seconds: data.seconds })
    }

    return getMobileLiveState()
}

export function mobilePointer(data: { tool: "focus" | "pointer" | "zoom" | "off"; x?: number; y?: number; size?: number }) {
    const tool = data?.tool || "off"
    if (tool === "off") {
        draw.set(null)
        return { ok: true }
    }

    const firstOutput = getFirstActiveOutput()
    const resolution = getOutputResolution(firstOutput?.id || "", get(outputs), true) || { width: 1920, height: 1080 }
    const normX = Math.max(0, Math.min(1, Number(data?.x ?? 0.5)))
    const normY = Math.max(0, Math.min(1, Number(data?.y ?? 0.5)))

    if (tool === "zoom") {
        const zoomSize = Math.max(50, Math.min(400, Number(data?.size || 180)))
        drawSettings.update((s) => {
            if (!s.zoom) s.zoom = {}
            s.zoom.size = zoomSize
            return s
        })
        drawTool.set("zoom")
        draw.set({ x: resolution.width * normX, y: resolution.height * normY, resolution })
        return { ok: true }
    }

    const activeTool: "focus" | "pointer" = tool === "focus" ? "focus" : "pointer"
    drawTool.set(activeTool)
    const currentSize = Number(data?.size || get(drawSettings)?.[activeTool]?.size || (activeTool === "focus" ? 300 : 80))

    drawSettings.update((s) => {
        if (!s[activeTool]) s[activeTool] = { color: "#ff3366", opacity: 0.9, size: currentSize }
        else if (data?.size) s[activeTool].size = currentSize
        return s
    })

    draw.set({
        x: resolution.width * normX - currentSize / 2,
        y: resolution.height * normY - currentSize / 2,
        resolution
    })

    return { ok: true }
}

// --- REAL-TIME LIVE PUSH SUBSCRIBER ---

let liveSyncInitialized = false
export function setupMobileRemoteLiveSync() {
    if (liveSyncInitialized) return
    liveSyncInitialized = true

    let broadcastTimeout: ReturnType<typeof setTimeout> | null = null
    const schedulePush = () => {
        if (get(mobileRemoteClients).length === 0) return
        if (broadcastTimeout) clearTimeout(broadcastTimeout)
        broadcastTimeout = setTimeout(() => {
            broadcastTimeout = null
            if (get(mobileRemoteClients).length === 0) return
            sendMain(Main.MOBILE_BROADCAST, {
                event: "mobile_live_state",
                data: getMobileLiveState()
            })
        }, 80)
    }

    outputs.subscribe(schedulePush)
    activeShow.subscribe(schedulePush)
    activeProject.subscribe(schedulePush)
    outLocked.subscribe(schedulePush)
    outputDisplay.subscribe(schedulePush)
    activeTimers.subscribe(schedulePush)
    mobileStageAlert.subscribe(schedulePush)

    // Push immediate snapshot whenever a new mobile client connects
    let prevCount = 0
    mobileRemoteClients.subscribe((clients) => {
        if (clients.length > prevCount) {
            schedulePush()
            const promos = get(mobileSoftwarePromotions)
            if (promos.length > 0) {
                sendMain(Main.MOBILE_BROADCAST, {
                    event: "mobile_promotions",
                    data: { promotions: promos }
                })
            }
        }
        prevCount = clients.length
    })

    mobileSoftwarePromotions.subscribe((promos) => {
        if (get(mobileRemoteClients).length === 0) return
        sendMain(Main.MOBILE_BROADCAST, {
            event: "mobile_promotions",
            data: { promotions: promos }
        })
    })
}
