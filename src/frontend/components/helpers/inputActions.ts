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
    // Marks the field as Nepali-typing capable for Google transliteration suggestions.
    if ((node as HTMLElement).dataset.disableNepaliTyping !== "true") {
        node.dataset.nepaliTyping = "true"
    }

    return {
        destroy() {
            // no-op: Google Nepali typing listens globally via initGoogleNepaliTyping()
        }
    }
}
