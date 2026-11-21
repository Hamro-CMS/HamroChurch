<script lang="ts">
    import type { StutiMediaSong } from "../../../../types/Main"
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain, sendMain } from "../../../IPC/main"
    import { language, playerVideos, stutiMediaActiveVideoId, stutiMediaAutoPlayQueue, stutiMediaFavorites, stutiMediaFeed, stutiMediaFilter } from "../../../stores"
    import Icon from "../../helpers/Icon.svelte"
    import { setOutput } from "../../helpers/output"
    import { clearSlide } from "../../output/clear"

    export let searchValue = ""

    let refreshing = false

    $: isNe = $language === "ne"
    $: videos = $stutiMediaFeed?.videos || []
    $: shorts = $stutiMediaFeed?.shorts || []
    $: subscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"

    $: baseList = getFilteredSource($stutiMediaFilter, videos, shorts, $stutiMediaFavorites)

    function getFilteredSource(filter: "all" | "latest" | "favorites" | "shorts", vidList: StutiMediaSong[], shortList: StutiMediaSong[], favIds: string[]): StutiMediaSong[] {
        if (filter === "shorts") return shortList
        if (filter === "favorites") {
            const combined = [...vidList, ...shortList]
            return combined.filter((song) => favIds.includes(song.video_id))
        }
        return vidList
    }

    $: normalizedSearch = searchValue.trim().toLowerCase()
    $: filteredSongs = normalizedSearch
        ? baseList.filter((song) => {
              const title = (song.title || "").toLowerCase()
              const desc = (song.description || "").toLowerCase()
              return title.includes(normalizedSearch) || desc.includes(normalizedSearch)
          })
        : baseList

    function selectSong(song: StutiMediaSong) {
        stutiMediaActiveVideoId.set(song.video_id)
    }

    function projectSong(song: StutiMediaSong) {
        stutiMediaActiveVideoId.set(song.video_id)
        const rid = "stuti_" + song.video_id
        playerVideos.update((a) => ({
            ...a,
            [rid]: { id: song.video_id, name: song.title, type: "youtube" }
        }))
        clearSlide()
        setOutput("background", { id: rid, type: "player", muted: false, loop: false, startAt: 0, ignoreLayer: true })
    }

    function toggleFavorite(videoId: string) {
        stutiMediaFavorites.update((ids) => {
            if (ids.includes(videoId)) return ids.filter((id) => id !== videoId)
            return [videoId, ...ids]
        })
    }

    function toggleAutoPlayQueue() {
        const next = !$stutiMediaAutoPlayQueue
        stutiMediaAutoPlayQueue.set(next)
        if (next && !$stutiMediaActiveVideoId && videos.length > 0) {
            stutiMediaActiveVideoId.set(videos[0].video_id)
        }
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

    function openExternal(url: string) {
        sendMain(Main.URL, url)
    }
</script>

<div class="browser">
    <div class="headerBar">
        <div class="headerTitle">
            <Icon id="youtube" size={0.95} />
            <span class="titleText">Stuti Media</span>
            <span class="countMeta">{filteredSongs.length}</span>
        </div>

        <div class="headerActions">
            <button
                type="button"
                class="headerBtn"
                class:active={$stutiMediaAutoPlayQueue}
                title={isNe ? "Play All (संगति अगाडि लगातार बजाउनुहोस्)" : "Play All (Pre-Service Worship)"}
                on:click={toggleAutoPlayQueue}
            >
                <Icon id="play" size={0.85} white={!$stutiMediaAutoPlayQueue} />
                <span>Play All</span>
            </button>

            <button
                type="button"
                class="headerBtn iconOnly"
                title={isNe ? "गीत सूची ताजा गर्नुहोस्" : "Refresh catalog"}
                disabled={refreshing}
                on:click={refreshCatalog}
            >
                <Icon id="refresh" size={0.85} white />
            </button>

            <button type="button" class="headerBtn" on:click={() => openExternal(subscribeUrl)}>
                <Icon id="youtube" size={0.85} white />
                <span>Subscribe</span>
            </button>
        </div>
    </div>

    {#if filteredSongs.length === 0}
        <div class="emptyState">
            <p>
                {#if $stutiMediaFilter === "favorites"}
                    {isNe ? "कुनै मनपर्ने गीत छैन। गीतको तारा आइकन थिचेर थप्नुहोस्।" : "No favorite songs yet. Click the star icon on any song card."}
                {:else if normalizedSearch}
                    {isNe ? `"${searchValue}" सँग मिल्ने गीत फेला परेन।` : `No songs matching "${searchValue}".`}
                {:else}
                    {isNe ? "गीतहरू लोड हुँदैछन्..." : "Loading worship songs..."}
                {/if}
            </p>
        </div>
    {:else}
        <div class="grid">
            {#each filteredSongs as song (song.video_id)}
                {@const isActive = $stutiMediaActiveVideoId === song.video_id}
                {@const isFav = $stutiMediaFavorites.includes(song.video_id)}

                <div
                    class="card"
                    class:active={isActive}
                    role="button"
                    tabindex="0"
                    on:click={() => selectSong(song)}
                    on:dblclick={() => projectSong(song)}
                    on:keydown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault()
                            selectSong(song)
                        }
                    }}
                >
                    <div class="thumbWrap">
                        <img src={song.thumbnail_url} alt={song.title} loading="lazy" />

                        <div class="hoverOverlay">
                            <span class="playCenter">
                                <Icon id="play" size={1.15} white />
                            </span>

                            <button
                                type="button"
                                class="projectHoverBtn"
                                title={isNe ? "प्रोजेक्टरमा देखाउनुहोस्" : "Project to Output"}
                                on:click|stopPropagation={() => projectSong(song)}
                            >
                                <Icon id="display" size={0.82} white />
                            </button>
                        </div>

                        {#if song.duration_text}
                            <span class="durationBadge">{song.duration_text}</span>
                        {/if}

                        <button
                            type="button"
                            class="favBtn"
                            class:starred={isFav}
                            title={isNe ? "मनपर्नेमा राख्नुहोस्" : "Toggle favorite"}
                            on:click|stopPropagation={() => toggleFavorite(song.video_id)}
                        >
                            <Icon id="star" size={0.82} white={!isFav} />
                        </button>
                    </div>

                    <div class="cardBody">
                        <div class="songTitle" title={song.title}>{song.title}</div>
                    </div>
                </div>
            {/each}
        </div>
    {/if}
</div>

<style>
    .browser {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        overflow: hidden;
    }

    .headerBar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
        padding: 7px 12px;
        border-bottom: 1px solid var(--primary-lighter);
        background: var(--primary-darker);
        flex-shrink: 0;
    }

    .headerTitle {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    .titleText {
        font-size: 0.84rem;
        font-weight: 600;
        color: var(--text);
    }

    .countMeta {
        font-size: 0.72rem;
        padding: 1px 6px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.08);
        color: var(--text);
        opacity: 0.75;
    }

    .headerActions {
        display: flex;
        align-items: center;
        gap: 6px;
        flex-shrink: 0;
    }

    .headerBtn {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        height: 27px;
        padding: 0 9px;
        border-radius: 6px;
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(255, 255, 255, 0.04);
        color: var(--text);
        font-family: inherit;
        font-size: 0.74rem;
        font-weight: 500;
        cursor: pointer;
        transition: background-color 0.15s ease, border-color 0.15s ease;
    }

    .headerBtn.iconOnly {
        padding: 0 7px;
    }

    .headerBtn:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.12);
        border-color: rgba(255, 255, 255, 0.24);
    }

    .headerBtn:disabled {
        opacity: 0.5;
        cursor: default;
    }

    .headerBtn.active {
        border-color: var(--secondary);
        background: color-mix(in srgb, var(--secondary) 18%, transparent);
    }

    .emptyState {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        text-align: center;
        opacity: 0.65;
        font-size: 0.88rem;
    }

    .grid {
        flex: 1;
        overflow-y: auto;
        padding: 10px 12px 14px;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(186px, 1fr));
        gap: 10px;
        align-content: start;
    }

    .card {
        display: flex;
        flex-direction: column;
        background: var(--primary-darker);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 8px;
        overflow: hidden;
        cursor: pointer;
        transition: border-color 0.15s ease, transform 0.15s ease;
    }

    .card:hover {
        border-color: rgba(255, 255, 255, 0.24);
        transform: translateY(-1px);
    }

    .card.active {
        border-color: var(--secondary);
        box-shadow: 0 0 0 1px var(--secondary);
    }

    .thumbWrap {
        position: relative;
        width: 100%;
        aspect-ratio: 16 / 9;
        background: #000;
        overflow: hidden;
    }

    .thumbWrap img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .hoverOverlay {
        position: absolute;
        inset: 0;
        background: rgba(0, 0, 0, 0.34);
        opacity: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: opacity 0.15s ease;
    }

    .card:hover .hoverOverlay,
    .card:focus-within .hoverOverlay {
        opacity: 1;
    }

    .playCenter {
        width: 36px;
        height: 36px;
        border-radius: 999px;
        background: rgba(0, 0, 0, 0.68);
        border: 1px solid rgba(255, 255, 255, 0.25);
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .projectHoverBtn {
        position: absolute;
        left: 6px;
        bottom: 6px;
        width: 26px;
        height: 24px;
        border-radius: 5px;
        border: 1px solid rgba(255, 255, 255, 0.24);
        background: rgba(0, 0, 0, 0.72);
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
    }

    .projectHoverBtn:hover {
        background: var(--secondary);
        border-color: var(--secondary);
    }

    .durationBadge {
        position: absolute;
        right: 6px;
        bottom: 6px;
        font-size: 0.66rem;
        font-weight: 700;
        padding: 2px 5px;
        border-radius: 4px;
        background: rgba(0, 0, 0, 0.82);
        color: #fff;
        z-index: 2;
    }

    .favBtn {
        position: absolute;
        top: 6px;
        right: 6px;
        width: 24px;
        height: 24px;
        border-radius: 999px;
        border: none;
        background: rgba(0, 0, 0, 0.65);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.15s ease, background-color 0.15s ease;
        z-index: 2;
    }

    .card:hover .favBtn,
    .card:focus-within .favBtn,
    .favBtn.starred {
        opacity: 1;
    }

    .favBtn:hover {
        background: rgba(0, 0, 0, 0.88);
    }

    .cardBody {
        padding: 7px 9px;
    }

    .songTitle {
        font-size: 0.79rem;
        font-weight: 500;
        line-height: 1.3;
        color: var(--text);
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }
</style>
