<script lang="ts">
    import { commitCandidate, googleTypingPopup, hidePopup, initGoogleNepaliTyping, selectCandidate } from "../../utils/googleNepaliTyping"
    import { onDestroy, onMount, tick } from "svelte"

    const COMPACT_FALLBACK_WIDTH = 380

    let cleanup: (() => void) | null = null
    let popupEl: HTMLDivElement | null = null
    let measuredWidth = 0
    let vw = typeof window !== "undefined" ? window.innerWidth : 1280

    function syncViewport() {
        if (typeof window === "undefined") return
        vw = window.innerWidth
    }

    async function measurePopup(visible: boolean, _roman: string, _count: number) {
        if (!visible) {
            measuredWidth = 0
            return
        }
        await tick()
        if (popupEl) {
            measuredWidth = popupEl.offsetWidth || 0
        }
    }

    onMount(() => {
        cleanup = initGoogleNepaliTyping()
        syncViewport()
        window.addEventListener("resize", syncViewport)
    })

    onDestroy(() => {
        cleanup?.()
        if (typeof window !== "undefined") {
            window.removeEventListener("resize", syncViewport)
        }
    })

    $: state = $googleTypingPopup
    $: void measurePopup(state.visible, state.roman, state.candidates.length)

    $: estWidth = Math.min(measuredWidth || COMPACT_FALLBACK_WIDTH, vw - 16)
    $: preferredLeft = state.anchorLeft || state.x
    $: left = Math.max(8, Math.min(preferredLeft, vw - estWidth - 8))
    $: dockAbove = (state.anchorTop || state.y) >= 48
    $: top = dockAbove ? Math.max(36, (state.anchorTop || state.y) - 6) : (state.anchorBottom || state.y) + 5
    $: positionStyle = dockAbove ? `left: ${left}px; top: ${top}px; transform: translateY(-100%);` : `left: ${left}px; top: ${top}px;`
    $: romanIndex = state.candidates.length - 1
</script>

{#if state.visible && state.candidates.length}
    <div bind:this={popupEl} class="googleTypingPopup" style={positionStyle} role="listbox" aria-label="Nepali transliteration suggestions" on:mousedown|preventDefault|stopPropagation>
        <span class="compactRoman">{state.roman}</span>
        <div class="compactCandidates">
            {#each state.candidates as candidate, i}
                <button type="button" class="compactChip" class:selected={i === state.selected} class:roman={i === romanIndex} role="option" aria-selected={i === state.selected} on:mouseenter={() => selectCandidate(i)} on:mousedown|preventDefault|stopPropagation={() => commitCandidate(i, "")}>
                    <span class="chipNumber">{i + 1}</span>
                    <span class="chipText">{candidate}</span>
                </button>
            {/each}
        </div>
        <button type="button" class="compactClose" title="Esc" aria-label="Close Nepali suggestions" on:mousedown|preventDefault|stopPropagation={hidePopup}>✕</button>
    </div>
{/if}

<style>
    .googleTypingPopup {
        position: fixed;
        z-index: 2147483000;
        display: inline-flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 5px;
        min-width: 0;
        max-width: min(680px, calc(100vw - 16px));
        padding: 4px 7px;
        background: rgb(255 255 255 / 0.98);
        color: #1e293b;
        border: 1px solid #cbd5e1;
        border-radius: 9px;
        box-shadow:
            0 8px 22px -4px rgb(15 23 42 / 0.24),
            0 2px 6px rgb(15 23 42 / 0.12);
        backdrop-filter: blur(8px);
        font-family: "Noto Sans Devanagari", Arial, sans-serif;
        user-select: none;
    }

    .compactRoman {
        padding: 1px 6px;
        font-size: 0.74rem;
        font-weight: 600;
        color: #64748b;
        background: #f1f5f9;
        border-radius: 5px;
        font-family: Arial, sans-serif;
        line-height: 1.4;
    }

    .compactCandidates {
        display: inline-flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 4px;
    }

    .compactChip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        font-size: 0.92rem;
        line-height: 1.3;
        color: #1e293b;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        cursor: pointer;
        font-family: inherit;
        transition:
            background-color 0.12s ease,
            border-color 0.12s ease;
    }

    .compactChip:hover {
        background: #eff6ff;
        border-color: #bfdbfe;
    }

    .compactChip.selected {
        background: #dbeafe;
        border-color: #60a5fa;
        color: #1e3a8a;
        font-weight: 600;
    }

    .compactChip.roman .chipText {
        font-family: Arial, sans-serif;
        font-size: 0.82rem;
        color: #475569;
    }

    .chipNumber {
        font-size: 0.68rem;
        font-weight: 600;
        color: #64748b;
        font-family: Arial, sans-serif;
    }

    .compactChip.selected .chipNumber {
        color: #1d4ed8;
    }

    .compactClose {
        margin-left: 2px;
        padding: 1px 5px;
        font-size: 0.74rem;
        line-height: 1.2;
        color: #94a3b8;
        background: transparent;
        border: 0;
        border-radius: 4px;
        cursor: pointer;
    }

    .compactClose:hover {
        color: #334155;
        background: #f1f5f9;
    }
</style>
