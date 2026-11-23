import { get, writable } from "svelte/store"
import { uid } from "uid"
import { fromNepaliDigits, shouldUseNepaliLocale, toNepaliDigits } from "../../../../common/nepali"
import { romanizeNepali } from "../../../../lib/nepaliTyping"
import type { Show } from "../../../../types/Show"
import { ShowObj } from "../../../classes/Show"
import { createCategory } from "../../../converters/importHelpers"
import { requestMain } from "../../../IPC/main"
import { activeEdit, activePage, activePopup, activeProject, activeShow, focusMode, language, projects, projectView, shows, showsCache } from "../../../stores"
import { newToast } from "../../../utils/common"
import { translateText } from "../../../utils/language"
import { confirmCustom } from "../../../utils/popup"
import { save } from "../../../utils/save"
import { history } from "../../helpers/history"
import { loadShows, saveTextCache } from "../../helpers/setShow"
import { checkName } from "../../helpers/show"
import { Main } from "../../../../types/IPC/Main"

export type HymnCategoryId = "all" | "bhajan" | "chorus" | "children" | "new"
export type HymnTypingLanguage = "ne" | "en"

type RawHymnSong = {
    sourceKey?: string
    sourcePage?: string
    title?: string
    titleEn?: string | null
    category?: string
    language?: string
    sourceRefs?: string[]
    lyrics?: string
    slides?: string[]
    authors?: string
    hasLyrics?: boolean
    updatedAt?: number
    rawDetails?: string
}

type RawHymnPayload = {
    version?: number
    source?: { name?: string; url?: string }
    language?: string
    categories?: { name?: string; songs?: RawHymnSong[] }[]
}

export type HymnRecord = {
    id: string
    sourceKey: string
    sourcePage: string
    isCustom: boolean
    title: string
    titleEn: string
    transliteration: string
    categoryRaw: string
    categoryId: HymnCategoryId
    number: string
    lyrics: string
    verses: string[]
    authors: string
    updatedAt: number
    rawDetails: string
    firstLine: string
    slidesCount: number
    wordCount: number
    repeatChorus: boolean
    searchTitle: string
    searchTitleEn: string
    searchTransliteration: string
    searchLyrics: string
    searchNumber: string
}

const TYPING_LANGUAGE_KEY = "hamrochurch.hymns.typing"
const FAVORITES_KEY = "hamrochurch.hymns.favorites"

export const hymnItems = writable<HymnRecord[]>([])
export const hymnLoading = writable(false)
export const hymnLoadError = writable("")
export const hymnSearchValue = writable("")
export const hymnSelectedId = writable("")
export const hymnSelectedCategories = writable<HymnCategoryId[]>(["all"])
export const hymnTypingLanguage = writable<HymnTypingLanguage>("ne")
export const hymnFavoriteIds = writable<string[]>([])
export const hymnSourcePath = writable("")
export const hymnSourceDates = writable<{ created: number | null; modified: number | null }>({ created: null, modified: null })

export type HymnSortField = "number" | "name"
export const hymnSort = writable<{ field: HymnSortField | null; direction: "asc" | "desc" }>({ field: "number", direction: "asc" })
export const hymnEditTarget = writable<HymnRecord | null>(null)

const EXCLUDED_HYMN_SOURCE_KEYS = new Set(["song1928", "song1929", "song1930"])

export function toggleHymnSort(field: HymnSortField) {
    hymnSort.update((current) => {
        if (current.field !== field) return { field, direction: "asc" as const }
        if (current.direction === "asc") return { field, direction: "desc" as const }
        return { field: null, direction: "asc" as const }
    })
}

const categoryKeyMap: Record<Exclude<HymnCategoryId, "all">, string> = {
    bhajan: "hymns.bhajan",
    chorus: "hymns.chorus",
    children: "hymns.children",
    new: "hymns.new"
}

export function getHymnCategoryLabel(categoryId: HymnCategoryId, _locale = get(language)) {
    if (categoryId === "all") return translateText("hymns.all")
    return translateText(categoryKeyMap[categoryId])
}

export function getHymnCategoryDisplay(categoryId: HymnCategoryId, locale = get(language)) {
    const labels: Record<HymnCategoryId, { en: string; ne: string }> = {
        all: { en: "All Hymns", ne: "सबै भजन" },
        bhajan: { en: "Bhajan", ne: "भजन" },
        chorus: { en: "Chorus", ne: "कोरस" },
        children: { en: "Bal Sangati", ne: "बाल सङ्गित" },
        new: { en: "New Songs", ne: "नयाँ गीत" }
    }

    const entry = labels[categoryId]
    return shouldUseNepaliLocale(locale) ? `${entry.ne} / ${entry.en}` : `${entry.en} / ${entry.ne}`
}

