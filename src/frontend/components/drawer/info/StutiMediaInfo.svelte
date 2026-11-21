<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import type { StutiMediaSong } from "../../../../types/Main"
    import { sendMain } from "../../../IPC/main"
    import { language, playerVideos, stutiMediaActiveVideoId, stutiMediaAutoPlayQueue, stutiMediaFeed } from "../../../stores"
    import { setOutput } from "../../helpers/output"
    import { clearSlide } from "../../output/clear"
    import YouTubePlayer from "../player/YouTubePlayer.svelte"

    $: isNe = $language === "ne"
    $: videos = $stutiMediaFeed?.videos || []
    $: shorts = $stutiMediaFeed?.shorts || []
    $: allSongs = [...videos, ...shorts]
    $: activeSong = allSongs.find((s) => s.video_id === $stutiMediaActiveVideoId) || videos[0] || null
    $: subscribeUrl = $stutiMediaFeed?.channel?.subscribe_url || "https://www.youtube.com/@stutimedianepal?sub_confirmation=1"

    function handleEnded() {
        if (!$stutiMediaAutoPlayQueue || videos.length === 0) return
        const currentId = activeSong?.video_id
        const idx = videos.findIndex((v) => v.video_id === currentId)
        const nextIdx = idx >= 0 ? (idx + 1) % videos.length : 0
        stutiMediaActiveVideoId.set(videos[nextIdx].video_id)
    }

    function projectActiveSong(song: StutiMediaSong) {
        const rid = "stuti_" + song.video_id
        playerVideos.update((a) => ({
            ...a,
            [rid]: { id: song.video_id, name: song.title, type: "youtube" }
        }))
        clearSlide()
        setOutput("background", { id: rid, type: "player", muted: false, loop: false, startAt: 0, ignoreLayer: true })
    }

    function openExternal(url: string) {
        sendMain(Main.URL, url)
    }
</script>

{#if activeSong}
    <div class="infoWrap">
        <div class="playerFrame">
            {#key activeSong.video_id}
                <YouTubePlayer
                    class="ytPlayerBox"
                    videoId={activeSong.video_id}
                    options={{ playerVars: { autoplay: $stutiMediaAutoPlayQueue ? 1 : 0, rel: 0, fs: 0, controls: 1 } }}
                    on:end={handleEnded}
                />
            {/key}
        </div>

        <div class="songMeta">
            <h3 class="title">{activeSong.title}</h3>
            <div class="metaLine">
                <span>@StutiMediaNepal</span>
                {#if activeSong.published_label}
                    <span>• {activeSong.published_label}</span>
                {/if}
                {#if activeSong.duration_text}
                    <span>• {activeSong.duration_text}</span>
                {/if}
            </div>
        </div>

        <div class="actions">
            <button type="button" class="btn primary" on:click={() => projectActiveSong(activeSong)}>
                📺 {isNe ? "प्रोजेक्टरमा देखाउनुहोस्" : "Project on Screen"}
            </button>

            <button type="button" class="btn secondary" on:click={() => openExternal(activeSong.watch_url)}>
                ↗ {isNe ? "YouTube मा हेर्नुहोस्" : "Watch on YouTube"}
            </button>

            <button type="button" class="btn subscribe" on:click={() => openExternal(subscribeUrl)}>
                🔔 {isNe ? "Subscribe" : "Subscribe"}
            </button>
        </div>

        {#if $stutiMediaAutoPlayQueue}
            <div class="queueHint">
                ▶ {isNe ? "Play All सक्रिय छ — गीत सकिएपछि अर्को गीत स्वतः बज्नेछ।" : "Play All active — next song plays automatically."}
            </div>
        {/if}
    </div>
{:else}
    <div class="empty">
        <p>{isNe ? "कुनै आराधना गीत छनोट गरिएको छैन।" : "Select a Stuti Media song to preview."}</p>
    </div>
{/if}

<style>
    .infoWrap {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 10px;
    }

    .playerFrame {
        width: 100%;
        aspect-ratio: 16 / 9;
        border-radius: 8px;
        overflow: hidden;
        background: #000;
        border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .playerFrame :global(.ytPlayerBox),
    .playerFrame :global(iframe) {
        width: 100%;
        height: 100%;
        display: block;
        border: 0;
    }

    .songMeta {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    .title {
        margin: 0;
        font-size: 0.88rem;
        font-weight: 700;
        line-height: 1.3;
        color: var(--text);
    }

    .metaLine {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        font-size: 0.72rem;
        opacity: 0.7;
    }

    .actions {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .btn {
        width: 100%;
        padding: 7px 10px;
        border-radius: 6px;
        font-size: 0.78rem;
        font-weight: 600;
        cursor: pointer;
        border: 1px solid rgba(255, 255, 255, 0.14);
        background: rgba(255, 255, 255, 0.06);
        color: var(--text);
        font-family: inherit;
        text-align: center;
    }

    .btn:hover {
        background: rgba(255, 255, 255, 0.14);
    }

    .btn.primary {
        border-color: rgba(56, 189, 248, 0.5);
        background: rgba(14, 165, 233, 0.25);
        color: #e0f2fe;
    }

    .btn.primary:hover {
        background: rgba(14, 165, 233, 0.38);
    }

    .btn.subscribe {
        border-color: rgba(239, 68, 68, 0.5);
        background: rgba(239, 68, 68, 0.2);
        color: #fecaca;
    }

    .btn.subscribe:hover {
        background: rgba(239, 68, 68, 0.34);
    }

    .queueHint {
        font-size: 0.72rem;
        padding: 6px 8px;
        border-radius: 6px;
        background: rgba(16, 185, 129, 0.14);
        border: 1px solid rgba(16, 185, 129, 0.32);
        color: #a7f3d0;
    }

    .empty {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 20px;
        opacity: 0.65;
        font-size: 0.84rem;
        text-align: center;
    }
</style>
