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

    export let hideStutiCard = false

    type SpotlightItem =
        | {
              key: string
              kind: "promo"
              promo: SoftwarePromotionPayload
              title: string
              subtitle: string
              ctaLabel: string
              imageSrc: string
          }
        | {
              key: string
              kind: "stuti"
              song: StutiMediaSong
              title: string
              subtitle: string
              subscribeUrl: string
          }
        | {
              key: string
              kind: "hamrocms"
              title: string
              subtitle: string
              ctaLabel: string
              ctaUrl: string
          }

    const SAFE_RASTER_DATA_URI = /^data:image\/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+$/i
    const DEFAULT_SUBSCRIBE_URL = "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"
    const HAMROCMS_SPOTLIGHT_URL = "https://hamrocms.com/register?utm_source=aradhana_studio&utm_medium=dock_spotlight"

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

    $: latestStutiSong = ($stutiMediaFeed?.videos || [])[0] || null
    $: subscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || DEFAULT_SUBSCRIBE_URL

    $: spotlightItems = buildSpotlightItems(eligiblePromos, latestStutiSong, hideStutiCard, isNe, imageErrorVersion, subscribeUrl)

    function buildSpotlightItems(promos: SoftwarePromotionPayload[], stutiSong: StutiMediaSong | null, hideStuti: boolean, nepali: boolean, _errVer: number, subUrl: string): SpotlightItem[] {
        const items: SpotlightItem[] = []

        for (const promo of promos) {
            const rawSrc = !failedImagesByKey.has(promo.campaign_key) ? promo.resolved_image_src || "" : ""
            const imageSrc = SAFE_RASTER_DATA_URI.test(rawSrc) ? rawSrc : ""
            items.push({
                key: `promo:${promo.campaign_key}`,
                kind: "promo",
                promo,
                title: stripPlainText(promo.title) || "Aradhana Studio",
                subtitle: stripPlainText(promo.body),
                ctaLabel: stripPlainText(promo.cta_label) || (nepali ? "हेर्नुहोस्" : "Learn More"),
                imageSrc
            })
        }

        if (!hideStuti && stutiSong) {
            items.push({
                key: `stuti:${stutiSong.video_id}`,
                kind: "stuti",
                song: stutiSong,
                title: stutiSong.title,
                subtitle: stutiSong.published_label || (nepali ? "@StutiMediaNepal • नयाँ आराधना गीत" : "@StutiMediaNepal • New Worship Song"),
                subscribeUrl: subUrl
            })
        }

        items.push({
            key: "builtin:hamrocms",
            kind: "hamrocms",
            title: nepali ? "चर्च सदस्य, भेटी र दशांश हिसाब अब सजिलो" : "All-in-One Church Management Cloud",
            subtitle: nepali ? "नेपाली पात्रो र रसिदसहितको आधुनिक चर्च सफ्टवेयर" : "Members, tithes, expenses & receipts on HamroCMS.com",
            ctaLabel: nepali ? "सुरु गर्नुहोस् ↗" : "Try Free ↗",
            ctaUrl: HAMROCMS_SPOTLIGHT_URL
        })

        return items
    }

    $: if (spotlightItems.length === 0) {
        activeIndex = 0
        rotateElapsedMs = 0
    } else if (activeIndex >= spotlightItems.length) {
        activeIndex = 0
    }

    $: currentItem = spotlightItems[activeIndex] ?? spotlightItems[0] ?? null

    $: if (!$currentWindow && !$activePopup && currentItem?.kind === "promo" && currentItem.promo.campaign_key && !recordedImpressions.has(currentItem.promo.campaign_key)) {
        recordedImpressions.add(currentItem.promo.campaign_key)
        sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
            id: currentItem.promo.id,
            campaignKey: currentItem.promo.campaign_key,
            eventUrl: currentItem.promo.event_url
        })
    }

    function selectIndex(idx: number) {
        if (spotlightItems.length === 0) return
        activeIndex = ((idx % spotlightItems.length) + spotlightItems.length) % spotlightItems.length
        rotateElapsedMs = 0
    }

    function openPromoCta(promo: SoftwarePromotionPayload) {
        sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
            id: promo.id,
            url: promo.cta_url || "https://hamrocms.com",
            eventUrl: promo.event_url
        })
    }

    function openStutiSong(song: StutiMediaSong) {
        if ($drawer.height <= 40) {
            drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
        }
        stutiMediaActiveVideoId.set(song.video_id)
        activeDrawerTab.set("stuti")
    }

    function openExternalUrl(url: string) {
        sendMain(Main.URL, url)
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
            if ($activePopup || isHovered || hasActiveProjection || spotlightItems.length <= 1) return

            const activeItem = spotlightItems[activeIndex]
            const intervalSec = activeItem?.kind === "promo" ? Number(activeItem.promo.rotate_interval_seconds) || 9 : 9
            const intervalMs = Math.max(4, intervalSec) * 1000

            rotateElapsedMs += 250
            if (rotateElapsedMs >= intervalMs) {
                rotateElapsedMs = 0
                selectIndex(activeIndex + 1)
            }
        }, 250)
    })

    onDestroy(() => {
        if (delayTimer) clearInterval(delayTimer)
        if (rotateTimer) clearInterval(rotateTimer)
    })