function escapeRegExp(value: string) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

function escapeHtml(value: string) {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&#39;")
}

function normalizeWhitespace(value: string) {
    return value.replace(/\u00a0/g, " ").replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\n{3,}/g, "\n\n").trim()
}

function normalizeSearchText(value: string) {
    return fromNepaliDigits(normalizeWhitespace(value).toLowerCase())
}

function mapCategoryId(rawCategory: string) {
    const normalized = normalizeSearchText(rawCategory)
    if (normalized.includes("chorus") || normalized.includes("कोरस") || normalized.startsWith("को.")) return "chorus"
    if (normalized.includes("bal sangati") || normalized.includes("बाल सङ्गती") || normalized.includes("children")) return "children"
    if (normalized.includes("new songs") || normalized.includes("नयाँ भजन") || normalized.includes("new song")) return "new"
    return "bhajan"
}

type HymnBookCategoryId = Exclude<HymnCategoryId, "all">

const HYMN_NUMBER_PATTERNS: Record<HymnBookCategoryId, RegExp> = {
    bhajan: /^(?:[a-z]+)?s(\d+)$/i,
    chorus: /^(?:[a-z]+)?c(\d+)$/i,
    children: /^(?:[a-z]+)?ch(\d+)$/i,
    new: /^(?:[a-z]+)?(?:nw|np|ms)(\d+)$/i
}
// order matters: "ch" (children) must be tested before "c" (chorus)
const KB_CATEGORY_ORDER: HymnBookCategoryId[] = ["children", "chorus", "bhajan"]

function splitRef(ref: string) {
    const [prefix = "", code = ""] = String(ref || "").split(":")
    return { prefix: prefix.toLowerCase(), code: fromNepaliDigits(code.trim()) }
}

/**
 * Resolves the hymn book number (and the category it belongs to).
 * The "kb" (ख्रीष्टीय भजन) reference is the source of truth for numbering. Some songs in the bundled data
 * are filed under a different category than their kb number (e.g. Bhajan 516 filed as Chorus), which used to hide the number.
 */
function resolveHymnNumber(sourceRefs: string[] = [], categoryId: HymnBookCategoryId = "bhajan"): { number: string; categoryId: HymnBookCategoryId } {
    const toNumber = (value: string) => String(Number.parseInt(value, 10))

    // 1. User-entered numbers from the add/edit hymn form (manual:<prefix><number>) always win.
    for (const ref of sourceRefs) {
        const { prefix, code } = splitRef(ref)
        if (prefix !== "manual") continue
        const digits = code.match(/(\d+)\s*$/)
        if (digits?.[1]) return { number: toNumber(digits[1]), categoryId }
    }

    // 2. kb number matching the song's category.
    for (const ref of sourceRefs) {
        const { prefix, code } = splitRef(ref)
        if (prefix !== "kb") continue
        const match = code.match(HYMN_NUMBER_PATTERNS[categoryId])
        if (match?.[1]) return { number: toNumber(match[1]), categoryId }
    }

    // 3. kb number of another category: the song was filed in the wrong category, follow the hymn book.
    for (const ref of sourceRefs) {
        const { prefix, code } = splitRef(ref)
        if (prefix !== "kb") continue
        for (const kbCategory of KB_CATEGORY_ORDER) {
            const match = code.match(HYMN_NUMBER_PATTERNS[kbCategory])
            if (match?.[1]) return { number: toNumber(match[1]), categoryId: kbCategory }
        }
    }

    // 4. New songs are numbered by other song books (nw/np/ms).
    if (categoryId === "new") {
        for (const ref of sourceRefs) {
            const match = splitRef(ref).code.match(HYMN_NUMBER_PATTERNS.new)
            if (match?.[1]) return { number: toNumber(match[1]), categoryId }
        }
    }

    return { number: "", categoryId }
}

function shouldExcludeHymn(song: RawHymnSong) {
    return EXCLUDED_HYMN_SOURCE_KEYS.has(String(song.sourceKey || ""))
}

