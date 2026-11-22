<script lang="ts">
    import type JSONBible from "json-bible"
    import type { ApiBible } from "json-bible/lib/api"
    import type { Verse } from "json-bible/lib/Bible"
    import type { VerseReference } from "json-bible/lib/reference"
    import { onDestroy, onMount } from "svelte"
    import { fromNepaliDigits, localizeNumberText, shouldUseNepaliLocale } from "../../../../common/nepali"
    import { sanitizeVerseText } from "../../../../common/scripture/sanitizeVerseText"
    import { defaultBibleBookNames } from "../../../converters/bebliaBible"
    import { activeEdit, activeScripture, activeTriggerFunction, customScriptureBooks, language, notFound, openScripture, outLocked, outputs, resized, scriptureHistory, scriptureHistoryUsed, scriptureMode, scriptures, scripturesCache, scriptureSettings, selected } from "../../../stores"
    import { buildBibleIndex, fastBibleSearch, normalizeBibleLooseText, normalizeBibleSearchText } from "../../../utils/searchFast"
    import { wait } from "../../../utils/common"
    import { translateText } from "../../../utils/language"
    import { clone } from "../../helpers/array"
    import Icon from "../../helpers/Icon.svelte"
    import { nepaliTypingInput } from "../../helpers/inputActions"
    import T from "../../helpers/T.svelte"
    import FloatingInputs from "../../input/FloatingInputs.svelte"
    import InputRow from "../../input/InputRow.svelte"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import MaterialCheckbox from "../../inputs/MaterialCheckbox.svelte"
    import MaterialNumberInput from "../../inputs/MaterialNumberInput.svelte"
    import TextInput from "../../inputs/TextInput.svelte"
    import Loader from "../../main/Loader.svelte"
    import Center from "../../system/Center.svelte"
    import { createScriptureShow, formatBibleText, getVerseIdParts, getVersePartLetter, joinRange, loadJsonBible, moveSelection, outputIsScripture, playScripture, scriptureRangeSelect, sortScriptureSelection, splitText, swapPreviewBible } from "./scripture"

    export let active: string | null
    export let searchValue: string

    $: activeScriptureId = active || ""
    $: activeScriptures = [activeScriptureId]
    $: {
        const activeScriptureData = $scriptures[activeScriptureId]
        if (activeScriptureData?.collection?.versions) activeScriptures = activeScriptureData.collection.versions
    }

    $: previewBibleIndex = $scriptures[activeScriptureId]?.collection?.previewIndex || 0
    $: previewBibleId = activeScriptures[previewBibleIndex] || activeScriptures[0]
    $: previewBibleData = clone($scriptures[previewBibleId] || null)

    $: isApi = !!previewBibleData?.api
    $: isCollection = activeScriptures.length > 1

    // custom data
    $: if (previewBibleData?.copyright) previewBibleData.metadata = { ...(previewBibleData.metadata || {}), copyright: previewBibleData.copyright }
    $: if (previewBibleData?.customName) previewBibleData.name = previewBibleData.customName

    // auto load scriptures when changed
    // timeout is to load drawer tab "instantly", before loading scripture
    $: setTimeout(() => loadScripture(previewBibleId), 10)

    // Load all scriptures in collection for multi-version display
    $: if (isCollection) {
        activeScriptures.forEach((id) => {
            if (id && !data[id]) {
                loadScriptureForCollection(id)
            }
        })
    }

    async function loadScriptureForCollection(id: string) {
        if (!id || data[id]) return
        try {
            const jsonBible = await loadJsonBible(id)
            if (!jsonBible) return

            data[id] = { bibleData: jsonBible }
            data = data // trigger reactivity
        } catch (err) {
            console.error("Error loading collection scripture:", id, err)
        }
    }

    // Track what book/chapter each scripture's data is loaded for
    let collectionLoadedFor: { [scriptureId: string]: { book: number | string | null; chapter: number | string | null } } = {}

    // Reset tracking when the active scripture/collection changes
    $: if (activeScriptureId) collectionLoadedFor = {}

    // Load book/chapter data for all collection scriptures when navigating
    async function loadCollectionBookChapter(targetBook: number | string, targetChapter: number | string) {
        if (!isCollection) return

        for (const scriptureId of activeScriptures) {
            if (scriptureId === previewBibleId) continue
            const scriptureData = data[scriptureId]
            if (!scriptureData?.bibleData) continue

            const currentLoaded = collectionLoadedFor[scriptureId]
            const bookNeedsReload = !scriptureData.bookData || currentLoaded?.book !== targetBook
            const chapterNeedsReload = !scriptureData.chapterData || currentLoaded?.chapter !== targetChapter || bookNeedsReload

            try {
                // Load book if needed (book changed or not loaded)
                if (bookNeedsReload) {
                    scriptureData.bookData = await scriptureData.bibleData.getBook(targetBook)
                    // Clear chapter data when book changes since it's for the old book
                    delete scriptureData.chapterData
                }
                // Load chapter if needed (chapter changed or not loaded)
                if (chapterNeedsReload && scriptureData.bookData) {
                    scriptureData.chapterData = await scriptureData.bookData.getChapter(Number(targetChapter))
                }

                // Update tracking for this scripture
                collectionLoadedFor[scriptureId] = { book: targetBook, chapter: targetChapter }

                data = data // trigger reactivity
            } catch (err) {
                console.error("Error loading collection book/chapter:", scriptureId, err)
            }
        }
    }

    // Trigger loading book/chapter for all collection scriptures when reference changes
    $: if (isCollection && activeReference.book && activeReference.chapters.length) {
        loadCollectionBookChapter(activeReference.book, activeReference.chapters[0])
    }

    const GOLDEN_ANGLE = 137.508
    const BASE_HUE = 330
    function getVersionColor(index: number): string {
        const hue = (BASE_HUE + index * GOLDEN_ANGLE) % 360
        return `hsl(${hue}, 75%, 65%)`
    }
    function getVersionBgColor(index: number): string {
        const hue = (BASE_HUE + index * GOLDEN_ANGLE) % 360
        return `hsla(${hue}, 70%, 50%, 0.12)`
    }

    // Get verses for all scriptures in a collection
    function getCollectionVerses(verseId: string | number, _updater: any): { id: string; name: string; text: string; isSplit: boolean }[] {
        if (!isCollection) return []

        const { id, subverse } = getVerseIdParts(verseId)
        const isSplit = subverse > 0

        return activeScriptures
            .map((scriptureId) => {
                const scriptureData = data[scriptureId]
                const scriptureMeta = $scriptures[scriptureId]
                const name = scriptureMeta?.customName || scriptureMeta?.name || scriptureId

                const offset = getOffset(scriptureId)
                const currentId = id + offset

                if (!scriptureData?.chapterData || (offset && (currentId < 1 || currentId > scriptureData.chapterData.data.verses.length))) {
                    return { id: scriptureId, name, text: "", isSplit: false }
                }

                try {
                    const verse = scriptureData.chapterData.getVerse(currentId)
                    const fullText = verse.getHTML() || verse?.data?.text || ""

                    if (isSplit && fullText) {
                        const splitParts = splitText(fullText, splitChars, splitTolerance)
                        if (splitParts.length > 1) {
                            return { id: scriptureId, name, text: splitParts[subverse - 1] || "", isSplit: true }
                        }
                    }

                    return { id: scriptureId, name, text: fullText, isSplit: false }
                } catch {
                    return { id: scriptureId, name, text: "", isSplit: false }
                }
            })
            .filter((v) => v.text)
    }

    $: isActiveInOutput = outputIsScripture($outputs)

    type Reference = {
        book: number | string | null
        chapters: (number | string)[] // can have multiple chapters open
        verses: (number | string)[][] // can have multiple verses open per chapter
    }

    let activeReference: Reference = {
        book: $activeScripture.reference?.book || null,
        chapters: $activeScripture.reference?.chapters || [],
        verses: $activeScripture.reference?.verses || []
    }

    type BibleReturn = Awaited<ReturnType<typeof JSONBible>> | Awaited<ReturnType<typeof ApiBible>>
    type TBook = Awaited<ReturnType<BibleReturn["getBook"]>>
    type TChapter = Awaited<ReturnType<TBook["getChapter"]>>
    type TVerse = ReturnType<TChapter["getVerses"]>
    type Data = {
        bibleData?: BibleReturn
        bookData?: TBook
        chapterData?: TChapter
        verseData?: TVerse
    }

    let data: { [key: string]: Data } = {}
    let referenceSearchField = ""

    $: currentBibleData = data[previewBibleId] || null
    $: currentBible = currentBibleData?.bibleData?.data || null
    $: books = currentBible?.books || null
    $: chapters = currentBibleData?.bookData?.data?.chapters || null
    // $: verses = currentBibleData?.chapterData?.data?.verses || null
    $: verses = currentBibleData?.chapterData?.data?.verses?.map((a) => ({ ...a, text: currentBibleData?.chapterData?.getVerse(a.number).getHTML() || "" })) || null

    // category color / abbreviation data
    $: booksData = currentBibleData?.bibleData?.getBooksData() || []

    // Check if any translation in collection supports splitting for verses
    function checkCollectionSplitSupport(): { [verseNumber: number]: number } {
        const currentVerses = verses
        if (!isCollection || !$scriptureSettings.splitLongVerses || !currentVerses) return {}

        const splitCounts: { [verseNumber: number]: number } = {}

        activeScriptures.forEach((scriptureId) => {
            const chapterData = data[scriptureId]?.chapterData
            if (!chapterData) return

            currentVerses.forEach((verse) => {
                try {
                    const verseObj = chapterData.getVerse(verse.number)
                    const fullText = verseObj.getHTML() || verseObj?.data?.text || ""
                    if (!fullText) return

                    const splitParts = splitText(fullText, splitChars, splitTolerance)
                    if (splitParts.length > 1) {
                        splitCounts[verse.number] = Math.max(splitCounts[verse.number] || 0, splitParts.length)
                    }
                } catch {}
            })
        })

        return splitCounts
    }

    // Extract values to ensure proper reactivity
    $: splitChars = Number($scriptureSettings.longVersesChars || 100)
    $: splitTolerance = Number($scriptureSettings.longVersesTolerance || 0)
    $: splitEnabled = $scriptureSettings.splitLongVerses

    let collectionSplitCounts: { [verseNumber: number]: number } = {}
    $: collectionSplitCounts = isCollection && splitEnabled ? checkCollectionSplitSupport() : {}

    let splittedVerses: (Verse & { id: string })[] = []
    $: splittedVerses = updateSplitted(verses, $scriptureSettings, collectionSplitCounts)

    let apiError = false

    async function loadScripture(id: string) {
        apiError = false

        if (!id) return
        if (data[id]) {
            const existingRaw = $scripturesCache[id] || data[id]?.bibleData?.data
            if (!$scriptures[id]?.api && existingRaw?.books?.length) {
                void buildBibleIndex(id, existingRaw)
            }
            openBook()
            return
        }

        try {
            const jsonBible = await loadJsonBible(id)
            if (!jsonBible) return

            data[id] = { bibleData: jsonBible }
            const rawLoaded = $scripturesCache[id] || jsonBible?.data
            if (!$scriptures[id]?.api && rawLoaded?.books?.length) {
                void buildBibleIndex(id, rawLoaded)
            }
        } catch (err) {
            console.error(err)
            if (isApi) apiError = true
            return
        }

        openBook()
    }

    // WIP similar to getSplittedVerses in scripture.ts
    function updateSplitted(verses: Verse[] | null, _updater: any, collectionSplitCounts: { [verseNumber: number]: number } = {}) {
        if (!verses) return []
        if (!$scriptureSettings.splitLongVerses) return verses.map((verse) => ({ ...verse, id: (verse.number || "").toString() + (verse.endNumber ? "-" + verse.endNumber : "") }))

        const newVerses: (Verse & { id: string })[] = []
        verses.forEach((verse) => {
            const sanitizedVerse = sanitizeVerseText(verse.text)
            const newVerseStrings = splitText(sanitizedVerse, splitChars, splitTolerance)
            const end = verse.endNumber ? `-${verse.endNumber}` : ""
            const numParts = Math.max(newVerseStrings.length, collectionSplitCounts[verse.number] || 0)

            if (numParts > 1) {
                for (let i = 0; i < numParts; i++) {
                    const text = newVerseStrings[i] || (i === 0 ? newVerseStrings[0] || verse.text : "")
                    newVerses.push({ ...verse, id: `${verse.number}_${i + 1}${end}`, text })
                }
            } else {
                newVerses.push({ ...verse, id: `${verse.number}${end}`, text: newVerseStrings[0] || verse.text })
            }
        })

        return newVerses
    }

    function buildVerseLabel(id: number, subverse: number, endNumber: number, showSuffix: boolean) {
        const baseVisible = !subverse || subverse === 1 || showSuffix
        const base = baseVisible ? localizeNumberText(`${id}${endNumber ? "-" + endNumber : ""}`, $language) : ""
        const suffix = showSuffix && subverse ? getVersePartLetter(subverse) : ""
        return { base, suffix }
    }

    function isNepaliBibleActive() {
        return previewBibleData?.name === "Nepali Bible" || previewBibleData?.metadata?.abbreviation === "NNRV"
    }

    function getDisplayBookName(book: { name?: string; number?: number | string } | undefined, index: number) {
        const customName = $customScriptureBooks[previewBibleId]?.[index]
        const englishName = defaultBibleBookNames[Number(book?.number) || index + 1] || ""

        if (isNepaliBibleActive()) return customName || book?.name || englishName
        if (shouldUseNepaliLocale($language)) return customName || book?.name || englishName
        return englishName || customName || book?.name || ""
    }

    function referenceInputChanged(e: any) {
        const nextVal = e.target?.value || ""
        referenceSearchField = nextVal
        lastSyncedDrawerSearch = nextVal
        searchValue = nextVal
        scheduleUnifiedBibleSearch(nextVal, "toolbar")
    }

    function getLocalizedReferenceRange() {
        return localizeNumberText(joinRange(sortScriptureSelection(activeReference.verses[0] || [])), $language)
    }

    function getLocalizedChapterReference() {
        return activeReference.chapters.map((chapter) => localizeNumberText(chapter, $language)).join(",")
    }

    function toggleChapter(e: any, id: string) {
        if (e.ctrlKey || e.metaKey) {
            if (activeReference.chapters.find((cid) => cid?.toString() === id)) {
                // remove chapter
                const newChapters = activeReference.chapters.filter((cid) => cid.toString() !== id)
                const newVerses = activeReference.verses.filter((_, i) => i < newChapters.length)
                openChapter(newChapters, newVerses)
            } else {
                // add chapter
                openChapter([...activeReference.chapters, id], [...activeReference.verses, []])
            }
        } else openChapter([id])
    }

    let navigationId = 0

    let pendingBookId: string | null = null
    let pendingBookPromise: any | null = null

    let pendingChapterId: string | null = null
    let pendingChapterPromise: any | null = null

    async function openBook(bookNumber?: number | string, chapterNumbers?: (number | string)[], verseNumbers?: (number | string)[][], navId?: number): Promise<boolean> {
        const currentNavId = navId ?? ++navigationId

        const targetBookNumber = bookNumber ?? activeReference.book ?? 1

        const currentData = data[previewBibleId]?.bibleData
        if (!currentData) return false

        // remove current data so loading shows again
        delete data[previewBibleId].bookData
        delete data[previewBibleId].chapterData
        delete data[previewBibleId].verseData
        data = data

        // load new data
        const bookId = `${previewBibleId}_${targetBookNumber}`
        if (pendingBookId !== bookId) {
            pendingBookId = bookId
            pendingBookPromise = currentData.getBook(targetBookNumber)
        }
        data[previewBibleId].bookData = await pendingBookPromise

        if (currentNavId !== navigationId) return false

        // reset chapter and verse when changing book
        if (bookNumber && !chapterNumbers) activeReference.chapters = []
        if (bookNumber && !verseNumbers) activeReference.verses = []
        activeReference.book = targetBookNumber

        return await openChapter(chapterNumbers, verseNumbers, currentNavId)
    }

    async function openChapter(chapterNumbers?: (number | string)[], verseNumbers?: (number | string)[][], navId?: number): Promise<boolean> {
        const currentNavId = navId ?? ++navigationId
        const targetChapterNumbers = chapterNumbers?.length ? chapterNumbers : activeReference.chapters?.length ? activeReference.chapters : [1]
        const currentData = data[previewBibleId]?.bookData
        if (!currentData) return false

        // remove current data so loading shows again
        delete data[previewBibleId].chapterData
        delete data[previewBibleId].verseData
        data = data

        // load new data
        // NOTE: if chapter is not a number it does not work
        const chapterNumber = Number(targetChapterNumbers[targetChapterNumbers.length - 1])

        const chapterId = `${previewBibleId}_${activeReference.book}_${chapterNumber}`
        if (pendingChapterId !== chapterId) {
            pendingChapterId = chapterId
            pendingChapterPromise = currentData.getChapter(chapterNumber)
        }
        data[previewBibleId].chapterData = await pendingChapterPromise

        if (currentNavId !== navigationId) return false

        // reset verse when changing chapter
        if (chapterNumbers && !verseNumbers) activeReference.verses = []
        activeReference.chapters = targetChapterNumbers

        openVerse(verseNumbers)
        return true
    }

    let playWhenLoaded = false
    async function openVerse(verseNumbers?: (number | string)[][]) {
        verseNumbers = verseNumbers?.length ? verseNumbers : activeReference.verses.length ? activeReference.verses : [[1]]
        activeReference.verses = verseNumbers

        const currentData = data[previewBibleId]?.chapterData
        if (!currentData) return

        // load new data
        try {
            data[previewBibleId].verseData = currentData.getVerses(verseNumbers.map(Number))
        } catch {
            // failed to load
            return
        }

        // newToast(translateText("toast.verse_undefined").replace("{}", verse))

        if (playWhenLoaded) setTimeout(playScripture)
        playWhenLoaded = false
    }

    // update active reference
    $: if (activeReference.verses[0]?.length && activeReference.book !== null) {
        activeScripture.set({
            id: previewBibleId,
            reference: {
                book: activeReference.book,
                chapters: activeReference.chapters,
                verses: activeReference.verses
            }
        })
    }

    // WIP move this?
    // select book & chapter when opening bible show reference
    $: if ($openScripture) setTimeout(openReference, 200)
    function openReference() {
        if ($openScripture?.book === undefined) {
            openScripture.set(null)
            return
        }

        if ($openScripture.play) playWhenLoaded = true

        let verses = $openScripture.verses
        if (!Array.isArray(verses)) verses = [[1]]
        if (!Array.isArray(verses[0])) verses = [verses]

        openBook(Number($openScripture.book), [$openScripture.chapter], verses)

        openScripture.set(null)
    }

    /// HISTORY ///

    let historyOpened = false
    $: currentHistory = clone($scriptureHistory.filter((a) => a.id === previewBibleId)).reverse()

    /// AUTOSCROLL ///

    let booksScrollElem: HTMLElement | undefined
    let chaptersScrollElem: HTMLElement | undefined
    let versesScrollElem: HTMLElement | undefined
    $: if (activeScriptureId && activeReference.book) setTimeout(() => scrollToActive(booksScrollElem))
    $: if (activeScriptureId && activeReference.chapters.length) setTimeout(() => scrollToActive(chaptersScrollElem))
    $: if (activeScriptureId && activeReference.verses[0]?.length) setTimeout(() => scrollToActive(versesScrollElem))
    function scrollToActive(scrollElem) {
        if (!scrollElem || isSelected) return

        let selectedElemTop = scrollElem.querySelector(".isActive")?.offsetTop || 0

        // don't scroll if elem is in view
        let visibleElemPos = selectedElemTop - scrollElem.scrollTop
        if (visibleElemPos > 0 && visibleElemPos < scrollElem.offsetHeight) return

        scrollElem.scrollTo(0, Math.max(0, selectedElemTop - 70))
    }

    /// SELECTION ///

    onMount(() => selected.set({ id: "scripture", data: [] }))

    let previousSelection: (number | string)[] = []
    let isSelected = false
    let selectedTimeout: NodeJS.Timeout | null = null
    function updateVersesSelection(e: any, verseNumber: string, isClick: boolean = false) {
        const selectedVerses = clone(activeReference.verses)

        if (isClick) {
            if (previousSelection.find((a) => a.toString() === verseNumber)) {
                return [[verseNumber]]
            }
            return selectedVerses
        }

        previousSelection = clone(selectedVerses[selectedVerses.length - 1])

        isSelected = true
        if (selectedTimeout) clearTimeout(selectedTimeout)
        selectedTimeout = setTimeout(() => (isSelected = false), 100)

        const keys = e.ctrlKey || e.metaKey || e.shiftKey
        if (keys || !selectedVerses[selectedVerses.length - 1]?.find((a) => a && (a.toString() === verseNumber || a === getVerseId(verseNumber)))) {
            selectedVerses[selectedVerses.length - 1] = scriptureRangeSelect(e, selectedVerses[selectedVerses.length - 1], verseNumber, splittedVerses)

            // Remove plain verse IDs if split versions exist (e.g., remove "1" if "1_1", "1_2" are present)
            const currentSelection = selectedVerses[selectedVerses.length - 1]
            const splitVerseIds = currentSelection.filter((id) => id.toString().includes("_"))
            if (splitVerseIds.length > 0) {
                const baseIdsToRemove = new Set(splitVerseIds.map((id) => id.toString().split("_")[0]))
                selectedVerses[selectedVerses.length - 1] = currentSelection.filter((id) => {
                    const idStr = id.toString()
                    return idStr.includes("_") || !baseIdsToRemove.has(idStr)
                })
            }

            // deselecting a verse
            if (!selectedVerses[selectedVerses.length - 1]?.find((id) => id.toString() === verseNumber)) {
                previousSelection = clone(selectedVerses[selectedVerses.length - 1])
            }
        }

        // drop action (create slide/show from drag&drop)
        selected.set({ id: "scripture", data: [] })

        return selectedVerses

        function getVerseId(verseRef: number | string) {
            if (!verseRef) return 1
            return Number(verseRef.toString().split("_")[0])
        }
    }

    $: if ($activeTriggerFunction === "scripture_selectAll") selectAllVerses()
    function selectAllVerses() {
        if (!splittedVerses) return

        openVerse([splittedVerses.map((a) => a.id)])

        // update
        setTimeout(() => (activeReference = activeReference))
    }

    /// SEARCH ///

    const ROMAN_NEPALI_BOOK_ALIASES: Record<number, string[]> = {
        1: ["utpatti", "gen", "genesis"],
        2: ["prasthan", "exo", "exod", "exodus"],
        3: ["levi", "lev", "leviticus"],
        4: ["ganti", "num", "numbers"],
        5: ["vyavastha", "byawastha", "deu", "deut", "deuteronomy"],
        6: ["yahoshu", "jos", "josh", "joshua"],
        7: ["nyayakartaharu", "nyayakarta", "jdg", "judg", "judges"],
        8: ["ruth", "rut"],
        9: ["1 shamuel", "1 samuel", "1sam", "1 sam", "1sa"],
        10: ["2 shamuel", "2 samuel", "2sam", "2 sam", "2sa"],
        11: ["1 rajaharu", "1 raja", "1 kings", "1ki", "1 kgs"],
        12: ["2 rajaharu", "2 raja", "2 kings", "2ki", "2 kgs"],
        13: ["1 itihas", "1 chronicles", "1ch", "1 chr"],
        14: ["2 itihas", "2 chronicles", "2ch", "2 chr"],
        15: ["ejra", "ezra", "ezr"],
        16: ["nahemyah", "nehemiah", "neh"],
        17: ["estar", "esther", "est"],
        18: ["ayyub", "ayub", "job"],
        19: ["bhajansangraha", "bhajan sangraha", "psalm", "psalms", "ps", "psa"],
        20: ["hitopadesh", "proverbs", "prov", "pro"],
        21: ["upadeshak", "ecclesiastes", "eccl", "ecc"],
        22: ["shreshthagit", "song of solomon", "song of songs", "sos", "sng"],
        23: ["yashaiya", "yesaiya", "isaiah", "isa"],
        24: ["yarmiya", "jeremiah", "jer"],
        25: ["yarmiyako vilap", "vilap", "lamentations", "lam"],
        26: ["ijakiel", "ezekiel", "ezek", "ezk"],
        27: ["daniyal", "daniel", "dan"],
        28: ["hoshe", "hosea", "hos"],
        29: ["yoel", "joel", "jol"],
        30: ["amos", "amo"],
        31: ["obadiya", "obadiah", "oba"],
        32: ["yona", "jonah", "jon"],
        33: ["mika", "micah", "mic"],
        34: ["nahum", "nah", "nam"],
        35: ["habakuk", "habakkuk", "hab"],
        36: ["sapanyah", "zephaniah", "zeph", "zep"],
        37: ["haggai", "hag"],
        38: ["jakariya", "zechariah", "zech", "zec"],
        39: ["malaki", "malachi", "mal"],
        40: ["matti", "matthew", "matt", "mat"],
        41: ["markus", "mark", "mrk", "mk"],
        42: ["luka", "luke", "luk", "lk"],
        43: ["yuhanna", "john", "jhn", "jn"],
        44: ["preritharuka kam", "prerit", "acts", "act"],
        45: ["romi", "romans", "rom"],
        46: ["1 korinthi", "1 corinthians", "1cor", "1 cor", "1co"],
        47: ["2 korinthi", "2 corinthians", "2cor", "2 cor", "2co"],
        48: ["galati", "galatians", "gal"],
        49: ["ephisi", "ephesians", "eph"],
        50: ["philippi", "philippians", "phil", "php"],
        51: ["kalassi", "colossians", "col"],
        52: ["1 thissaloniki", "1 thessalonians", "1thess", "1 thess", "1th"],
        53: ["2 thissaloniki", "2 thessalonians", "2thess", "2 thess", "2th"],
        54: ["1 timothi", "1 timothy", "1tim", "1 tim", "1ti"],
        55: ["2 timothi", "2 timothy", "2tim", "2 tim", "2ti"],
        56: ["titas", "titus", "tit"],
        57: ["philemon", "phlm", "phm"],
        58: ["hibru", "hebrews", "heb"],
        59: ["yakub", "james", "jas", "jam"],
        60: ["1 patrus", "1 peter", "1pet", "1 pet", "1pe"],
        61: ["2 patrus", "2 peter", "2pet", "2 pet", "2pe"],
        62: ["1 yuhanna", "1 john", "1jn", "1 jn"],
        63: ["2 yuhanna", "2 john", "2jn", "2 jn"],
        64: ["3 yuhanna", "3 john", "3jn", "3 jn"],
        65: ["yahuda", "jude", "jud"],
        66: ["prakash", "revelation", "rev"]
    }

    let selectAllTimeout: NodeJS.Timeout | null = null
    let unifiedSearchDebounce: ReturnType<typeof setTimeout> | null = null
    let bottomSearchDebounce: ReturnType<typeof setTimeout> | null = null
    let currentSearchId = 0
    let lastSyncedDrawerSearch = ""
    let lastPreviewBibleId = ""
    let exactBookSearchCandidate = ""
    let activeVerseSearchQuery = ""

    let contentSearchFieldActive = false
    let contentSearchValue = ""
    let contentSearchResults: VerseReference[] | null = null

    $: if (previewBibleId && previewBibleId !== lastPreviewBibleId) {
        if (lastPreviewBibleId) {
            resetContentSearch(false)
        }
        lastPreviewBibleId = previewBibleId
    }

    $: if (searchValue !== lastSyncedDrawerSearch) {
        lastSyncedDrawerSearch = searchValue
        referenceSearchField = searchValue
        scheduleUnifiedBibleSearch(searchValue, "drawer")
    }

    onDestroy(() => {
        if (selectAllTimeout) clearTimeout(selectAllTimeout)
        if (unifiedSearchDebounce) clearTimeout(unifiedSearchDebounce)
        if (bottomSearchDebounce) clearTimeout(bottomSearchDebounce)
    })

    function normalizeBookLookupKey(value: string): string {
        return normalizeBibleSearchText(fromNepaliDigits(String(value || ""))).replace(/\s+/g, " ").trim()
    }

    function parseVerseSpecList(rawSpec: string): number[] {
        if (!rawSpec) return []
        const result: number[] = []
        for (const chunk of rawSpec.split(/[+,]/)) {
            const trimmed = chunk.trim()
            if (!trimmed) continue
            const rangeMatch = trimmed.match(/^(\d+)\s*-\s*(\d+)$/)
            if (rangeMatch) {
                let start = Number(rangeMatch[1])
                let end = Number(rangeMatch[2])
                if (start > end) [start, end] = [end, start]
                if (start > 0 && end <= 200) {
                    for (let v = start; v <= end; v++) result.push(v)
                }
            } else {
                const single = Number(trimmed)
                if (Number.isFinite(single) && single > 0 && single <= 200) result.push(single)
            }
        }
        return result
    }

    function resolveSmartBookReference(rawQuery: string): { book: number; chapter: number; verses: number[]; exactBook: boolean } | null {
        const cleaned = fromNepaliDigits(String(rawQuery || ""))
            .replace(/[\u200B-\u200D\uFEFF]/g, "")
            .replace(/\s+/g, " ")
            .trim()
        if (!cleaned) return null

        const refMatch = cleaned.match(/^(.+?)(?:\s+(\d+)(?:\s*[:.,]\s*([0-9,\-+]+))?)?$/u)
        if (!refMatch) return null

        const rawBookPart = refMatch[1].trim()
        const chapter = refMatch[2] ? Number(refMatch[2]) : 0
        const verses = refMatch[3] ? parseVerseSpecList(refMatch[3]) : []
        const bookKey = normalizeBookLookupKey(rawBookPart)
        const bookKeyNoSpace = bookKey.replace(/\s+/g, "")
        const bookLoose = normalizeBibleLooseText(bookKey)
        if (!bookKey) return null

        const bibleBooks = books || []
        const customNames = $customScriptureBooks[previewBibleId] || []

        // Pass 1: Exact match against local book name, custom name, English name, abbreviation, or Romanized Nepali alias
        for (let i = 0; i < Math.max(bibleBooks.length, 66); i++) {
            const bookObj = bibleBooks[i]
            const bookNum = Number(bookObj?.number) || i + 1
            if (bibleBooks.length && !bibleBooks.some((b) => Number(b.number) === bookNum)) continue

            const namesToCheck = [
                bookObj?.name,
                customNames[i],
                bookObj?.abbreviation,
                defaultBibleBookNames[bookNum],
                ...(ROMAN_NEPALI_BOOK_ALIASES[bookNum] || [])
            ].filter(Boolean) as string[]

            for (const candidate of namesToCheck) {
                const candKey = normalizeBookLookupKey(candidate)
                if (!candKey) continue
                if (candKey === bookKey || candKey.replace(/\s+/g, "") === bookKeyNoSpace || normalizeBibleLooseText(candKey) === bookLoose) {
                    return { book: bookNum, chapter, verses, exactBook: true }
                }
            }
        }

        // Pass 2: Prefix match when a chapter number is provided (e.g. "उत्प 1:2", "मत 5:3", "Gen 1:2", "Matt 5")
        if (chapter > 0 && bookKey.length >= 2) {
            const prefixMatches: number[] = []
            for (let i = 0; i < Math.max(bibleBooks.length, 66); i++) {
                const bookObj = bibleBooks[i]
                const bookNum = Number(bookObj?.number) || i + 1
                if (bibleBooks.length && !bibleBooks.some((b) => Number(b.number) === bookNum)) continue

                const namesToCheck = [
                    bookObj?.name,
                    customNames[i],
                    defaultBibleBookNames[bookNum],
                    ...(ROMAN_NEPALI_BOOK_ALIASES[bookNum] || [])
                ].filter(Boolean) as string[]

                if (namesToCheck.some((cand) => {
                    const ck = normalizeBookLookupKey(cand)
                    return ck.startsWith(bookKey) || normalizeBibleLooseText(ck).startsWith(bookLoose)
                })) {
                    prefixMatches.push(bookNum)
                }
            }
            if (prefixMatches.length === 1) {
                return { book: prefixMatches[0], chapter, verses, exactBook: false }
            }
        }

        // Fallback to json-bible bookSearch ONLY when chapter > 0 so partial words never hijack verse text searches
        if (chapter > 0) {
            const jb = currentBibleData?.bibleData?.bookSearch(cleaned)
            if (jb?.book) {
                return {
                    book: Number(jb.book),
                    chapter: Number(jb.chapter || chapter),
                    verses: jb.verses?.length ? jb.verses : verses,
                    exactBook: false
                }
            }
        }

        return null
    }

    function scheduleUnifiedBibleSearch(rawVal: string, source: "drawer" | "toolbar") {
        if (unifiedSearchDebounce) clearTimeout(unifiedSearchDebounce)
        const trimmed = rawVal.trim()
        if (!trimmed) {
            exactBookSearchCandidate = ""
            activeVerseSearchQuery = ""
            contentSearchResults = null
            return
        }

        unifiedSearchDebounce = setTimeout(() => {
            void runUnifiedBibleSearch(rawVal, source)
        }, 110)
    }

    async function runUnifiedBibleSearch(rawVal: string, _source: "drawer" | "toolbar") {
        const searchId = ++currentSearchId
        if (selectAllTimeout) clearTimeout(selectAllTimeout)
        exactBookSearchCandidate = ""

        const normalizedDigitsInput = fromNepaliDigits(rawVal).replace(/[\u200B-\u200D\uFEFF]/g, "").trim()
        if (!normalizedDigitsInput) {
            contentSearchResults = null
            return
        }

        // 1) Multi-chapter / semicolon reference (e.g. "उत्पत्ति 1:1-5;2:1-3" or "Genesis 1:1-12;2:1-10")
        const multiReference = await parseMultiChapterReference(normalizedDigitsInput)
        if (searchId !== currentSearchId) return
        if (multiReference) {
            contentSearchResults = null
            activeVerseSearchQuery = ""
            await openBook(multiReference.bookNumber, multiReference.chapters, multiReference.verses)
            return
        }

        // 2) Smart Book / Chapter / Verse reference (Nepali, Nepali digits, Romanized Nepali, or English)
        const smartRef = resolveSmartBookReference(rawVal)
        if (smartRef?.book) {
            if (smartRef.chapter > 0) {
                contentSearchResults = null
                activeVerseSearchQuery = ""
                const bookChanged = smartRef.book.toString() !== activeReference.book?.toString()
                const chapterChanged = !activeReference.chapters.length || smartRef.chapter.toString() !== activeReference.chapters[0]?.toString()
                const newVerses = smartRef.verses.length
                    ? !bookChanged && !chapterChanged && splittedVerses?.length
                        ? [splittedVerses.filter((a) => smartRef.verses.includes(a.number)).map((a) => a.id)]
                        : [smartRef.verses]
                    : undefined

                let success = true
                if (bookChanged) {
                    success = await openBook(smartRef.book, [smartRef.chapter], newVerses)
                } else if (chapterChanged) {
                    success = await openChapter([smartRef.chapter], newVerses)
                } else if (newVerses) {
                    openVerse(newVerses)
                }

                if (searchId === currentSearchId && success && !newVerses) {
                    selectAllTimeout = setTimeout(selectAllVerses)
                }
                return
            }

            if (smartRef.exactBook) {
                contentSearchResults = null
                activeVerseSearchQuery = ""
                exactBookSearchCandidate = normalizedDigitsInput
                if (smartRef.book.toString() !== activeReference.book?.toString()) {
                    await openBook(smartRef.book)
                }
                return
            }
        }

        // 3) Otherwise, search all Bible verses directly (e.g. "येशू ख्रीष्ट", "परमेश्वरले संसारलाई", "प्रेम", "love")
        if (normalizedDigitsInput.length >= 2) {
            contentSearchValue = rawVal
            await searchInBible(rawVal)
        } else {
            contentSearchResults = null
        }
    }

    type MultiReference = {
        bookNumber: number
        referenceLabel: string
        chapters: (number | string)[]
        verses: (number | string)[][]
    }

    // Parse references like "Genesis 1:1-12;2:1-10" into discrete chapter/verse selections.
    async function parseMultiChapterReference(value: string): Promise<MultiReference | null> {
        const bibleData = currentBibleData?.bibleData
        if (!bibleData) return null

        const sanitizedValue = fromNepaliDigits(value || "").replace(/[\u200B-\u200D\uFEFF]/g, "").replace(/\s+/g, " ").trim()
        if (!sanitizedValue) return null

        const rawSegments = sanitizedValue
            .split(";")
            .map((segment) => segment.trim())
            .filter(Boolean)
        const segmentsToProcess = rawSegments.length ? rawSegments : [sanitizedValue]

        const firstSegment = segmentsToProcess[0]
        const smartFirst = resolveSmartBookReference(firstSegment.includes("-") ? firstSegment.split("-")[0]?.trim() || firstSegment : firstSegment)
        let bookNumber = Number(smartFirst?.book || 0)
        if (!bookNumber) {
            let baseResult = bibleData.bookSearch(firstSegment)
            if (!baseResult?.book && firstSegment.includes("-")) {
                const fallbackTarget = firstSegment.split("-")[0]?.trim()
                if (fallbackTarget) baseResult = bibleData.bookSearch(fallbackTarget)
            }
            bookNumber = Number(baseResult?.book || 0)
        }
        if (!bookNumber) return null

        const canonicalBookName = books?.find((book) => Number(book.number) === bookNumber)?.name || firstSegment

        const resolvedSegments: string[] = []
        for (let i = 0; i < segmentsToProcess.length; i++) {
            const rawSegment = segmentsToProcess[i]
            const normalizedSegment = normalizeSegment(rawSegment, canonicalBookName, bookNumber, bibleData)
            const expandedSegments = await expandCrossChapterSegment(normalizedSegment, canonicalBookName, bookNumber, bibleData)
            resolvedSegments.push(...expandedSegments)
        }

        const hasExplicitSplit = rawSegments.length > 1
        if (!hasExplicitSplit && resolvedSegments.length <= 1) return null

        const chapters: (number | string)[] = []
        const verses: (number | string)[][] = []
        for (const segment of resolvedSegments) {
            const smartParsed = resolveSmartBookReference(segment)
            const parsed = smartParsed?.chapter ? { chapter: smartParsed.chapter, verses: smartParsed.verses } : bibleData.bookSearch(segment)
            if (!parsed?.chapter) return null

            const chapterNumber = Number(parsed.chapter)
            const verseList = parsed.verses?.length ? parsed.verses : await getEntireChapterVerses(bookNumber, chapterNumber, bibleData)
            if (!verseList?.length) return null

            chapters.push(chapterNumber)
            verses.push(verseList)
        }

        return {
            bookNumber,
            referenceLabel: buildReferenceLabel(canonicalBookName, resolvedSegments),
            chapters,
            verses
        }
    }

    // Ensure a reference chunk includes the book name so bookSearch can resolve it reliably.
    function normalizeSegment(segment: string, bookName: string, bookNumber: number, bibleData: any) {
        const trimmed = segment?.trim()
        if (!trimmed) return bookName

        const smartAttempt = resolveSmartBookReference(trimmed)
        if (smartAttempt?.book === bookNumber) return `${bookName} ${smartAttempt.chapter}${smartAttempt.verses.length ? ":" + smartAttempt.verses.join(",") : ""}`.trim()

        const attempt = bibleData.bookSearch(trimmed)
        if (attempt?.book === bookNumber) return trimmed

        return `${bookName} ${trimmed}`.replace(/\s+/g, " ").trim()
    }

    // Break a cross-chapter span (e.g. "1:1-2:10") into per-chapter segments.
    async function expandCrossChapterSegment(segment: string, bookName: string, bookNumber: number, bibleData: any) {
        const escapedBook = escapeRegExp(bookName)
        const remainder = segment.replace(new RegExp(`^${escapedBook}\\s*`, "i"), "").trim()
        const multiChapterMatch = remainder.match(/^(\d+):(\d+)\s*-\s*(\d+):(\d+)/)
        if (!multiChapterMatch) return [segment]

        const [_, startChapterStr, startVerseStr, endChapterStr, endVerseStr] = multiChapterMatch
        const startChapter = Number(startChapterStr)
        const startVerse = Number(startVerseStr)
        const endChapter = Number(endChapterStr)
        const endVerse = Number(endVerseStr)
        if (!startChapter || !endChapter || startChapter === endChapter) return [segment]

        const expanded: string[] = []
        for (let chapter = startChapter; chapter <= endChapter; chapter++) {
            const chapterStart = chapter === startChapter ? startVerse : 1
            const chapterEnd = chapter === endChapter ? endVerse : await getChapterLastVerse(bookNumber, chapter, bibleData)
            if (!chapterEnd) return [segment]
            expanded.push(`${bookName} ${chapter}:${chapterStart}-${chapterEnd}`)
        }

        return expanded
    }

    // Get the final verse number for a chapter so we can include the whole section when needed.
    async function getChapterLastVerse(bookNumber: number, chapterNumber: number, bibleData: any) {
        const bookData = await bibleData.getBook(bookNumber)
        const chapterData = await bookData.getChapter(chapterNumber)
        const verseEntries = chapterData?.data?.verses || []
        return Number(verseEntries[verseEntries.length - 1]?.number || verseEntries.length || 0)
    }

    // Return every verse index for a chapter when no explicit range was provided.
    async function getEntireChapterVerses(bookNumber: number, chapterNumber: number, bibleData: any) {
        const bookData = await bibleData.getBook(bookNumber)
        const chapterData = await bookData.getChapter(chapterNumber)
        return (chapterData?.data?.verses || []).map((verse) => Number(verse.number)).filter(Boolean)
    }

    // Format the combined reference so the UI shows "Book 1:1-12 ; 2:1-10".
    function buildReferenceLabel(bookName: string, segments: string[]) {
        const escapedBook = escapeRegExp(bookName)
        const bookRegex = new RegExp(`^${escapedBook}\\s*`, "i")
        const [firstSegment, ...rest] = segments
        const firstLabel = firstSegment.replace(bookRegex, "").trim() || firstSegment
        const restLabels = rest.map((segment) => segment.replace(bookRegex, "").trim())
        const suffix = restLabels.length ? restLabels.map((label) => ` ; ${label}`).join("") : ""
        return `${bookName} ${firstLabel}${suffix}`.trim()
    }

    // Escape user-facing book names before building regular expressions.
    function escapeRegExp(value: string) {
        return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
    }

    type RankedVerseReference = VerseReference & { score: number; source: "book" | "text" }

    function getVerseMatchKey(match: VerseReference) {
        return `${match.book}-${match.chapter}-${match.verse?.number || 0}`
    }

    function addRankedMatch(bucket: Map<string, RankedVerseReference>, match: VerseReference, score: number, source: "book" | "text") {
        if (!match?.book || !match?.chapter || !match?.verse?.number) return

        const key = getVerseMatchKey(match)
        const existing = bucket.get(key)
        if (!existing || score > existing.score) {
            bucket.set(key, { ...match, score, source })
        }
    }

    function getTextMatchScore(match: VerseReference, query: string) {
        const normalizedQuery = normalizeBibleSearchText(query)
        const reference = normalizeBibleSearchText(String(match.reference || ""))
        const verseText = normalizeBibleSearchText(String(match.verse?.text || ""))

        let score = 0
        if (reference.startsWith(normalizedQuery)) score += 600
        else if (reference.includes(normalizedQuery)) score += 450

        if (verseText.startsWith(normalizedQuery)) score += 230
        else if (verseText.includes(normalizedQuery)) score += 120

        return score
    }

    function searchValueChanged(e: any) {
        contentSearchValue = e.target?.value || ""
        if (bottomSearchDebounce) clearTimeout(bottomSearchDebounce)

        const trimmed = contentSearchValue.trim()
        if (trimmed.length < 2) {
            contentSearchResults = null
            activeVerseSearchQuery = ""
            return
        }

        bottomSearchDebounce = setTimeout(() => {
            void searchInBible(contentSearchValue)
        }, 110)
    }

    async function searchInBible(explicitQuery?: string | Event) {
        const rawQuery = typeof explicitQuery === "string" ? explicitQuery : contentSearchValue
        const query = rawQuery.trim()
        if (query.length < 2) {
            contentSearchResults = null
            activeVerseSearchQuery = ""
            return
        }

        const bibleData = currentBibleData?.bibleData
        if (!bibleData) {
            contentSearchResults = null
            return
        }

        activeVerseSearchQuery = query
        const rankedMatches = new Map<string, RankedVerseReference>()

        // Priority 1: reference/book matches.
        const smartRef = resolveSmartBookReference(query)
        const referenceResult = smartRef?.book ? { book: smartRef.book, chapter: smartRef.chapter || 1, verses: smartRef.verses } : bibleData.bookSearch(fromNepaliDigits(query))
        if (referenceResult?.book) {
            try {
                const bookData = await bibleData.getBook(referenceResult.book)
                const chapterNumber = Number(referenceResult.chapter || 1)
                const chapterData = await bookData.getChapter(chapterNumber)
                const verseNumbers = referenceResult.verses?.length ? referenceResult.verses : [1]

                verseNumbers.slice(0, 40).forEach((verseNumber, index) => {
                    const verse = chapterData.getVerse(Number(verseNumber))
                    const verseText = verse?.getHTML?.() || verse?.data?.text || ""
                    const match = {
                        reference: `${bookData.name} ${chapterNumber}:${Number(verseNumber)}`,
                        book: Number(referenceResult.book),
                        chapter: chapterNumber,
                        verse: { number: Number(verseNumber), text: verseText }
                    } as VerseReference

                    addRankedMatch(rankedMatches, match, 10_000 - index, "book")
                })
            } catch {
                // Ignore malformed reference results and continue with text search.
            }
        }

        // Priority 2: verse text search (with local fast index when available).
        let textMatches: VerseReference[] = []
        const scriptureMeta = $scriptures[previewBibleId]
        if (!scriptureMeta?.api && previewBibleId) {
            const rawBible = $scripturesCache[previewBibleId] || currentBibleData?.bibleData?.data
            if (rawBible?.books?.length) {
                await buildBibleIndex(previewBibleId, rawBible)
                const fastMatches = fastBibleSearch(query, 150)
                textMatches = fastMatches.map(
                    (match) =>
                        ({
                            reference: match.reference,
                            book: match.book,
                            chapter: match.chapter,
                            verse: { number: match.verse, text: match.text }
                        }) as VerseReference
                )
            }
        }

        if (!textMatches.length) {
            const cleanQueryForFallback = fromNepaliDigits(query).replace(/[\u200B-\u200D\uFEFF]/g, "")
            textMatches = ((await bibleData.textSearch(cleanQueryForFallback)) || []) as VerseReference[]
        }

        textMatches.forEach((match, index) => {
            const score = getTextMatchScore(match, query) + Math.max(0, 150 - index)
            addRankedMatch(rankedMatches, match, score, "text")
        })

        contentSearchResults = Array.from(rankedMatches.values())
            .sort((left, right) => {
                if (left.source !== right.source) return left.source === "book" ? -1 : 1
                return right.score - left.score
            })
            .map(({ score: _score, source: _source, ...match }) => match as VerseReference)
    }

    function resetContentSearch(clearInputs = true) {
        contentSearchValue = ""
        contentSearchResults = null
        contentSearchFieldActive = false
        activeVerseSearchQuery = ""
        exactBookSearchCandidate = ""
        if (clearInputs) {
            referenceSearchField = ""
            if (searchValue) {
                lastSyncedDrawerSearch = ""
                searchValue = ""
            }
        }
    }

    function formatMatchReference(match: VerseReference): string {
        const bookIdx = Math.max(Number(match.book || 1) - 1, 0)
        const bookObj = books?.find((b) => Number(b.number) === Number(match.book)) || books?.[bookIdx]
        const displayBook = getDisplayBookName(bookObj, bookIdx) || match.reference?.split(/\s+\d+/)[0] || ""
        const divider = $scriptureSettings.referenceDivider || ":"
        const chapVerse = localizeNumberText(`${match.chapter}${divider}${match.verse?.number || 1}`, $language)
        return `${displayBook} ${chapVerse}`.trim()
    }

    function highlightBibleMatchText(formattedHtml: string, rawQuery: string): string {
        if (!formattedHtml || !rawQuery) return formattedHtml
        const cleanedWords = fromNepaliDigits(rawQuery)
            .replace(/[\u200B-\u200D\uFEFF]/g, "")
            .split(/\s+/)
            .map((w) => w.replace(/[.,\/#!?$%\^&\*;:{}=\-_'"´`~()\[\]“”‘’—–।॥]/g, "").trim())
            .filter((w) => w.length >= 2)
        if (!cleanedWords.length) return formattedHtml

        const patterns = cleanedWords
            .sort((a, b) => b.length - a.length)
            .map((w) => [...w].map((ch) => escapeRegExp(ch)).join("[\\u200B-\\u200D]*"))
        if (!patterns.length) return formattedHtml

        const tokenRegex = new RegExp(`(${patterns.join("|")})`, "giu")
        // Only highlight outside HTML tags
        return formattedHtml
            .split(/(<[^>]+>)/g)
            .map((segment) => (segment.startsWith("<") ? segment : segment.replace(tokenRegex, '<mark class="bibleMatchMark">$1</mark>')))
            .join("")
    }

    async function selectSearchMatch(match: VerseReference, shouldPlay = false) {
        if (!match?.book || !match?.chapter || !match?.verse?.number) return
        if (shouldPlay) {
            playWhenLoaded = true
            resetContentSearch(false)
        }
        await openBook(match.book, [match.chapter], [[match.verse.number]])
        if (!shouldPlay && isActiveInOutput) {
            setTimeout(playScripture)
        }
    }

    async function focusSearchMatchForContext(match: VerseReference) {
        if (!match?.book || !match?.chapter || !match?.verse?.number) return
        selected.set({ id: "scripture", data: [] })
        await openBook(match.book, [match.chapter], [[match.verse.number]])
    }

    /// KEYBOARD SHORTCUTS ///

    function mouseup(e: any) {
        if (e.target.closest(".drawer")) return
        if (!contentSearchResults) contentSearchFieldActive = false
    }

    function keydown(e: KeyboardEvent) {
        if (e.key === "Escape") {
            resetContentSearch()
            return
        }

        if (e.key === "Enter") {
            // Enter in search to add to project or play
            if (e.target?.closest(".search")) {
                const enterSwapped = $scriptureSettings.enterSwapped
                const ctrlKey = e.ctrlKey || e.metaKey
                if (enterSwapped ? !ctrlKey : ctrlKey) {
                    playScripture()
                    ;(document.activeElement as any)?.blur()
                } else {
                    createScriptureShow()

                    const searchElem = document.activeElement
                    setTimeout(() => (searchElem ? (searchElem as any).focus() : null))
                }
                return
            }

            // Ctrl+Enter to play
            if (e.target?.closest(".edit")) return
            if (e.ctrlKey || e.metaKey) playScripture()
            return
        }

        // assign chapter:verse divider when pressing arrow right
        if (e.key === "ArrowRight" && document.activeElement?.classList?.contains("search")) {
            if (searchValue.includes(" ") && searchValue.length > 3 && /\d/.test(searchValue) && !searchValue.includes(":")) {
                searchValue += ":"

                // move caret
                let searchInput: any = document.activeElement
                setTimeout(() => (searchInput.selectionStart = searchInput.selectionEnd = 100))
            }

            return
        }

        if (!e.ctrlKey && !e.metaKey) return

        // Ctrl+N Converts to show (shortcuts.ts)

        // Refresh
        if (e.key === "r") {
            if (!isActiveInOutput) return
            e.preventDefault()
            playScripture()
            return
        }

        // Toggle History
        if (e.key === "h") {
            e.preventDefault()
            historyOpened = !historyOpened
            scriptureHistoryUsed.set(true)
            return
        }

        // toggle Bible content search
        if (e.key === "b") {
            if (contentSearchFieldActive) resetContentSearch()
            else contentSearchFieldActive = true
            return
        }

        if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return
        if ($activeEdit.items.length) return

        // go to next/previous verse
        let left = e.key.includes("Left")
        _moveSelection(left)
    }

    /// MOVE SELECTION ///

    let chapterLengths: { [key: number]: number } = {}
    $: if ($activeTriggerFunction === "scripture_next") _moveSelection(false)
    $: if ($activeTriggerFunction === "scripture_previous") _moveSelection(true)
    async function _moveSelection(moveLeft: boolean) {
        if (!activeReference.book) return

        // WIP this seems like duplicated code
        // most of the time the code underneath is never run I think, but it's the main code

        // Check if we're dealing with split verses
        const currentVerses = sortScriptureSelection(activeReference.verses[0] || [])
        const currentVerseId = currentVerses[0]?.toString()
        const selectionCount = currentVerses.length
        if (currentVerseId && splittedVerses.length) {
            const currentIndex = splittedVerses.findIndex((v) => v.id === currentVerseId || getVerseIdParts(v.id).id === Number(currentVerseId))
            if (currentIndex !== -1) {
                // Navigate within split verses, maintaining selection count
                const newIndex = moveLeft ? currentIndex - selectionCount : currentIndex + selectionCount

                if (newIndex >= 0 && newIndex + selectionCount - 1 < splittedVerses.length) {
                    // Stay within current split verses range, select the same count
                    const newSelection: string[] = []
                    for (let i = 0; i < selectionCount; i++) {
                        newSelection.push(splittedVerses[newIndex + i].id)
                    }

                    await wait(1) // this fixes API next not changing selection
                    openVerse([newSelection])
                    if (isActiveInOutput) setTimeout(playScripture)
                    return
                } else if (newIndex < 0 || newIndex + selectionCount - 1 >= splittedVerses.length) {
                    // Need to move to next/previous chapter or book
                    // Fall through to the regular moveSelection logic below
                }
            }
        }

        let versesCount = verses?.length || 0 // splittedVerses.length

        // get length from previous chapter when moving left from verse 1
        const normalizedVerses = (verses || []).map((v) => getVerseIdParts(v.number).id)
        const firstVerse = normalizedVerses[0]
        if (moveLeft && firstVerse === 1) {
            // get verses count from previous chapter
            const chapterNumber = activeReference.chapters[0]
            const prevChapterIndex = (chapters || []).findIndex((c) => c.number?.toString() === chapterNumber?.toString()) - 1
            const prevChapter = chapters?.[prevChapterIndex]
            versesCount = prevChapter ? prevChapter.verses.length || chapterLengths[Number(chapterNumber) - 1] : 0
        }

        const lengths = {
            book: books?.length || 0,
            chapters: chapters?.length || 0,
            verses: versesCount
        }

        const selection = {
            book: Number(activeReference.book),
            chapters: [Number(activeReference.chapters[0])],
            verses: sortScriptureSelection(activeReference.verses[0] || [])
        }

        // store
        if (lengths.verses) chapterLengths[selection.chapters[0]] = lengths.verses

        const newSelection = moveSelection(lengths, selection, moveLeft)

        openBook(newSelection.book, newSelection.chapters, [newSelection.verses])

        if (isActiveInOutput) setTimeout(playScripture)
    }

    $: reference = getReference({ data, activeReference })
    function getReference(_updater: any) {
        const bookData = data[previewBibleId]?.bookData
        const bookIndex = Math.max(Number(bookData?.number || activeReference.book || 1) - 1, 0)
        const book = getDisplayBookName(bookData, bookIndex)
        const referenceDivider = $scriptureSettings.referenceDivider || ":"
        const range = getLocalizedReferenceRange()
        const chapterReference = getLocalizedChapterReference()
        const reference = `${book} ${chapterReference}${referenceDivider}${range}`
        return reference
    }

    // OFFSETS

    $: useOffset = Object.keys($scriptures[activeScriptureId]?.collection?.offsets || {}).length > 0
    function updateOffset(scriptureId: string, value: number) {
        const offsetId = `${scriptureId}-${activeReference.book}-${activeReference.chapters[0]}`

        scriptures.update((a) => {
            const collection = a[activeScriptureId]?.collection
            if (!collection) return a

            const currentOffsets = collection.offsets || {}
            const newOffsets = { ...currentOffsets, [offsetId]: value }
            a[activeScriptureId].collection!.offsets = newOffsets

            return a
        })
    }
    function getOffset(scriptureId: string, _updater: any = null) {
        const offsetId = `${scriptureId}-${activeReference.book}-${activeReference.chapters[0]}`

        const offsets = $scriptures[activeScriptureId]?.collection?.offsets || {}
        return offsets[offsetId] || 0
    }
</script>

<svelte:window on:keydown={keydown} on:mouseup={mouseup} />

<div class="bibleTopToolbar">
    <div class="bibleTopSearchBox">
        <Icon id="search" size={0.95} white />
        <input
            class="bibleTopSearchInput edit"
            type="text"
            value={referenceSearchField}
            on:input={referenceInputChanged}
            placeholder={shouldUseNepaliLocale($language) ? "पुस्तक, अध्याय:पद वा बाइबलका शब्दहरू खोज्नुहोस् (जस्तै: मत्ती ५:३, येशू ख्रीष्ट)..." : "Search book, chapter:verse, or verse words (e.g. John 3:16, येशू ख्रीष्ट)..."}
            use:nepaliTypingInput
        />
        {#if referenceSearchField || contentSearchResults !== null}
            <button type="button" class="bibleClearBtn" on:click={() => resetContentSearch(true)} title={translateText("clear.search")}>✕</button>
        {/if}
    </div>

    {#if exactBookSearchCandidate && contentSearchResults === null}
        <button type="button" class="bibleToolbarActionBtn" on:click={() => searchInBible(exactBookSearchCandidate)}>
            {shouldUseNepaliLocale($language) ? `"${exactBookSearchCandidate}" पदहरूमा खोज्नुहोस्` : `Search "${exactBookSearchCandidate}" in verses`}
        </button>
    {/if}

    {#if contentSearchResults !== null}
        <span class="bibleResultsBadge">
            {localizeNumberText(contentSearchResults.length, $language)}
            {shouldUseNepaliLocale($language) ? "पदहरू" : contentSearchResults.length === 1 ? "verse" : "verses"}
        </span>
        <button type="button" class="bibleToolbarActionBtn" on:click={() => resetContentSearch(true)}>
            ← {shouldUseNepaliLocale($language) ? "फर्कनुहोस्" : "Back"}
        </button>
    {/if}
</div>

<div class="scroll" style="flex: 1;overflow-y: auto;min-height: 0;">
    <div class="main scripture">
        {#if !previewBibleId || $notFound.bible?.includes(previewBibleId) || !$scriptures[previewBibleId] || apiError}
            <Center faded>
                <T id="error.bible{apiError ? '_api' : ''}" />
            </Center>
        {:else if contentSearchResults !== null}
            {#if contentSearchResults.length}
                <div class="verses verseList context #scripture_verse">
                    {#each contentSearchResults as match}
                        {@const isMatchActive =
                            activeReference.book?.toString() === match.book?.toString() &&
                            activeReference.chapters[0]?.toString() === match.chapter?.toString() &&
                            activeReference.verses[0]?.some((v) => Number(String(v).split("_")[0]) === Number(match.verse?.number))}
                        <span
                            class="verse searchResultRow"
                            class:isActive={isMatchActive}
                            class:showAllText={$resized.rightPanelDrawer <= 5}
                            on:click={() => selectSearchMatch(match, false)}
                            on:dblclick={() => selectSearchMatch(match, true)}
                            on:contextmenu={() => focusSearchMatchForContext(match)}
                            data-title={formatBibleText(match.verse.text)}
                            role="none"
                        >
                            <span style="width: 190px;text-align: start;color: var(--secondary);flex-shrink: 0;" class="v">{formatMatchReference(match)}</span>{@html highlightBibleMatchText(formatBibleText(match.verse.text, true), activeVerseSearchQuery)}
                        </span>
                    {/each}
                </div>
            {:else}
                <Center faded>
                    <T id="empty.search" />
                </Center>
            {/if}
        {:else if historyOpened}
            {#if currentHistory.length}
                <div class="verses verseList">
                    {#each currentHistory as verse}
                        <span
                            class="verse"
                            class:showAllText={$resized.rightPanelDrawer <= 5}
                            on:dblclick={() => {
                                openBook(verse.book, [verse.chapter], [verse.verse])
                                playWhenLoaded = true
                            }}
                            data-title={formatBibleText(verse.text)}
                        >
                            <span style="width: 250px;text-align: start;color: var(--text);" class="v">{verse.reference}</span>{@html formatBibleText(verse.text, true)}
                        </span>
                    {/each}
                </div>
            {:else}
                <Center faded>
                    <T id="empty.general" />
                </Center>
            {/if}
        {:else}
            <!-- LIST/GRID MODE -->
            <div class={$scriptureMode === "grid" ? "grid" : "list"}>
                <div class="books" bind:this={booksScrollElem} class:center={!books?.length}>
                    {#if books?.length}
                        {#key books}
                            {#each books as book, i}
                                {@const id = book.number?.toString()}
                                {@const color = booksData[i]?.category?.color || ""}
                                {@const name = $scriptureMode === "grid" && !isNepaliBibleActive() ? booksData[i]?.abbreviation || getDisplayBookName(book, i) : getDisplayBookName(book, i)}
                                {@const isActive = activeReference.book?.toString() === id}

                                <span {id} class={isApi || isCollection || !Object.values(defaultBibleBookNames).includes(book.name) ? "" : "context #bible_book_local"} class:isActive style="{color ? `border-${$scriptureMode === 'grid' ? 'bottom' : 'left'}: 2px solid ${color};` : ''}{$scriptureMode === 'grid' ? 'border-radius: 2px;' : ''}" on:click={() => openBook(id)} role="none">
                                    {name}
                                </span>
                            {/each}
                        {/key}
                    {:else}
                        <Loader />
                    {/if}
                </div>
                <div class="content">
                    <div class="chapters context #scripture_chapter" bind:this={chaptersScrollElem} style="text-align: center;" class:center={!chapters?.length}>
                        {#if chapters?.length}
                            {#each chapters as chapter}
                                {@const id = chapter.number.toString()}
                                {@const isActive = activeReference.chapters.find((cid) => cid?.toString() === id)}

                                <span
                                    {id}
                                    class:isActive
                                    on:click={(e) => toggleChapter(e, id)}
                                    on:contextmenu={() => {
                                        openChapter([id])
                                        setTimeout(selectAllVerses)
                                    }}
                                    role="none"
                                >
                                    {localizeNumberText(chapter.number, $language)}
                                </span>
                            {/each}
                        {:else}
                            <Loader />
                        {/if}
                    </div>
                    <div class="verses context #scripture_verse" class:showFloatingButtons={$resized.rightPanelDrawer > 5 && splittedVerses.length > 10} bind:this={versesScrollElem} class:center={!splittedVerses.length}>
                        {#if isCollection && useOffset && splittedVerses.length}
                            <InputRow style="width: 100%;margin-bottom: 5px;">
                                {#each activeScriptures as scriptureId, i}
                                    {@const scripture = $scriptures[scriptureId]}
                                    <MaterialNumberInput label={scripture.name} value={getOffset(scriptureId, activeReference)} disabled={i === 0} min={-10} max={10} on:change={(e) => updateOffset(scriptureId, e.detail)} />
                                {/each}
                            </InputRow>
                        {/if}

                        {#if splittedVerses.length}
                            {#each splittedVerses as content}
                                {@const { id, subverse, endNumber } = getVerseIdParts(content.id)}
                                {@const showSplitSuffix = $scriptureSettings.splitLongVersesSuffix}
                                {@const showSuffixInPicker = $scriptureMode === "grid" || (isCollection && $scriptureSettings.showAllVersions) ? true : showSplitSuffix}
                                {@const verseLabel = buildVerseLabel(id, subverse, endNumber, showSuffixInPicker)}
                                {@const isActive = activeReference.verses[activeReference.verses.length - 1]?.find((vid) => vid.toString() === content.id || vid.toString() === id.toString())}
                                {@const text = formatBibleText(content.text, true) || (isCollection ? '<span style="opacity: 0.6; font-size: 0.85em; margin: 0; padding: 0;">~</span>' : "")}
                                {@const collectionVerses = $scriptureSettings.showAllVersions && isCollection && $scriptureMode !== "grid" ? getCollectionVerses(content.id, $scriptures) : []}

                                <!-- custom drag -->
                                <span
                                    id={content.id.toString()}
                                    class="verse"
                                    class:showAllText={$resized.rightPanelDrawer <= 5}
                                    class:isActive
                                    class:collection-verse={isCollection && $scriptureMode !== "grid"}
                                    data-title="{text}<br><br>{translateText('tooltip.scripture')}"
                                    draggable="true"
                                    on:mousedown={(e) => openVerse(updateVersesSelection(e, content.id))}
                                    on:click={(e) => openVerse(updateVersesSelection(e, content.id, true))}
                                    on:click={(e) => (isActiveInOutput && !e.ctrlKey && !e.metaKey ? playScripture() : false)}
                                    on:dblclick={(e) => (isActiveInOutput && !e.ctrlKey && !e.metaKey ? false : playScripture())}
                                    role="none"
                                >
                                    <span class="v" style={endNumber && subverse && showSuffixInPicker ? "width: 60px;" : ""}
                                        >{verseLabel.base}{#if verseLabel.suffix}<span style="padding: 0;color: var(--text);opacity: 0.5;font-size: 0.8em;">{verseLabel.suffix}</span>{/if}</span
                                    >

                                    {#if $scriptureMode !== "grid"}
                                        {#if isCollection && $scriptureSettings.showAllVersions}
                                            <!-- Show all versions for collections -->
                                            <div class="collection-versions">
                                                {#each collectionVerses as cv, cvIndex}
                                                    <div class="version-item" style="--version-color: {getVersionColor(cvIndex)}; --version-bg: {getVersionBgColor(cvIndex)}">
                                                        <span class="version-text">
                                                            <!-- && cv.text !== collectionVerses.reduce((acc, v) => acc + v.text, "") -->
                                                            {#if cv.isSplit}
                                                                <span style="opacity: 0.6; font-size: 0.85em; margin: 0; padding: 0;">~</span>
                                                            {/if}
                                                            {@html formatBibleText(cv.text, true)}
                                                        </span>
                                                    </div>
                                                {/each}
                                            </div>
                                        {:else}
                                            {@html text}
                                        {/if}
                                    {/if}
                                </span>
                            {/each}

                            {#if $scriptureMode !== "grid"}
                                {#if previewBibleData?.metadata?.copyright}
                                    <copy>{previewBibleData?.metadata?.copyright}</copy>
                                {/if}
                            {/if}
                        {:else}
                            <Loader />
                        {/if}
                    </div>
                </div>
            </div>
        {/if}
    </div>
</div>

{#if $scriptureMode !== "grid"}
    <FloatingInputs side="left">
        <span class="version" style={isCollection && $scriptureSettings.showAllVersions === false ? "padding-left: 0;" : ""}>
            {#if previewBibleData?.name}
                <!-- swap translation preview in collections -->
                {#if isCollection && $scriptureSettings.showAllVersions}
                    <!--  -->
                {:else if isCollection}
                    <MaterialButton icon="refresh" on:click={() => swapPreviewBible(activeScriptureId)} title={$scriptures[activeScriptures[(previewBibleIndex + 1) % activeScriptures.length]]?.name || ""} style="padding-right: 0.2em;font-weight: normal;">
                        {#if isApi}<Icon id="web" style="margin: 0 5px;" size={0.8} white />{/if}
                        {previewBibleData.name}:
                    </MaterialButton>
                {:else}
                    {#if isApi}<Icon id="web" style="margin-right: 5px;" white />{/if}
                    <span style="opacity: 0.8;">{previewBibleData.name}:</span>
                {/if}

                {#key data}
                    {reference}

                    {#if isCollection}
                        <MaterialCheckbox
                            label="scripture.show_all"
                            checked={$scriptureSettings.showAllVersions}
                            on:change={(e) => {
                                scriptureSettings.update((s) => ({ ...s, showAllVersions: e.detail }))
                            }}
                            small
                        />

                        <MaterialCheckbox label="edit.offset" checked={useOffset} on:change={(e) => (useOffset = e.detail)} small />
                    {/if}

                    <!-- WIP had some issues with selecting multiple verses -->
                    <!-- !NaN = temp solution to split long verses -->
                    <!-- {#if !currentBibleData?.verseData?.getReference()?.includes("NaN")}
                        {currentBibleData?.verseData?.getReference() || "..."}
                    {/if} -->
                {/key}
            {/if}
        </span>
    </FloatingInputs>
{/if}

{#if contentSearchFieldActive}
    <FloatingInputs>
        <TextInput placeholder={translateText("scripture.search")} value={contentSearchValue} on:input={searchValueChanged} on:change={searchInBible} style="width: 280px;border-radius: 20px;" autofocus />
        <MaterialButton title={translateText("clear.search")} on:click={() => resetContentSearch(true)} style="padding: 0.35em 0.65em;">
            <Icon size={1} id="close" white />
        </MaterialButton>
    </FloatingInputs>
{:else if $scriptureMode !== "grid" || $resized.rightPanelDrawer > 5}
    <FloatingInputs arrow let:open>
        {#if open || isActiveInOutput}
            <MaterialButton disabled={activeReference.book?.toString() === "1" && !!activeReference.chapters.find((a) => a.toString() === "1") && !!activeReference.verses[0]?.find((a) => a.toString() === "1")} title="{translateText('preview._previous_slide')} [Ctrl+Arrow Left]" on:click={() => _moveSelection(true)}>
                <Icon size={1.3} id="previous" white={!isActiveInOutput} />
            </MaterialButton>
            <MaterialButton disabled={activeReference.book?.toString() === books?.length.toString() && activeReference.chapters.includes(chapters ? chapters.length : 1) && activeReference.verses[0]?.includes(verses ? verses.length : 1)} title="{translateText('preview._next_slide')} [Ctrl+Arrow Right]" on:click={() => _moveSelection(false)}>
                <Icon size={1.3} id="next" white={!isActiveInOutput} />
            </MaterialButton>
        {/if}

        <MaterialButton disabled={$outLocked} title={isActiveInOutput ? "preview._update [Ctrl+R]" : "menu._title_display"} on:click={playScripture}>
            <Icon size={isActiveInOutput ? 1.1 : 1.3} id={isActiveInOutput ? "refresh" : "play"} white={!isActiveInOutput} />
        </MaterialButton>

        <div class="divider" />

        <MaterialButton disabled={historyOpened} on:click={() => scriptureMode.set($scriptureMode === "list" ? "grid" : "list")} title="show.{[$scriptureMode === 'grid' ? 'grid' : 'list']}">
            <Icon size={1.3} id={$scriptureMode === "grid" ? "grid" : "list"} white />
        </MaterialButton>

        {#if open || $scriptureHistoryUsed}
            <div class="divider" />

            <MaterialButton
                disabled={!currentHistory.length && !historyOpened}
                isActive={historyOpened}
                on:click={() => {
                    historyOpened = !historyOpened
                    scriptureHistoryUsed.set(true)
                }}
                title="popup.history [Ctrl+H]"
            >
                <Icon size={1.2} id="history" white={!currentHistory.length} />
            </MaterialButton>
        {/if}

        <MaterialButton title="scripture.search [Ctrl+B]" on:click={() => (contentSearchFieldActive = true)}>
            <Icon size={1.1} id="search" white />
        </MaterialButton>
    </FloatingInputs>
{/if}

<style>
    .main {
        display: flex;
        height: 100%;
        width: 100%;
    }
    .main div {
        display: flex;
        flex-direction: column;
        overflow-y: auto;
        overflow-x: hidden;
        align-content: flex-start;

        position: relative;
        scroll-behavior: smooth;
    }
    .main div:not(.verses):not(.grid):not(.list):not(.list .content):not(.grid div) {
        border-inline-end: 2px solid var(--primary-lighter);
    }
    .main div:not(.grid):not(.list):not(.list .content):not(.grid div) {
        padding-bottom: 60px;
    }
    .main .verses {
        flex: 1;
        flex-flow: wrap;
    }

    .main .grid .verses.showFloatingButtons::after {
        content: "";
        display: block;
        width: 250px;
        height: 50px;
        flex-shrink: 0;
    }

    .main div.center {
        display: flex;
        align-content: center;
        justify-content: center;
    }

    .main span {
        padding: 4px 10px;
    }
    .main span.isActive,
    .main :global(p).isActive {
        background-color: var(--focus);
        outline: none;
    }
    .main span:hover:not(.isActive):not(.v),
    .main :global(p):hover:not(.isActive) {
        background-color: var(--hover);
    }
    .main span:focus,
    .main span:active:not(.isActive):not(.v),
    .main :global(p):focus,
    .main :global(p):active:not(.isActive) {
        background-color: var(--focus);
    }

    .main span.verse {
        width: 100%;
        padding: 4px 10px;

        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;

        /* text-align-last: justify; */
    }
    .main .list :global(.v),
    .main .verseList :global(.v) {
        color: var(--secondary);
        font-weight: bold;
        display: inline-block;
        width: 45px;
        margin-inline-end: 10px;
        text-align: center;
        white-space: nowrap;
    }
    .main span.verse.showAllText {
        white-space: initial;
    }
    /* .add, .wj, .w, .xt */
    /* .main :global(.add) {
    font-style: italic;
  } */
    .main :global(.wj) {
        color: #ff5050;
    }
    .main :global(.xt) {
        position: absolute;
        display: flex;
        width: 400px;
        white-space: initial;
        background-color: var(--primary-lighter);
        padding: 10px;

        display: none;
    }
    .main copy {
        opacity: 0.5;
        padding: 32px 120px;
        font-size: 0.8em;
        font-style: italic;
        width: 100%;
        text-align: center;
    }

    /* bible parts */
    .main :global(.uncertain) {
        opacity: 0.7;
        font-size: 0.8em;
        font-style: italic;
    }

    /* LIST MODE */

    .list {
        display: flex !important;
        flex-direction: row !important;
        width: 100%;
    }

    .list .content {
        display: flex;
        flex-direction: row;
        flex: 1;
    }

    /* GRID MODE */

    .grid {
        display: flex;
        flex-direction: column;
    }
    .grid .books {
        border-bottom: 2px solid var(--primary-lighter);
    }
    .grid .chapters {
        border-inline-end: 2px solid var(--primary-lighter);
    }

    .grid .books,
    .grid .content {
        flex-direction: row;
        height: 50%;
    }
    .grid .chapters,
    .grid .verses {
        flex-direction: row;
        width: 50%;
    }

    .grid .books,
    .grid .chapters,
    .grid .verses {
        flex-wrap: wrap;
        align-content: normal;
    }

    .grid span {
        display: flex;
        justify-content: center;
        align-items: center;

        min-width: 40px;
        flex: 1;

        font-weight: 600;
    }
    .grid .verses .v span {
        display: inline;
        flex: none;
        min-width: 0;
    }
    .grid .books span {
        min-width: 52px;
    }

    .grid .verses {
        color: var(--secondary);
        font-weight: bold;
    }

    /* Version */

    .version {
        flex: 1;
        padding: 0 10px;
        display: flex;
        gap: 5px;
        align-items: center;
    }

    /* Collection multi-version display */
    .verse.collection-verse {
        display: block !important;
        flex: none !important;
        height: auto !important;
        min-height: 0 !important;
        padding: 2px 10px 2px 0 !important;
        margin: 0 !important;
        white-space: normal !important;
        overflow: visible !important;
        text-overflow: unset !important;
    }

    .collection-versions {
        display: block !important;
        width: 100%;
        padding: 0 !important;
        margin: 2px 0 0 0 !important;
    }

    .version-item {
        display: block !important;
        padding: 3px 6px 3px 8px !important;
        margin: 0 0 2px 0 !important;
        border-left: 4px solid var(--version-color, var(--secondary));
        background: var(--version-bg, transparent);
        border-radius: 0 4px 4px 0;
        line-height: 1.2;
    }

    .version-item:last-child {
        margin-bottom: 0 !important;
    }

    .version-text {
        display: inline !important;
        font-size: 0.9em;
        line-height: 1.2;
        font-weight: normal;
        padding: 0 !important;
        margin: 0 !important;
    }

    .bibleTopToolbar {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.36rem 0.65rem;
        border-bottom: 1px solid var(--primary-lighter);
        background: linear-gradient(180deg, color-mix(in srgb, var(--primary-darkest) 92%, var(--secondary) 8%) 0%, var(--primary-darker) 100%);
        flex-shrink: 0;
    }

    .bibleTopSearchBox {
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: center;
        gap: 0.45rem;
        padding: 0 0.65rem;
        height: 33px;
        border-radius: 999px;
        background: var(--primary-darkest);
        border: 1px solid color-mix(in srgb, var(--secondary) 36%, var(--primary-lighter) 64%);
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .bibleTopSearchBox:focus-within {
        border-color: var(--secondary);
        box-shadow: 0 0 0 2px color-mix(in srgb, var(--secondary) 22%, transparent 78%);
    }

    .bibleTopSearchInput {
        flex: 1;
        min-width: 0;
        height: 100%;
        border: none;
        outline: none;
        background: transparent;
        color: var(--text);
        font-family: inherit;
        font-size: 0.85rem;
        padding: 0;
    }

    .bibleTopSearchInput::placeholder {
        color: var(--text);
        opacity: 0.52;
    }

    .bibleClearBtn {
        width: 21px;
        height: 21px;
        border-radius: 50%;
        border: none;
        background: rgba(255, 255, 255, 0.08);
        color: var(--text);
        font-size: 0.72rem;
        line-height: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        padding: 0;
        flex-shrink: 0;
    }

    .bibleClearBtn:hover {
        background: color-mix(in srgb, var(--secondary) 35%, transparent 65%);
        color: #ffffff;
    }

    .bibleResultsBadge {
        display: inline-flex;
        align-items: center;
        height: 28px;
        padding: 0 0.65rem;
        border-radius: 999px;
        background: color-mix(in srgb, var(--secondary) 18%, var(--primary-darkest) 82%);
        border: 1px solid color-mix(in srgb, var(--secondary) 45%, transparent 55%);
        color: var(--secondary);
        font-size: 0.78rem;
        font-weight: 700;
        white-space: nowrap;
        flex-shrink: 0;
    }

    .bibleToolbarActionBtn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        height: 28px;
        padding: 0 0.68rem;
        border-radius: 999px;
        border: 1px solid color-mix(in srgb, var(--secondary) 42%, var(--primary-lighter) 58%);
        background: var(--primary-darkest);
        color: var(--text);
        font-family: inherit;
        font-size: 0.78rem;
        font-weight: 600;
        cursor: pointer;
        white-space: nowrap;
        flex-shrink: 0;
    }

    .bibleToolbarActionBtn:hover {
        background: color-mix(in srgb, var(--secondary) 22%, var(--primary-darkest) 78%);
        border-color: var(--secondary);
    }

    .main span.searchResultRow {
        cursor: pointer;
        border-bottom: 1px solid color-mix(in srgb, var(--primary-lighter) 45%, transparent 55%);
        padding: 6px 12px;
    }

    .main :global(mark.bibleMatchMark) {
        background: color-mix(in srgb, var(--secondary) 34%, transparent 66%);
        color: inherit;
        border-radius: 3px;
        padding: 0 2px;
    }
</style>
