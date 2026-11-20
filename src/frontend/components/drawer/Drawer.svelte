<script lang="ts">
    import type { DrawerTabIds } from "../../../types/Tabs"
    import { activeDrawerTab, activeEdit, activePage, activePopup, activeProject, activeShow, activeTriggerFunction, dictionary, drawer, drawerOpenedInEdit, drawerTabsData, focusMode, labelsDisabled, os, previousShow, projects, quickTextCache, scriptureSettings, selected, showsCache } from "../../stores"
    import { DEFAULT_DRAWER_HEIGHT, DEFAULT_WIDTH, MENU_BAR_HEIGHT } from "../../utils/common"
    import { translateText } from "../../utils/language"
    import { getAccess } from "../../utils/profile"
    import { shouldOpenReplace } from "../../utils/shortcuts"
    import { drawerTabs } from "../../values/tabs"
    import Content from "../drawer/Content.svelte"
    import Navigation from "../drawer/Navigation.svelte"
    import { keysToID } from "../helpers/array"
    import { history } from "../helpers/history"
    import Icon from "../helpers/Icon.svelte"
    import { nepaliTypingInput, selectTextOnFocus } from "../helpers/inputActions"
    import { setOutput } from "../helpers/output"
    import { loadShows } from "../helpers/setShow"
    import { getLayoutRef } from "../helpers/show"
    import { updateOut } from "../helpers/showActions"
    import T from "../helpers/T.svelte"
    import Button from "../inputs/Button.svelte"
    import MaterialButton from "../inputs/MaterialButton.svelte"
    import Resizeable from "../system/Resizeable.svelte"
    import Info from "./info/Info.svelte"

    const minHeight = 40
    const topHeight = 44
    let maxHeight = window.innerHeight - topHeight - ($os.platform === "win32" ? MENU_BAR_HEIGHT - 0.3 : 0)
    $: height = $drawer.height

    let move = false
    let mouse: null | { x: number; y: number; offsetY: number } = null
    function mousedown(e: any) {
        if (e.target.closest(".search")) return

        maxHeight = window.innerHeight - topHeight - ($os.platform === "win32" ? MENU_BAR_HEIGHT - 0.3 : 0)
        mouse = {
            x: e.clientX,
            y: e.clientY,
            offsetY: window.innerHeight - height - e.clientY
        }
    }

    // open drawer if autoclosed
    $: if ($activePage === "show" && $drawer.autoclosed) setTimeout(() => drawer.set({ height: $drawer.stored ?? DEFAULT_DRAWER_HEIGHT, stored: null }), 100)

    function mousemove(e: any) {
        if (!mouse) return

        drawer.set({ height: getHeight(window.innerHeight - e.clientY - mouse.offsetY), stored: null })

        selected.set({ id: null, data: [] })

        const isClosed = $drawer.height <= minHeight
        if (isClosed) drawerOpenedInEdit.set(false)
        else if ($activePage === "edit") drawerOpenedInEdit.set(true)
    }

    function getHeight(height: number) {
        if (height < minHeight * 2) return minHeight
        if (height > maxHeight) return maxHeight
        move = true
        return height
    }

    $: storeHeight = $drawer.stored
    function click(e: any) {
        if ((height > minHeight && e?.target?.closest("button")) || move || e?.target instanceof HTMLInputElement) {
            move = false
            return
        }

        if (height > minHeight) {
            // close drawer
            if (e === null || e?.target.classList.contains("top") || e?.target?.closest("#" + $activeDrawerTab)) closeDrawer()
            drawerOpenedInEdit.set(false)
            return
        }

        // open drawer
        drawer.set({ height: storeHeight === null || storeHeight < DEFAULT_DRAWER_HEIGHT ? DEFAULT_DRAWER_HEIGHT : storeHeight, stored: null })
        if ($activePage === "edit") drawerOpenedInEdit.set(true)

        // if drawer is closed when searching, set category to "all"
        if (e === null && ["shows", "overlays", "templates", "media", "audio"].includes($activeDrawerTab)) {
            drawerTabsData.update((a) => {
                a[$activeDrawerTab].activeSubTab = "all"
                return a
            })
        }
    }

    function closeDrawer() {
        drawer.set({ height: minHeight, stored: height })
        drawerOpenedInEdit.set(false)
    }

    function mouseup(e: any) {
        if (!e.target.closest("input") && !e.target.closest(".contextMenu") && !searchValue.length) searchActive = false

        mouse = null
        if (!e.target.closest(".top")) move = false
    }

    $: activeTab = $activeDrawerTab
    function openDrawerTab(tab: { id: string; name: string; icon: string }) {
        const newId = tab.id as DrawerTabIds
        if ($activeDrawerTab === newId) return

        // open visually immediately
        activeTab = newId

        // allow click event first
        setTimeout(() => {
            activeDrawerTab.set(newId)

            // remove focus for search function to work
            setTimeout(() => (document.activeElement as any)?.blur(), 10)
        }, 10)
    }

    let searchValue = ""
    $: searchValue = searchValue.endsWith(" ") ? removeWhitespace(searchValue) + " " : removeWhitespace(searchValue)
    $: if ($activeDrawerTab) searchValue = ""
    const removeWhitespace = (v: string) =>
        v
            .split(" ")
            .filter((n) => n)
            .join(" ")
    function search() {
        if (storeHeight === null && $drawer.height > minHeight) return

        click(null)
        // if ($activeDrawerTab === "shows") {
        // }
    }

    let firstMatch: null | any = null
    let searchElem: HTMLInputElement | undefined
    async function keydown(e: KeyboardEvent) {
        if ((e.ctrlKey || e.metaKey) && e.key === "f") {
            if ($activePopup === "show" || shouldOpenReplace()) return
            if ($activeDrawerTab === "hymns") {
                e.preventDefault()
                window.dispatchEvent(new CustomEvent("hamro-hymns-focus-search"))
                return
            }
            focusSearch()

            // change to "Show" and "All" when searching when drawer is closed
            // (not needed now as there is Quick search)
            // if ($drawer.height <= minHeight) {
            //     setDrawerTabData("shows", "all")
            //     activeDrawerTab.set("shows")
            // }
        } else if ((e.ctrlKey || e.metaKey) && e.key === "d") {
            if (!$selected?.id && !$activeEdit.items.length) click(null)
        } else if (e.key === "Enter") {
            if (document.activeElement !== searchElem || !searchValue.length || !firstMatch || !$activeProject || $focusMode) return
            if ($activeDrawerTab !== "shows") return

            let match = $activeShow?.data?.searchInput === true ? { id: $activeShow.id } : firstMatch

            // play
            if (e.ctrlKey || e.metaKey) {
                const showId = match.id
                await loadShows([showId])
                let layoutRef = getLayoutRef(showId)
                let firstEnabledIndex = layoutRef.findIndex((a) => !a.data.disabled)
                if (firstEnabledIndex === -1) return
                updateOut("active", firstEnabledIndex, layoutRef)
                setOutput("slide", { id: showId, layout: $showsCache[showId].settings.activeLayout, index: firstEnabledIndex })
                return
            }

            // create from search
            if (match === "SEARCH_CREATE") {
                quickTextCache.set({ name: searchValue[0]?.toUpperCase() + searchValue.slice(1), text: "", fromSearch: true })
                activePopup.set("show")
                return
            }

            searchElem.select()
            let newIndex = ($activeShow?.index ?? $projects[$activeProject]?.shows?.length - 1) + 1
            if ($activePage === "show") history({ id: "UPDATE", newData: { key: "shows", index: newIndex, data: { id: match.id } }, oldData: { id: $activeProject }, location: { page: "show", id: "project_ref" } })
            activeShow.set({ ...match, index: newIndex })
            searchValue = ""
        } else if (e.key === "Escape") {
            if (!searchValue.length && searchActive) searchActive = false
        }
    }

    $: if ($activeShow?.type === undefined || $activeShow?.type === "show") previousShow.set(JSON.stringify($activeShow))

    $: tabs = keysToID(drawerTabs)

    let searchActive = false
    $: if (searchActive) {
        setTimeout(() => {
            searchElem?.focus()
        }, 10)
    }
    $: if ($activeTriggerFunction === "drawer_search") focusSearch()
    function focusSearch() {
        searchActive = false
        setTimeout(() => (searchActive = true))
    }

    const hiddenInFocusMode = ["templates", "calendar"]
