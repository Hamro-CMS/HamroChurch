import { get } from "svelte/store"
import { uid } from "uid"
import type { Output } from "../../types/Output"
import type { Metadata, Themes } from "../../types/Settings"
import { clone, keysToID } from "../components/helpers/array"
import { sanitizeAllowedScriptures } from "../components/drawer/bible/scripture"
import { checkWindowCapture, setOutput, toggleOutputs } from "../components/helpers/output"
import { defaultThemes } from "../components/settings/tabs/defaultThemes"
import {
    actionTags,
    actions,
    activePopup,
    activeProject,
    alertUpdates,
    audioChannelsData,
    audioEffects,
    audioFolders,
    audioPlaylists,
    audioStreams,
    autoOutput,
    autosave,
    calendarAddShow,
    categories,
    cloudSyncData,
    companion,
    contentProviderData,
    customMetadata,
    customizedIcons,
    dataPath,
    deletedDefaults,
    disabledServers,
    drawSettings,
    drawer,
    drawerTabsData,
    driveData,
    effects,
    effectsLibrary,
    emitters,
    eqPresets,
    formatNewShow,
    fullColors,
    gain,
    globalRegexes,
    globalTags,
    groupNumbers,
    groups,
    labelsDisabled,
    language,
    loaded,
    loadedState,
    lockedOverlays,
    mediaFolders,
    mediaOptions,
    mediaTags,
    metronome,
    obsData,
    openedFolders,
    os,
    outLocked,
    overlayCategories,
    overlays,
    playerTags,
    playerVideos,
    ports,
    profiles,
    projectView,
    remotePassword,
    resized,
    serverData,
    showRecentlyUsedProjects,
    showsPath,
    slidesOptions,
    sorted,
    special,
    styles,
    templateCategories,
    theme,
    themes,
    timeFormat,
    timecode,
    timeline,
    timerTags,
    timers,
    variableTags,
    variables,
    version,
    videoMarkers,
    videosData,
    videosTime
} from "../stores"
import { OUTPUT } from "./../../types/Channels"
import type { SaveListSettings, SaveListSyncedSettings } from "./../../types/Save"
import { maxConnections, outputs, scriptureSettings, scriptures, splitLines, transitionData, volume } from "./../stores"
import { checkForUpdates } from "./checkForUpdates"
import { isMainWindow, startAutosave } from "./common"
import { setLanguage } from "./language"
import { send } from "./request"

export function updateSyncedSettings(data: any) {
    if (!data || !Object.keys(data).length) return

    // pre v1.6.1 (triggers are now actions)
    data = convertTriggersToActions(data)

    Object.entries(data).forEach(([key, value]: any) => {
        if (updateList[key as SaveListSyncedSettings]) updateList[key as SaveListSyncedSettings](value)
        else console.info("RECEIVED UNKNOWN SETTINGS KEY:", key)
    })

    loadedState.set([...get(loadedState), "synced_settings"])
}

export function updateSettings(data: any) {
    // pre v0.8.2 (data contains SaveListSyncedSettings, but it gets overwritten and removed on first save)

    // pre v1.6.1 (equalizerConfig was not in audioEffects)
    if (data.equalizerConfig && !data.audioEffects?.main) {
        data.audioEffects = { main: { equalizer: clone(data.equalizerConfig) } }
        delete data.equalizerConfig
    }

    Object.entries(data).forEach(([key, value]: any) => {
        if (updateList[key as SaveListSettings]) updateList[key as SaveListSettings](value)
        else console.info("RECEIVED UNKNOWN SETTINGS KEY:", key)
    })

    if (!isMainWindow()) return

    // output
    if (data.outputs) {
        // wait until content is loaded
        setTimeout(
            () => {
                restartOutputs()
                const delay = 1200
                if (get(autoOutput)) setTimeout(() => toggleOutputs(null, { autoStartup: true }), get(os).platform === "darwin" ? delay + 300 : delay)
                setTimeout(() => checkWindowCapture(true), get(os).platform === "darwin" ? delay + 300 + 500 : delay + 500)
            },
            get(os).platform === "darwin" ? 3500 : 2500
        )
    }

    // remote connections disabled for now
    if (data.disabledServers) {
        disabledServers.set({ ...data.disabledServers, remote: true, stage: true, controller: true, output_stream: true })
    } else {
        disabledServers.set({ remote: true, stage: true, controller: true, output_stream: true })
    }

    // theme
    const activeThemeId = data.theme || "default"
    let currentTheme = get(themes)[activeThemeId] || clone(defaultThemes.default)
    if (currentTheme?.colors) {
        // upgrade legacy FreeShow pink/purple default theme to Aradhana Studio Midnight Blue
        const legacySec = currentTheme.colors.secondary?.toLowerCase()
        const legacyPri = currentTheme.colors.primary?.toLowerCase()
        const isLegacyDefault = ["#f0008c", "#e6349c", "#cb9a41"].includes(legacySec || "") || ["#242832", "#292c36", "#f8f6f0"].includes(legacyPri || "")
        if (activeThemeId === "default" && isLegacyDefault) {
            themes.update((a) => {
                a.default = clone(defaultThemes.default)
                currentTheme = a.default
                return a
            })
        }

        updateThemeValues(currentTheme)
    }

    // load all shows
    // loadShows(Object.keys(get(shows)))

    loaded.set(true)

    window.api.send("LOADED")
}

