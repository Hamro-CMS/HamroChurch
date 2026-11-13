<script lang="ts">
    import { commitCandidate, googleTypingPopup, initGoogleNepaliTyping, selectCandidate } from "../../utils/googleNepaliTyping"
    import { onDestroy, onMount } from "svelte"

    let cleanup: (() => void) | null = null
    onMount(() => {
        cleanup = initGoogleNepaliTyping()
    })
    onDestroy(() => cleanup?.())

    const POPUP_WIDTH = 240
    const POPUP_HEIGHT = 230

    $: state = $googleTypingPopup
    $: left = Math.max(4, Math.min(state.x, window.innerWidth - POPUP_WIDTH - 8))
    // open above the caret when there is no room below
    $: top = state.y + POPUP_HEIGHT > window.innerHeight ? Math.max(4, state.y - POPUP_HEIGHT - 32) : state.y
    $: romanIndex = state.candidates.length - 1
</script>

{#if state.visible}
    <div class="googleTypingPopup" style="left: {left}px; top: {top}px;" role="listbox" on:mousedown|preventDefault|stopPropagation>
        <div class="header">{state.roman}</div>
        {#each state.candidates as candidate, i}
            <div class="candidate" class:selected={i === state.selected} class:roman={i === romanIndex} role="option" aria-selected={i === state.selected} on:mouseenter={() => selectCandidate(i)} on:mousedown|preventDefault|stopPropagation={() => commitCandidate(i, "")}>
                <span class="number">{i + 1}</span>
                <span class="text">{candidate}</span>
            </div>
        {/each}
        <div class="footer">
            <span class="status" class:online={state.online === true} class:offline={state.online === false}>
                {#if state.online === null}…{:else if state.online}Google{:else}Offline{/if}
            </span>
            <span class="hint">Space ✓ · Esc ✕</span>
        </div>
    </div>
{/if}

<style>
    .googleTypingPopup {
        position: fixed;
        z-index: 2147483000;
        min-width: 170px;
        max-width: 280px;
        background: #ffffff;
        color: #1b1b1b;
        border: 1px solid #9a9a9a;
        border-radius: 4px;
        box-shadow: 0 6px 18px rgb(0 0 0 / 0.35);
        font-family: "Noto Sans Devanagari", Arial, sans-serif;
        user-select: none;
        overflow: hidden;
    }

    .header {
        padding: 4px 10px;
        font-size: 0.85rem;
        color: #444;
        background: #f1f1f1;
        border-bottom: 1px solid #ddd;
        font-family: Arial, sans-serif;
    }

    .candidate {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 4px 10px;
        font-size: 1.05rem;
        cursor: pointer;
    }
    .candidate .number {
        width: 1em;
        font-size: 0.8rem;
        color: #777;
        font-family: Arial, sans-serif;
    }
    .candidate.selected {
        background: #d8e6fd;
    }
    .candidate.roman .text {
        font-family: Arial, sans-serif;
        color: #555;
    }

    .footer {
        display: flex;
        justify-content: space-between;
        gap: 10px;
        padding: 3px 10px;
        font-size: 0.72rem;
        color: #777;
        background: #f7f7f7;
        border-top: 1px solid #e3e3e3;
        font-family: Arial, sans-serif;
    }
    .status.online {
        color: #1a73e8;
        font-weight: 600;
    }
    .status.offline {
        color: #b26a00;
    }
</style>
