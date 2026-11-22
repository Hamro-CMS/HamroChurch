<script lang="ts">
    import { activePopup, activeScripture, drawerTabsData, labelsDisabled, openScripture, scriptures } from "../../../stores"
    import { getAccess } from "../../../utils/profile"
    import { keysToID } from "../../helpers/array"
    import Icon from "../../helpers/Icon.svelte"
    import T from "../../helpers/T.svelte"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import NavigationSections from "./NavigationSections.svelte"

    const profile = getAccess("scripture")
    $: readOnly = profile.global === "read"

    $: activeSubTab = $drawerTabsData.scripture?.activeSubTab || ""

    $: scripturesList = keysToID($scriptures)

    $: collections = scripturesList.filter((a) => a.collection)
    $: allBibles = scripturesList
        .filter((a) => !a.collection)
        .sort((a, b) => {
            const aNepali = /nepali|नेपाली/i.test(String(a.customName || a.name || "")) ? 0 : 1
            const bNepali = /nepali|नेपाली/i.test(String(b.customName || b.name || "")) ? 0 : 1
            if (aNepali !== bNepali) return aNepali - bNepali
            return String(a.customName || a.name || "").localeCompare(String(b.customName || b.name || ""))
        })
    $: favoritesList = scripturesList.filter((a) => a.favorite)

    let sections: any[] = []
    $: sections = [
        ...(favoritesList.length ? [[{ id: "TITLE", label: "category.favourites" }, ...convertToButton(favoritesList, false)]] : []),
        [{ id: "TITLE", label: "scripture.bibles_section" }, ...convertToButton(allBibles, false)],
        ...(collections.length ? [[{ id: "TITLE", label: "scripture.collections" }, ...convertToButton(collections, true)]] : [])
    ]

    function convertToButton(categories: any[], shouldSort = true) {
        const ordered = shouldSort
            ? [...categories].sort((a, b) => (a.customName || a.name).localeCompare(b.customName || b.name))
            : categories
        return ordered.map((a: any) => {
            const icon = a.api ? "scripture_alt" : a.collection ? "collection" : "scripture"
            const count = a.collection?.versions?.length || 0
            return { id: a.id, label: a.customName || a.name, icon, count, onDoubleClick: () => startScripture(), boxedIcon: true }
        })
    }

    function startScripture() {
        openScripture.set({ play: true, book: Number($activeScripture.reference?.book || 1), chapter: $activeScripture.reference?.chapters[0], verses: $activeScripture.reference?.verses })
    }

    function createCollection() {
        activePopup.set("create_collection")
    }

    function updateName(e: any) {
        const { id, value } = e.detail
        scriptures.update((a) => {
            a[id].customName = value
            return a
        })
    }
</script>

<NavigationSections {sections} active={activeSubTab} on:rename={updateName}>
    <div slot="section_0" style={favoritesList.length ? "" : "padding: 8px;{allBibles.length ? 'padding-top: 10px;' : ''}"}>
        {#if !favoritesList.length && allBibles.length >= 2}
            <MaterialButton disabled={readOnly} style="width: 100%;" title="new.collection" variant="outlined" on:click={createCollection} small>
                <Icon id="add" size={$labelsDisabled ? 0.9 : 1} white={$labelsDisabled} />
                {#if !$labelsDisabled}<T id="new.collection" />{/if}
            </MaterialButton>
        {/if}
    </div>
    <div slot="section_1" style="padding: 8px;{allBibles.length ? 'padding-top: 10px;' : ''}">
        {#if favoritesList.length && allBibles.length >= 2}
            <MaterialButton disabled={readOnly} style="width: 100%;" title="new.collection" variant="outlined" on:click={createCollection} small>
                <Icon id="add" size={$labelsDisabled ? 0.9 : 1} white={$labelsDisabled} />
                {#if !$labelsDisabled}<T id="new.collection" />{/if}
            </MaterialButton>
        {/if}
    </div>
</NavigationSections>
