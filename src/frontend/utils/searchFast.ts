import { get } from "svelte/store"
import { generateLocalCandidates } from "../../lib/googleNepaliTyping"
import { handleKeyPress } from "../../lib/nepaliTyping"
import { ShowList } from "../../types/Show"
import { categories, drawerTabsData, textCache } from "../stores"
import { formatSearch, tokenize } from "./search"
import { sortObjectNumbers } from "../components/helpers/array"

// Adapted from the upstream search optimization.

// ==========================================
// INVERTED INDEX FOR O(1) WORD LOOKUPS
// ==========================================

interface IndexEntry {
    showId: string
    field: "name" | "content"
    frequency: number // How many times word appears
}

interface SearchIndex {
    wordIndex: Map<string, IndexEntry[]> // word -> [showId, field, freq]
    phraseIndex: Map<string, Set<string>> // 2-gram phrase -> Set<showId>
    showData: Map<string, { name: string; content: string }>
    lastBuildTime: number
}

export interface BibleVerseRef {
    book: number
    chapter: number
    verse: number
    reference: string
    text: string
    norm: string
    loose: string
}

const BIBLE_INDEX_VERSION = 3 // Increment this to force index rebuild

interface BibleIndex {
    entries: BibleVerseRef[]
    bibleId: string
    version: number
}

let searchIndex: SearchIndex | null = null
let bibleIndex: BibleIndex | null = null
let bibleIndexBuildingId = ""
let bibleIndexBuildPromise: Promise<BibleIndex> | null = null
let indexVersion = 0

