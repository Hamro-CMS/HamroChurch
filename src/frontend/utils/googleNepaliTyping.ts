// Google Input Tools style Nepali typing.
// Type a roman word -> a popup under the caret shows Nepali candidates -> Space/Enter/Tab/1-5/click commits.
// Active whenever Nepali typing ("G") is ON; old instant phonetic typing has been removed.

import { get, writable } from "svelte/store"
import { dictionaryCandidates, generateLocalCandidates, uniqueCandidates } from "../../lib/googleNepaliTyping"
import { Main } from "../../types/IPC/Main"
import { requestMain } from "../IPC/main"
import { nepaliTypingEnabled } from "../stores"

export type NepaliTypingMode = "google"

const MAX_NEPALI_CANDIDATES = 5

export const nepaliTypingMode = writable<NepaliTypingMode>("google")

export function setNepaliTypingMode(_mode: NepaliTypingMode) {
    nepaliTypingMode.set("google")
}

export function isGoogleTypingActive() {
    return get(nepaliTypingEnabled)
}

// ---------------- popup state ----------------

export type GoogleTypingPopupState = {
    visible: boolean
    x: number
    y: number
    roman: string
    candidates: string[] // nepali candidates + roman word as the last option
    selected: number
    online: boolean | null // null = still loading
}

const hiddenState: GoogleTypingPopupState = { visible: false, x: 0, y: 0, roman: "", candidates: [], selected: 0, online: null }
export const googleTypingPopup = writable<GoogleTypingPopupState>({ ...hiddenState })

type InputContext = { kind: "input"; el: HTMLInputElement | HTMLTextAreaElement; start: number; end: number; roman: string }
type EditableContext = { kind: "editable"; root: HTMLElement; node: Text; start: number; end: number; roman: string }
type WordContext = InputContext | EditableContext

let context: WordContext | null = null
let committing = false
let requestCounter = 0
let fetchTimeout: ReturnType<typeof setTimeout> | null = null
const candidateCache = new Map<string, string[]>()

function hidePopup() {
    context = null
    requestCounter++
    if (fetchTimeout) clearTimeout(fetchTimeout)
    if (get(googleTypingPopup).visible) googleTypingPopup.set({ ...hiddenState })
}

// ---------------- element helpers ----------------

function isNepaliHost(el: Element | null) {
    if (!el) return false
    const host = el.closest?.("[data-nepali-typing]") as HTMLElement | null
    if (!host) return false
    return (el as HTMLElement).dataset?.disableNepaliTyping !== "true"
}

function isTextField(el: any): el is HTMLInputElement | HTMLTextAreaElement {
    if (el instanceof HTMLTextAreaElement) return true
    return el instanceof HTMLInputElement && ["text", "search", ""].includes(el.type || "text")
}

const ROMAN_WORD_BEFORE = /[a-zA-Z]+$/
const LETTER = /[a-zA-Z]/
const URL_OR_EMAIL_BEFORE = /(?:https?:\/\/|www\.|\S+@)\S*$/i

function hasUrlOrHandlePrefix(textBeforeWordEnd: string, wordStart: number) {
    if (wordStart > 0 && /[@/:\\]/.test(textBeforeWordEnd.charAt(wordStart - 1))) return true
    return URL_OR_EMAIL_BEFORE.test(textBeforeWordEnd)
}

function findInputWord(el: HTMLInputElement | HTMLTextAreaElement): InputContext | null {
    const caret = el.selectionStart
    if (caret === null || caret !== el.selectionEnd) return null
    const before = el.value.slice(0, caret)
    const match = before.match(ROMAN_WORD_BEFORE)
    if (!match || LETTER.test(el.value.charAt(caret))) return null
    const start = caret - match[0].length
    if (hasUrlOrHandlePrefix(before, start)) return null
    return { kind: "input", el, start, end: caret, roman: match[0] }
}