function deriveVerses(song: RawHymnSong) {
    const slides = Array.isArray(song.slides) ? song.slides.map((entry) => normalizeWhitespace(String(entry))).filter(Boolean) : []
    if (slides.length >= 1) return slides

    const lyrics = normalizeWhitespace(String(song.lyrics || ""))
    if (!lyrics) return []

    const blocks = lyrics
        .split(/\n\s*\n/)
        .map((entry) => entry.trim())
        .filter(Boolean)

    return blocks.length ? blocks : [lyrics]
}

export function inferSlideGroup(text: string) {
    const trimmed = text.trim().toLowerCase()
    if (trimmed.startsWith("को.")) return "chorus"
    if (trimmed.includes("कोरस") || trimmed.includes("chorus")) return "chorus"
    return "verse"
}

function compareHymnNumbers(left: HymnRecord, right: HymnRecord): number {
    const leftNumber = Number.parseInt(left.searchNumber, 10)
    const rightNumber = Number.parseInt(right.searchNumber, 10)
    const leftHas = Number.isFinite(leftNumber)
    const rightHas = Number.isFinite(rightNumber)
    if (leftHas && rightHas && leftNumber !== rightNumber) return leftNumber - rightNumber
    if (leftHas !== rightHas) return leftHas ? -1 : 1
    return left.title.localeCompare(right.title, "ne")
}

function scoreHymn(hymn: HymnRecord, query: string): number {
    if (hymn.searchTitle === query || hymn.searchTitleEn === query) return 0
    if (hymn.searchNumber && hymn.searchNumber === query) return 25
    if (hymn.searchTitle.startsWith(query) || hymn.searchTitleEn.startsWith(query)) return 100

    const titlePositions = [hymn.searchTitle.indexOf(query), hymn.searchTitleEn.indexOf(query)].filter((value) => value >= 0)
    if (titlePositions.length) return 200 + Math.min(...titlePositions)

    const numberPosition = hymn.searchNumber.indexOf(query)
    if (numberPosition >= 0) return 350 + numberPosition

    const lyricsPosition = hymn.searchLyrics.indexOf(query)
    if (lyricsPosition >= 0) return 1000 + lyricsPosition

    return Number.MAX_SAFE_INTEGER
}

function getLocalStorageSafe() {
    try {
        return window.localStorage
    } catch {
        return null
    }
}

function getSessionStorageSafe() {
    try {
        return window.sessionStorage
    } catch {
        return null
    }
}

export function initializeHymnsPreferences() {
    const sessionStorage = getSessionStorageSafe()
    const localStorage = getLocalStorageSafe()
    const defaultLanguage: HymnTypingLanguage = shouldUseNepaliLocale(get(language)) ? "ne" : "en"

    if (!sessionStorage) {
        hymnTypingLanguage.set(defaultLanguage)
    } else {
        const savedTypingLanguage = sessionStorage.getItem(TYPING_LANGUAGE_KEY)
        hymnTypingLanguage.set(savedTypingLanguage === "en" || savedTypingLanguage === "ne" ? savedTypingLanguage : defaultLanguage)
    }

    const savedFavorites = localStorage?.getItem(FAVORITES_KEY)
    if (!savedFavorites) return

    try {
        const parsed = JSON.parse(savedFavorites)
        if (Array.isArray(parsed)) hymnFavoriteIds.set(parsed.map(String))
    } catch {
        hymnFavoriteIds.set([])
    }
}

export function setTypingLanguage(value: HymnTypingLanguage) {
    hymnTypingLanguage.set(value)
    const storage = getSessionStorageSafe()
    if (storage) storage.setItem(TYPING_LANGUAGE_KEY, value)
}

export function toggleFavorite(id: string) {
    hymnFavoriteIds.update((current) => {
        const next = current.includes(id) ? current.filter((value) => value !== id) : [...current, id]
        const storage = getLocalStorageSafe()
        if (storage) storage.setItem(FAVORITES_KEY, JSON.stringify(next))
        return next
    })
}

export function toggleCategory(categoryId: HymnCategoryId) {
    hymnSelectedCategories.set([categoryId])
}

export function getCategoryCounts(items: HymnRecord[]) {
    const counts: Record<HymnCategoryId, number> = { all: items.length, bhajan: 0, chorus: 0, children: 0, new: 0 }
    items.forEach((item) => {
        counts[item.categoryId] += 1
    })
    return counts
}

export function getActiveCategoryId(categories = get(hymnSelectedCategories)) {
    return categories[0] || "all"
}

