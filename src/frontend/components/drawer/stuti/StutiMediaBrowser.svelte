<script lang="ts">
    import type { StutiMediaSong } from "../../../../types/Main"
    import { Main } from "../../../../types/IPC/Main"
    import { sendMain } from "../../../IPC/main"
    import { language, playerVideos, stutiMediaActiveVideoId, stutiMediaFavorites, stutiMediaFeed, stutiMediaFilter } from "../../../stores"
    import { setOutput } from "../../helpers/output"
    import { clearSlide } from "../../output/clear"

    export let searchValue = ""

    $: isNe = $language === "ne"
    $: videos = $stutiMediaFeed?.videos || []
    $: shorts = $stutiMediaFeed?.shorts || []
    $: newestIds = new Set(videos.slice(0, 3).map((s) => s.video_id))

    $: baseList = getFilteredSource($stutiMediaFilter, videos, shorts, $stutiMediaFavorites)

    function getFilteredSource(filter: "all" | "latest" | "favorites" | "shorts", vidList: StutiMediaSong[], shortList: StutiMediaSong[], favIds: string[]): StutiMediaSong[] {
        if (filter === "shorts") return shortList
        if (filter === "latest") return vidList.slice(0, 6)
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

    function openExternal(url: string) {
        sendMain(Main.URL, url)
    }
</script>

<div class="browser">
    <div class="headerBar">
        <div class="headerTitle">
            <span class="channelBadge">▶ Stuti Media</span>
            <span class="subMeta">
                {isNe
                    ? `${filteredSongs.length} आराधना गीत • क्लिक: पूर्वावलोकन, डबल-क्लिक: प्रोजेक्टर`
                    : `${filteredSongs.length} worship songs • Click: preview, Double-click: project`}
            </span>
        </div>
        <button type="button" class="subscribeChip" on:click={() => openExternal($stutiMediaFeed?.channel?.subscribe_url || "https://www.youtube.com/@stutimedianepal?sub_confirmation=1")}>
            🔔 {isNe ? "Subscribe गर्नुहोस्" : "Subscribe"}
        </button>
    </div>

    {#if filteredSongs.length === 0}
        <div class="emptyState">
            <p>
                {#if $stutiMediaFilter === "favorites"}
                    {isNe ? "कुनै मनपर्ने गीत छैन। गीतको ★ आइकन थिचेर थप्नुहोस्।" : "No favorite songs yet. Click the ★ button on any song card."}
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
                {@const isNew = song.video_type !== "short" && newestIds.has(song.video_id)}

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
                        {#if isNew}
                            <span class="newBadge">NEW</span>
                        {:else if song.video_type === "short"}
                            <span class="shortBadge">SHORT</span>
                        {/if}
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
                            {isFav ? "★" : "☆"}
                        </button>
                    </div>

                    <div class="cardBody">
                        <div class="songTitle" title={song.title}>{song.title}</div>
                        <div class="metaRow">
                            <span class="metaText">{song.published_label || song.view_count_text || "@StutiMediaNepal"}</span>
                            <button
                                type="button"
                                class="outputBtn"
                                title={isNe ? "प्रोजेक्टरमा देखाउनुहोस्" : "Send to Output"}
                                on:click|stopPropagation={() => projectSong(song)}
                            >
                                📺 Output
                            </button>
                        </div>
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
        padding: 8px 12px;
        border-bottom: 1px solid var(--primary-lighter);
        background: var(--primary-darker);
        flex-shrink: 0;
    }

    .headerTitle {
        display: flex;
        align-items: center;
        gap: 10px;
        min-width: 0;
        flex-wrap: wrap;
    }

    .channelBadge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 999px;
        font-size: 0.72rem;
        font-weight: 700;
        background: rgba(239, 68, 68, 0.2);
        color: #fca5a5;
        border: 1px solid rgba(239, 68, 68, 0.4);
    }

    .subMeta {
        font-size: 0.76rem;
        color: var(--text);
        opacity: 0.72;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .subscribeChip {
        border: 1px solid rgba(239, 68, 68, 0.5);
        background: rgba(239, 68, 68, 0.22);
        color: #fee2e2;
        border-radius: 999px;
        padding: 4px 10px;
        font-size: 0.74rem;
        font-weight: 600;
        cursor: pointer;
        flex-shrink: 0;
        font-family: inherit;
    }

    .subscribeChip:hover {
        background: rgba(239, 68, 68, 0.36);
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
        grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
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

    .newBadge,
    .shortBadge {
        position: absolute;
        top: 6px;
        left: 6px;
        font-size: 0.62rem;
        font-weight: 800;
        letter-spacing: 0.04em;
        padding: 2px 6px;
        border-radius: 4px;
        color: #fff;
    }

    .newBadge {
        background: #dc2626;
    }

    .shortBadge {
        background: rgba(15, 23, 42, 0.86);
        border: 1px solid rgba(255, 255, 255, 0.25);
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
    }

    .favBtn {
        position: absolute;
        top: 5px;
        right: 5px;
        width: 24px;
        height: 24px;
        border-radius: 999px;
        border: none;
        background: rgba(0, 0, 0, 0.62);
        color: rgba(255, 255, 255, 0.78);
        font-size: 0.85rem;
        line-height: 1;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .favBtn:hover {
        background: rgba(0, 0, 0, 0.85);
        color: #fde047;
    }

    .favBtn.starred {
        color: #facc15;
    }

    .cardBody {
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 8px;
        flex: 1;
        justify-content: space-between;
    }

    .songTitle {
        font-size: 0.8rem;
        font-weight: 600;
        line-height: 1.3;
        color: var(--text);
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .metaRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
    }

    .metaText {
        font-size: 0.69rem;
        opacity: 0.65;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .outputBtn {
        flex-shrink: 0;
        border: 1px solid rgba(56, 189, 248, 0.4);
        background: rgba(14, 165, 233, 0.2);
        color: #e0f2fe;
        font-size: 0.68rem;
        font-weight: 600;
        padding: 2px 7px;
        border-radius: 5px;
        cursor: pointer;
        font-family: inherit;
    }

    .outputBtn:hover {
        background: rgba(14, 165, 233, 0.35);
    }
</style>
