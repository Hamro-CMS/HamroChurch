<script lang="ts">
    import { onMount } from "svelte"
    import { activeDrawerTab, activePopup, activeProject, drawer, language, projects, projectView, quickSearchActive, showRecentlyUsedProjects, shows, special, version } from "../../stores"
    import { DEFAULT_DRAWER_HEIGHT } from "../../utils/common"
    import { history } from "../helpers/history"
    import Icon from "../helpers/Icon.svelte"
    import T from "../helpers/T.svelte"
    import Link from "../inputs/Link.svelte"
    import MaterialButton from "../inputs/MaterialButton.svelte"
    import Center from "../system/Center.svelte"
    import { getVOTD } from "./votd"

    function openStutiTab() {
        if ($drawer.height <= 40) {
            drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
        }
        activeDrawerTab.set("stuti")
    }

    let logoSrc = "./logo-splash.png"
    let logoFailed = false

    function handleLogoError() {
        if (logoSrc !== "./logo.png") {
            logoSrc = "./logo.png"
        } else {
            logoFailed = true
        }
    }

    function createProject() {
        // if opened project is empty go to project list (to reduce confusion)
        if ($projects[$activeProject || ""]?.shows?.length === 0) {
            activeProject.set(null)
            projectView.set(true)
        }

        history({ id: "UPDATE", location: { page: "show", id: "project" } })

        showRecentlyUsedProjects.set(false)
    }

    let links: string[] = []
    function extractLinksAndCleanText(text: string) {
        links = []

        // extract and remove links from <a> tags
        const textWithoutATags = text.replace(/<a\s[^>]*href=["'](https?:\/\/[^"']+)["'][^>]*>.*?<\/a>/gi, (_match, url) => {
            links.push(url)
            return ""
        })
        // extract and remove raw links from plain text
        const finalText = textWithoutATags.replace(/https?:\/\/[^\s<>"']+/gi, (url) => {
            links.push(url)
            return ""
        })

        return finalText.replaceAll("\n", "<br>").replace(/\s+/g, " ").trim()
    }

    let votd: string = ""
    onMount(async () => {
        if ($special.splashText) return
        votd = await getVOTD()
    })
</script>

