<script lang="ts">
    import type { TopViews } from "../../../types/Tabs"
    import { activeEdit, activePage, activeShow, dictionary, editHistory, labelsDisabled, shows, special } from "../../stores"
    import { translateText } from "../../utils/language"
    import Icon from "../helpers/Icon.svelte"
    import T from "../helpers/T.svelte"
    import MaterialButton from "./MaterialButton.svelte"

    export let id: TopViews
    export let red = false
    export let disabled = false
    export let hideLabel: null | boolean = null
    $: label = hideLabel === null ? !$labelsDisabled : !hideLabel

    const keys = { show: 1, edit: 2, stage: 3, draw: 4, settings: 5 }

    function openPage() {
        activePage.set(id)

        if (id === "edit") openActiveShow()
    }

    function openActiveShow() {
        let showIsActive = $activeShow && ($activeShow.type || "show") === "show"
        let noEditSlide = $activeEdit.slide === null || $activeEdit.slide === undefined
        if (showIsActive && !$activeEdit.id) openEdit()
        else if (showIsActive && noEditSlide) updateEditItem()
        else if (showIsActive && $activeEdit.showId && $activeEdit.showId !== $activeShow?.id) openEdit()

        function updateEditItem() {
            return // more annoying than helpful
            // set to show if: media has been opened AND show has not been opened AND it's not locked
            if ($activeEdit.id && (!$editHistory.find((a) => $activeEdit.id === a.edit?.id) || $editHistory.find((a) => $activeShow?.id === a.show?.id))) return
            if ($shows[$activeShow?.id || ""]?.locked) return

            openEdit()
        }

        function openEdit() {
            activeEdit.set({ slide: $activeEdit.slide || 0, items: [], showId: $activeShow?.id })
        }
    }
</script>

<MaterialButton class="topNavCapsule" style="border-radius: 8px;margin: 5px 3px;min-height: 32px;border: 1px solid transparent;{label ? 'padding: 0.24em 1.05em;' : 'padding: 0.24em 0.65em;'}" title={translateText(`menu._title_${id}${$special.numberKeys ? "" : ` [${keys[id]}]`}`, $dictionary)} isActive={$activePage === id} {disabled} on:click={openPage} {red}>
    <Icon {id} size={1.35} white={$activePage === id} />
    {#if label}<span><T id="menu.{id}" /></span>{/if}
</MaterialButton>

<style>
    :global(button.topNavCapsule.isActive) {
        background: linear-gradient(180deg, color-mix(in srgb, var(--secondary) 28%, var(--primary) 72%) 0%, color-mix(in srgb, var(--secondary) 16%, var(--primary-darkest) 84%) 100%) !important;
        border: 1px solid color-mix(in srgb, var(--secondary) 62%, transparent) !important;
        border-bottom: 1px solid color-mix(in srgb, var(--secondary) 72%, transparent) !important;
        box-shadow: 0 2px 10px color-mix(in srgb, var(--secondary) 20%, transparent);
        color: #ffffff !important;
    }

    :global(button.topNavCapsule:not(.isActive):not(:disabled):hover) {
        background: color-mix(in srgb, var(--secondary) 10%, var(--primary) 90%) !important;
        border-color: color-mix(in srgb, var(--secondary) 28%, transparent) !important;
    }

    @media screen and (max-width: 750px) {
        span {
            display: none;
        }
    }
</style>
