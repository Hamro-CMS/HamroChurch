<script lang="ts">
    import { onDestroy, onMount } from "svelte"
    import { currentWindow, language, nepaliTypingEnabled } from "../../stores"
    import { initializeNepaliTypingPreference, toggleNepaliTyping } from "../../utils/nepaliTyping"

    function keydown(event: KeyboardEvent) {
        const hasAltGr = typeof event.getModifierState === "function" && event.getModifierState("AltGraph")
        const isCtrlAlt = (event.ctrlKey && event.altKey) || (event.metaKey && event.altKey) || hasAltGr
        const isKeyN = event.code === "KeyN" || event.key?.toLowerCase() === "n"

        if (!isCtrlAlt || !isKeyN || event.shiftKey || event.repeat) return
        if ($currentWindow) return

        event.preventDefault()
        event.stopPropagation()
        toggleNepaliTyping()
    }

    onMount(() => {
        initializeNepaliTypingPreference()
        window.addEventListener("keydown", keydown, true)
    })

    onDestroy(() => {
        window.removeEventListener("keydown", keydown, true)
    })

    $: modeTitle =
        $language === "ne"
            ? $nepaliTypingEnabled
                ? "नेपाली टाइपिङ (NE) सक्रिय छ — शब्द टाइप गरी Space थिच्नुहोस्। अंग्रेजी (EN) मा बदल्न क्लिक वा Ctrl+Alt+N थिच्नुहोस्।"
                : "अंग्रेजी टाइपिङ (EN) सक्रिय छ। नेपाली (NE) टाइपिङ खोल्न क्लिक वा Ctrl+Alt+N थिच्नुहोस्।"
            : $nepaliTypingEnabled
              ? "Nepali typing (NE) is ON — type a word and press Space. Click or press Ctrl+Alt+N to switch to EN."
              : "English typing (EN) is ON. Click or press Ctrl+Alt+N to switch to Nepali (NE)."
</script>

{#if !$currentWindow}
    <div class="typingControls">
        <button class="typingMode" class:nepali={$nepaliTypingEnabled} on:click={() => toggleNepaliTyping()} title={modeTitle} aria-label={modeTitle}>
            <span class="langOption" class:active={!$nepaliTypingEnabled}>EN</span>
            <span class="divider">/</span>
            <span class="langOption" class:active={$nepaliTypingEnabled}>NE</span>
        </button>
    </div>
{/if}

<style>
    .typingControls {
        position: fixed;
        right: 18px;
        bottom: 18px;
        display: flex;
        align-items: center;
        z-index: 31;
    }

    .typingMode {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        height: 36px;
        padding: 3px 5px;
        border: 1px solid color-mix(in srgb, var(--secondary) 45%, var(--primary-lighter) 55%);
        background: color-mix(in srgb, var(--primary-darkest) 90%, black 10%);
        color: var(--text);
        border-radius: 999px;
        box-shadow: 0 8px 24px rgb(0 0 0 / 0.28);
        cursor: pointer;
        font: inherit;
        font-weight: 700;
        font-size: 0.78rem;
        letter-spacing: 0.03em;
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .typingMode.nepali {
        border-color: var(--secondary);
    }

    .langOption {
        padding: 4px 8px;
        border-radius: 999px;
        opacity: 0.55;
        transition: background-color 0.15s ease, color 0.15s ease, opacity 0.15s ease;
    }

    .langOption.active {
        opacity: 1;
        background: var(--secondary);
        color: var(--secondary-text);
    }

    .divider {
        opacity: 0.35;
        font-weight: 500;
        font-size: 0.75rem;
    }

    .typingMode:hover {
        border-color: var(--secondary);
    }

    @media (max-width: 900px) {
        .typingControls {
            right: 12px;
            bottom: 12px;
        }
    }
</style>