</script>

{#if !$currentWindow && currentItem}
    <section
        class="spotlightDock"
        aria-label={isNe ? "स्तुति मिडिया र हाम्रो CMS" : "Stuti Media & HamroCMS Spotlight"}
        on:mouseenter={() => (isHovered = true)}
        on:mouseleave={() => (isHovered = false)}
    >
        {#if currentItem.kind === "stuti"}
            <div class="topRow">
                <span class="badge stutiBadge">🎵 Stuti Media</span>
                <div class="rightMeta">
                    {#if hasActiveProjection}
                        <span class="pausedHint" title={isNe ? "प्रोजेक्सन चलिरहेकाले रोटेसन रोकिएको छ" : "Rotation paused while projecting"}>LIVE</span>
                    {/if}
                    <button type="button" class="subPill" on:click={() => openExternalUrl(currentItem.subscribeUrl)}>
                        Subscribe ↗
                    </button>
                </div>
            </div>

            <div class="mediaRow">
                {#if currentItem.song.thumbnail_url}
                    <button type="button" class="thumbBtn" on:click={() => currentItem.kind === "stuti" && openStutiSong(currentItem.song)} title={currentItem.title}>
                        <img src={currentItem.song.thumbnail_url} alt={currentItem.title} loading="lazy" />
                        {#if currentItem.song.duration_text}
                            <span class="duration">{currentItem.song.duration_text}</span>
                        {/if}
                    </button>
                {/if}
                <div class="textCol">
                    <div class="title" title={currentItem.title}>{currentItem.title}</div>
                    <div class="subtitle">{currentItem.subtitle}</div>
                    <div class="actionsRow">
                        <button type="button" class="primaryBtn stutiBtn" on:click={() => currentItem.kind === "stuti" && openStutiSong(currentItem.song)}>
                            ▶ {isNe ? "सुन्नुहोस्" : "Listen"}
                        </button>
                        <button type="button" class="ghostBtn" on:click={() => currentItem.kind === "stuti" && openExternalUrl(currentItem.song.watch_url)}>
                            YouTube ↗
                        </button>
                    </div>
                </div>
            </div>
        {:else if currentItem.kind === "promo"}
            <div class="topRow">
                <span class="badge promoBadge">✦ {isNe ? "सूचना / अफर" : "Spotlight"}</span>
                {#if hasActiveProjection}
                    <span class="pausedHint" title={isNe ? "प्रोजेक्सन चलिरहेकाले रोटेसन रोकिएको छ" : "Rotation paused while projecting"}>LIVE</span>
                {/if}
            </div>

            <div class="mediaRow">
                {#if currentItem.imageSrc}
                    <button type="button" class="thumbBtn promoThumb" on:click={() => currentItem.kind === "promo" && openPromoCta(currentItem.promo)}>
                        <img src={currentItem.imageSrc} alt={currentItem.title} on:error={() => currentItem.kind === "promo" && handlePromoImageError(currentItem.promo.campaign_key)} />
                    </button>
                {/if}
                <div class="textCol">
                    <div class="title" title={currentItem.title}>{currentItem.title}</div>
                    {#if currentItem.subtitle}
                        <div class="subtitle">{currentItem.subtitle}</div>
                    {/if}
                    <div class="actionsRow">
                        <button type="button" class="primaryBtn" on:click={() => currentItem.kind === "promo" && openPromoCta(currentItem.promo)}>
                            {currentItem.ctaLabel}
                        </button>
                    </div>
                </div>
            </div>
        {:else}
            <div class="topRow">
                <span class="badge cmsBadge">☁️ HamroCMS.com</span>
                {#if hasActiveProjection}
                    <span class="pausedHint" title={isNe ? "प्रोजेक्सन चलिरहेकाले रोटेसन रोकिएको छ" : "Rotation paused while projecting"}>LIVE</span>
                {/if}
            </div>

            <div class="textCol">
                <div class="title">{currentItem.title}</div>
                <div class="subtitle">{currentItem.subtitle}</div>
                <div class="actionsRow">
                    <button type="button" class="primaryBtn cmsBtn" on:click={() => currentItem.kind === "hamrocms" && openExternalUrl(currentItem.ctaUrl)}>
                        {currentItem.ctaLabel}
                    </button>
                </div>
            </div>
        {/if}

        {#if spotlightItems.length > 1}
            <div class="pagerRow">
                <button type="button" class="navBtn" on:click={() => selectIndex(activeIndex - 1)} aria-label="Previous spotlight">‹</button>
                <div class="dots" role="tablist" aria-label="Spotlight items">
                    {#each spotlightItems as item, idx (item.key)}
                        <button
                            type="button"
                            class="dot"
                            class:active={idx === activeIndex}
                            on:click={() => selectIndex(idx)}
                            aria-label={`Spotlight ${idx + 1}`}
                        />
                    {/each}
                </div>
                <button type="button" class="navBtn" on:click={() => selectIndex(activeIndex + 1)} aria-label="Next spotlight">›</button>
            </div>
        {/if}
    </section>
{/if}

<style>
    .spotlightDock {
        flex-shrink: 0;
        border-top: 1px solid var(--primary-lighter);
        background: var(--primary-darker);
        padding: 8px 10px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        font-size: 0.78rem;
    }

    .topRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
    }

    .rightMeta {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    .badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 0.68rem;
        font-weight: 700;
        letter-spacing: 0.02em;
        padding: 2px 7px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.08);
        color: var(--text);
    }

    .stutiBadge {
        background: rgba(239, 68, 68, 0.18);
        color: #fca5a5;
        border: 1px solid rgba(239, 68, 68, 0.35);
    }

    .cmsBadge {
        background: rgba(56, 189, 248, 0.16);
        color: #7dd3fc;
        border: 1px solid rgba(56, 189, 248, 0.35);
    }

    .promoBadge {
        background: rgba(245, 158, 11, 0.18);
        color: #fcd34d;
        border: 1px solid rgba(245, 158, 11, 0.35);
    }

    .pausedHint {
        font-size: 0.62rem;
        font-weight: 700;
        padding: 1px 5px;
        border-radius: 4px;
        background: rgba(16, 185, 129, 0.18);
        color: #6ee7b7;
        border: 1px solid rgba(16, 185, 129, 0.35);
    }

    .subPill {
        border: 1px solid rgba(239, 68, 68, 0.45);
        background: rgba(239, 68, 68, 0.16);
        color: #fecaca;
        font-size: 0.68rem;
        font-weight: 600;
        padding: 2px 7px;
        border-radius: 999px;
        cursor: pointer;
        font-family: inherit;
    }

    .subPill:hover {
        background: rgba(239, 68, 68, 0.3);
    }

    .mediaRow {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        min-width: 0;
    }

    .thumbBtn {
        position: relative;
        flex-shrink: 0;
        width: 78px;
        aspect-ratio: 16 / 9;
        padding: 0;
        border: 1px solid rgba(255, 255, 255, 0.12);
        border-radius: 6px;
        overflow: hidden;
        background: #000;
        cursor: pointer;
    }

    .thumbBtn img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .duration {
        position: absolute;
        right: 3px;
        bottom: 3px;
        font-size: 0.6rem;
        font-weight: 700;
        line-height: 1;
        padding: 2px 4px;
        border-radius: 3px;
        background: rgba(0, 0, 0, 0.82);
        color: #fff;
    }

    .textCol {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 3px;
    }

    .title {
        font-size: 0.78rem;
        font-weight: 600;
        color: var(--text);
        line-height: 1.25;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .subtitle {
        font-size: 0.7rem;
        color: var(--text);
        opacity: 0.72;
        line-height: 1.25;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .actionsRow {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-top: 2px;
        flex-wrap: wrap;
    }

    .primaryBtn {
        border: 1px solid rgba(245, 158, 11, 0.45);
        background: rgba(245, 158, 11, 0.22);
        color: #fef3c7;
        font-size: 0.7rem;
        font-weight: 600;
        padding: 3px 8px;
        border-radius: 5px;
        cursor: pointer;
        font-family: inherit;
    }

    .primaryBtn:hover {
        background: rgba(245, 158, 11, 0.34);
    }

    .stutiBtn {
        border-color: rgba(239, 68, 68, 0.5);
        background: rgba(239, 68, 68, 0.24);
        color: #fee2e2;
    }

    .stutiBtn:hover {
        background: rgba(239, 68, 68, 0.38);
    }

    .cmsBtn {
        border-color: rgba(56, 189, 248, 0.45);
        background: rgba(14, 165, 233, 0.24);
        color: #e0f2fe;
    }

    .cmsBtn:hover {
        background: rgba(14, 165, 233, 0.38);
    }

    .ghostBtn {
        border: 1px solid rgba(255, 255, 255, 0.14);
        background: rgba(255, 255, 255, 0.05);
        color: var(--text);
        font-size: 0.7rem;
        padding: 3px 7px;
        border-radius: 5px;
        cursor: pointer;
        font-family: inherit;
        opacity: 0.85;
    }

    .ghostBtn:hover {
        opacity: 1;
        background: rgba(255, 255, 255, 0.12);
    }

    .pagerRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-top: 2px;
    }

    .dots {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    .dot {
        width: 6px;
        height: 6px;
        padding: 0;
        border: none;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.25);
        cursor: pointer;
        transition: width 0.18s ease, background-color 0.18s ease;
    }

    .dot.active {
        width: 14px;
        background: var(--secondary);
    }

    .navBtn {
        border: none;
        background: transparent;
        color: var(--text);
        opacity: 0.65;
        font-size: 0.9rem;
        line-height: 1;
        padding: 1px 5px;
        cursor: pointer;
        border-radius: 4px;
    }

    .navBtn:hover {
        opacity: 1;
        background: rgba(255, 255, 255, 0.08);
    }
</style>
