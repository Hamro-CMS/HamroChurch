<script lang="ts">
    import { onDestroy, onMount } from "svelte"
    import { fade, fly } from "svelte/transition"
    import { Main } from "../../../types/IPC/Main"
    import type { SoftwarePromotionPayload, SoftwarePromotionTransitionStyle } from "../../../types/Main"
    import { requestMain, sendMain } from "../../IPC/main"
    import { activePopup, currentWindow, language, softwarePromotion, softwarePromotions } from "../../stores"

    type FlipAngle = "flip-y" | "flip-x" | "flip-diag-a" | "swing-3d" | "flip-diag-b"
    type FlipPhase = "idle" | "out" | "in-prep" | "in"

    const MIX_SEQUENCE: FlipAngle[] = ["flip-y", "flip-x", "flip-diag-a", "swing-3d", "flip-diag-b"]

    const appOpenedAt = Date.now()
    const firstSeenByKey = new Map<string, number>()
    const failedImagesByKey = new Set<string>()
    const recordedImpressions = new Set<string>()

    let asideEl: HTMLElement | null = null
    let nowTick = Date.now()
    let delayTimer: ReturnType<typeof setInterval> | null = null
    let rotateTimer: ReturnType<typeof setInterval> | null = null
    let flipStepTimerA: ReturnType<typeof setTimeout> | null = null
    let flipStepTimerB: ReturnType<typeof setTimeout> | null = null

    let activeIndex = 0
    let angleCounter = 0
    let currentAngle: FlipAngle = "flip-y"
    let flipPhase: FlipPhase = "idle"
    let isFlipping = false
    let isHovered = false
    let rotateElapsedMs = 0
    let imageErrorVersion = 0

    // Free drag state
    let customPos: { x: number; y: number } | null = null
    let isDragging = false
    let suppressClickUntil = 0
    let dragState: {
        startClientX: number
        startClientY: number
        startLeft: number
        startTop: number
        moved: boolean
    } | null = null

    $: allPromos = Array.isArray($softwarePromotions) ? $softwarePromotions : []

    $: {
        const now = Date.now()
        for (const p of allPromos) {
            if (p?.campaign_key && !firstSeenByKey.has(p.campaign_key)) {
                firstSeenByKey.set(p.campaign_key, now)
            }
        }
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

    $: syncDelayTimer(allPromos)

    $: eligiblePromos = allPromos.filter((p) => isDelayUnlocked(p, nowTick))

    $: if (eligiblePromos.length === 0) {
        activeIndex = 0
        rotateElapsedMs = 0
    } else if (activeIndex >= eligiblePromos.length) {
        activeIndex = 0
    }

    $: promo = eligiblePromos.length > 0 ? (eligiblePromos[activeIndex] ?? eligiblePromos[0]) : null

    $: if (!$currentWindow && !$activePopup && promo?.campaign_key && !recordedImpressions.has(promo.campaign_key)) {
        recordedImpressions.add(promo.campaign_key)
        sendMain(Main.RECORD_SOFTWARE_PROMOTION_IMPRESSION, {
            id: promo.id,
            campaignKey: promo.campaign_key,
            eventUrl: promo.event_url
        })
    }

    $: imageSrc = promo && imageErrorVersion >= 0 && !failedImagesByKey.has(promo.campaign_key) ? promo.resolved_image_src || promo.image_url || "" : ""

    $: isBottomLeft = promo?.position === "bottom_left"
    $: closeLabel = $language === "ne" ? "बन्द गर्नुहोस् (Close)" : "Close advertisement"
    $: dragHint = $language === "ne" ? "सार्नको लागि तान्नुहोस् (Drag to move)" : "Drag anywhere to move"

    $: positionStyle = customPos ? `left: ${customPos.x}px; top: ${customPos.y}px; right: auto; bottom: auto;` : ""

    function pickNextAngle(style: SoftwarePromotionTransitionStyle): FlipAngle {
        if (style === "flip_y") return "flip-y"
        if (style === "flip_x") return "flip-x"
        if (style === "swing_3d") return "swing-3d"
        if (style === "flip_diagonal") {
            const angle = angleCounter % 2 === 0 ? "flip-diag-a" : "flip-diag-b"
            angleCounter += 1
            return angle
        }
        const angle = MIX_SEQUENCE[angleCounter % MIX_SEQUENCE.length]
        angleCounter += 1
        return angle
    }

    function clearFlipTimers() {
        if (flipStepTimerA) {
            clearTimeout(flipStepTimerA)
            flipStepTimerA = null
        }
        if (flipStepTimerB) {
            clearTimeout(flipStepTimerB)
            flipStepTimerB = null
        }
    }

    function flipToIndex(targetIndex: number) {
        if (isFlipping || eligiblePromos.length <= 1) return
        const total = eligiblePromos.length
        const normalized = ((targetIndex % total) + total) % total
        if (normalized === activeIndex) return

        clearFlipTimers()
        isFlipping = true
        rotateElapsedMs = 0
        currentAngle = pickNextAngle(promo?.transition_style || "auto_mix")
        flipPhase = "out"

        flipStepTimerA = setTimeout(() => {
            flipStepTimerA = null
            activeIndex = normalized
            flipPhase = "in-prep"

            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    flipPhase = "in"
                    flipStepTimerB = setTimeout(() => {
                        flipStepTimerB = null
                        flipPhase = "idle"
                        isFlipping = false
                    }, 330)
                })
            })
        }, 250)
    }

    function setupRotationLoop() {
        if (rotateTimer) return
        rotateTimer = setInterval(() => {
            if ($currentWindow || $activePopup) return
            if (eligiblePromos.length <= 1) {
                rotateElapsedMs = 0
                return
            }
            if (isHovered || isDragging || isFlipping) return

            const intervalMs = Math.max(3, Number(promo?.rotate_interval_seconds) || 8) * 1000
            rotateElapsedMs += 500
            if (rotateElapsedMs >= intervalMs) {
                rotateElapsedMs = 0
                flipToIndex(activeIndex + 1)
            }
        }, 500)
    }

    onMount(() => {
        if ($currentWindow) return
        setupRotationLoop()
        void requestMain(Main.GET_SOFTWARE_PROMOTION, undefined, (data) => {
            if (Array.isArray(data)) {
                softwarePromotions.set(data)
                softwarePromotion.set(data[0] ?? null)
            }
        })
    })

    onDestroy(() => {
        if (delayTimer) clearInterval(delayTimer)
        if (rotateTimer) clearInterval(rotateTimer)
        clearFlipTimers()
        cleanupWindowDragListeners()
    })

    function handleImageError() {
        if (!promo?.campaign_key) return
        failedImagesByKey.add(promo.campaign_key)
        imageErrorVersion += 1
    }

    function dismissAllVisiblePromos() {
        const sourceList = allPromos.length > 0 ? allPromos : promo ? [promo] : []
        if (!sourceList.length) return

        const items = sourceList.map((item) => ({
            campaignKey: item.campaign_key,
            dismissMode: item.dismiss_mode || "once_per_day"
        }))

        softwarePromotions.set([])
        softwarePromotion.set(null)
        sendMain(Main.DISMISS_SOFTWARE_PROMOTION, { items })
    }

    function openCta() {
        if (!promo) return
        if (Date.now() < suppressClickUntil) return
        const targetUrl = promo.cta_url || "https://hamrocms.com"
        sendMain(Main.OPEN_SOFTWARE_PROMOTION, {
            id: promo.id,
            url: targetUrl,
            eventUrl: promo.event_url
        })
    }

    function clampPosition(x: number, y: number): { x: number; y: number } {
        const w = asideEl?.offsetWidth || 300
        const h = asideEl?.offsetHeight || 210
        const maxX = Math.max(8, window.innerWidth - w - 8)
        const maxY = Math.max(8, window.innerHeight - h - 8)
        return {
            x: Math.max(8, Math.min(maxX, Math.round(x))),
            y: Math.max(8, Math.min(maxY, Math.round(y)))
        }
    }

    function handleWindowResize() {
        if (!customPos) return
        customPos = clampPosition(customPos.x, customPos.y)
    }

    function onWindowPointerMove(e: PointerEvent) {
        if (!dragState) return
        const dx = e.clientX - dragState.startClientX
        const dy = e.clientY - dragState.startClientY

        if (!dragState.moved && Math.hypot(dx, dy) < 5) {
            return
        }

        dragState.moved = true
        isDragging = true
        customPos = clampPosition(dragState.startLeft + dx, dragState.startTop + dy)
    }

    function onWindowPointerUp() {
        if (dragState?.moved) {
            suppressClickUntil = Date.now() + 240
        }
        dragState = null
        isDragging = false
        cleanupWindowDragListeners()
    }

    function cleanupWindowDragListeners() {
        if (typeof window === "undefined") return
        window.removeEventListener("pointermove", onWindowPointerMove)
        window.removeEventListener("pointerup", onWindowPointerUp)
        window.removeEventListener("pointercancel", onWindowPointerUp)
    }

    function startPointerDrag(e: PointerEvent) {
        if (e.button !== 0 || !asideEl) return
        const target = e.target as HTMLElement | null
        if (target?.closest(".noDragControl")) return

        const rect = asideEl.getBoundingClientRect()
        dragState = {
            startClientX: e.clientX,
            startClientY: e.clientY,
            startLeft: rect.left,
            startTop: rect.top,
            moved: false
        }

        cleanupWindowDragListeners()
        window.addEventListener("pointermove", onWindowPointerMove)
        window.addEventListener("pointerup", onWindowPointerUp)
        window.addEventListener("pointercancel", onWindowPointerUp)
    }

    function resetDraggedPosition() {
        customPos = null
    }
