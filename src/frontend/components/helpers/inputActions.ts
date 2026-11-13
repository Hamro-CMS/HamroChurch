import { handleKeyPress, type NepaliTypingEventLike } from "../../../lib/nepaliTyping"
import { get } from "svelte/store"
import { nepaliTypingEnabled } from "../../stores"
import { nepaliTypingMode } from "../../utils/googleNepaliTyping"

/** Selects the text inside a text node when the node is focused */
export function selectTextOnFocus(node: HTMLInputElement | HTMLTextAreaElement) {
    const handleFocus = () => {
        node.select?.()
    }

    node.addEventListener("focus", handleFocus)

    return {
        destroy() {
            node.removeEventListener("focus", handleFocus)
        }
    }
}

/** Blurs the node when Escape is pressed */
export function blurOnEscape(node: any) {
    const handleKey = (event: any) => {
        if (event.key === "Escape" && node && typeof node.blur === "function") node.blur()
    }

    node.addEventListener("keydown", handleKey)

    return {
        destroy() {
            node.removeEventListener("keydown", handleKey)
        }
    }
}

export function nepaliTypingInput(node: HTMLInputElement | HTMLTextAreaElement) {
    let syntheticInput = false

    // marks the field as Nepali-typing capable (used by the Google suggestions mode)
    node.dataset.nepaliTyping = "true"

    const shouldHandle = () => {
        if (!get(nepaliTypingEnabled)) return false
        // "Google suggestions" mode handles typing itself (see utils/googleNepaliTyping.ts)
        if (get(nepaliTypingMode) === "google") return false
        if ((node as HTMLElement).dataset.disableNepaliTyping === "true") return false
        if (node instanceof HTMLInputElement) return ["text", "search"].includes(node.type || "text")
        return true
    }

    const dispatchSyntheticInput = () => {
        syntheticInput = true
        node.dispatchEvent(new Event("input", { bubbles: true }))
        queueMicrotask(() => {
            syntheticInput = false
        })
    }

    const handleBeforeInput = (event: InputEvent) => {
        if (!shouldHandle() || syntheticInput) return
        if (event.isComposing || event.inputType !== "insertText" || !event.data) return

        const start = node.selectionStart ?? node.value.length
        const end = node.selectionEnd ?? start
        const currentValue = node.value.slice(0, start) + event.data + node.value.slice(end)
        const result = handleKeyPress({ data: event.data, inputType: event.inputType } as NepaliTypingEventLike, currentValue, start + event.data.length)
        if (!result) return

        event.preventDefault()
        node.value = result.newValue
        node.setSelectionRange?.(result.newCursor, result.newCursor)
        dispatchSyntheticInput()
    }

    node.addEventListener("beforeinput", handleBeforeInput)

    return {
        destroy() {
            node.removeEventListener("beforeinput", handleBeforeInput)
        }
    }
}
