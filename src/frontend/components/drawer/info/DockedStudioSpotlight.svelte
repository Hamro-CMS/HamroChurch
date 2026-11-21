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
              caption: string
              imageSrc: string
          }
        | {
              key: string
              kind: "stuti"
              song: StutiMediaSong
              title: string
              caption: string
          }
        | {
              key: string
              kind: "hamrocms"
              title: string
              caption: string
              url: string
          }

    const SAFE_RASTER_DATA_URI = /^data:image\/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+$/i
    const HAMROCMS_SIDEBAR_URL = "https://hamrocms.com/?utm_source=aradhana_studio&utm_medium=sidebar_footer"

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

    $: spotlightItems = buildSpotlightItems(eligiblePromos, latestStutiSong, hideStutiCard, isNe, imageErrorVersion)

    function buildSpotlightItems(promos: SoftwarePromotionPayload[], stutiSong: StutiMediaSong | null, hideStuti: boolean, nepali: boolean, _errVer: number): SpotlightItem[] {
        const items: SpotlightItem[] = []

        for (const promo of promos) {
            const rawSrc = !failedImagesByKey.has(promo.campaign_key) ? promo.resolved_image_src || "" : ""
            const imageSrc = SAFE_RASTER_DATA_URI.test(rawSrc) ? rawSrc : ""
            items.push({
                key: `promo:${promo.campaign_key}`,
                kind: "promo",
                promo,
                title: stripPlainText(promo.title) || "Aradhana Studio",
                caption: stripPlainText(promo.cta_label) || "hamrocms.com",
                imageSrc
            })
        }

        if (!hideStuti && stutiSong) {
            items.push({
                key: `stuti:${stutiSong.video_id}`,
                kind: "stuti",
                song: stutiSong,
                title: stutiSong.title,
                caption: "Stuti Media"
            })
        }

        if (promos.length === 0) {
            items.push({
                key: "builtin:hamrocms",
                kind: "hamrocms",
                title: nepali ? "HamroCMS चर्च व्यवस्थापन" : "HamroCMS Workspace",
                caption: "hamrocms.com",
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

    $: if (!$currentWindow && !hidden && !$activePopup && currentItem?.kind === "promo" && currentItem.promo.campaign_key && !recordedImpressions.has(currentItem.promo.campaign_key)) {
        recordedImpressions.add(currentItem.promo.campaign_key)
        sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
            id: currentItem.promo.id,
            campaignKey: currentItem.promo.campaign_key,
            eventUrl: currentItem.promo.event_url
        })
    }

    function handleClick() {
        if (!currentItem) return
        if (currentItem.kind === "promo") {
            sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
                id: currentItem.promo.id,
                url: currentItem.promo.cta_url || "https://hamrocms.com",
                eventUrl: currentItem.promo.event_url
            })
            return
        }
        if (currentItem.kind === "stuti") {
            if ($drawer.height <= 40) {
                drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
            }
            stutiMediaActiveVideoId.set(currentItem.song.video_id)
            activeDrawerTab.set("stuti")
            return
        }
        sendMain(Main.URL, currentItem.url)
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

            const activeItem = spotlightItems[activeIndex]
            const intervalSec = activeItem?.kind === "promo" ? Math.max(8, Number(activeItem.promo.rotate_interval_seconds) || 12) : 12
            const intervalMs = intervalSec * 1000

            rotateElapsedMs += 250
            if (rotateElapsedMs >= intervalMs) {
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
    <button
        type="button"
        class="sidebarPartnerFooter"
        on:click={handleClick}
        on:mouseenter={() => (isHovered = true)}
        on:mouseleave={() => (isHovered = false)}
        title={currentItem.title}
    >
        {#if currentItem.kind === "stuti"}
            <div class="thumb">
                <img src={currentItem.song.thumbnail_url} alt={currentItem.title} loading="lazy" />
            </div>
        {:else if currentItem.kind === "promo" && currentItem.imageSrc}
            <div class="thumb">
                <img src={currentItem.imageSrc} alt={currentItem.title} on:error={() => currentItem.kind === "promo" && handlePromoImageError(currentItem.promo.campaign_key)} />
            </div>
        {:else}
            <div class="iconBox">
                <Icon id={currentItem.kind === "promo" && currentItem.promo.cta_url?.includes("youtube.com") ? "youtube" : "web"} size={0.85} white />
            </div>
        {/if}

        <div class="meta">
            <div class="title">{currentItem.title}</div>
            <div class="caption">{currentItem.caption}</div>
        </div>
    </button>
{/if}

<style>
    .sidebarPartnerFooter {
        flex-shrink: 0;
        width: 100%;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 7px 10px;
        border: none;
        border-top: 1px solid rgba(255, 255, 255, 0.06);
        background: rgba(255, 255, 255, 0.015);
        color: var(--text);
        font-family: inherit;
        text-align: left;
        cursor: pointer;
        transition: background-color 0.15s ease;
    }

    .sidebarPartnerFooter:hover {
        background: rgba(255, 255, 255, 0.05);
    }

    .thumb {
        width: 52px;
        aspect-ratio: 16 / 9;
        border-radius: 4px;
        overflow: hidden;
        flex-shrink: 0;
        background: #000;
    }

    .thumb img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .iconBox {
        width: 30px;
        height: 30px;
        border-radius: 6px;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.05);
        opacity: 0.85;
    }

    .meta {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    .title {
        font-size: 0.74rem;
        font-weight: 500;
        line-height: 1.25;
        opacity: 0.9;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .caption {
        font-size: 0.66rem;
        opacity: 0.55;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
</style>