</script>

<svelte:window on:resize={handleWindowResize} />

{#if !$currentWindow && !$activePopup && promo}
    <aside bind:this={asideEl} class="promoStage" class:bottomLeft={!customPos && isBottomLeft} class:dragging={isDragging} style={positionStyle} aria-label="HamroCMS Promotion" on:pointerdown={startPointerDrag} on:mouseenter={() => (isHovered = true)} on:mouseleave={() => (isHovered = false)} in:fly={{ y: 16, duration: 220 }} out:fade={{ duration: 150 }}>
        <div class="promoCard {currentAngle} phase-{flipPhase}">
            <!-- Top Drag Handle + Counter + Close (×) -->
            <div class="dragHeader" title={dragHint} on:dblclick={resetDraggedPosition}>
                <div class="brandGrip">
                    <span class="gripDots" aria-hidden="true">⋮⋮</span>
                    <span class="badge">HamroCMS.com</span>
                </div>

                <div class="headerControls noDragControl">
                    {#if eligiblePromos.length > 1}
                        <button type="button" class="miniFlipBtn" title="Previous" on:click|stopPropagation={() => flipToIndex(activeIndex - 1)}>‹</button>
                        <span class="counterText">{activeIndex + 1}/{eligiblePromos.length}</span>
                        <button type="button" class="miniFlipBtn" title="Next" on:click|stopPropagation={() => flipToIndex(activeIndex + 1)}>›</button>
                    {/if}

                    <button type="button" class="closeBtn" title={closeLabel} aria-label={closeLabel} on:click|stopPropagation={dismissAllVisiblePromos}> × </button>
                </div>
            </div>

            {#if imageSrc}
                <button type="button" class="mediaWrap" on:click={openCta} title={promo.title}>
                    <img src={imageSrc} alt={promo.title} draggable={false} on:error={handleImageError} />
                </button>
            {/if}

            <div class="content">
                <h4 class="title">{promo.title}</h4>

                {#if promo.body}
                    <p class="body">{promo.body}</p>
                {/if}

                <div class="actions">
                    <button type="button" class="ctaBtn" on:click={openCta}>
                        <span>{promo.cta_label || ($language === "ne" ? "HamroCMS हेर्नुहोस्" : "Explore HamroCMS.com")}</span>
                        <span aria-hidden="true">↗</span>
                    </button>

                    {#if eligiblePromos.length > 1}
                        <div class="dotsRow noDragControl" role="tablist" aria-label="Active promotions">
                            {#each eligiblePromos as item, idx (item.campaign_key)}
                                <button type="button" class="dotBtn" class:active={idx === activeIndex} aria-label={`Advertisement ${idx + 1}`} on:click|stopPropagation={() => flipToIndex(idx)} />
                            {/each}
                        </div>
                    {/if}
                </div>
            </div>
        </div>
    </aside>
{/if}

<style>
    .promoStage {
        position: fixed;
        right: 14px;
        bottom: 60px;
        width: min(304px, calc(100vw - 24px));
        z-index: 32;
        perspective: 1150px;
        pointer-events: auto;
        cursor: grab;
        user-select: none;
        touch-action: none;
    }

    .promoStage.bottomLeft {
        right: auto;
        left: 14px;
        bottom: 18px;
    }

    .promoStage.dragging {
        cursor: grabbing;
    }

    .promoCard {
        position: relative;
        width: 100%;
        border-radius: 14px;
        border: 1px solid color-mix(in srgb, var(--secondary) 52%, var(--primary-lighter) 48%);
        background: linear-gradient(165deg, color-mix(in srgb, var(--primary-darker) 94%, #1a1a28 6%), color-mix(in srgb, var(--primary-darkest) 96%, black 4%));
        color: var(--text);
        box-shadow:
            0 16px 38px rgb(0 0 0 / 0.56),
            0 0 0 1px rgb(255 255 255 / 0.04) inset;
        overflow: hidden;
        transform-style: preserve-3d;
        backface-visibility: hidden;
        will-change: transform, opacity;
        transform: rotate3d(0, 1, 0, 0deg) scale(1);
        opacity: 1;
    }

    /* Phase timings */
    .promoCard.phase-out {
        transition:
            transform 250ms cubic-bezier(0.55, 0.06, 0.68, 0.19),
            opacity 250ms ease-in;
        opacity: 0.35;
    }

    .promoCard.phase-in-prep {
        transition: none !important;
        opacity: 0.35;
    }

    .promoCard.phase-in {
        transition:
            transform 320ms cubic-bezier(0.22, 1, 0.36, 1),
            opacity 260ms ease-out;
        transform: translateZ(0) rotate3d(0, 1, 0, 0deg) scale(1);
        opacity: 1;
    }

    /* Angle 1: Horizontal 3D Y-Axis Flip */
    .promoCard.flip-y.phase-out {
        transform: rotateY(90deg) scale(0.93);
    }
    .promoCard.flip-y.phase-in-prep {
        transform: rotateY(-90deg) scale(0.93);
    }

    /* Angle 2: Vertical 3D X-Axis Flip */
    .promoCard.flip-x.phase-out {
        transform: rotateX(90deg) scale(0.93);
    }
    .promoCard.flip-x.phase-in-prep {
        transform: rotateX(-90deg) scale(0.93);
    }

    /* Angle 3: Diagonal 3D Tilt-Flip (+X +Y) */
    .promoCard.flip-diag-a.phase-out {
        transform: rotate3d(1, 1, 0, 90deg) scale(0.9);
    }
    .promoCard.flip-diag-a.phase-in-prep {
        transform: rotate3d(1, 1, 0, -90deg) scale(0.9);
    }

    /* Angle 4: 3D Cube / Turntable Spin */
    .promoCard.swing-3d.phase-out {
        transform: translateZ(-44px) rotateY(-88deg) rotateZ(-6deg) scale(0.9);
    }
    .promoCard.swing-3d.phase-in-prep {
        transform: translateZ(-44px) rotateY(88deg) rotateZ(6deg) scale(0.9);
    }

    /* Angle 5: Counter-Diagonal 3D Tilt-Flip (+X -Y) */
    .promoCard.flip-diag-b.phase-out {
        transform: rotate3d(1, -1, 0, 90deg) scale(0.9);
    }
    .promoCard.flip-diag-b.phase-in-prep {
        transform: rotate3d(1, -1, 0, -90deg) scale(0.9);
    }

    .dragHeader {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
        padding: 6px 8px 6px 10px;
        background: rgb(0 0 0 / 0.26);
        border-bottom: 1px solid rgb(255 255 255 / 0.08);
    }

    .brandGrip {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
    }

    .gripDots {
        font-size: 11px;
        line-height: 1;
        letter-spacing: -1px;
        opacity: 0.55;
    }

    .badge {
        display: inline-flex;
        align-items: center;
        padding: 2px 8px;
        border-radius: 999px;
        font-size: 0.65rem;
        font-weight: 700;
        letter-spacing: 0.03em;
        background: color-mix(in srgb, var(--secondary) 18%, transparent);
        border: 1px solid color-mix(in srgb, var(--secondary) 42%, transparent);
        color: color-mix(in srgb, var(--secondary) 75%, white 25%);
    }

    .headerControls {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: default;
    }

    .miniFlipBtn {
        width: 20px;
        height: 20px;
        border-radius: 6px;
        border: 1px solid rgb(255 255 255 / 0.14);
        background: rgb(255 255 255 / 0.06);
        color: var(--text);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 700;
        line-height: 1;
        cursor: pointer;
        padding: 0;
        transition: background-color 0.15s ease;
    }

    .miniFlipBtn:hover {
        background: color-mix(in srgb, var(--secondary) 55%, transparent);
        color: #ffffff;
    }

    .counterText {
        font-size: 0.64rem;
        font-weight: 700;
        opacity: 0.72;
        padding: 0 2px;
        font-variant-numeric: tabular-nums;
    }

    .closeBtn {
        width: 23px;
        height: 23px;
        border-radius: 999px;
        border: 1px solid rgb(255 255 255 / 0.2);
        background: rgb(10 10 16 / 0.78);
        color: #ffffff;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 15px;
        font-weight: 700;
        line-height: 1;
        cursor: pointer;
        padding: 0;
        transition:
            background-color 0.15s ease,
            transform 0.15s ease,
            border-color 0.15s ease;
    }

    .closeBtn:hover {
        background: var(--secondary);
        color: var(--secondary-text);
        border-color: var(--secondary);
        transform: scale(1.06);
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
        pointer-events: none;
    }

    .content {
        padding: 11px 13px 12px;
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .title {
        margin: 0;
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
        justify-content: space-between;
        gap: 8px;
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

    .dotsRow {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: default;
    }

    .dotBtn {
        width: 6px;
        height: 6px;
        padding: 0;
        border-radius: 999px;
        border: 0;
        background: rgb(255 255 255 / 0.28);
        cursor: pointer;
        transition:
            width 0.2s ease,
            background-color 0.2s ease;
    }

    .dotBtn.active {
        width: 14px;
        background: var(--secondary);
    }

    @media (max-width: 900px) {
        .promoStage {
            right: 10px;
            bottom: 54px;
            width: min(278px, calc(100vw - 20px));
        }

        .promoStage.bottomLeft {
            left: 10px;
            bottom: 12px;
        }
    }
</style>
