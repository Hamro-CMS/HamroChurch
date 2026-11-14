<script type="ts">
    import { slide } from "svelte/transition"
    import { activeEdit, activePage, activeProfile, activeShow, dictionary, drawSettings, drawTool, os, outputDisplay, outputs, paintCache, profiles, saved, settingsTab, shows } from "../../stores"
    import { translateText } from "../../utils/language"
    import Icon from "../helpers/Icon.svelte"
    import { toggleOutputs } from "../helpers/output"
    import T from "../helpers/T.svelte"
    import Button from "../inputs/Button.svelte"
    import TopButton from "../inputs/TopButton.svelte"

    export let isWindows = false

    $: show = $shows[$activeShow?.id || ""]
    $: showProfile = profile?.access.shows || {}
    $: isLocked = show?.locked || showProfile.global === "read" || showProfile[show?.category || ""] === "read"

    // && !$editHistory.length
    $: editDisabled = $activeEdit.id && ($activeEdit.type || "show") !== "show" ? false : $activeShow && ($activeShow?.type || "show") === "show" ? isLocked : $activeShow?.type === "pdf" || !$activeShow?.id
    $: physicalOutputWindows = Object.values($outputs).filter((a) => a.enabled && !a.invisible)

    let confirm = false
    let disableClick = false
    let cancelConfirmTimeout: NodeJS.Timeout | null = null
    function toggleOutput(e: any) {
        if (cancelConfirmTimeout) clearTimeout(cancelConfirmTimeout)

        const forceKey = e.ctrlKey || e.metaKey

        if (!$outputDisplay || confirm) {
            if (confirm) {
                // prevent displaying just after close
                disableClick = true
                setTimeout(() => (disableClick = false), 800)
            }

            confirm = false
            toggleOutputs(null, { force: forceKey })
            return
        }

        if (forceKey) return

        confirm = true
        cancelConfirmTimeout = setTimeout(() => {
            confirm = false
        }, 1800)
    }

    function openOutputSettings() {
        settingsTab.set("display_settings")
        activePage.set("settings")
    }

    // disabled tabs

    let settingsDisabled = false
    $: profile = $profiles[$activeProfile || ""]
    $: settingsDisabled = Object.keys(profile?.access.settings || {}).length > 6

    $: noPhysicalOutputWindows = (!$outputDisplay && !physicalOutputWindows.length) || disableClick
</script>

<div class="top" class:drag={!isWindows}>
    <span style="flex: 1 1 0; min-width: 0;">
        {#if !$saved && $os.platform !== "win32"}
            <div class="unsaved" />
        {/if}
        <h1 class="brandTitle">Hamro Church <span class="brandDomain">(hamrocms.com)</span></h1>
    </span>
    <span>
        <TopButton id="show" />
        <TopButton id="edit" disabled={editDisabled} />
        <TopButton id="stage" />
    </span>
    <span style="flex: 1 1 0; min-width: 0; justify-content: flex-end;">
        <TopButton id="draw" red={$drawTool === "fill" || ($drawTool === "zoom" && $drawSettings.zoom?.size !== 100) || !!($drawTool === "paint" && $paintCache?.length)} hideLabel />
        {#if !settingsDisabled}
            <TopButton id="settings" hideLabel />
        {/if}

        <!-- <MaterialButton id="output_window_button" class="context #output display {$outputDisplay ? 'on' : 'off'}" title="menu.{$outputDisplay ? (confirm ? 'again_confirm' : '_title_display_stop') : '_title_display'} [Ctrl+O]" style={$outputDisplay || disableClick ? "" : "border-bottom: 2px solid var(--secondary);"} on:click={toggleOutput} disabled={(!$outputDisplay && !physicalOutputWindows.length) || disableClick} red={$outputDisplay}>
            {#if $outputDisplay}
                {#if confirm}
                    <Icon id="close" size={1.6} white />
                {:else}
                    <Icon id="cancelDisplay" size={1.6} white />
                {/if}
            {:else}
                <Icon id="outputs" size={1.6} white />
            {/if}

            {#if $outputDisplay && confirm}
                <div class="click_again" transition:slide>
                    <T id="menu.again_confirm" />
                </div>
            {/if}
        </MaterialButton> -->
        <Button id="output_window_button" title={translateText(`menu.${$outputDisplay ? (confirm ? "again_confirm" : "_title_display_stop") : "_title_display"} [Ctrl+O]`, $dictionary)} style={$outputDisplay || disableClick ? "" : "border-bottom: 2px solid var(--secondary);"} on:click={toggleOutput} class="context #output display {$outputDisplay ? 'on' : 'off'}" red={$outputDisplay} disabled={noPhysicalOutputWindows}>
            {#if $outputDisplay}
                {#if confirm}
                    <Icon id="close" size={1.6} white />
                {:else}
                    <Icon id="cancelDisplay" size={1.6} white />
                {/if}
            {:else}
                <Icon id="outputs" size={1.6} white />
            {/if}

            {#if $outputDisplay && confirm}
                <div class="click_again" transition:slide>
                    <T id="menu.again_confirm" />
                </div>
            {/if}
        </Button>
        {#if !$outputDisplay && !physicalOutputWindows.length}
            <div data-title={translateText("No physical outputs!<br>'settings.invisible_window' is turned on in the outputs settings.")} style="position: absolute;top: 0;inset-inline-end: 0;height: 100%;width: 60px;" role="none" on:click={openOutputSettings} />
        {/if}
    </span>
</div>

<style>
    .top {
        position: relative;
        display: flex;
        justify-content: space-between;
        z-index: 30;
        min-height: 40px;
        height: 40px;

        box-shadow: 0 0 4px rgb(0 0 0 / 0.4);

        /* disabled because it's causing unexpected behaviour in Windows 11 */
        /* -webkit-app-region: drag; */
    }
    .top span {
        display: flex;
    }

    .top.drag {
        -webkit-app-region: drag;
    }

    .top.drag :global(button) {
        -webkit-app-region: no-drag;
    }

    /* .dragZone {
        position: absolute;

        display: flex;
        justify-content: space-between;
        background-color: var(--primary-darker);
        width: 100%;
        height: 3px;

        z-index: 2;
        -webkit-app-region: drag;
    } */

    div :global(button.display) {
        display: flex;
        justify-content: center;
        min-width: 60px;
    }

    .unsaved {
        position: absolute;
        left: 0;
        height: 100%;
        width: 5px;
        background-color: var(--red);
    }

    .brandTitle {
        align-self: center;
        padding-inline: 42px 12px;
        font-size: 1.8em;
        display: inline-flex;
        align-items: baseline;
        gap: 8px;
        min-width: 0;
    }

    .brandDomain {
        font-size: 0.62em;
        font-weight: 600;
        color: var(--text);
        opacity: 0.88;
        letter-spacing: 0.01em;
    }

    @media screen and (max-width: 780px) {
        .brandTitle {
            padding-inline: 14px 8px;
            font-size: 1.45em;
        }
    }

    /* .logo {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 0px 10px;
        width: 100%;
        gap: 10px;
    } */

    @media screen and (max-width: 580px) {
        .top span:first-child {
            display: none;
        }
    }

    .click_again {
        position: absolute;
        bottom: 0;
        inset-inline-end: 0;
        transform: translateY(100%);

        font-size: 1.1em;
        padding: 4px 10px;
        font-weight: normal;
        background-color: var(--primary-darker);
        color: var(--text);

        border: 2px solid var(--secondary);
        border-top: none;
        border-inline-end: none;
    }
</style>
