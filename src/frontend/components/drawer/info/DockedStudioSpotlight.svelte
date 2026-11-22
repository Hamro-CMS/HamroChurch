<script lang="ts">
    import { onDestroy, onMount } from "svelte"
    import { Main } from "../../../../types/IPC/Main"
    import type { SoftwarePromotionPayload, StutiMediaSong } from "../../../../types/Main"
    import { requestMain, sendMain } from "../../../IPC/main"
    import {
        activeDrawerTab,
        activePopup,
        currentWindow,
        drawer,
        language,
        outputs,
        softwarePromotion,
        softwarePromotions,
        stutiMediaActiveVideoId,
        stutiMediaFeed
    } from "../../../stores"
    import { DEFAULT_DRAWER_HEIGHT } from "../../../utils/common"
    import Icon from "../../helpers/Icon.svelte"

    export let hideStutiCard = false
    export let hidden = false

    type SpotlightItem =
        | {
              key: string
              kind: "promo"
              promo: SoftwarePromotionPayload
              title: string
              subtitle: string
              ctaLabel: string
              badgeLabel: string
              imageSrc: string
          }
        | {
              key: string
              kind: "stuti"
              song: StutiMediaSong
              title: string
              subtitle: string
              ctaLabel: string
              badgeLabel: string
          }
        | {
              key: string
              kind: "hamrocms"
              title: string
              subtitle: string
              ctaLabel: string
              badgeLabel: string
              url: string
          }

    const SAFE_RASTER_DATA_URI = /^data:image\/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+$/i
    const HAMROCMS_SIDEBAR_URL = "https://hamrocms.com/?utm_source=aradhana_studio&utm_medium=sidebar_spotlight"

    const appOpenedAt = Date.now()
    const firstSeenByKey = new Map<string, number>()
    const failedImagesByKey = new Set<string>()
    const recordedImpressions = new Set<string>()

    let nowTick = Date.now()
    let activeIndex = 0
    let rotateElapsedMs = 0
    let isHovered = false
    let imageErrorVersion = 0
    let delayTimer: ReturnType<typeof setInterval> | null = null
    let rotateTimer: ReturnType<typeof setInterval> | null = null

    function stripPlainText(input: unknown): string {
        if (typeof input !== "string") return ""
        return input
            .replace(/<[^>]*>/g, "")
            .replace(/[<>]/g, "")
            .trim()
    }

    function isDelayUnlocked(promo: SoftwarePromotionPayload, tick: number): boolean {
        const delayMs = Math.max(0, Number(promo?.delay_seconds) || 0) * 1000
        if (delayMs <= 0) return true
        const firstSeen = firstSeenByKey.get(promo.campaign_key) ?? appOpenedAt
        const anchor = firstSeen - appOpenedAt <= 15000 ? appOpenedAt : firstSeen
        return tick - anchor >= delayMs
    }

    function syncDelayTimer(list: SoftwarePromotionPayload[]) {
        const current = Date.now()
        nowTick = current
        const pending = list.some((p) => !isDelayUnlocked(p, current))

        if (pending && !delayTimer) {
            delayTimer = setInterval(() => {
                const tick = Date.now()
                nowTick = tick
                if (!allPromos.some((p) => !isDelayUnlocked(p, tick)) && delayTimer) {
                    clearInterval(delayTimer)
                    delayTimer = null
                }
            }, 1000)
        } else if (!pending && delayTimer) {
            clearInterval(delayTimer)
            delayTimer = null
        }
    }

    $: isNe = $language === "ne"
    $: allPromos = Array.isArray($softwarePromotions) ? $softwarePromotions : []

    $: {
        const now = Date.now()
        for (const p of allPromos) {
            if (p?.campaign_key && !firstSeenByKey.has(p.campaign_key)) {
                firstSeenByKey.set(p.campaign_key, now)
            }
        }
    }

    $: syncDelayTimer(allPromos)

    $: eligiblePromos = allPromos.filter((p) => isDelayUnlocked(p, nowTick))

    $: hasActiveProjection = Object.values($outputs || {}).some((o: any) => !o?.stageOutput && Boolean(o?.out?.slide || o?.out?.background || o?.out?.overlays?.length))

    $: stutiSongs = ($stutiMediaFeed?.videos || []).slice(0, 5)

    $: spotlightItems = buildSpotlightItems(eligiblePromos, stutiSongs, hideStutiCard, isNe, imageErrorVersion)

    function buildSpotlightItems(promos: SoftwarePromotionPayload[], songs: StutiMediaSong[], hideStuti: boolean, nepali: boolean, _errVer: number): SpotlightItem[] {
        const items: SpotlightItem[] = []

        const maxLen = Math.max(promos.length, hideStuti ? 0 : songs.length)
        for (let i = 0; i < maxLen; i++) {
            if (i < promos.length) {
                const promo = promos[i]
                const rawSrc = !failedImagesByKey.has(promo.campaign_key) ? promo.resolved_image_src || "" : ""
                const imageSrc = SAFE_RASTER_DATA_URI.test(rawSrc) ? rawSrc : ""
                items.push({
                    key: `promo:${promo.campaign_key}`,
                    kind: "promo",
                    promo,
                    title: stripPlainText(promo.title) || "Aradhana Studio",
                    subtitle: stripPlainText(promo.body) || "hamrocms.com",
                    ctaLabel: stripPlainText(promo.cta_label) || (nepali ? "अहिले हेर्नुहोस्" : "Explore Now"),
                    badgeLabel: "HAMROCMS",
                    imageSrc
                })
            }

            if (!hideStuti && i < songs.length) {
                const song = songs[i]
                items.push({
                    key: `stuti:${song.video_id}`,
                    kind: "stuti",
                    song,
                    title: song.title,
                    subtitle: nepali ? "स्तुति मिडिया आराधना गीत" : "Stuti Media Worship Song",
                    ctaLabel: nepali ? "गीत सुन्नुहोस्" : "Watch & Listen",
                    badgeLabel: "STUTI MEDIA"
                })
            }
        }

        if (items.length === 0) {
            items.push({
                key: "builtin:hamrocms",
                kind: "hamrocms",
                title: nepali ? "HamroCMS चर्च व्यवस्थापन प्रणाली" : "Complete Church Management",
                subtitle: nepali ? "सदस्य, भेटी र सेवा व्यवस्थापन एकै ठाउँमा" : "Manage members, finance & events online",
                ctaLabel: nepali ? "hamrocms.com खोल्नुस्" : "Visit HamroCMS.com",
                badgeLabel: "HAMROCMS",
                url: HAMROCMS_SIDEBAR_URL
            })
        }

        return items
    }

    $: if (spotlightItems.length === 0) {
        activeIndex = 0
        rotateElapsedMs = 0
    } else if (activeIndex >= spotlightItems.length) {
        activeIndex = 0
    }

    $: currentItem = spotlightItems[activeIndex] ?? spotlightItems[0] ?? null

    $: activeIntervalMs = ((currentItem?.kind === "promo" ? Math.max(7, Number(currentItem.promo.rotate_interval_seconds) || 10) : 9) * 1000)
    $: progressPct = spotlightItems.length > 1 ? Math.min(100, Math.max(0, (rotateElapsedMs / activeIntervalMs) * 100)) : 0

    $: if (!$currentWindow && !hidden && !$activePopup && currentItem?.kind === "promo" && currentItem.promo.campaign_key && !recordedImpressions.has(currentItem.promo.campaign_key)) {
        recordedImpressions.add(currentItem.promo.campaign_key)
        sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
            id: currentItem.promo.id,
            campaignKey: currentItem.promo.campaign_key,
            eventUrl: currentItem.promo.event_url
        })
    }

    function openStutiMediaTab(videoId?: string) {
        if ($drawer.height <= 40) {
            drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
        }
        const targetId = videoId || stutiSongs[0]?.video_id || ""
        if (targetId) {
            stutiMediaActiveVideoId.set(targetId)
        }
        activeDrawerTab.set("stuti")
    }

    function handleClick() {
        if (!currentItem) return
        if (currentItem.kind === "stuti") {
            openStutiMediaTab(currentItem.song.video_id)
            return
        }
        if (currentItem.kind === "promo") {
            const rawUrl = String(currentItem.promo.cta_url || "").trim()
            // If promo points to Stuti Media YouTube channel/video and feed is available, open Stuti Media tab inside app
            if (/youtube\.com\/@stutimedianepal/i.test(rawUrl) && stutiSongs.length > 0) {
                sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
                    id: currentItem.promo.id,
                    campaignKey: currentItem.promo.campaign_key,
                    eventUrl: currentItem.promo.event_url,
                    event: "click"
                })
                openStutiMediaTab(stutiSongs[0]?.video_id)
                return
            }
            sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
                id: currentItem.promo.id,
                url: rawUrl || "https://hamrocms.com",
                eventUrl: currentItem.promo.event_url
            })
            return
        }
        sendMain(Main.URL, currentItem.url)
    }

    function stepSlide(delta: number, e: MouseEvent) {
        e.stopPropagation()
        if (spotlightItems.length <= 1) return
        rotateElapsedMs = 0
        activeIndex = (activeIndex + delta + spotlightItems.length) % spotlightItems.length
    }

    function handleQuickStutiOpen(e: MouseEvent) {
        e.stopPropagation()
        openStutiMediaTab(currentItem?.kind === "stuti" ? currentItem.song.video_id : stutiSongs[0]?.video_id)
    }

    function handlePromoImageError(campaignKey: string) {
        failedImagesByKey.add(campaignKey)
        imageErrorVersion += 1
    }

    onMount(() => {
        if ($currentWindow) return

        void requestMain(Main.GET_SOFTWARE_PROMOTION, undefined, (data) => {
            if (Array.isArray(data)) {
                softwarePromotions.set(data)
                softwarePromotion.set(data[0] ?? null)
            }
        })

        void requestMain(Main.GET_STUTI_MEDIA_SONGS, undefined, (data) => {
            if (data?.videos) {
                stutiMediaFeed.set(data)
            }
        })

        rotateTimer = setInterval(() => {
            if ($activePopup || hidden || isHovered || hasActiveProjection || spotlightItems.length <= 1) return

            rotateElapsedMs += 250
            if (rotateElapsedMs >= activeIntervalMs) {
                rotateElapsedMs = 0
                activeIndex = (activeIndex + 1) % spotlightItems.length
            }
        }, 250)
    })

    onDestroy(() => {
        if (delayTimer) clearInterval(delayTimer)
        if (rotateTimer) clearInterval(rotateTimer)
    })