// pre v1.6.1
function convertTriggersToActions(data: any) {
    const triggers: { [key: string]: { name: string; type: "http"; value: string } } = data.triggers || {}
    if (!Object.keys(triggers).length) return data

    let tagId = "triggertag"
    if (typeof data.actionTags === "object") {
        data.actionTags[tagId] = { name: "Triggers", color: "#abb4e6" }

        // update store as this is non-synced settings
        setTimeout(() => {
            special.update((a) => {
                a["actions_grid" + tagId] = true
                return a
            })
        }, 1000)
    }

    const actions = data.midiIn || {}
    Object.entries(triggers).forEach(([key, trigger]) => {
        let emitterId = uid()
        data.emitters[emitterId] = { name: "Trigger: " + trigger.name, type: "http", signal: { url: trigger.value, method: "GET", contentType: "", payload: "" } }
        let triggerId = "emit_action:" + uid(5)

        actions[key] = {
            name: trigger.name,
            triggers: [triggerId],
            actionValues: { [triggerId]: { emitter: emitterId } },
            tags: [tagId]
        }
    })

    delete data.triggers
    data.midiIn = actions
    return data
}

let videoDataUpdating = false
export function restartOutputs(specificId = "") {
    const data = clone(get(videosData))
    const time = clone(get(videosTime))

    const allOutputs = keysToID(get(outputs))
    const outputIds = specificId ? [specificId] : allOutputs.filter((a) => a.enabled).map(({ id }) => id)

    outputIds.forEach((id: string) => {
        const output: Output = get(outputs)[id]
        if (!output) return

        // , rate: get(special).previewRate || "auto"
        send(OUTPUT, ["CREATE"], { ...output, id })
    })

    if (videoDataUpdating) return
    videoDataUpdating = true

    // restore output video data when recreating window
    // WIP values are empty when sent
    setTimeout(() => {
        send(OUTPUT, ["DATA"], data)
        send(OUTPUT, ["TIME"], time)
        videoDataUpdating = false
    }, 2200)
}

export function updateThemeValues(themeValues: Themes) {
    if (!themeValues?.colors) return

    Object.entries(themeValues.colors || {}).forEach(([key, value]) => document.documentElement.style.setProperty("--" + key, value))
    Object.entries(themeValues.font || {}).forEach(([key, value]) => {
        if (key === "family" && (!value || value === "sans-serif")) value = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif'
        document.documentElement.style.setProperty("--font-" + key, value)
    })

    // // border radius
    // if (!themeValues.border) themeValues.border = {}
    // // set to 0 if nothing is set
    // if (themeValues.border?.radius === undefined) themeValues.border.radius = "0"
    // Object.entries(themeValues.border).forEach(([key, value]) => document.documentElement.style.setProperty("--border-" + key, value))
}

