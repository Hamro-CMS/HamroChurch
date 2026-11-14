import { get } from "svelte/store"
import { shouldUseNepaliLocale } from "../../common/nepali"
import { nepaliTypingTips, romanizeNepali, toUnicode, type NepaliTypingEventLike } from "../../lib/nepaliTyping"
import { language, nepaliTypingEnabled } from "../stores"

const STORAGE_KEY = "hamrochurch.nepaliTyping.enabled"

function getStorageSafe() {
    try {
        return window.localStorage
    } catch {
        return null
    }
}

export function initializeNepaliTypingPreference() {
    const storage = getStorageSafe()
    const defaultValue = shouldUseNepaliLocale(get(language))

    if (!storage) {
        nepaliTypingEnabled.set(defaultValue)
        return
    }

    const stored = storage.getItem(STORAGE_KEY)
    if (stored === "true" || stored === "false") {
        nepaliTypingEnabled.set(stored === "true")
        return
    }

    nepaliTypingEnabled.set(defaultValue)
    storage.setItem(STORAGE_KEY, String(defaultValue))
}

export function setNepaliTypingEnabled(value: boolean) {
    nepaliTypingEnabled.set(value)
    const storage = getStorageSafe()
    if (storage) storage.setItem(STORAGE_KEY, String(value))
}

export function toggleNepaliTyping() {
    const nextValue = !get(nepaliTypingEnabled)
    setNepaliTypingEnabled(nextValue)
    return nextValue
}

export function transformTextInputTarget(target: HTMLInputElement | HTMLTextAreaElement, _event: Event) {
    return target.value
}

export function transformEditableTextNode(_textNode: Text, _cursorPosition: number, _eventLike: NepaliTypingEventLike) {
    return null
}

export { nepaliTypingTips, romanizeNepali, toUnicode }