const BIBLE_CLEAN_MAP: Record<string, string> = {
    "०": "0",
    "१": "1",
    "२": "2",
    "३": "3",
    "४": "4",
    "५": "5",
    "६": "6",
    "७": "7",
    "८": "8",
    "९": "9",
    "\u200b": "",
    "\u200c": "",
    "\u200d": "",
    "\ufeff": ""
}
const BIBLE_LOOSE_MAP: Record<string, string> = {
    "\u0940": "\u093f",
    "\u0908": "\u0907",
    "\u0942": "\u0941",
    "\u090a": "\u0909",
    "\u0936": "\u0938",
    "\u0937": "\u0938",
    "\u0901": "\u0902",
    "\u0935": "\u092c",
    "\u0923": "\u0928"
}
const BIBLE_CLEAN_RE = /[०-९\u200B-\u200D\uFEFF]/g
const BIBLE_LOOSE_RE = /[ीईूऊशषँवण]/g
const BIBLE_TAG_PUNCT_RE = /<[^>]+>|[.,\/#!?$%\^&\*;:{}=\-_'"´`~()\[\]“”‘’—–।॥]+/g
const BIBLE_LATIN_ACCENT_RE = /[\u00C0-\u024F\u1E00-\u1EFF]/

export function normalizeBibleSearchText(raw: string): string {
    if (!raw) return ""
    let out = raw
        .replace(BIBLE_CLEAN_RE, (ch) => BIBLE_CLEAN_MAP[ch] ?? "")
        .replace(BIBLE_TAG_PUNCT_RE, " ")
        .toLowerCase()
    if (BIBLE_LATIN_ACCENT_RE.test(out)) {
        out = out.normalize("NFD").replace(/[\u0300-\u036f]/g, "").normalize("NFC")
    }
    return out.replace(/\s+/g, " ").trim()
}

export function normalizeBibleLooseText(alreadyNormalized: string): string {
    if (!alreadyNormalized) return ""
    return alreadyNormalized.replace(BIBLE_LOOSE_RE, (ch) => BIBLE_LOOSE_MAP[ch] ?? ch)
}

function transliterateRomanQueryToken(token: string): string {
    let value = ""
    let cursor = 0
    for (const ch of token) {
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
    return value.replace(/\u094D$/g, "").trim()
}

function buildBibleQueryCandidates(rawQuery: string): string[] {
    const base = normalizeBibleSearchText(rawQuery)
    if (!base) return []
    const candidates = new Set<string>([base])

    if (/[a-z]/i.test(rawQuery)) {
        const rawWords = rawQuery.trim().split(/\s+/).filter(Boolean)
        const direct = rawWords.map((w) => (/[a-z]/i.test(w) ? transliterateRomanQueryToken(w) : w)).join(" ")
        const normDirect = normalizeBibleSearchText(direct)
        if (normDirect) candidates.add(normDirect)

        if (rawWords.length === 1) {
            for (const cand of generateLocalCandidates(rawWords[0], 4)) {
                const nc = normalizeBibleSearchText(cand)
                if (nc) candidates.add(nc)
            }
        } else {
            const topVariant = rawWords
                .map((w) => (/[a-z]/i.test(w) ? generateLocalCandidates(w, 2)[0] || transliterateRomanQueryToken(w) : w))
                .join(" ")
            const nt = normalizeBibleSearchText(topVariant)
            if (nt) candidates.add(nt)
        }
    }

    return Array.from(candidates).filter((c) => c.length >= 1)
}

/**
 * Build/rebuild the search index from shows and textCache
 * Call this when shows or textCache changes
 */
export function buildSearchIndex(shows: ShowList[]) {
    const wordIndex = new Map<string, IndexEntry[]>()
    const phraseIndex = new Map<string, Set<string>>()
    const showData = new Map<string, { name: string; content: string }>()
    const cache = get(textCache)

    for (const show of shows) {
        if (!show.id) continue

        const name = formatSearch(show.name || "", false)
        const content = formatSearch(cache[show.id] || "", false)
        showData.set(show.id, { name, content })

        // Index name words
        const nameWords = tokenize(name)
        indexWords(wordIndex, show.id, nameWords, "name")
        indexPhrases(phraseIndex, show.id, nameWords)

        // Index content words
        const contentWords = tokenize(content)
        indexWords(wordIndex, show.id, contentWords, "content")
        indexPhrases(phraseIndex, show.id, contentWords)
    }

    searchIndex = {
        wordIndex,
        phraseIndex,
        showData,
        lastBuildTime: Date.now()
    }
    indexVersion++

    return searchIndex
}

/**
 * Fast Bible search supporting Nepali Devanagari (including invisible ZWJ/ZWNJ,
 * agglutinative suffixes like ख्रीष्टको / परमेश्वरले, and loose vowel/consonant spelling),
 * Romanized Nepali queries, and English / Latin Bibles.
 */
export function fastBibleSearch(searchValue: string, MAX_RESULTS = 120): BibleVerseRef[] {
    if (!bibleIndex || !bibleIndex.entries.length) return []

    const candidates = buildBibleQueryCandidates(searchValue)
    if (candidates.length === 0) return []

    const entries = bibleIndex.entries
    const bestScores = new Map<number, number>()

    // Pass 1: Exact / substring normalized matching (preserves exact vowel lengths, ZWJ stripped)
    for (let cIdx = 0; cIdx < candidates.length; cIdx++) {
        const cand = candidates[cIdx]
        const words = cand.split(" ").filter(Boolean)
        if (!words.length) continue
        const w0 = words[0]
        const candidateBonus = cIdx === 0 ? 15 : 0

        for (let i = 0; i < entries.length; i++) {
            const norm = entries[i].norm
            if (!norm.includes(w0)) continue

            let allFound = true
            for (let j = 1; j < words.length; j++) {
                if (!norm.includes(words[j])) {
                    allFound = false
                    break
                }
            }
            if (!allFound) continue

            let score = 30 + candidateBonus
            if (norm.includes(cand)) score += 120
            if (norm.startsWith(cand)) score += 35

            const padded = ` ${norm} `
            for (let j = 0; j < words.length; j++) {
                if (padded.includes(` ${words[j]} `)) score += 14
            }

            const prev = bestScores.get(i) || 0
            if (score > prev) bestScores.set(i, score)
        }
    }

    // Pass 2: Loose Devanagari phonetic equivalence (येशु <-> येशू, ख्रिष्ट <-> ख्रीष्ट, बिस्वास <-> विश्वास)
    if (bestScores.size < Math.min(MAX_RESULTS, 25)) {
        const looseCandidates = Array.from(new Set(candidates.map((c) => normalizeBibleLooseText(c)).filter(Boolean)))
        for (const lCand of looseCandidates) {
            const lWords = lCand.split(" ").filter(Boolean)
            if (!lWords.length) continue
            const lw0 = lWords[0]

            for (let i = 0; i < entries.length; i++) {
                if (bestScores.has(i)) continue
                const loose = entries[i].loose
                if (!loose.includes(lw0)) continue

                let allFound = true
                for (let j = 1; j < lWords.length; j++) {
                    if (!loose.includes(lWords[j])) {
                        allFound = false
                        break
                    }
                }
                if (!allFound) continue

                let score = 18
                if (loose.includes(lCand)) score += 65
                if (loose.startsWith(lCand)) score += 20
                bestScores.set(i, score)
            }
        }
    }

    return Array.from(bestScores.entries())
        .sort((a, b) => b[1] - a[1] || a[0] - b[0])
        .slice(0, MAX_RESULTS)
        .map(([idx]) => entries[idx])
}

/**
 * Build search index for a Bible in non-blocking slices so UI never freezes.
 */
export async function buildBibleIndex(bibleId: string, bibleData: any) {
    if (bibleIndex?.bibleId === bibleId && bibleIndex?.version === BIBLE_INDEX_VERSION) return bibleIndex
    if (bibleIndexBuildPromise && bibleIndexBuildingId === bibleId) return bibleIndexBuildPromise

    bibleIndexBuildingId = bibleId
    bibleIndexBuildPromise = (async () => {
        const entries: BibleVerseRef[] = []
        const books = bibleData?.books || []
        let processedSinceYield = 0

        for (const book of books) {
            const bookIndex = Number(book.number)
            const bookName = String(book.name || "")
            const chapters = book.chapters || []

            for (const chapter of chapters) {
                const chapterIndex = Number(chapter.number)
                const verses = chapter.verses || []

                for (const verse of verses) {
                    const verseIndex = Number(verse.number)
                    const text = String(verse.text || "")
                    const norm = normalizeBibleSearchText(text)
                    if (!norm) continue

                    entries.push({
                        book: bookIndex,
                        chapter: chapterIndex,
                        verse: verseIndex,
                        reference: `${bookName} ${chapterIndex}:${verseIndex}`,
                        text,
                        norm,
                        loose: normalizeBibleLooseText(norm)
                    })

                    processedSinceYield++
                    if (processedSinceYield >= 5000) {
                        processedSinceYield = 0
                        await new Promise<void>((resolve) => setTimeout(resolve, 0))
                    }
                }
            }
        }

        const built: BibleIndex = {
            entries,
            bibleId,
            version: BIBLE_INDEX_VERSION
        }
        bibleIndex = built
        return built
    })().finally(() => {
        if (bibleIndexBuildingId === bibleId) {
            bibleIndexBuildingId = ""
            bibleIndexBuildPromise = null
        }
    })

    return bibleIndexBuildPromise
}

function indexWords(index: Map<string, IndexEntry[]>, showId: string, words: string[], field: "name" | "content") {
    const wordFreq = new Map<string, number>()
    for (const word of words) {
        if (word.length < 2) continue // Skip very short words
        wordFreq.set(word, (wordFreq.get(word) || 0) + 1)
    }

    for (const [word, freq] of wordFreq) {
        if (!index.has(word)) {
            index.set(word, [])
        }
        index.get(word)!.push({ showId, field, frequency: freq })
    }
}

function indexPhrases(index: Map<string, Set<string>>, showId: string, words: string[]) {
    // Index n-grams (2 to 5 words) for phrase matching
    for (let i = 0; i < words.length - 1; i++) {
        let phrase = words[i]
        for (let size = 2; size <= 5; size++) {
            if (i + size - 1 < words.length) {
                phrase += " " + words[i + size - 1]
                if (!index.has(phrase)) index.set(phrase, new Set())
                index.get(phrase)!.add(showId)
            } else {
                break
            }
        }
    }
}

/**
 * Fast search using the inverted index
 * Generates subphrases and individual words from the query
 */
export function fastSearch(searchValue: string, shows: ShowList[]): ShowList[] {
    // Ensure index is built
    if (!searchIndex || searchIndex.showData.size !== shows.length) {
        buildSearchIndex(shows)
    }

    const formattedQuery = formatSearch(searchValue, false)
    const queryWords = tokenize(formattedQuery)

    if (queryWords.length === 0) return []

    // Score accumulator: showId -> score
    const scores = new Map<string, number>()

    // Generate all subphrases and words to search for
    const searchTerms = generateSearchTerms(queryWords)

    // O(1) lookup for each term
    for (const { term, weight, isPhrase } of searchTerms) {
        if (isPhrase) {
            // Phrase lookup
            const matchingShows = searchIndex!.phraseIndex.get(term)
            if (matchingShows) {
                for (const showId of matchingShows) {
                    scores.set(showId, (scores.get(showId) || 0) + weight * 20)
                }
            }
        } else {
            // Word lookup
            const entries = searchIndex!.wordIndex.get(term)
            if (entries) {
                for (const entry of entries) {
                    const fieldBonus = entry.field === "name" ? 3 : 1
                    const freqBonus = Math.min(entry.frequency, 5) * 0.5
                    scores.set(entry.showId, (scores.get(entry.showId) || 0) + weight * fieldBonus + freqBonus)
                }
            }

            // Prefix matching for partial words
            if (term.length >= 3) {
                for (const [indexedWord, entries] of searchIndex!.wordIndex) {
                    if (indexedWord.startsWith(term) && indexedWord !== term) {
                        for (const entry of entries) {
                            const fieldBonus = entry.field === "name" ? 2 : 0.5
                            scores.set(entry.showId, (scores.get(entry.showId) || 0) + weight * 0.3 * fieldBonus)
                        }
                    }
                }
            }
        }
    }

    // Convert scores to results
    const showMap = new Map(shows.map((s) => [s.id, s]))
    let results: ShowList[] = []

    for (const [showId, score] of scores) {
        const show = showMap.get(showId)
        if (show && score > 0) {
            // Don't include archived unless viewing archive
            const isArchived = get(categories)[show.category || ""]?.isArchive
            if (isArchived && get(drawerTabsData).shows?.activeSubTab !== show.category) continue

            results.push({ ...show, match: score })
        }
    }

    // Sort by score descending
    results = sortObjectNumbers(results, "match", true)

    // Normalize scores to 0-100
    const maxScore = results[0]?.match || 1
    results = results.map((r) => ({ ...r, originalMatch: r.match, match: ((r.match || 0) / maxScore) * 100 }))

    return results
}

/**
 * Generate all search terms from query words
 * For "valley of dry bones" generates:
 * - Full phrase: "valley of dry bones" (weight: 1.0)
 * - Subphrases: "valley of dry", "of dry bones", "valley of", "dry bones" (weight: 0.8)
 * - Individual words: "valley", "dry", "bones" (weight: 0.5), skip "of" (too short)
 */
function generateSearchTerms(words: string[], minWordLength = 3): { term: string; weight: number; isPhrase: boolean }[] {
    const terms: { term: string; weight: number; isPhrase: boolean }[] = []
    const n = words.length

    if (n === 0) return terms

    // Full phrase (highest weight)
    if (n >= 2) {
        terms.push({ term: words.join(" "), weight: 1.0, isPhrase: true })
    }

    // Contiguous subphrases (sliding window)
    for (let windowSize = Math.min(n - 1, 5); windowSize >= 2; windowSize--) {
        for (let i = 0; i <= n - windowSize; i++) {
            const subphrase = words.slice(i, i + windowSize).join(" ")
            // Weight decreases as subphrase gets shorter
            const weight = 0.6 + (windowSize / n) * 0.2
            terms.push({ term: subphrase, weight, isPhrase: true })
        }
    }

    // Individual words (filter out very short ones)
    for (const word of words) {
        if (word.length >= minWordLength) {
            terms.push({ term: word, weight: 0.5, isPhrase: false })
        }
    }

    return terms
}

// invalidate search index when text cache changes
export function invalidateSearchIndex() {
    searchIndex = null
}
