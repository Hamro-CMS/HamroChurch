<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import type { StutiMediaSong } from "../../../../types/Main"
    import { sendMain } from "../../../IPC/main"
    import { language, playerVideos, stutiMediaActiveVideoId, stutiMediaAutoPlayQueue, stutiMediaFeed } from "../../../stores"
    import Icon from "../../helpers/Icon.svelte"
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

        <div class="actions">
            <button type="button" class="btn primary" on:click={() => projectActiveSong(activeSong)}>
                <Icon id="display" size={0.88} white />
                <span>{isNe ? "प्रोजेक्टरमा" : "Project"}</span>
            </button>

            <button type="button" class="btn" on:click={() => openExternal(activeSong.watch_url)}>
                <Icon id="launch" size={0.85} white />
                <span>YouTube</span>
            </button>

            <button type="button" class="btn" on:click={() => openExternal(subscribeUrl)}>
                <Icon id="youtube" size={0.85} white />
                <span>Subscribe</span>
            </button>
        </div>

        {#if $stutiMediaAutoPlayQueue}
            <div class="queueHint">
                <Icon id="play" size={0.78} white />
                <span>{isNe ? "Play All सक्रिय छ — अर्को गीत स्वतः बज्नेछ।" : "Play All active — auto-advancing."}</span>
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
        gap: 8px;
        padding: 10px 10px 52px;
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

    .actions {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
    }

    .btn {
        flex: 1 1 auto;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 5px;
        padding: 6px 9px;
        border-radius: 6px;
        font-size: 0.74rem;
        font-weight: 500;
        cursor: pointer;
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(255, 255, 255, 0.05);
        color: var(--text);
        font-family: inherit;
        white-space: nowrap;
        transition: background-color 0.15s ease, border-color 0.15s ease;
    }

    .btn:hover {
        background: rgba(255, 255, 255, 0.12);
        border-color: rgba(255, 255, 255, 0.24);
    }

    .btn.primary {
        border-color: rgba(56, 189, 248, 0.45);
        background: rgba(14, 165, 233, 0.22);
        color: #e0f2fe;
        font-weight: 600;
    }

    .btn.primary:hover {
        background: rgba(14, 165, 233, 0.35);
    }

    .queueHint {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 0.71rem;
        padding: 5px 8px;
        border-radius: 6px;
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid rgba(255, 255, 255, 0.08);
        opacity: 0.8;
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