<Center class="context #splash" style="justify-content: flex-start;padding: 14px 16px;box-sizing: border-box;">
    <div class="splashInner">
        {#if !logoFailed}
            <div class="splashLogoWrap">
                <img src={logoSrc} alt="Aradhana Studio (आराधना स्टुडियो)" draggable={false} on:error={handleLogoError} />
            </div>
        {:else}
            <h1>{$language === "ne" ? "आराधना स्टुडियो" : "Aradhana Studio"}</h1>
        {/if}

        <p class="versionText">v{$version}</p>

        {#if $special.splashText}
            <p style="padding-top: 12px">
                {@html extractLinksAndCleanText($special.splashText)}
                <span class="links" style="display: flex;flex-direction: column;align-items: center;">
                    {#each links as link}
                        <Link url={link}>
                            {link.replace(/^(https?:\/\/)/, "")}
                            <Icon id="launch" white />
                        </Link>
                    {/each}
                </span>
            </p>
        {:else if Object.keys($shows).length < 20}
            <!-- shows up for new users (can be found in "About" menu) -->
            <p style="padding-top: 12px">
                <Link url="https://hamrocms.com/docs">
                    <T id="main.docs" />
                    <Icon id="launch" white />
                </Link>
            </p>
        {:else if votd}
            <p class="votd" style="padding-top: 12px" data-title="Verse of the Day [votd.org]">
                <Link url="https://votd.org/">
                    {votd}
                </Link>
            </p>
        {/if}

        <span style="padding-top: 16px" class="buttons">
            <MaterialButton icon="search" title="main.quick_search" on:click={() => quickSearchActive.set(true)}>
                <T id="main.quick_search" />
            </MaterialButton>
            <MaterialButton icon="project" title="tooltip.project" on:click={createProject}>
                <T id="new.project" />
            </MaterialButton>
            <MaterialButton
                icon="add"
                title="tooltip.show"
                on:click={(e) => {
                    if (e.detail.ctrl) {
                        history({ id: "UPDATE", newData: { remember: { project: $activeProject } }, location: { page: "show", id: "show" } })
                    } else activePopup.set("show")
                }}
            >
                <T id="new.show" />
            </MaterialButton>
            <MaterialButton icon="youtube" title="tabs.stuti_info" on:click={openStutiTab}>
                {$language === "ne" ? "स्तुति मिडिया" : "Stuti Media"}
            </MaterialButton>
        </span>

        <div class="securityNotice">
            <span class="shieldBadge">🛡</span>
            <span>
                {#if $language === "ne"}
                    सुरक्षा सूचना: मालवेयर वा नक्कली सफ्टवेयरबाट बच्न सधैं आधिकारिक साइट
                    <Link url="https://hamrocms.com/download">hamrocms.com/download</Link>
                    बाट मात्र डाउनलोड र अपडेट गर्नुहोस्।
                {:else}
                    Security Notice: Download &amp; update only from official site
                    <Link url="https://hamrocms.com/download">hamrocms.com/download</Link>
                    to avoid fake or malware copies.
                {/if}
            </span>
        </div>
    </div>
</Center>

<style>
    .splashInner {
        margin: auto 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        width: 100%;
        flex-shrink: 0;
    }

    .splashLogoWrap {
        width: min(350px, 78vw);
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: visible;
        padding-top: 4px;
        user-select: none;
        pointer-events: none;
    }

    .splashLogoWrap img {
        width: 100%;
        max-height: clamp(86px, 21vh, 154px);
        height: auto;
        display: block;
        object-fit: contain;
        filter: drop-shadow(0 8px 22px rgb(0 0 0 / 0.45));
    }

    .securityNotice {
        margin-top: 16px;
        padding: 6px 12px;
        max-width: min(560px, 90vw);
        display: inline-flex;
        align-items: center;
        gap: 7px;
        border-radius: 9px;
        border: 1px solid color-mix(in srgb, #10b981 38%, var(--primary-lighter) 62%);
        background: color-mix(in srgb, #064e3b 28%, var(--primary-darkest) 72%);
        color: color-mix(in srgb, var(--text) 90%, #a7f3d0 10%);
        font-size: 0.77rem;
        line-height: 1.4;
        text-align: left;
        white-space: normal;
    }

    .shieldBadge {
        flex-shrink: 0;
        font-size: 0.9rem;
    }

    @media screen and (max-height: 540px) {
        .securityNotice {
            display: none;
        }
    }

    .versionText {
        opacity: 0.72;
        margin-top: 2px;
        font-size: 0.92em;
    }

    h1 {
        font-size: 4em;
        overflow: initial;
    }

    p {
        overflow: initial;
    }

    .buttons {
        display: flex;
        flex-direction: column;
        gap: 5px;
    }

    .buttons :global(button) {
        justify-content: start;
        padding: 8px 12px;
    }

    .votd {
        padding: 0 10px;
        max-width: 580px;
        white-space: normal;
        text-align: left;
        font-style: italic;
        font-size: 0.9em;
    }
    .votd :global(a) {
        text-decoration: none;
    }

    @media screen and (max-height: 640px) {
        .splashLogoWrap {
            width: min(300px, 75vw);
        }
    }
    @media screen and (max-height: 500px) {
        .splashLogoWrap {
            width: min(230px, 68vw);
        }
        h1 {
            font-size: 3em;
        }
    }
    @media screen and (max-height: 400px) {
        .splashLogoWrap {
            width: min(180px, 60vw);
        }
        h1 {
            font-size: 2em;
        }
    }
    @media screen and (max-width: 800px) {
        .splashLogoWrap {
            width: min(280px, 78vw);
        }
        h1 {
            font-size: 2em;
        }
    }
</style>
