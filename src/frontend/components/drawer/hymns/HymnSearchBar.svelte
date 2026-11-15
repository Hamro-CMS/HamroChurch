<script lang="ts">
    import { createEventDispatcher, onDestroy, onMount } from "svelte"
    import Icon from "../../helpers/Icon.svelte"
    import { nepaliTypingInput } from "../../helpers/inputActions"
    import { hymnSearchValue } from "./hymns"
    import { nepaliTypingEnabled } from "../../../stores"
    import { translateText } from "../../../utils/language"

    const dispatch = createEventDispatcher<{ input: string }>()

    let value = $hymnSearchValue
    let isFocused = false
    let searchInput: HTMLInputElement | null = null
    let debounceTimeout: ReturnType<typeof setTimeout> | null = null

    // Only mirror the store into the field when the user is not actively editing,
    // so typing and deleting are never overwritten mid-edit.
    $: if (!isFocused && value !== $hymnSearchValue) value = $hymnSearchValue
    $: placeholder = translateText($nepaliTypingEnabled ? "hymns.search_placeholder_ne" : "hymns.search_placeholder_en")

    function emitSearch(nextValue: string) {
        hymnSearchValue.set(nextValue)
        dispatch("input", nextValue)
    }

    function onInput(event: Event) {
        const target = event.currentTarget as HTMLInputElement
        const nextValue = target.value
        if (debounceTimeout) clearTimeout(debounceTimeout)
        debounceTimeout = setTimeout(() => emitSearch(nextValue), 300)
    }

    function clearSearch() {
        if (debounceTimeout) clearTimeout(debounceTimeout)
        value = ""
        emitSearch("")
        searchInput?.focus()
    }

    function focusSearch(event?: Event) {
        if (event) event.preventDefault()
        searchInput?.focus()
        searchInput?.select()
    }

    onMount(() => {
        window.addEventListener("hamro-hymns-focus-search", focusSearch)
    })

    onDestroy(() => {
        if (debounceTimeout) clearTimeout(debounceTimeout)
        window.removeEventListener("hamro-hymns-focus-search", focusSearch)
    })
</script>

<div class="searchbar">
    <span class="searchIcon" aria-hidden="true">
        <Icon id="search" size={0.92} white />
    </span>
    <input id="hymnSearchInput" bind:this={searchInput} type="text" class="searchInput edit" bind:value on:input={onInput} on:focus={() => (isFocused = true)} on:blur={() => (isFocused = false)} use:nepaliTypingInput {placeholder} aria-label={placeholder} title={translateText("hymns.search_focus_tip")} inputmode="text" autocapitalize="off" autocomplete="off" spellcheck="false" />
    {#if value}
        <button type="button" class="clear" on:click={clearSearch} aria-label="Clear hymn search">×</button>
    {/if}
</div>

<style>
    .searchbar {
        position: relative;
        flex: 1 1 200px;
        min-width: 175px;
        display: flex;
        align-items: center;
    }

    .searchIcon {
        position: absolute;
        left: 0.68rem;
        top: 50%;
        transform: translateY(-50%);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        opacity: 0.65;
        pointer-events: none;
    }

    .searchInput {
        width: 100%;
        height: 36px;
        border: 1px solid color-mix(in srgb, var(--secondary) 30%, var(--primary-lighter) 70%);
        border-radius: 9px;
        background: var(--primary-darkest);
        color: var(--text);
        padding: 0 2.05rem 0 2.15rem;
        font: inherit;
        font-size: 0.91rem;
        line-height: 1.2;
        outline: none;
        transition:
            border-color 0.15s ease,
            box-shadow 0.15s ease;
    }

    .searchInput::placeholder {
        opacity: 0.58;
        font-size: 0.84rem;
    }

    .searchInput:focus {
        border-color: var(--secondary);
        box-shadow: 0 0 0 1px color-mix(in srgb, var(--secondary) 55%, transparent 45%);
    }

    .clear {
        position: absolute;
        right: 0.45rem;
        top: 50%;
        transform: translateY(-50%);
        border: none;
        background: transparent;
        color: var(--text);
        opacity: 0.72;
        font-size: 1.25rem;
        line-height: 1;
        padding: 0.15rem 0.25rem;
        cursor: pointer;
    }

    .clear:hover {
        opacity: 1;
    }
</style>