</script>

<svelte:window on:mouseup={mouseup} on:mousemove={mousemove} on:keydown={keydown} />

<!-- <Resizeable id="drawer" side="bottom" minWidth={50}> -->
<section class="drawer" style="height: {height}px">
    <div
        class="top context #drawer_top"
        on:mousedown={mousedown}
        on:click={click}
        on:keydown={() => {
            // does not work and prevents search input keys
            // if (e.key === "Enter" || e.key === " ") {
            //     e.preventDefault()
            //     click(e)
            // }
        }}
    >
        <!-- role="button"
        tabindex="0" -->
        <span class="tabs">
            {#each tabs as tab, i}
                {#if $drawerTabsData[tab.id]?.enabled !== false && getAccess(tab.id).global !== "none" && (!$focusMode || !hiddenInFocusMode.includes(tab.id))}
                    <MaterialButton id={tab.id} style="border-radius: 7px;margin: 3px 2px;border: 1px solid transparent;padding: 0.2em 0.82em;" class="drawerNavCapsule context #drawer_top" title="<b>{tab.name.split('.')[0]}.{tab.name.split('.')[1]}</b>{tab.title ? `\n${tab.title}` : ''} [Ctrl+{i + 1}]" isActive={activeTab === tab.id} on:click={() => openDrawerTab(tab)} on:dblclick={closeDrawer}>
                        <Icon id={tab.icon} size={1.25} white={activeTab === tab.id} />
                        {#if !$labelsDisabled && !$focusMode}
                            <span><T id={tab.name} /></span>
                        {/if}
                    </MaterialButton>
                {/if}
            {/each}
        </span>

        <input bind:this={searchElem} class:hidden={!searchActive && !searchValue.length} class="search edit drawer_search" type="text" placeholder={translateText("main.search...", $dictionary)} bind:value={searchValue} on:input={search} use:selectTextOnFocus use:nepaliTypingInput />
        {#if !searchActive && !searchValue.length}
            <Button class="search" style="border-bottom: 2px solid var(--secondary);" on:click={() => (searchActive = true)} title={translateText("tabs.search_tip [Ctrl+F]")} bold={false}>
                <Icon id="search" size={1.4} white right={!$labelsDisabled && !$focusMode} />
                {#if !$labelsDisabled && !$focusMode}<p style="opacity: 0.8;font-size: 1.1em;"><T id="main.search" /></p>{/if}
            </Button>
        {:else}
            {#if $activeDrawerTab === "scripture"}
                <div class="clearSearch autocomplete">
                    <Icon id="autofill" white />
                </div>
                <div class="clearSearch options" on:mousedown|stopPropagation on:mouseup|stopPropagation>
                    <Button style="height: 100%;" title={translateText("edit.options")} on:click={() => activePopup.set("drawer_search_options")}>
                        <Icon id="options" white={!$scriptureSettings.enterSwapped} />
                    </Button>
                </div>
            {/if}
            <div class="clearSearch">
                <Button
                    style="height: 100%;"
                    title={translateText("clear.search")}
                    on:click={() => {
                        searchValue = ""
                        searchElem?.focus()
                    }}
                >
                    <Icon id="clear" white />
                </Button>
            </div>
        {/if}
    </div>

    <div class="content">
        <Resizeable id="leftPanelDrawer">
            <Navigation id={$activeDrawerTab} />
        </Resizeable>
        <Content id={$activeDrawerTab} bind:searchValue bind:firstMatch />
        <Resizeable id="rightPanelDrawer" let:width side="right">
            <div class="right" class:row={width > DEFAULT_WIDTH * 1.8}>
                <Info id={$activeDrawerTab} />
            </div>
        </Resizeable>
    </div>
</section>

<style>
    section {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100px;
        z-index: 20;

        background-color: var(--primary);

        /* box-shadow: 0 -2px 14px rgb(0 0 0 / 0.12); */
    }

    .top {
        position: relative;
        height: 40px;
        display: flex;
        justify-content: space-between;
        padding-top: 4px;
        padding-inline: 4px;

        background: linear-gradient(180deg, color-mix(in srgb, var(--primary) 92%, #193055 8%) 0%, color-mix(in srgb, var(--primary-darker) 88%, #0d1a30 12%) 100%);
        border-bottom: 1px solid color-mix(in srgb, var(--secondary) 18%, var(--primary-lighter) 82%);
        box-shadow: 0 -2px 10px rgb(0 0 0 / 0.28);
        z-index: 1;
    }
    .top::after {
        content: "";
        background-color: color-mix(in srgb, var(--secondary) 28%, var(--primary-lighter) 72%);
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 3px;
        cursor: ns-resize;
    }

    .top .tabs {
        display: flex;
        align-items: center;
        overflow-x: auto;
        overflow-y: hidden;
    }

    .top :global(button.drawerNavCapsule.isActive) {
        background: linear-gradient(180deg, color-mix(in srgb, var(--secondary) 26%, var(--primary) 74%) 0%, color-mix(in srgb, var(--secondary) 14%, var(--primary-darkest) 86%) 100%) !important;
        border: 1px solid color-mix(in srgb, var(--secondary) 58%, transparent) !important;
        border-bottom: 1px solid color-mix(in srgb, var(--secondary) 68%, transparent) !important;
        color: #ffffff !important;
    }

    .search {
        background-color: var(--primary-darkest);
        color: var(--text);
        font-size: inherit;
        font-family: inherit;
        width: var(--navigation-width);
        min-width: var(--navigation-width);
        margin: 3px 2px;
        padding: 0 10px;
        padding-right: 40px;
        border: 1px solid var(--primary-lighter);
        border-radius: 7px;
    }
    .search:active,
    .search:focus {
        outline: 2px solid var(--secondary);
        outline-offset: -2px;
    }
    .search::placeholder {
        color: inherit;
        opacity: 0.4;
    }

    .clearSearch {
        position: absolute;
        right: 0;
        height: calc(100% - 4px);
        z-index: 1;
    }
    .clearSearch.options {
        right: 40px;
    }
    .clearSearch.autocomplete {
        right: 90px;
        display: flex;
        align-items: center;
        opacity: 0.2;
        pointer-events: none;
    }

    .hidden {
        display: none;
    }

    .content {
        display: flex;
        height: calc(100% - 40px);
        justify-content: space-between;
    }

    .right {
        display: contents;
    }
    .right.row :global(.scroll.split) {
        flex-direction: row-reverse;
    }
    .right.row :global(.scroll.split .border) {
        border-inline-end: 2px solid var(--primary-lighter);
        overflow-y: auto;
    }
    /* scripture preview */
    .right.row :global(.scroll.split .zoomed) {
        flex: 1;
    }

    @media screen and (max-width: 750px) {
        .tabs span {
            display: none;
        }
    }
</style>