function findEditableWord(root: HTMLElement): EditableContext | null {
    const selection = window.getSelection()
    if (!selection?.isCollapsed) return null
    const node = selection.anchorNode
    if (!(node instanceof Text) || !root.contains(node)) return null
    const offset = selection.anchorOffset
    const before = node.data.slice(0, offset)
    const match = before.match(ROMAN_WORD_BEFORE)
    if (!match || LETTER.test(node.data.charAt(offset))) return null
    const start = offset - match[0].length
    if (hasUrlOrHandlePrefix(before, start)) return null
    return { kind: "editable", root, node, start, end: offset, roman: match[0] }
}

function contextStillValid(ctx: WordContext) {
    if (ctx.kind === "input") return ctx.el.isConnected && ctx.el.value.slice(ctx.start, ctx.end) === ctx.roman
    return ctx.node.isConnected && ctx.node.data.slice(ctx.start, ctx.end) === ctx.roman
}

function contextFocused(ctx: WordContext) {
    const active = document.activeElement
    if (ctx.kind === "input") return active === ctx.el
    return !!active && (active === ctx.root || ctx.root.contains(active))
}

// ---------------- positioning ----------------

const MIRROR_STYLE_PROPS = ["direction", "boxSizing", "width", "height", "overflowX", "overflowY", "borderTopWidth", "borderRightWidth", "borderBottomWidth", "borderLeftWidth", "borderStyle", "paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "fontStyle", "fontVariant", "fontWeight", "fontStretch", "fontSize", "lineHeight", "fontFamily", "textAlign", "textTransform", "textIndent", "letterSpacing", "wordSpacing", "tabSize"]

function getFieldCaretPosition(el: HTMLInputElement | HTMLTextAreaElement, position: number) {
    const rect = el.getBoundingClientRect()
    const style = window.getComputedStyle(el)
    const isInput = el.tagName === "INPUT"

    const mirror = document.createElement("div")
    mirror.style.position = "fixed"
    mirror.style.visibility = "hidden"
    mirror.style.whiteSpace = isInput ? "pre" : "pre-wrap"
    mirror.style.wordWrap = "break-word"
    mirror.style.top = `${rect.top}px`
    mirror.style.left = `${rect.left}px`
    MIRROR_STYLE_PROPS.forEach((prop) => ((mirror.style as any)[prop] = (style as any)[prop]))

    const before = el.value.slice(0, position)
    mirror.textContent = isInput ? before.replace(/\s/g, "\u00a0") : before
    const marker = document.createElement("span")
    marker.textContent = el.value.slice(position) || "."
    mirror.appendChild(marker)

    document.body.appendChild(mirror)
    const markerRect = marker.getBoundingClientRect()
    const lineHeight = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.35 || 22
    document.body.removeChild(mirror)

    const x = Math.max(rect.left + 2, Math.min(rect.right - 20, markerRect.left - el.scrollLeft))
    const y = isInput ? rect.bottom + 2 : Math.max(rect.top + 18, Math.min(rect.bottom + 2, markerRect.top - el.scrollTop + lineHeight + 2))
    return { x, y }
}

function getContextPosition(ctx: WordContext) {
    if (ctx.kind === "input") return getFieldCaretPosition(ctx.el, ctx.start)

    const range = document.createRange()
    range.setStart(ctx.node, ctx.start)
    range.setEnd(ctx.node, ctx.end)
    const rect = range.getBoundingClientRect()
    if (!rect.width && !rect.height) {
        const rootRect = ctx.root.getBoundingClientRect()
        return { x: rootRect.left, y: rootRect.bottom + 4 }
    }
    return { x: rect.left, y: rect.bottom + 4 }
}

// ---------------- candidates ----------------

function updateForContext(ctx: WordContext) {
    const sameWord = context?.roman === ctx.roman && get(googleTypingPopup).visible
    context = ctx

    const position = getContextPosition(ctx)
    const cacheKey = ctx.roman.toLowerCase()
    const cached = candidateCache.get(cacheKey)
    const nepali = cached || generateLocalCandidates(ctx.roman, 4)

    googleTypingPopup.update((state) => ({
        visible: true,
        x: position.x,
        y: position.y,
        roman: ctx.roman,
        candidates: [...nepali, ctx.roman],
        selected: sameWord ? Math.min(state.selected, nepali.length) : 0,
        online: cached ? true : null
    }))

    if (cached) return

    const requestId = ++requestCounter
    if (fetchTimeout) clearTimeout(fetchTimeout)
    fetchTimeout = setTimeout(async () => {
        let google: string[] = []
        try {
            google = (await requestMain(Main.NEPALI_TRANSLITERATE, { text: ctx.roman, num: MAX_NEPALI_CANDIDATES }, undefined, 4000)) || []
        } catch {
            google = []
        }
        if (requestId !== requestCounter || context?.roman !== ctx.roman) return

        const merged = uniqueCandidates([...dictionaryCandidates(ctx.roman), ...google, ...generateLocalCandidates(ctx.roman, 4)], ctx.roman).slice(0, MAX_NEPALI_CANDIDATES)
        if (google.length) candidateCache.set(cacheKey, merged)
        googleTypingPopup.update((state) => ({ ...state, candidates: [...merged, ctx.roman], selected: Math.min(state.selected, merged.length), online: google.length > 0 }))
    }, 90)
}

// ---------------- commit ----------------

function insertText(text: string) {
    // execCommand keeps native undo history and fires a regular "input" event, so Svelte bindings and the slide editor update normally
    try {
        if (document.execCommand("insertText", false, text)) return true
    } catch {
        // fall through
    }
    return false
}

export function commitCandidate(index: number = get(googleTypingPopup).selected, suffix = "", keepFocus = true) {
    const ctx = context
    const state = get(googleTypingPopup)
    const candidate = state.candidates[index] ?? state.candidates[0]
    if (!ctx || !candidate || !contextStillValid(ctx)) {
        hidePopup()
        return
    }

    const text = candidate + suffix
    committing = true
    try {
        if (ctx.kind === "input") {
            if (keepFocus && document.activeElement !== ctx.el) ctx.el.focus()
            const isFocused = document.activeElement === ctx.el
            if (isFocused) ctx.el.setSelectionRange(ctx.start, ctx.end)
            if (!(isFocused && insertText(text))) {
                const value = ctx.el.value
                ctx.el.value = value.slice(0, ctx.start) + text + value.slice(ctx.end)
                const caret = ctx.start + text.length
                if (isFocused) ctx.el.setSelectionRange(caret, caret)
                ctx.el.dispatchEvent(new InputEvent("input", { bubbles: true, data: text, inputType: "insertReplacementText" }))
            }
        } else {
            // Mutate the existing Text node directly so the slide editor's styled <span> (and its data-chords / data-sourcedynamickey attributes) is never stripped when replacing from index 0 to node.length.
            ctx.node.replaceData(ctx.start, ctx.end - ctx.start, candidate)
            const afterWord = Math.min(ctx.start + candidate.length, ctx.node.length)

            if (keepFocus && document.activeElement !== ctx.root && !ctx.root.contains(document.activeElement)) {
                ctx.root.focus()
            }
            const isFocused = document.activeElement === ctx.root || ctx.root.contains(document.activeElement)
            const selection = window.getSelection()
            if (isFocused && selection) {
                const caret = document.createRange()
                caret.setStart(ctx.node, afterWord)
                caret.collapse(true)
                selection.removeAllRanges()
                selection.addRange(caret)
            }

            let firedInput = false
            if (suffix) {
                if (isFocused) firedInput = insertText(suffix)
                if (!firedInput) {
                    const domSuffix = suffix === " " && afterWord === ctx.node.length ? "\u00a0" : suffix
                    ctx.node.insertData(afterWord, domSuffix)
                    if (isFocused && selection) {
                        const nextCaret = document.createRange()
                        nextCaret.setStart(ctx.node, Math.min(afterWord + domSuffix.length, ctx.node.length))
                        nextCaret.collapse(true)
                        selection.removeAllRanges()
                        selection.addRange(nextCaret)
                    }
                }
            }

            if (!firedInput) {
                ctx.root.dispatchEvent(new InputEvent("input", { bubbles: true, data: text, inputType: "insertReplacementText" }))
            }
        }
    } finally {
        committing = false
        hidePopup()
    }
}

export function selectCandidate(index: number) {
    googleTypingPopup.update((state) => ({ ...state, selected: Math.max(0, Math.min(index, state.candidates.length - 1)) }))
}

// ---------------- global listeners ----------------

const IGNORED_INPUT_TYPES = ["insertFromPaste", "insertFromDrop", "historyUndo", "historyRedo"]

function onInput(event: Event) {
    if (committing) return
    if (!isGoogleTypingActive()) {
        if (context) hidePopup()
        return
    }

    const inputType = (event as InputEvent).inputType || ""
    if (IGNORED_INPUT_TYPES.some((prefix) => inputType.startsWith(prefix))) {
        if (context) hidePopup()
        return
    }

    const target = event.target as HTMLElement | null
    if (!target || !isNepaliHost(target)) {
        if (context) hidePopup()
        return
    }

    let ctx: WordContext | null = null
    if (isTextField(target)) ctx = findInputWord(target)
    else if (target.isContentEditable) ctx = findEditableWord(target)

    if (ctx) updateForContext(ctx)
    else hidePopup()
}

const NAVIGATION_KEYS = ["ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown"]

function onKeydown(event: KeyboardEvent) {
    const state = get(googleTypingPopup)
    if (!state.visible || !context) return
    if (!contextFocused(context)) {
        hidePopup()
        return
    }
    if (event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return

    const stop = () => {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
    }

    const count = state.candidates.length
    if (event.key === "ArrowDown") {
        stop()
        selectCandidate((state.selected + 1) % count)
        return
    }
    if (event.key === "ArrowUp") {
        stop()
        selectCandidate((state.selected - 1 + count) % count)
        return
    }
    if (event.key === " ") {
        stop()
        commitCandidate(state.selected, " ")
        return
    }
    if (event.key === "Enter" || event.key === "Tab") {
        stop()
        commitCandidate(state.selected, "")
        return
    }
    if (event.key === "Escape") {
        stop()
        hidePopup()
        return
    }
    if (/^[1-9]$/.test(event.key)) {
        const index = Number(event.key) - 1
        if (index < count) {
            stop()
            commitCandidate(index, " ")
        }
        return
    }
    if (/^[.,?!;:]$/.test(event.key)) {
        stop()
        commitCandidate(state.selected, event.key === "." ? "।" : event.key)
        return
    }
    if (NAVIGATION_KEYS.includes(event.key)) hidePopup()
}

function onMousedown(event: MouseEvent) {
    if (!context || !get(googleTypingPopup).visible) return
    const target = event.target as Node | null
    if (target instanceof Element && target.closest(".googleTypingPopup")) return

    const host = context.kind === "input" ? context.el : context.root
    if (target && (target === host || host.contains(target))) {
        // clicked inside the same field: just move the caret
        hidePopup()
        return
    }
    // clicked somewhere else (save button, other slide...): commit the highlighted word like Google Input Tools without stealing focus from the clicked target
    commitCandidate(get(googleTypingPopup).selected, "", false)
}

function onScrollOrResize() {
    if (!context || !get(googleTypingPopup).visible) return
    if (!contextStillValid(context)) {
        hidePopup()
        return
    }
    const position = getContextPosition(context)
    googleTypingPopup.update((state) => ({ ...state, x: position.x, y: position.y }))
}

let initialized = false
export function initGoogleNepaliTyping() {
    if (initialized) return () => {}
    initialized = true

    document.addEventListener("input", onInput, true)
    window.addEventListener("keydown", onKeydown, true)
    window.addEventListener("mousedown", onMousedown, true)
    window.addEventListener("scroll", onScrollOrResize, true)
    window.addEventListener("resize", onScrollOrResize)
    const unsubscribe = nepaliTypingEnabled.subscribe((enabled) => {
        if (!enabled) hidePopup()
    })

    return () => {
        initialized = false
        document.removeEventListener("input", onInput, true)
        window.removeEventListener("keydown", onKeydown, true)
        window.removeEventListener("mousedown", onMousedown, true)
        window.removeEventListener("scroll", onScrollOrResize, true)
        window.removeEventListener("resize", onScrollOrResize)
        unsubscribe()
        hidePopup()
    }
}
