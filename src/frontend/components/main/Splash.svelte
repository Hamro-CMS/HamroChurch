<script lang="ts">
    import { onMount } from "svelte"
    import { Main } from "../../../types/IPC/Main"
    import { activeDrawerTab, activePopup, activeProject, drawer, language, projects, projectView, quickSearchActive, showRecentlyUsedProjects, shows, softwarePromotion, special, stutiMediaActiveVideoId, stutiMediaFeed, version } from "../../stores"
    import { sendMain } from "../../IPC/main"
    import { DEFAULT_DRAWER_HEIGHT } from "../../utils/common"
    import { history } from "../helpers/history"
    import Icon from "../helpers/Icon.svelte"
    import T from "../helpers/T.svelte"
    import Link from "../inputs/Link.svelte"
    import MaterialButton from "../inputs/MaterialButton.svelte"
    import Center from "../system/Center.svelte"
    import { getVOTD } from "./votd"

    const DEFAULT_SUBSCRIBE_URL = "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"
    const HAMROCMS_SPLASH_URL = "https://hamrocms.com/register?utm_source=aradhana_studio&utm_medium=splash_hub"

    $: latestStutiSong = ($stutiMediaFeed?.videos || [])[0] || null
    $: splashSubscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || DEFAULT_SUBSCRIBE_URL

    function openStutiFromSplash(videoId: string) {
        if ($drawer.height <= 40) {
            drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
        }
        stutiMediaActiveVideoId.set(videoId)
        activeDrawerTab.set("stuti")
    }

    function openPromoOrHamroCms() {
        const promo = $softwarePromotion
        if (promo) {
            sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
                id: promo.id,
                url: promo.cta_url || "https://hamrocms.com",
                eventUrl: promo.event_url
            })
            return
        }
        sendMain(Main.URL, HAMROCMS_SPLASH_URL)
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
        </span>

        <div class="studioSpotlightHub">
            {#if latestStutiSong}
                <div class="hubCard stutiHubCard">
                    <button type="button" class="hubThumbBtn" on:click={() => openStutiFromSplash(latestStutiSong.video_id)} title={latestStutiSong.title}>
                        <img src={latestStutiSong.thumbnail_url} alt={latestStutiSong.title} loading="lazy" />
                        <span class="hubPlayPill">▶</span>
                    </button>
                    <div class="hubContent">
                        <div class="hubTopLine">
                            <span class="hubBadge stutiHubBadge">🎵 Stuti Media</span>
                            <button type="button" class="hubSubBtn" on:click={() => sendMain(Main.URL, splashSubscribeUrl)}>Subscribe ↗</button>
                        </div>
                        <button type="button" class="hubTitleBtn" on:click={() => openStutiFromSplash(latestStutiSong.video_id)}>
                            {latestStutiSong.title}
                        </button>
                        <div class="hubSub">
                            {$language === "ne" ? "आराधना गीत लाइब्रेरी खोल्न क्लिक गर्नुहोस्" : "Open Stuti Media worship library"}
                        </div>
                    </div>
                </div>
            {/if}

            <button type="button" class="hubCard cmsHubCard" on:click={openPromoOrHamroCms}>
                <div class="hubContent">
                    <div class="hubTopLine">
                        <span class="hubBadge cmsHubBadge">☁️ HamroCMS.com</span>
                        <span class="hubActionHint">{$softwarePromotion?.cta_label || ($language === "ne" ? "सुरु गर्नुहोस् ↗" : "Explore ↗")}</span>
                    </div>
                    <div class="hubTitle">
                        {$softwarePromotion?.title || ($language === "ne" ? "चर्च सदस्य, भेटी र दशांश व्यवस्थापन" : "All-in-One Church Management")}
                    </div>
                    <div class="hubSub">
                        {$softwarePromotion?.body || ($language === "ne" ? "नेपाली पात्रो, आर्थिक रिपोर्ट र रसिद — एकै ठाउँमा" : "Members, tithes, expenses & Nepali calendar")}
                    </div>
                </div>
            </button>
        </div>

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
    .studioSpotlightHub {
        margin-top: 14px;
        width: min(680px, 92%);
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
        text-align: left;
    }

    @media (max-width: 620px) {
        .studioSpotlightHub {
            grid-template-columns: 1fr;
        }
    }

    @media (max-height: 580px) {
        .studioSpotlightHub {
            display: none;
        }
    }

    .hubCard {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 9px 11px;
        border-radius: 10px;
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: var(--text);
        font-family: inherit;
        text-align: left;
        min-width: 0;
        transition: border-color 0.15s ease, background-color 0.15s ease;
    }

    .cmsHubCard {
        cursor: pointer;
    }

    .hubCard:hover {
        background: rgba(255, 255, 255, 0.07);
        border-color: rgba(255, 255, 255, 0.22);
    }

    .hubThumbBtn {
        position: relative;
        flex-shrink: 0;
        width: 82px;
        aspect-ratio: 16 / 9;
        padding: 0;
        border: none;
        border-radius: 6px;
        overflow: hidden;
        background: #000;
        cursor: pointer;
    }

    .hubThumbBtn img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .hubPlayPill {
        position: absolute;
        inset: auto auto 4px 4px;
        font-size: 0.6rem;
        padding: 2px 5px;
        border-radius: 4px;
        background: rgba(220, 38, 38, 0.9);
        color: #fff;
        font-weight: 700;
    }

    .hubContent {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 3px;
    }

    .hubTopLine {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
    }

    .hubBadge {
        font-size: 0.66rem;
        font-weight: 700;
        padding: 2px 6px;
        border-radius: 999px;
    }

    .stutiHubBadge {
        background: rgba(239, 68, 68, 0.18);
        color: #fca5a5;
        border: 1px solid rgba(239, 68, 68, 0.35);
    }

    .cmsHubBadge {
        background: rgba(56, 189, 248, 0.18);
        color: #7dd3fc;
        border: 1px solid rgba(56, 189, 248, 0.35);
    }

    .hubSubBtn {
        border: 1px solid rgba(239, 68, 68, 0.45);
        background: rgba(239, 68, 68, 0.16);
        color: #fecaca;
        font-size: 0.65rem;
        font-weight: 600;
        padding: 2px 6px;
        border-radius: 999px;
        cursor: pointer;
        font-family: inherit;
    }

    .hubActionHint {
        font-size: 0.68rem;
        font-weight: 600;
        color: #7dd3fc;
    }

    .hubTitleBtn,
    .hubTitle {
        border: none;
        background: transparent;
        padding: 0;
        color: var(--text);
        font-family: inherit;
        font-size: 0.78rem;
        font-weight: 600;
        line-height: 1.25;
        text-align: left;
        cursor: pointer;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .hubSub {
        font-size: 0.69rem;
        opacity: 0.72;
        line-height: 1.25;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

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
