<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain, sendMain } from "../../../IPC/main"
    import { language, stutiMediaActiveVideoId, stutiMediaAutoPlayQueue, stutiMediaFavorites, stutiMediaFeed, stutiMediaFilter } from "../../../stores"

    let refreshing = false

    $: isNe = $language === "ne"
    $: videosCount = ($stutiMediaFeed?.videos || []).length
    $: latestCount = Math.min(6, videosCount)
    $: shortsCount = ($stutiMediaFeed?.shorts || []).length
    $: favoritesCount = ($stutiMediaFavorites || []).length
    $: subscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"

    $: if ($stutiMediaFilter === "shorts" && shortsCount === 0) {
        stutiMediaFilter.set("all")
    }

    $: categories = [
        { id: "all" as const, icon: "●", label: isNe ? "सबै गीतहरू" : "All Songs", count: videosCount },
        { id: "latest" as const, icon: "✦", label: isNe ? "नयाँ रिलिज" : "Latest", count: latestCount },
        { id: "favorites" as const, icon: "★", label: isNe ? "मनपर्ने" : "Favorites", count: favoritesCount },
        ...(shortsCount > 0 ? [{ id: "shorts" as const, icon: "▶", label: "Shorts", count: shortsCount }] : [])
    ]

    function toggleAutoPlayQueue() {
        const next = !$stutiMediaAutoPlayQueue
        stutiMediaAutoPlayQueue.set(next)
        if (next && !$stutiMediaActiveVideoId && $stutiMediaFeed?.videos?.length) {
            stutiMediaActiveVideoId.set($stutiMediaFeed.videos[0].video_id)
        }
    }

    function openSubscribe() {
        sendMain(Main.URL, subscribeUrl)
    }

    function refreshCatalog() {
        if (refreshing) return
        refreshing = true
        void requestMain(Main.REFRESH_STUTI_MEDIA_SONGS, undefined, (data) => {
            refreshing = false
            if (data?.videos) {
                stutiMediaFeed.set(data)
            }
        }).finally(() => {
            refreshing = false
        })
    }
</script>

<div class="categories">
    <div class="channelHeader">
        <span class="dot">●</span>
        <span class="channelName">@StutiMediaNepal</span>
    </div>

    {#each categories as cat}
        <button type="button" class="item" class:active={$stutiMediaFilter === cat.id} on:click={() => stutiMediaFilter.set(cat.id)}>
            <span class="left">
                <span class="icon">{cat.icon}</span>
                <span class="label">{cat.label}</span>
            </span>
            <span class="count">{cat.count}</span>
        </button>
    {/each}

    <hr class="divider" />

    <div class="actionStack">
        <button type="button" class="actionBtn playAllBtn" class:enabled={$stutiMediaAutoPlayQueue} on:click={toggleAutoPlayQueue}>
            <span>{$stutiMediaAutoPlayQueue ? "⏸" : "▶"}</span>
            <span>{isNe ? "Play All (संगति अघिको आराधना)" : "Play All (Pre-Service)"}</span>
        </button>

        <button type="button" class="actionBtn subscribeBtn" on:click={openSubscribe}>
            <span>🔴</span>
            <span>{isNe ? "Subscribe @StutiMediaNepal" : "Subscribe @StutiMediaNepal"}</span>
        </button>

        <button type="button" class="actionBtn refreshBtn" on:click={refreshCatalog} disabled={refreshing}>
            <span>🔄</span>
            <span>{refreshing ? (isNe ? "ताजा हुँदैछ..." : "Refreshing...") : isNe ? "ताजा गर्नुहोस् (Refresh)" : "Refresh Catalog"}</span>
        </button>
    </div>
</div>

<style>
    .categories {
        display: flex;
        flex-direction: column;
        padding: 4px 0 8px;
        gap: 2px;
        height: 100%;
        overflow-y: auto;
    }

    .channelHeader {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px 4px;
        font-size: 0.76rem;
        font-weight: 700;
        color: #fca5a5;
        letter-spacing: 0.02em;
    }

    .channelHeader .dot {
        color: #ef4444;
        font-size: 0.65rem;
    }

    .item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        width: 100%;
        padding: 7px 12px;
        border: none;
        border-left: 3px solid transparent;
        background: transparent;
        color: var(--text);
        font-family: inherit;
        font-size: 0.85rem;
        cursor: pointer;
        text-align: left;
    }

    .item:hover {
        background: var(--hover);
    }

    .item.active {
        background: var(--primary-darker);
        border-left-color: #ef4444;
        font-weight: 600;
    }

    .left {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    .icon {
        width: 16px;
        text-align: center;
        font-size: 0.78rem;
        opacity: 0.85;
    }

    .item.active .icon {
        color: #f87171;
        opacity: 1;
    }

    .count {
        font-size: 0.74rem;
        opacity: 0.6;
        flex-shrink: 0;
    }

    .divider {
        margin: 8px 10px 6px;
        border: none;
        border-top: 1px solid var(--primary-lighter);
    }

    .actionStack {
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 0 10px;
    }

    .actionBtn {
        display: flex;
        align-items: center;
        gap: 7px;
        width: 100%;
        padding: 7px 9px;
        border-radius: 6px;
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(255, 255, 255, 0.04);
        color: var(--text);
        font-family: inherit;
        font-size: 0.77rem;
        font-weight: 600;
        cursor: pointer;
        text-align: left;
    }

    .actionBtn:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.1);
    }

    .actionBtn:disabled {
        opacity: 0.6;
        cursor: default;
    }

    .playAllBtn.enabled {
        border-color: rgba(16, 185, 129, 0.55);
        background: rgba(16, 185, 129, 0.2);
        color: #a7f3d0;
    }

    .subscribeBtn {
        border-color: rgba(239, 68, 68, 0.45);
        background: rgba(239, 68, 68, 0.16);
        color: #fecaca;
    }

    .subscribeBtn:hover {
        background: rgba(239, 68, 68, 0.28);
    }
</style>