export function formatHymnNumber(value: string, typingLanguage: HymnTypingLanguage | string = get(language)) {
    if (!value) return "—"
    return typingLanguage === "ne" || shouldUseNepaliLocale(typingLanguage) ? toNepaliDigits(value) : value
}

export async function loadHymns() {
    hymnLoading.set(true)
    hymnLoadError.set("")

    try {
        const result = await requestMain(Main.READ_HYMNS)
        const content = result?.content || ""
        if (!content) throw new Error("No hymn data found")

        hymnSourcePath.set(result?.path || "")
        if (result?.path) {
            const fileInfo = await requestMain(Main.FILE_INFO, result.path)
            hymnSourceDates.set({ created: fileInfo?.stat?.birthtimeMs || null, modified: fileInfo?.stat?.mtimeMs || null })
        } else {
            hymnSourceDates.set({ created: null, modified: null })
        }

        const parsed = JSON.parse(content) as RawHymnPayload
        const items: HymnRecord[] = []

        ;(parsed.categories || []).forEach((category) => {
            ;(category.songs || []).forEach((song, index) => {
                if (shouldExcludeHymn(song)) return

                const title = normalizeWhitespace(String(song.title || ""))
                const titleEn = normalizeWhitespace(String(song.titleEn || ""))
                const verses = deriveVerses(song)
                const lyrics = verses.length > 1 ? verses.join("\n\n") : normalizeWhitespace(String(song.lyrics || verses[0] || ""))
                if (!title || !lyrics || !verses.length) return

                const resolved = resolveHymnNumber(song.sourceRefs || [], mapCategoryId(song.category || category.name || "") as HymnBookCategoryId)
                const categoryId = resolved.categoryId
                const number = resolved.number
                const firstLine = verses[0]?.split("\n").map((line) => line.trim()).find(Boolean) || title
                const transliteration = normalizeWhitespace(titleEn || romanizeNepali(title))
                const slidesCount = verses.length
                const wordCount = lyrics.split(/\s+/).filter(Boolean).length
                const repeatChorus = verses.some((verse) => inferSlideGroup(verse) === "chorus") && verses.length > 1
                const updatedAt = Number(song.updatedAt || 0) || 0

                const rawSourceKey = String(song.sourceKey || "")
                const isCustom = !/^song\d+$/i.test(rawSourceKey)

                items.push({
                    id: rawSourceKey || `hymn-${index}-${uid(4)}`,
                    sourceKey: rawSourceKey,
                    sourcePage: String(song.sourcePage || ""),
                    isCustom,
                    title,
                    titleEn,
                    transliteration,
                    categoryRaw: String(song.category || category.name || ""),
                    categoryId,
                    number,
                    lyrics,
                    verses,
                    authors: normalizeWhitespace(String(song.authors || "")),
                    updatedAt,
                    rawDetails: normalizeWhitespace(String(song.rawDetails || "")),
                    firstLine,
                    slidesCount,
                    wordCount,
                    repeatChorus,
                    searchTitle: normalizeSearchText(title),
                    searchTitleEn: normalizeSearchText(titleEn),
                    searchTransliteration: normalizeSearchText(transliteration),
                    searchLyrics: normalizeSearchText(lyrics),
                    searchNumber: normalizeSearchText(number)
                })
            })
        })

        // Assign deterministic sequential continuation numbers to unnumbered songs in each category
        // (e.g. Bhajan 849+, Chorus 337+, Bal Sangati 71+, New Songs 16+) while preserving 100% of official book numbers.
        const bookCategories: HymnBookCategoryId[] = ["bhajan", "chorus", "children", "new"]
        for (const cat of bookCategories) {
            let maxBundled = 0
            for (const item of items) {
                if (item.categoryId !== cat || item.isCustom || !item.number) continue
                const parsedNum = Number.parseInt(item.number, 10)
                if (Number.isFinite(parsedNum) && parsedNum > maxBundled) {
                    maxBundled = parsedNum
                }
            }

            const unnumberedBundled = items
                .filter((item) => item.categoryId === cat && !item.isCustom && !item.number)
                .sort((left, right) => left.title.localeCompare(right.title, "ne") || left.sourceKey.localeCompare(right.sourceKey))

            let nextNum = maxBundled
            for (const item of unnumberedBundled) {
                nextNum += 1
                const assigned = String(nextNum)
                item.number = assigned
                item.searchNumber = normalizeSearchText(assigned)
            }

            let maxCategory = nextNum
            for (const item of items) {
                if (item.categoryId !== cat || !item.number) continue
                const parsedNum = Number.parseInt(item.number, 10)
                if (Number.isFinite(parsedNum) && parsedNum > maxCategory) {
                    maxCategory = parsedNum
                }
            }

            for (const item of items) {
                if (item.categoryId !== cat || !item.isCustom || item.number) continue
                maxCategory += 1
                const assigned = String(maxCategory)
                item.number = assigned
                item.searchNumber = normalizeSearchText(assigned)
            }
        }

        hymnItems.set(items)
        const currentSelected = get(hymnSelectedId)
        if ((!currentSelected || !items.some((item) => item.id === currentSelected)) && items[0]) {
            hymnSelectedId.set(items[0].id)
        }
    } catch (error) {
        hymnLoadError.set(error instanceof Error ? error.message : String(error))
        hymnItems.set([])
        hymnSelectedId.set("")
        hymnSourcePath.set("")
        hymnSourceDates.set({ created: null, modified: null })
    } finally {
        hymnLoading.set(false)
    }
}

