<script lang="ts">
    import { onMount } from "svelte"
    import { fade, fly } from "svelte/transition"
    import { Main } from "../../../types/IPC/Main"
    import { requestMain, sendMain } from "../../IPC/main"
    import { activePopup, currentWindow, language, softwarePromotion } from "../../stores"

    let imageFailed = false
    let lastCampaignKey = ""

    $: promo = $softwarePromotion
    $: if (promo?.campaign_key && promo.campaign_key !== lastCampaignKey) {
        lastCampaignKey = promo.campaign_key
        imageFailed = false
    }
    $: imageSrc = !imageFailed ? promo?.resolved_image_src || promo?.image_url || "" : ""
    $: isBottomLeft = promo?.position === "bottom_left"
    $: closeLabel = $language === "ne" ? "बन्द गर्नुहोस् (Close)" : "Close advertisement"

    onMount(() => {
        if ($currentWindow) return
        void requestMain(Main.GET_SOFTWARE_PROMOTION, undefined, (data) => {
            if (data !== undefined) {
                softwarePromotion.set(data ?? null)
            }
        })
    })

    function dismissPromo() {
        if (!promo) return
        const campaignKey = promo.campaign_key
        const dismissMode = promo.dismiss_mode || "once_per_day"
        softwarePromotion.set(null)
        sendMain(Main.DISMISS_SOFTWARE_PROMOTION, { campaignKey, dismissMode })
    }

    function openCta() {
        if (!promo) return
        const targetUrl = promo.cta_url || "https://hamrocms.com"
        sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
            id: promo.id,
            url: targetUrl,
            eventUrl: promo.event_url
        })
    }
</script>

{#if !$currentWindow && !$activePopup && promo}
    <aside class="promoPopup" class:bottomLeft={isBottomLeft} aria-label="HamroCMS Promotion" in:fly={{ y: 16, duration: 220 }} out:fade={{ duration: 150 }}>
        <button type="button" class="closeBtn" title={closeLabel} aria-label={closeLabel} on:click|stopPropagation={dismissPromo}> × </button>

        {#if imageSrc}
            <button type="button" class="mediaWrap" on:click={openCta} title={promo.title}>
                <img src={imageSrc} alt={promo.title} on:error={() => (imageFailed = true)} />
            </button>
        {/if}

        <div class="content">
            <div class="badgeRow">
                <span class="badge">HamroCMS.com</span>
            </div>

            <h4 class="title">{promo.title}</h4>

            {#if promo.body}
                <p class="body">{promo.body}</p>
            {/if}

            <div class="actions">
                <button type="button" class="ctaBtn" on:click={openCta}>
                    <span>{promo.cta_label || ($language === "ne" ? "HamroCMS हेर्नुहोस्" : "Explore HamroCMS.com")}</span>
                    <span aria-hidden="true">↗</span>
                </button>
            </div>
        </div>
    </aside>
{/if}

<style>
    .promoPopup {
        position: fixed;
        right: 14px;
        bottom: 60px;
        width: min(300px, calc(100vw - 28px));
        z-index: 32;
        border-radius: 14px;
        border: 1px solid color-mix(in srgb, var(--secondary) 50%, var(--primary-lighter) 50%);
        background: linear-gradient(165deg, color-mix(in srgb, var(--primary-darker) 94%, #1a1a28 6%), color-mix(in srgb, var(--primary-darkest) 96%, black 4%));
        color: var(--text);
        box-shadow:
            0 16px 38px rgb(0 0 0 / 0.55),
            0 0 0 1px rgb(255 255 255 / 0.04) inset;
        overflow: hidden;
        pointer-events: auto;
    }

    .promoPopup.bottomLeft {
        right: auto;
        left: 14px;
        bottom: 18px;
    }

    .closeBtn {
        position: absolute;
        top: 8px;
        right: 8px;
        z-index: 3;
        width: 26px;
        height: 26px;
        border-radius: 999px;
        border: 1px solid rgb(255 255 255 / 0.2);
        background: rgb(10 10 16 / 0.78);
        color: #ffffff;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        font-weight: 700;
        line-height: 1;
        cursor: pointer;
        transition:
            background-color 0.15s ease,
            transform 0.15s ease,
            border-color 0.15s ease;
    }

    .closeBtn:hover {
        background: var(--secondary);
        color: var(--secondary-text);
        border-color: var(--secondary);
        transform: scale(1.05);
    }

    .mediaWrap {
        display: block;
        width: 100%;
        max-height: 150px;
        padding: 0;
        margin: 0;
        border: 0;
        background: rgb(0 0 0 / 0.35);
        cursor: pointer;
        overflow: hidden;
    }

    .mediaWrap img {
        display: block;
        width: 100%;
        max-height: 150px;
        object-fit: cover;
    }

    .content {
        padding: 12px 13px 13px;
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .badgeRow {
        display: flex;
        align-items: center;
        padding-right: 26px;
    }

    .badge {
        display: inline-flex;
        align-items: center;
        padding: 2px 8px;
        border-radius: 999px;
        font-size: 0.66rem;
        font-weight: 700;
        letter-spacing: 0.03em;
        background: color-mix(in srgb, var(--secondary) 18%, transparent);
        border: 1px solid color-mix(in srgb, var(--secondary) 42%, transparent);
        color: color-mix(in srgb, var(--secondary) 75%, white 25%);
    }

    .title {
        margin: 0;
        padding-right: 18px;
        font-size: 0.84rem;
        font-weight: 800;
        line-height: 1.35;
        color: var(--text);
        word-break: break-word;
    }

    .body {
        margin: 0;
        font-size: 0.74rem;
        line-height: 1.45;
        color: color-mix(in srgb, var(--text) 80%, transparent);
        display: -webkit-box;
        -webkit-line-clamp: 4;
        -webkit-box-orient: vertical;
        overflow: hidden;
        word-break: break-word;
    }

    .actions {
        margin-top: 3px;
        display: flex;
        align-items: center;
    }

    .ctaBtn {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 6px 12px;
        border-radius: 8px;
        border: 1px solid color-mix(in srgb, var(--secondary) 70%, white 15%);
        background: var(--secondary);
        color: var(--secondary-text);
        font: inherit;
        font-size: 0.74rem;
        font-weight: 700;
        cursor: pointer;
        box-shadow: 0 4px 14px rgb(0 0 0 / 0.28);
        transition:
            filter 0.15s ease,
            transform 0.15s ease;
    }

    .ctaBtn:hover {
        filter: brightness(1.08);
        transform: translateY(-1px);
    }

    @media (max-width: 900px) {
        .promoPopup {
            right: 10px;
            bottom: 54px;
            width: min(275px, calc(100vw - 20px));
        }

        .promoPopup.bottomLeft {
            left: 10px;
            bottom: 12px;
        }
    }
</style>