</script>

{#if !$currentWindow && !hidden && currentItem}
    <div
        class="spotlightWrap"
        on:mouseenter={() => (isHovered = true)}
        on:mouseleave={() => (isHovered = false)}
    >
        <div
            role="button"
            tabindex="0"
            class="spotlightCard kind-{currentItem.kind}"
            on:click={handleClick}
            on:keydown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault()
                    handleClick()
                }
            }}
            title={currentItem.title}
        >
            {#if spotlightItems.length > 1}
                <div class="progressTrack">
                    <div class="progressFill" style="width: {progressPct}%;" />
                </div>
            {/if}

            <div class="mediaBanner">
                {#if currentItem.kind === "stuti"}
                    <img src={currentItem.song.thumbnail_url} alt={currentItem.title} loading="lazy" />
                    <div class="mediaVignette" />
                    <div class="playCircle" aria-hidden="true">
                        <Icon id="play" size={1.05} white />
                    </div>
                {:else if currentItem.kind === "promo" && currentItem.imageSrc}
                    <img
                        src={currentItem.imageSrc}
                        alt={currentItem.title}
                        on:error={() => currentItem.kind === "promo" && handlePromoImageError(currentItem.promo.campaign_key)}
                    />
                    <div class="mediaVignette" />
                {:else}
                    <div class="heroGraphic">
                        <div class="heroOrb" />
                        <Icon id={currentItem.kind === "promo" && currentItem.promo.cta_url?.includes("youtube.com") ? "youtube" : "web"} size={2.1} white />
                        <span class="heroDomain">hamrocms.com</span>
                    </div>
                {/if}

                <div class="bannerTopRow">
                    <span class="badgePill {currentItem.kind === 'stuti' ? 'stutiBadge' : 'cmsBadge'}">
                        {#if currentItem.kind === "stuti"}
                            <span class="livePulse" />
                        {/if}
                        {currentItem.badgeLabel}
                    </span>

                    {#if stutiSongs.length > 0 && currentItem.kind !== "stuti"}
                        <button
                            type="button"
                            class="stutiQuickPill"
                            on:click={handleQuickStutiOpen}
                            title={isNe ? "स्तुति मिडिया भजन भिडियो खोल्नुस्" : "Open Stuti Media Worship Songs"}
                        >
                            <Icon id="youtube" size={0.72} white />
                            <span>Stuti Media</span>
                        </button>
                    {:else if spotlightItems.length > 1}
                        <span class="counterPill">{activeIndex + 1}/{spotlightItems.length}</span>
                    {/if}
                </div>
            </div>

            <div class="cardBody">
                <div class="cardTitle">{currentItem.title}</div>

                <div class="cardFooter">
                    <span class="ctaBtn {currentItem.kind === 'stuti' ? 'ctaStuti' : 'ctaCms'}">
                        {#if currentItem.kind === "stuti"}
                            <Icon id="play" size={0.78} white />
                        {/if}
                        <span>{currentItem.ctaLabel}</span>
                    </span>

                    {#if spotlightItems.length > 1}
                        <div class="navControls">
                            <button type="button" class="navDotBtn" on:click={(e) => stepSlide(-1, e)} title="Previous">‹</button>
                            <button type="button" class="navDotBtn" on:click={(e) => stepSlide(1, e)} title="Next">›</button>
                        </div>
                    {/if}
                </div>
            </div>
        </div>
    </div>
{/if}

<style>
    .spotlightWrap {
        flex-shrink: 0;
        width: 100%;
        padding: 7px 8px 8px;
        box-sizing: border-box;
        background: linear-gradient(180deg, rgba(7, 13, 25, 0) 0%, rgba(7, 13, 25, 0.85) 100%);
    }

    .spotlightCard {
        position: relative;
        width: 100%;
        display: flex;
        flex-direction: column;
        border-radius: 11px;
        overflow: hidden;
        cursor: pointer;
        user-select: none;
        background: linear-gradient(155deg, #11203a 0%, #091224 100%);
        border: 1px solid color-mix(in srgb, var(--secondary) 42%, var(--primary-lighter) 58%);
        box-shadow:
            0 8px 22px rgba(0, 0, 0, 0.42),
            0 0 0 1px rgba(14, 165, 233, 0.08) inset;
        transition:
            transform 0.18s ease,
            border-color 0.18s ease,
            box-shadow 0.18s ease;
    }

    .spotlightCard.kind-stuti {
        border-color: rgba(245, 158, 11, 0.45);
    }

    .spotlightCard:hover {
        transform: translateY(-1px);
        border-color: var(--secondary);
        box-shadow:
            0 10px 26px rgba(0, 0, 0, 0.55),
            0 0 16px rgba(14, 165, 233, 0.22);
    }

    .spotlightCard.kind-stuti:hover {
        border-color: #f59e0b;
        box-shadow:
            0 10px 26px rgba(0, 0, 0, 0.55),
            0 0 16px rgba(245, 158, 11, 0.22);
    }

    .progressTrack {
        width: 100%;
        height: 2.5px;
        background: rgba(255, 255, 255, 0.09);
        overflow: hidden;
    }

    .progressFill {
        height: 100%;
        background: linear-gradient(90deg, #0ea5e9 0%, #f59e0b 100%);
        transition: width 0.24s linear;
    }

    .mediaBanner {
        position: relative;
        width: 100%;
        height: 102px;
        background: #060b16;
        overflow: hidden;
    }

    .mediaBanner img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
        transition: transform 0.35s ease;
    }

    .spotlightCard:hover .mediaBanner img {
        transform: scale(1.045);
    }

    .mediaVignette {
        position: absolute;
        inset: 0;
        background: linear-gradient(
            180deg,
            rgba(6, 11, 22, 0.48) 0%,
            rgba(6, 11, 22, 0.05) 45%,
            rgba(9, 18, 36, 0.88) 100%
        );
        pointer-events: none;
    }

    .heroGraphic {
        width: 100%;
        height: 100%;
        position: relative;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 4px;
        background: radial-gradient(circle at 25% 20%, rgba(14, 165, 233, 0.38), transparent 60%),
            radial-gradient(circle at 80% 80%, rgba(245, 158, 11, 0.28), transparent 60%),
            linear-gradient(135deg, #0c1b33 0%, #08101f 100%);
    }

    .heroOrb {
        position: absolute;
        width: 90px;
        height: 90px;
        border-radius: 50%;
        background: rgba(14, 165, 233, 0.16);
        filter: blur(18px);
        pointer-events: none;
    }

    .heroDomain {
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: #7dd3fc;
    }

    .playCircle {
        position: absolute;
        right: 8px;
        bottom: 7px;
        width: 30px;
        height: 30px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #ef4444 0%, #f59e0b 100%);
        box-shadow: 0 4px 12px rgba(239, 68, 68, 0.5);
        pointer-events: none;
    }

    .bannerTopRow {
        position: absolute;
        top: 6px;
        left: 7px;
        right: 7px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
        z-index: 2;
    }

    .badgePill {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 2px 7px;
        border-radius: 999px;
        font-size: 0.62rem;
        font-weight: 800;
        letter-spacing: 0.05em;
        backdrop-filter: blur(6px);
    }

    .cmsBadge {
        background: rgba(14, 165, 233, 0.26);
        border: 1px solid rgba(56, 189, 248, 0.55);
        color: #e0f2fe;
    }

    .stutiBadge {
        background: rgba(245, 158, 11, 0.26);
        border: 1px solid rgba(251, 191, 36, 0.6);
        color: #fef3c7;
    }

    .livePulse {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #fbbf24;
        box-shadow: 0 0 6px #fbbf24;
    }

    .stutiQuickPill {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 7px;
        border-radius: 999px;
        border: 1px solid rgba(251, 191, 36, 0.55);
        background: rgba(15, 23, 42, 0.78);
        color: #fde68a;
        font-size: 0.63rem;
        font-weight: 700;
        font-family: inherit;
        cursor: pointer;
        backdrop-filter: blur(6px);
        transition: background-color 0.15s ease, transform 0.15s ease;
    }

    .stutiQuickPill:hover {
        background: rgba(245, 158, 11, 0.32);
        color: #ffffff;
        transform: scale(1.04);
    }

    .counterPill {
        padding: 1.5px 6px;
        border-radius: 999px;
        background: rgba(7, 13, 25, 0.72);
        border: 1px solid rgba(255, 255, 255, 0.14);
        color: rgba(255, 255, 255, 0.85);
        font-size: 0.62rem;
        font-weight: 700;
    }

    .cardBody {
        display: flex;
        flex-direction: column;
        gap: 7px;
        padding: 8px 9px 9px;
    }

    .cardTitle {
        font-size: 0.77rem;
        font-weight: 700;
        line-height: 1.3;
        color: #f8fafc;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        min-height: 2em;
    }

    .cardFooter {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
    }

    .ctaBtn {
        flex: 1;
        min-width: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 5px;
        padding: 5px 9px;
        border-radius: 7px;
        font-size: 0.72rem;
        font-weight: 700;
        color: #ffffff;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        transition: filter 0.15s ease;
    }

    .ctaCms {
        background: linear-gradient(90deg, #0284c7 0%, #0ea5e9 100%);
        box-shadow: 0 2px 10px rgba(14, 165, 233, 0.3);
    }

    .ctaStuti {
        background: linear-gradient(90deg, #d97706 0%, #ea580c 100%);
        box-shadow: 0 2px 10px rgba(234, 88, 12, 0.32);
    }

    .spotlightCard:hover .ctaBtn {
        filter: brightness(1.1);
    }

    .navControls {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        flex-shrink: 0;
    }

    .navDotBtn {
        width: 22px;
        height: 22px;
        border-radius: 6px;
        border: 1px solid rgba(255, 255, 255, 0.15);
        background: rgba(255, 255, 255, 0.06);
        color: #e2e8f0;
        font-size: 0.9rem;
        font-weight: 700;
        line-height: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        padding: 0;
        font-family: inherit;
    }

    .navDotBtn:hover {
        background: rgba(14, 165, 233, 0.28);
        border-color: var(--secondary);
        color: #ffffff;
    }
</style>