export function getFilteredHymns(query: string, selectedCategories: HymnCategoryId[], favoritesOnly = false, items = get(hymnItems), favoriteIds = get(hymnFavoriteIds), sort = get(hymnSort)) {
    const normalizedQuery = normalizeSearchText(query)

    const filtered = items.filter((item) => {
        if (favoritesOnly && !favoriteIds.includes(item.id)) return false
        if (!selectedCategories.includes("all") && !selectedCategories.includes(item.categoryId)) return false
        if (!normalizedQuery) return true
        return item.searchTitle.includes(normalizedQuery) || item.searchTitleEn.includes(normalizedQuery) || item.searchTransliteration.includes(normalizedQuery) || item.searchNumber.includes(normalizedQuery) || item.searchLyrics.includes(normalizedQuery)
    })

    if (sort?.field) {
        const direction = sort.direction === "desc" ? -1 : 1
        const compare = sort.field === "number" ? compareHymnNumbers : (left: HymnRecord, right: HymnRecord) => left.title.localeCompare(right.title, "ne")
        return filtered.sort((left, right) => compare(left, right) * direction)
    }

    if (!normalizedQuery) return filtered

    return filtered.sort((left, right) => scoreHymn(left, normalizedQuery) - scoreHymn(right, normalizedQuery) || left.title.localeCompare(right.title, "ne"))
}

export function getSelectedHymn() {
    const selectedId = get(hymnSelectedId)
    return get(hymnItems).find((item) => item.id === selectedId) || null
}

export function highlightHymnText(text: string, query: string) {
    if (!query) return escapeHtml(text)
    const escaped = escapeRegExp(normalizeWhitespace(query))
    return escapeHtml(text).replace(new RegExp(escaped, "gi"), (match) => `<mark>${match}</mark>`)
}

export function buildHymnSlides(verses: string[]) {
    const slides: Record<string, any> = {}
    const layoutSlides: { id: string }[] = []

    verses.forEach((verseText) => {
        const slideId = uid()
        const grp = inferSlideGroup(verseText)
        slides[slideId] = {
            group: grp,
            globalGroup: grp,
            color: null,
            settings: {},
            notes: "",
            items: [
                {
                    type: "text",
                    style: "top:88px;left:50px;height:904px;width:1820px;font-family:NotoSansDevanagari;",
                    lines: verseText.split("\n").map((line) => ({ align: "", text: [{ value: line, style: "font-size: 100px;" }] }))
                }
            ]
        }
        layoutSlides.push({ id: slideId })
    })

    return { slides, layoutSlides }
}

