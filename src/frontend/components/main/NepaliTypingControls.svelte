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
                ? "नेपाली टाइपिङ सक्रिय छ (शब्द टाइप गरी Space थिच्नुहोस्)। बन्द गर्न क्लिक वा Ctrl+Alt+N थिच्नुहोस्।"
                : "नेपाली टाइपिङ खोल्न क्लिक गर्नुहोस् (Ctrl+Alt+N)।"
            : $nepaliTypingEnabled
              ? "Nepali typing is ON (type a word, press Space). Click or press Ctrl+Alt+N to turn off."
              : "Click to enable Nepali typing (Ctrl+Alt+N)."
</script>

{#if !$currentWindow}
    <div class="typingControls">
        <button class="typingMode" class:google={$nepaliTypingEnabled} class:dim={!$nepaliTypingEnabled} on:click={() => toggleNepaliTyping()} title={modeTitle} aria-label={modeTitle}>
            G
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
        width: 42px;
        height: 42px;
        border: 1px solid color-mix(in srgb, var(--secondary) 45%, var(--primary-lighter) 55%);
        background: color-mix(in srgb, var(--primary-darkest) 88%, black 12%);
        color: var(--text);
        border-radius: 999px;
        box-shadow: 0 8px 24px rgb(0 0 0 / 0.28);
        cursor: pointer;
        font: inherit;
        font-weight: 700;
        font-size: 1rem;
        transition: border-color 0.15s ease, color 0.15s ease, opacity 0.15s ease;
    }

    .typingMode.google {
        border-color: #4285f4;
        color: #8ab4f8;
    }

    .typingMode.dim {
        opacity: 0.55;
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
