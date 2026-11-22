<script lang="ts">
    import { onDestroy } from "svelte"
    import { get } from "svelte/store"
    import { fromNepaliDigits } from "../../../../common/nepali"
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain } from "../../../IPC/main"
    import { activePopup, shows, showsCache } from "../../../stores"
    import { applyHymnToExistingShow, hymnEditTarget, hymnItems, hymnSelectedId, loadHymns, type HymnRecord } from "../../drawer/hymns/hymns"
    import { saveTextCache } from "../../helpers/setShow"
    import { newToast } from "../../../utils/common"
    import { save } from "../../../utils/save"
    import T from "../../helpers/T.svelte"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import MaterialDropdown from "../../inputs/MaterialDropdown.svelte"
    import MaterialTextInput from "../../inputs/MaterialTextInput.svelte"
    import MaterialTextarea from "../../inputs/MaterialTextarea.svelte"

    const editTarget = get(hymnEditTarget)
    const isEditing = !!editTarget
    const editId = editTarget?.id || ""

    function sanitizeHymnNumber(raw: string) {
        return fromNepaliDigits(String(raw || "")).replace(/[^\d]/g, "")
    }

    let title = editTarget?.title || ""
    let titleEn = editTarget?.titleEn || ""
    let lyrics = editTarget?.lyrics || ""
    let number = sanitizeHymnNumber(editTarget?.number || "")
    let authors = editTarget?.authors || ""
    let saving = false
    let errorMessage = ""
    let categoryId: "bhajan" | "chorus" | "children" | "new" = editTarget && editTarget.categoryId !== "all" ? editTarget.categoryId : "bhajan"

    const categoryOptions = [
        { value: "bhajan", label: "Bhajan / भजन" },
        { value: "chorus", label: "Chorus / कोरस" },
        { value: "children", label: "Bal Sangati / बाल सङ्गित" },
        { value: "new", label: "New Songs / नयाँ गीत" }
    ]

    function syncLinkedHymnShows(updatedHymn: HymnRecord, cleanNumber: string) {
        const savedHymnId = updatedHymn.sourceKey || updatedHymn.id
        if (!savedHymnId) return

        const syncedAt = Math.max(Date.now(), updatedHymn.updatedAt || 0)
        const updatedEntries: [string, any][] = []

        showsCache.update((cache) => {
            for (const [showId, show] of Object.entries(cache || {})) {
                const metaKey = show?.quickAccess?.metadata?.hymnSourceKey
                const isLegacyMatch = show?.origin === "hamro-hymns" && !metaKey && show?.meta?.title === editTarget?.title
                if (metaKey !== savedHymnId && !isLegacyMatch) continue

                const refreshed = applyHymnToExistingShow(show, updatedHymn, syncedAt)
                cache[showId] = refreshed
                updatedEntries.push([showId, refreshed])
            }
            return cache
        })

        for (const [showId, refreshed] of updatedEntries) {
            saveTextCache(showId, refreshed)
        }

        shows.update((allShows) => {
            for (const [showId, entry] of Object.entries(allShows || {})) {
                const metaKey = entry?.quickAccess?.metadata?.hymnSourceKey
                const wasUpdatedInCache = updatedEntries.some(([id]) => id === showId)
                if (metaKey !== savedHymnId && !wasUpdatedInCache) continue

                const nextQuickAccess = {
                    ...(entry.quickAccess || {}),
                    metadata: {
                        ...(entry.quickAccess?.metadata || {}),
                        hymnSourceKey: savedHymnId,
                        hymnCategoryId: categoryId,
                        hymnTitleEn: titleEn.trim(),
                        hymnSyncedAt: syncedAt
                    }
                }
                if (cleanNumber) nextQuickAccess.number = cleanNumber
                else delete nextQuickAccess.number
                allShows[showId].quickAccess = nextQuickAccess
                if (allShows[showId].timestamps) allShows[showId].timestamps.modified = syncedAt
            }
            return allShows
        })

        if (updatedEntries.length) {
            save(false, { autosave: true })
        }
    }

    async function saveHymn() {
        if (!title.trim() || !lyrics.trim()) {
            errorMessage = "Title and lyrics are required."
            return
        }

        const cleanNumber = sanitizeHymnNumber(number)
        number = cleanNumber

        saving = true
        errorMessage = ""
        const result = await requestMain(Main.SAVE_HYMN, {
            id: editId || undefined,
            title,
            titleEn,
            lyrics,
            categoryId,
            number: cleanNumber,
            authors
        })
        saving = false

        if (!result?.success) {
            errorMessage = result?.error || "Could not save hymn."
            return
        }

        await loadHymns()
        const resolvedId = result.id || editId
        if (resolvedId) {
            hymnSelectedId.set(resolvedId)
            const updatedHymn = get(hymnItems).find((h) => h.id === resolvedId || h.sourceKey === resolvedId)
            if (updatedHymn) {
                syncLinkedHymnShows(updatedHymn, cleanNumber)
            }
        }
        if (isEditing) newToast("hymns.updated")
        activePopup.set(null)
    }

    onDestroy(() => hymnEditTarget.set(null))
</script>

<div class="form">
    <MaterialTextInput label="hymns.form_title" value={title} on:input={(e) => (title = e.detail)} on:change={(e) => (title = e.detail)} autofocus />
    <MaterialTextInput label="hymns.form_transliteration" value={titleEn} on:input={(e) => (titleEn = e.detail)} on:change={(e) => (titleEn = e.detail)} />
    <MaterialDropdown label="hymns.form_category" options={categoryOptions} value={categoryId} on:change={(e) => (categoryId = e.detail)} />
    <MaterialTextInput label="hymns.form_number" value={number} disableNepaliTyping on:input={(e) => (number = sanitizeHymnNumber(e.detail))} on:change={(e) => (number = sanitizeHymnNumber(e.detail))} />
    <MaterialTextInput label="hymns.form_author" value={authors} on:input={(e) => (authors = e.detail)} on:change={(e) => (authors = e.detail)} />
    <MaterialTextarea label="hymns.form_lyrics" value={lyrics} rows={10} on:input={(e) => (lyrics = e.detail)} on:change={(e) => (lyrics = e.detail)} />

    {#if errorMessage}
        <p class="error">{errorMessage}</p>
    {/if}

    <div class="actions">
        <MaterialButton variant="contained" icon="save" on:click={saveHymn} disabled={saving}>
            <T id={isEditing ? "hymns.update" : "actions.save"} />
        </MaterialButton>
    </div>
</div>

<style>
    .form {
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
        min-width: min(720px, 100%);
    }

    .actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 0.4rem;
    }

    .error {
        color: #ffb4a8;
        margin: 0;
    }
</style>