export function applyHymnToExistingShow(existingShow: Show, hymn: HymnRecord, syncedAt = Date.now()): Show {
    const { slides, layoutSlides } = buildHymnSlides(hymn.verses)
    const layoutIds = Object.keys(existingShow.layouts || {})
    const activeLayoutId = (existingShow.settings?.activeLayout && existingShow.layouts?.[existingShow.settings.activeLayout] ? existingShow.settings.activeLayout : layoutIds[0]) || uid()
    const baseLayout = existingShow.layouts?.[activeLayoutId] || { name: shouldUseNepaliLocale(get(language)) ? "मूल" : "Main", notes: "", slides: [] }

    const nextQuickAccess: Record<string, any> = {
        ...(existingShow.quickAccess || {}),
        metadata: {
            ...(existingShow.quickAccess?.metadata || {}),
            hymnSourceKey: hymn.sourceKey || hymn.id,
            hymnCategoryId: hymn.categoryId,
            hymnTitleEn: hymn.titleEn || "",
            hymnSyncedAt: syncedAt
        }
    }
    if (hymn.number) nextQuickAccess.number = hymn.number
    else delete nextQuickAccess.number

    return {
        ...existingShow,
        origin: "hamro-hymns",
        quickAccess: nextQuickAccess,
        meta: {
            ...(existingShow.meta || {}),
            title: hymn.title,
            number: hymn.number || undefined,
            author: hymn.authors || undefined,
            copyright: hymn.rawDetails || undefined
        },
        timestamps: {
            ...(existingShow.timestamps || { created: syncedAt, used: null }),
            modified: syncedAt
        },
        slides,
        layouts: {
            ...(existingShow.layouts || {}),
            [activeLayoutId]: {
                ...baseLayout,
                slides: layoutSlides
            }
        }
    }
}

export function buildHymnShow(hymn: HymnRecord): Show {
    const layoutId = uid()
    const created = Date.now()
    const { slides, layoutSlides } = buildHymnSlides(hymn.verses)

    const categoryNames: Record<Exclude<HymnCategoryId, "all">, string> = {
        bhajan: "bhajan",
        chorus: "chorus",
        children: "bal sangati",
        new: "new hymns"
    }
    const categoryName = categoryNames[hymn.categoryId as Exclude<HymnCategoryId, "all">] || "bhajan"
    const categoryId = createCategory(categoryName, "lyrics")
    const show = new ShowObj(false, categoryId, layoutId, created, "default")
    const titleWithNumber = hymn.number ? `${hymn.title} (${shouldUseNepaliLocale(get(language)) ? toNepaliDigits(hymn.number) : hymn.number})` : hymn.title

    show.name = checkName(titleWithNumber)
    show.origin = "hamro-hymns"
    show.quickAccess = hymn.number ? { number: hymn.number } : {}
    show.quickAccess.metadata = {
        hymnSourceKey: hymn.sourceKey || hymn.id,
        hymnCategoryId: hymn.categoryId,
        hymnTitleEn: hymn.titleEn || "",
        hymnSyncedAt: created
    }
    show.meta = {
        title: hymn.title,
        number: hymn.number || undefined,
        author: hymn.authors || undefined,
        copyright: hymn.rawDetails || undefined
    }
    show.slides = slides
    show.layouts = { [layoutId]: { name: shouldUseNepaliLocale(get(language)) ? "मूल" : "Main", notes: "", slides: layoutSlides } }
    show.media = {}

    return show
}

export async function insertSelectedHymnIntoSlides(targetHymnId?: string | Event) {
    if (typeof targetHymnId === "string" && targetHymnId) {
        hymnSelectedId.set(targetHymnId)
    }
    const hymn = typeof targetHymnId === "string" && targetHymnId ? get(hymnItems).find((item) => item.id === targetHymnId) || getSelectedHymn() : getSelectedHymn()
    if (!hymn) return

    const targetSourceKey = hymn.sourceKey || hymn.id
    let existingShowId = Object.entries(get(shows)).find(([, s]: any) => {
        const metaKey = s?.quickAccess?.metadata?.hymnSourceKey
        return metaKey && targetSourceKey && metaKey === targetSourceKey
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
                saveTextCache(existingShowId, refreshed)
            }
        }
    }

    if (existingShowId) {
        const projId = get(activeProject)
        let projectIndex: number | undefined = undefined
        if (projId && get(projects)[projId]) {
            projects.update((all) => {
                const proj = all[projId]
                if (!proj?.shows) return all
                let idx = proj.shows.findIndex((item) => item.id === existingShowId)
                if (idx === -1) {
                    const insertAt = get(activeShow)?.index === undefined ? proj.shows.length : get(activeShow)!.index! + 1
                    proj.shows.splice(insertAt, 0, { id: existingShowId!, type: "show" })
                    proj.modified = Date.now()
                    idx = insertAt
                }
                projectIndex = idx
                return all
            })
            projectView.set(false)
        }
        activeShow.set({ id: existingShowId, type: "show", ...(projectIndex !== undefined ? { index: projectIndex } : {}) })
        if (!get(focusMode)) {
            activeEdit.set({ type: "show", slide: 0, items: [], showId: existingShowId })
        }
        if (get(activePage) !== "show" && get(activePage) !== "edit") {
            activePage.set("show")
        }
        newToast("hymns.inserted")
        return
    }

    if (get(activeProject) && get(projects)[get(activeProject)!]) {
        projectView.set(false)
    }

    const show = buildHymnShow(hymn)
    const selectedIndex = get(activeShow)?.index === undefined ? undefined : get(activeShow)!.index! + 1
    history({ id: "UPDATE", newData: { data: show, remember: { project: get(activeProject), index: selectedIndex } }, location: { page: "show", id: "show" } })
    newToast("hymns.inserted")
}

