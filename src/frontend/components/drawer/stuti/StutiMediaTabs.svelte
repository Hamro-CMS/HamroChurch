<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain, sendMain } from "../../../IPC/main"
    import { language, stutiMediaActiveVideoId, stutiMediaAutoPlayQueue, stutiMediaFavorites, stutiMediaFeed, stutiMediaFilter } from "../../../stores"
    import Icon from "../../helpers/Icon.svelte"

    let refreshing = false

    $: isNe = $language === "ne"
    $: videosCount = ($stutiMediaFeed?.videos || []).length
    $: shortsCount = ($stutiMediaFeed?.shorts || []).length
    $: favoritesCount = ($stutiMediaFavorites || []).length
    $: subscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"

    $: if ($stutiMediaFilter === "latest" || ($stutiMediaFilter === "shorts" && shortsCount === 0)) {
        stutiMediaFilter.set("all")
    }

    $: categories = [
        { id: "all" as const, icon: "music", label: isNe ? "सबै गीतहरू" : "All Songs", count: videosCount },
        { id: "favorites" as const, icon: "star", label: isNe ? "मनपर्ने" : "Favorites", count: favoritesCount },
        ...(shortsCount > 0 ? [{ id: "shorts" as const, icon: "play", label: "Shorts", count: shortsCount }] : [])
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
        <Icon id="youtube" size={0.85} />
        <span class="channelName">@StutiMediaNepal</span>
    </div>

    {#each categories as cat}
        <button type="button" class="item" class:active={$stutiMediaFilter === cat.id} on:click={() => stutiMediaFilter.set(cat.id)}>
            <span class="left">
                <Icon id={cat.icon} size={0.86} white={$stutiMediaFilter !== cat.id} />
                <span class="label">{cat.label}</span>
            </span>
            <span class="count">{cat.count}</span>
        </button>
    {/each}

    <hr class="divider" />

    <div class="actionStack">
        <button type="button" class="actionBtn" class:enabled={$stutiMediaAutoPlayQueue} on:click={toggleAutoPlayQueue}>
            <Icon id="play" size={0.82} white={!$stutiMediaAutoPlayQueue} />
            <span>{isNe ? "Play All (संगति अगाडि)" : "Play All (Pre-Service)"}</span>
        </button>

        <button type="button" class="actionBtn" on:click={openSubscribe}>
            <Icon id="youtube" size={0.82} white />
            <span>Subscribe</span>
        </button>

        <button type="button" class="actionBtn" on:click={refreshCatalog} disabled={refreshing}>
            <Icon id="refresh" size={0.82} white />
            <span>{refreshing ? (isNe ? "ताजा हुँदैछ..." : "Refreshing...") : isNe ? "ताजा गर्नुहोस्" : "Refresh"}</span>
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
        gap: 7px;
        padding: 6px 12px 5px;
        font-size: 0.76rem;
        font-weight: 600;
        color: var(--text);
        opacity: 0.78;
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
        border-left-color: var(--secondary);
        font-weight: 600;
    }

    .left {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
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
        gap: 4px;
        padding: 0 8px;
    }

    .actionBtn {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        padding: 6px 10px;
        border-radius: 6px;
        border: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(255, 255, 255, 0.025);
        color: var(--text);
        font-family: inherit;
        font-size: 0.78rem;
        font-weight: 500;
        cursor: pointer;
        text-align: left;
        transition: background-color 0.15s ease, border-color 0.15s ease;
    }

    .actionBtn:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.08);
        border-color: rgba(255, 255, 255, 0.18);
    }

    .actionBtn:disabled {
        opacity: 0.55;
        cursor: default;
    }

    .actionBtn.enabled {
        border-color: var(--secondary);
        background: color-mix(in srgb, var(--secondary) 16%, transparent);
    }
</style>
