<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import type { StutiMediaSong } from "../../../../types/Main"
    import { activeDrawerTab, activePopup, drawer, language, stutiMediaActiveVideoId, stutiMediaFeed } from "../../../stores"
    import { sendMain } from "../../../IPC/main"
    import { DEFAULT_DRAWER_HEIGHT } from "../../../utils/common"
    import { copySelectedHymnToClipboard, deleteHymnById, formatHymnNumber, getHymnCategoryDisplay, hymnEditTarget, hymnFavoriteIds, hymnItems, hymnSelectedId, hymnSourceDates, toggleFavorite, insertSelectedHymnIntoSlides } from "../hymns/hymns"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import Icon from "../../helpers/Icon.svelte"
    import T from "../../helpers/T.svelte"
    import InfoMetadata from "./InfoMetadata.svelte"

    const GENERIC_TOKENS = new Set([
        "nepali",
        "christian",
        "worship",
        "song",
        "songs",
        "gospel",
        "official",
        "video",
        "music",
        "stuti",
        "media",
        "new",
        "bhajan",
        "chorus",
        "lyrics",
        "with"
    ])

    function normalizeTitleText(input: string): string {
        return (input || "")
            .toLowerCase()
            .replace(/[()[\]|।,.\-_:;!?"“”‘’/]+/g, " ")
            .replace(/\s+/g, " ")
            .trim()
    }

    function extractMeaningfulTokens(input: string): string[] {
        return normalizeTitleText(input)
            .split(" ")
            .filter((token) => {
                if (!token || GENERIC_TOKENS.has(token)) return false
                if (/[\u0900-\u097F]/.test(token)) return token.length >= 2
                return token.length >= 3
            })
    }

    function findMatchingStutiSong(sourceKey: string, title: string, transliteration: string, videos: StutiMediaSong[]): StutiMediaSong | null {
        if (!videos.length) return null
        if (sourceKey && sourceKey.startsWith("stuti-")) {
            const directId = sourceKey.slice(6)
            const directMatch = videos.find((v) => v.video_id === directId)
            if (directMatch) return directMatch
        }
        const normTitle = normalizeTitleText(title)
        const normTranslit = normalizeTitleText(transliteration)
        const hymnTokens = new Set([...extractMeaningfulTokens(title), ...extractMeaningfulTokens(transliteration)])

        for (const video of videos) {
            const normVideoTitle = normalizeTitleText(video.title)
            if (normTitle.length >= 6 && normVideoTitle.includes(normTitle)) {
                return video
            }
            if (normTranslit.length >= 6 && normVideoTitle.includes(normTranslit)) {
                return video
            }
            if (hymnTokens.size >= 2) {
                const videoTokens = new Set(extractMeaningfulTokens(video.title))
                let matches = 0
                for (const t of hymnTokens) {
                    if (videoTokens.has(t)) matches++
                }
                if (matches >= 2) {
                    return video
                }
            }
        }

        return null
    }

    function openMatchedStutiVideo(song: StutiMediaSong) {
        if ($drawer.height <= 40) {
            drawer.set({ height: $drawer.stored || DEFAULT_DRAWER_HEIGHT, stored: null })
        }
        stutiMediaActiveVideoId.set(song.video_id)
        activeDrawerTab.set("stuti")
    }

    function handleFavorite() {
        if (!hymn) return
        toggleFavorite(hymn.id)
    }

    function editSelectedHymn() {
        if (!hymn) return
        hymnEditTarget.set(hymn)
        activePopup.set("edit_hymn")
    }

    $: hymn = $hymnItems.find((item) => item.id === $hymnSelectedId) || null
    $: matchedStutiSong = hymn ? findMatchingStutiSong(hymn.sourceKey || hymn.id, hymn.title, hymn.transliteration || "", $stutiMediaFeed?.videos || []) : null
    $: info = hymn
        ? [
              { label: "info.created", value: $hymnSourceDates.created, type: "date" },
              { label: "info.modified", value: $hymnSourceDates.modified, type: "date" },
              { label: "hymns.repeat_chorus", value: hymn.repeatChorus ? "yes" : "no" },
              { label: "info.category", value: getHymnCategoryDisplay(hymn.categoryId, $language) },
              { label: "info.slides", value: hymn.slidesCount },
              { label: "info.words", value: hymn.wordCount },
              { label: "hymns.number", value: formatHymnNumber(hymn.number, $language) }
          ]
        : []
</script>

{#if hymn}
    <div class="infoPanel">
        <InfoMetadata title={hymn.title} {info} />

        <div class="translit">{hymn.transliteration}</div>

        <div class="actions">
            <MaterialButton variant="contained" icon="add" on:click={insertSelectedHymnIntoSlides}>
                <T id="hymns.insert" />
            </MaterialButton>
            <MaterialButton variant="outlined" icon="edit" on:click={editSelectedHymn} white>
                <T id="hymns.edit" />
            </MaterialButton>
            {#if hymn.isCustom}
                <MaterialButton variant="outlined" icon="delete" on:click={() => hymn && deleteHymnById(hymn.id)} white red>
                    <T id="hymns.delete" />
                </MaterialButton>
            {/if}
            <MaterialButton variant="outlined" icon="copy" on:click={copySelectedHymnToClipboard} white>
                <T id="hymns.copy" />
            </MaterialButton>
            <MaterialButton variant="outlined" icon={hymn && $hymnFavoriteIds.includes(hymn.id) ? "favorite" : "star"} on:click={handleFavorite} white>
                <T id="hymns.favorites" />
            </MaterialButton>
        </div>

        {#if matchedStutiSong}
            <div class="stutiMatchCard">
                <img src={matchedStutiSong.thumbnail_url} alt={matchedStutiSong.title} loading="lazy" />
                <div class="stutiMatchMeta">
                    <span class="stutiMatchLabel">
                        <Icon id="youtube" size={0.82} />
                        <span>{$language === "ne" ? "यो भजनको आधिकारिक भिडियो (Stuti Media)" : "Official Worship Video (Stuti Media)"}</span>
                    </span>
                    <div class="stutiMatchTitle">{matchedStutiSong.title}</div>
                    <div class="stutiMatchActions">
                        <button type="button" class="stutiListenBtn" on:click={() => matchedStutiSong && openMatchedStutiVideo(matchedStutiSong)}>
                            <Icon id="play" size={0.78} white />
                            <span>{$language === "ne" ? "सुन्नुहोस्" : "Listen"}</span>
                        </button>
                        <button type="button" class="stutiYtBtn" on:click={() => matchedStutiSong && sendMain(Main.URL, matchedStutiSong.watch_url)}>
                            <span>YouTube</span>
                        </button>
                    </div>
                </div>
            </div>
        {/if}

        <div class="lyricsCard">
            <h3><T id="hymns.lyrics" /></h3>
            {#each hymn.verses as verse, index}
                <section class="verse">
                    <h4>{verse.trim().startsWith("को.") || verse.includes("कोरस") ? ($language === "ne" ? "कोरस" : "Chorus") : `${$language === "ne" ? "अन्तरा" : "Verse"} ${index + 1}`}</h4>
                    <pre>{verse}</pre>
                </section>
            {/each}
        </div>
    </div>
{:else}
    <div class="emptyState"><T id="hymns.viewer_empty" /></div>
{/if}

<style>
    .stutiMatchCard {
        display: flex;
        align-items: center;
        gap: 9px;
        padding: 8px;
        border-radius: 8px;
        background: rgba(239, 68, 68, 0.1);
        border: 1px solid rgba(239, 68, 68, 0.3);
    }

    .stutiMatchCard img {
        width: 78px;
        aspect-ratio: 16 / 9;
        object-fit: cover;
        border-radius: 5px;
        flex-shrink: 0;
        background: #000;
    }

    .stutiMatchMeta {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 3px;
    }

    .stutiMatchLabel {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        font-size: 0.68rem;
        font-weight: 600;
        color: var(--text);
        opacity: 0.85;
    }

    .stutiMatchTitle {
        font-size: 0.76rem;
        font-weight: 600;
        line-height: 1.25;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .stutiMatchActions {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-top: 2px;
    }

    .stutiListenBtn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        border: 1px solid rgba(255, 255, 255, 0.16);
        background: rgba(255, 255, 255, 0.08);
        color: var(--text);
        font-size: 0.7rem;
        font-weight: 600;
        padding: 3px 8px;
        border-radius: 5px;
        cursor: pointer;
        font-family: inherit;
    }

    .stutiListenBtn:hover {
        background: rgba(255, 255, 255, 0.16);
    }

    .stutiYtBtn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        border: 1px solid rgba(255, 255, 255, 0.14);
        background: rgba(255, 255, 255, 0.04);
        color: var(--text);
        font-size: 0.7rem;
        padding: 3px 7px;
        border-radius: 5px;
        cursor: pointer;
        font-family: inherit;
    }

    .infoPanel {
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
        padding-bottom: 48px;
    }

    .translit {
        margin: 0 8px;
        padding: 0 8px;
        color: var(--text);
        opacity: 0.72;
        font-size: 0.92rem;
    }

    .actions {
        display: flex;
        gap: 0.6rem;
        flex-wrap: wrap;
        padding: 0 12px;
    }

    .lyricsCard {
        margin: 0 8px;
        padding: 1rem;
        background: color-mix(in srgb, var(--primary-darkest) 86%, black 14%);
        border: 1px solid var(--primary-lighter);
        border-radius: 12px;
        overflow-y: auto;
    }

    .lyricsCard h3,
    .verse h4,
    .verse pre {
        margin: 0;
        color: var(--text);
    }

    .lyricsCard h3 {
        margin-bottom: 0.9rem;
        color: var(--secondary);
    }

    .verse {
        padding: 0.8rem 0;
        border-top: 1px solid rgb(255 255 255 / 0.06);
    }

    .verse:first-of-type {
        border-top: none;
        padding-top: 0;
    }

    .verse h4 {
        margin-bottom: 0.45rem;
        font-size: 0.85rem;
        opacity: 0.78;
    }

    .verse pre {
        white-space: pre-wrap;
        font: inherit;
        line-height: 1.55;
    }

    .emptyState {
        display: flex;
        align-items: center;
        justify-content: center;
        flex: 1;
        opacity: 0.7;
        padding: 2rem;
        text-align: center;
    }
</style>