const updateList: { [key in SaveListSettings | SaveListSyncedSettings]: any } = {
    initialized: (v: any) => {
        if (!v) {
            // FIRST TIME USER
            activePopup.set("initialize")
        }
    },
    activeProject: (v: any) => {
        activeProject.set(v)
        if (v) projectView.set(false)
    },
    showsPath: (v: any) => {
        if (!v) return

        // DEPRECATED (keep for backward compatibility)
        showsPath.set(v)
    },
    dataPath: (v: any) => {
        if (!v) return

        // DEPRECATED (keep for backward compatibility)
        dataPath.set(v)
    },
    lockedOverlays: (v: any) => {
        // only get locked overlays
        v = v.filter((id) => get(overlays)[id]?.locked === true)

        lockedOverlays.set(v)

        // start overlays
        if (v.length) setOutput("overlays", v, false, "", true)
    },
    language: (v: any) => {
        language.set(v)
        setLanguage(v)
    },
    alertUpdates: (v: any) => {
        alertUpdates.set(v !== false)
        // make sure "special" is set before checking
        setTimeout(() => checkForUpdates(get(version)), 50)
    },
    autoOutput: (v: any) => autoOutput.set(v),
    maxConnections: (v: any) => maxConnections.set(v),
    ports: (v: any) => ports.set(v),
    disabledServers: (v: any) => disabledServers.set(v),
    serverData: (v: any) => serverData.set(v),
    autosave: (v: any) => {
        autosave.set(v)
        startAutosave()
    },
    timeFormat: (v: any) => timeFormat.set(v),
    outputs: (v: any) => {
        Object.keys(v).forEach((id: string) => {
            delete v[id].out
            if (typeof v[id]?.color === "string" && v[id].color.toLowerCase() === "#f0008c") {
                v[id].color = "#0EA5E9"
            }
        })
        outputs.set(v)
    },
    sorted: (v: any) => sorted.set(v),
    styles: (v: any) => {
        // convert settings (<= v1.5.7)
        Object.values(v).forEach((style: any) => {
            const metadata: Metadata = {}
            if (style.displayMetadata) metadata.display = style.displayMetadata
            if (style.metadataTemplate) metadata.template = style.metadataTemplate
            if (Object.keys(metadata).length) style.metadata = metadata
            delete style.metadataDivider
            delete style.displayMetadata
            delete style.metadataTemplate
            delete style.messageTemplate
        })

        styles.set(v)
    },
    profiles: (v: any) => profiles.set(v),
    remotePassword: (v: any) => remotePassword.set(v),
    audioFolders: (v: any) => audioFolders.set(v),
    categories: (v: any) => categories.set(v),
    drawer: (v: any) => drawer.set(v),
    drawerTabsData: (v: any) => drawerTabsData.set(v),
    drawSettings: (v: any) => drawSettings.set(v),
    groupNumbers: (v: any) => groupNumbers.set(v),
    fullColors: (v: any) => fullColors.set(v),
    formatNewShow: (v: any) => formatNewShow.set(v),
    groups: (v: any) => {
        if (v && typeof v === "object") {
            const legacyGroupMap: Record<string, { old: string; next: string }> = {
                break: { old: "#f5255e", next: "#ef4444" },
                bridge: { old: "#f52598", next: "#3b82f6" },
                chorus: { old: "#f525d2", next: "#f59e0b" },
                intro: { old: "#d525f5", next: "#14b8a6" },
                outro: { old: "#a525f5", next: "#6366f1" },
                pre_chorus: { old: "#8825f5", next: "#06b6d4" },
                tag: { old: "#7525f5", next: "#eab308" },
                verse: { old: "#5825f5", next: "#0ea5e9" }
            }
            Object.entries(legacyGroupMap).forEach(([key, { old, next }]) => {
                if (v[key]?.default && typeof v[key]?.color === "string" && v[key].color.toLowerCase() === old) {
                    v[key].color = next
                }
            })
        }
        groups.set(v)
    },
    labelsDisabled: (v: any) => labelsDisabled.set(v),
    mediaFolders: (v: any) => mediaFolders.set(v),
    mediaOptions: (v: any) => mediaOptions.set(v),
    openedFolders: (v: any) => openedFolders.set(v),
    outLocked: (v: any) => outLocked.set(v),
    overlayCategories: (v: any) => overlayCategories.set(v),
    playerVideos: (v: any) => playerVideos.set(v),
    resized: (v: any) => resized.set(v),
    scriptures: (v: any) => {
        const sanitized = sanitizeAllowedScriptures(v)
        scriptures.set(sanitized)
        drawerTabsData.update((tabs) => {
            const current = tabs.scripture?.activeSubTab || ""
            if (current && !sanitized[current]) {
                const nepaliKey = Object.entries(sanitized).find(([, entry]: any) => !entry?.api && /nepali|नेपाली/i.test(String(entry?.name || "")))?.[0]
                const nextKey = nepaliKey || Object.keys(sanitized)[0] || ""
                if (nextKey) {
                    tabs.scripture = { ...(tabs.scripture || { enabled: true }), activeSubTab: nextKey }
                }
            }
            return tabs
        })
    },
    scriptureSettings: (v: any) => scriptureSettings.set(v),
    slidesOptions: (v: any) => slidesOptions.set(v),
    splitLines: (v: any) => splitLines.set(v),
    templateCategories: (v: any) => templateCategories.set(v),
    timers: (v: any) => timers.set(v),
    variables: (v: any) => variables.set(v),
    audioStreams: (v: any) => audioStreams.set(v),
    audioPlaylists: (v: any) => audioPlaylists.set(v),
    theme: (v: any) => theme.set(v),
    transitionData: (v: any) => transitionData.set(v),
    volume: (v: any) => volume.set(v),
    gain: (v: any) => gain.set(v),
    audioChannelsData: (v: any) => audioChannelsData.set(v),
    emitters: (v: any) => emitters.set(v),
    midiIn: (v: any) => actions.set(v),
    videoMarkers: (v: any) => videoMarkers.set(v),
    mediaTags: (v: any) => mediaTags.set(v),
    playerTags: (v: any) => playerTags.set(v),
    actionTags: (v: any) => actionTags.set(v),
    variableTags: (v: any) => variableTags.set(v),
    timerTags: (v: any) => timerTags.set(v),
    customizedIcons: (v: any) => customizedIcons.set(v),
    cloudSyncData: (v: any) => cloudSyncData.set(v),
    driveData: (v: any) => driveData.set(v),
    calendarAddShow: (v: any) => calendarAddShow.set(v),
    metronome: (v: any) => metronome.set(v),
    audioEffects: (v: any) => audioEffects.set(v),
    eqPresets: (v: any) => eqPresets.set(v),
    effectsLibrary: (v: any) => effectsLibrary.set(v),
    globalTags: (v: any) => globalTags.set(v),
    globalRegexes: (v: any) => globalRegexes.set(v),
    customMetadata: (v: any) => customMetadata.set(v),
    companion: (v: any) => {
        const hasStrongToken = typeof v?.token === "string" && v.token.trim().length >= 16
        const shouldAutoEnable = v?.enabled === true && hasStrongToken
        companion.set({ ...v, enabled: shouldAutoEnable })
    },
    special: (v: any) => {
        if (!v || typeof v !== "object") v = {}
        if (v.autoUpdates === undefined) v.autoUpdates = true
        if (v.capitalize_words === undefined) v.capitalize_words = "Jesus, Lord" // God
        // don't backup when just initialized (or reset)
        if (!v.autoBackupPrevious) v.autoBackupPrevious = Date.now()
        if (v.startupProjectsList) {
            // skip the "Recently used" list, and open "all projects"
            // let "activeProject" setting update first
            setTimeout(() => projectView.set(true))
            showRecentlyUsedProjects.set(false)
        }

        // DEPRECATED (migrate)
        if (v.pcoLocalAlways) {
            contentProviderData.update((a) => ({ ...a, planningcenter: { localAlways: true } }))
            delete v.pcoLocalAlways
        }

        // DEPRECATED (migrate)
        v.customUserDataLocation = true

        // DEPRECATED (migrate)
        let deletedDefaultsValue = get(deletedDefaults)
        if (v.deletedTemplates) {
            deletedDefaultsValue.templates = v.deletedTemplates
            delete v.deletedTemplates
        }
        if (v.deletedOverlays) {
            deletedDefaultsValue.overlays = v.deletedOverlays
            delete v.deletedOverlays
        }
        if (v.deletedEffects) {
            deletedDefaultsValue.effects = v.deletedEffects
            delete v.deletedEffects
        }
        if (Object.keys(deletedDefaultsValue).length) deletedDefaults.set(deletedDefaultsValue)

        special.set(v)
    },
    timeline: (v: any) => timeline.set(v),
    timecode: (v: any) => timecode.set(v),
    // @ts-ignore - DEPERACTED (migrate)
    chumsSyncCategories: (v: any) => {
        if (v?.length > 1) contentProviderData.set({ ...get(contentProviderData), churchApps: { syncCategories: v } })
    },
    contentProviderData: (v: any) => contentProviderData.set(v),
    obsData: (v: any) => obsData.set(v),
    effects: (a: any) => effects.set(a),
    deletedDefaults: (a: any) => deletedDefaults.set({ ...get(deletedDefaults), ...a })
}