export function isCustomHymn(hymnOrId?: HymnRecord | string | null): boolean {
    if (!hymnOrId) return false
    if (typeof hymnOrId === "object") return Boolean(hymnOrId.isCustom)
    const found = get(hymnItems).find((item) => item.id === hymnOrId || item.sourceKey === hymnOrId)
    if (found) return Boolean(found.isCustom)
    return !/^song\d+$/i.test(String(hymnOrId).trim())
}

export function getNextAvailableHymnNumber(categoryId: Exclude<HymnCategoryId, "all"> = "bhajan", items = get(hymnItems)): string {
    let maxNum = 0
    for (const item of items) {
        if (item.categoryId !== categoryId || !item.number) continue
        const parsed = Number.parseInt(item.number, 10)
        if (Number.isFinite(parsed) && parsed > maxNum) {
            maxNum = parsed
        }
    }
    return String(maxNum + 1)
}

export async function deleteHymnById(targetHymnId?: string) {
    const hymn = targetHymnId ? get(hymnItems).find((item) => item.id === targetHymnId || item.sourceKey === targetHymnId) || null : getSelectedHymn()
    if (!hymn || !hymn.isCustom) return

    const confirmed = await confirmCustom(translateText("hymns.delete_confirm"))
    if (!confirmed) return

    const targetKey = hymn.sourceKey || hymn.id
    const result = await requestMain(Main.DELETE_HYMN, { id: targetKey })
    if (!result?.success) {
        newToast("hymns.load_error")
        return
    }

    const removedShowIds = new Set<string>(result.deletedShowIds || [])
    shows.update((allShows) => {
        for (const [showId, s] of Object.entries<any>(allShows || {})) {
            if (removedShowIds.has(showId) || String(s?.quickAccess?.metadata?.hymnSourceKey || "") === targetKey) {
                removedShowIds.add(showId)
                delete allShows[showId]
            }
        }
        return allShows
    })

    if (removedShowIds.size > 0) {
        showsCache.update((cache) => {
            for (const showId of removedShowIds) {
                delete cache[showId]
            }
            return cache
        })

        projects.update((allProjects) => {
            for (const proj of Object.values<any>(allProjects || {})) {
                if (Array.isArray(proj?.shows)) {
                    const before = proj.shows.length
                    proj.shows = proj.shows.filter((item: any) => !removedShowIds.has(String(item?.id || "")))
                    if (proj.shows.length !== before) {
                        proj.modified = Date.now()
                    }
                }
            }
            return allProjects
        })

        const currentActive = get(activeShow)
        if (currentActive?.id && removedShowIds.has(currentActive.id)) {
            activeShow.set(null)
        }
    }

    hymnFavoriteIds.update((current) => {
        if (!current.includes(hymn.id)) return current
        const next = current.filter((id) => id !== hymn.id)
        const storage = getLocalStorageSafe()
        if (storage) storage.setItem(FAVORITES_KEY, JSON.stringify(next))
        return next
    })

    await loadHymns()
    if (removedShowIds.size > 0) {
        save(false, { autosave: true })
    }
    newToast("hymns.deleted")
}

export function openEditHymnById(targetHymnId?: string) {
    if (targetHymnId) hymnSelectedId.set(targetHymnId)
    const hymn = targetHymnId ? get(hymnItems).find((item) => item.id === targetHymnId) || getSelectedHymn() : getSelectedHymn()
    if (!hymn) return
    hymnEditTarget.set(hymn)
    activePopup.set("edit_hymn")
}

export async function copySelectedHymnToClipboard() {
    const hymn = getSelectedHymn()
    if (!hymn) return

    await navigator.clipboard.writeText(hymn.lyrics)
    newToast("hymns.copied")
}