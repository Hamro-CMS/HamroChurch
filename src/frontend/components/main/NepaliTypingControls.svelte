<script lang="ts">
    import { onMount } from "svelte"
    import { activePopup, currentWindow, language, nepaliTypingEnabled } from "../../stores"
    import { initializeNepaliTypingPreference, setNepaliTypingEnabled, toggleNepaliTyping } from "../../utils/nepaliTyping"
    import { nepaliTypingMode, setNepaliTypingMode, toggleNepaliTypingMode } from "../../utils/googleNepaliTyping"

    function toggleTips() {
        activePopup.set("nepali_typing_tips")
    }

    function handleModeClick() {
        if (!$nepaliTypingEnabled) {
            if ($nepaliTypingMode !== "google") setNepaliTypingMode("google")
            setNepaliTypingEnabled(true)
            return
        }
        toggleNepaliTypingMode()
    }

    function keydown(event: KeyboardEvent) {
        if (!(event.ctrlKey && event.altKey && event.key.toLowerCase() === "n")) return
        if ($currentWindow) return
        event.preventDefault()
        toggleNepaliTyping()
    }

    onMount(() => {
        initializeNepaliTypingPreference()
    })

    $: label = $nepaliTypingEnabled ? "ने" : "EN"
    $: helpLabel = $language === "ne" ? "नेपाली टाइपिंग सुझावहरू" : "Nepali typing tips"
    $: googleMode = $nepaliTypingMode === "google"
    $: modeTitle =
        $language === "ne"
            ? !$nepaliTypingEnabled
                ? "Google नेपाली सुझाव मोड खोल्न क्लिक गर्नुहोस् (शब्द टाइप गरी Space थिच्नुहोस्)।"
                : googleMode
                  ? "टाइपिङ मोड: Google सुझाव (शब्द टाइप गरी Space थिच्नुहोस्)। तुरुन्त फोनेटिक (अ) मा फर्कन क्लिक गर्नुहोस्।"
                  : "टाइपिङ मोड: तुरुन्त फोनेटिक (अ)। Google सुझाव (G) प्रयोग गर्न क्लिक गर्नुहोस्।"
            : !$nepaliTypingEnabled
              ? "Click to enable Google Nepali suggestions (type a word, press Space)."
              : googleMode
                ? "Typing mode: Google suggestions (type a word, press Space). Click for instant phonetic (अ)."
                : "Typing mode: instant phonetic (अ). Click for Google suggestions (G)."
</script>

<svelte:window on:keydown={keydown} />

{#if !$currentWindow}
    <div class="typingControls">
        <button class:active={$nepaliTypingEnabled} class="toggle" on:click={() => toggleNepaliTyping()} title={helpLabel}>
            <span class="mode">{label}</span>
            <span class="shortcut">Ctrl+Alt+N</span>
        </button>
        <button class="typingMode" class:google={googleMode && $nepaliTypingEnabled} class:dim={!$nepaliTypingEnabled} on:click={handleModeClick} title={modeTitle} aria-label={modeTitle}>
            {googleMode ? "G" : "अ"}
        </button>
        <button class="help" on:click={toggleTips} aria-label={helpLabel}>?</button>
    </div>
{/if}

<style>
    .typingControls {
        position: fixed;
        right: 18px;
        bottom: 18px;
        display: flex;
        align-items: center;
        gap: 10px;
        z-index: 31;
    }

    .toggle,
    .typingMode,
    .help {
        border: 1px solid color-mix(in srgb, var(--secondary) 45%, var(--primary-lighter) 55%);
        background: color-mix(in srgb, var(--primary-darkest) 88%, black 12%);
        color: var(--text);
        border-radius: 999px;
        box-shadow: 0 8px 24px rgb(0 0 0 / 0.28);
    }

    .typingMode {
        width: 40px;
        height: 40px;
        cursor: pointer;
        font: inherit;
        font-weight: 700;
        font-size: 1rem;
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

    .toggle {
        display: inline-flex;
        align-items: center;
        gap: 12px;
        padding: 10px 16px;
        cursor: pointer;
        font: inherit;
        font-weight: 700;
    }

    .toggle.active {
        border-color: var(--secondary);
        color: var(--secondary);
    }

    .mode {
        font-size: 1rem;
        line-height: 1;
    }

    .shortcut {
        font-size: 0.82rem;
        opacity: 0.76;
    }

    .help {
        width: 40px;
        height: 40px;
        cursor: pointer;
        font: inherit;
        font-weight: 700;
        font-size: 1rem;
    }
    .help:hover,
    .toggle:hover {
        border-color: var(--secondary);
    }
    @media (max-width: 900px) {
        .typingControls {
            right: 12px;
            bottom: 12px;
        }

        .shortcut {
            display: none;
        }
    }
</style>
