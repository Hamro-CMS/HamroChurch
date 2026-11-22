import { get } from "svelte/store"
import { uid } from "uid"
import { OUTPUT, STARTUP } from "../../types/Channels"
import { Main } from "../../types/IPC/Main"
import { checkStartupActions } from "../components/actions/actions"
import { setupMobileRemoteLiveSync } from "../components/actions/mobileRemoteApi"
import { getTimeFromInterval } from "../components/helpers/time"
import { requestMain, requestMainMultiple, sendMain, sendMainMultiple } from "../IPC/main"
import { cameraManager } from "../media/cameraManager"
import { activePopup, cachePath, companion, currentWindow, deviceId, drawerTabsData, isDev, loaded, loadedState, os, ports, scriptures, special, version, windowState } from "../stores"
import { startTracking } from "./analytics"
import { wait, waitUntilValueIsDefined } from "./common"
import { getDefaultElements } from "./createData"
import { setLanguage } from "./language"
import { storeSubscriber } from "./listeners"
import { autoOpenLastUsedProfile, openProfileByName } from "./profile"
import { receiveOUTPUTasOUTPUT, setupMainReceivers } from "./receivers"
import { destroy, receive, send } from "./request"
import { save, unsavedUpdater } from "./save"

let initialized = false
let startupProfile = ""

export async function startup() {
    if (!window.api) {
        // wait for window.api to be available (preload script might not be ready yet) - not likely
        await waitUntilValueIsDefined(() => window.api, 20, 5000)
        if (!window.api) return console.error("window.api is not available after waiting")
    }

    window.api.receive(
        STARTUP,
        (msg) => {
            if (initialized || msg.channel !== "TYPE") return
            initialized = true // only call this once per window
            destroy(STARTUP, "startup")

            const type = msg.data
            currentWindow.set(type)

            if (type) loaded.set(true)

            if (type === "pdf") return
            if (type === "output") {
                startupOutput()
                return
            }

            startupProfile = msg.autoProfile

            startupMain()
        },
        "startup"
    )
}

async function startupMain() {
    setLanguage("", true)
    setupMainReceivers()
    getMainData()

    await wait(50)
    getStoredData()

    await waitUntilValueIsDefined(() => get(loaded), 50, 8000)

    if (startupProfile) openProfileByName(startupProfile)
    else autoOpenLastUsedProfile()

    storeSubscriber()
    setupMobileRemoteLiveSync()
    const companionCfg = get(companion)
    if (companionCfg?.enabled === true && typeof companionCfg.token === "string" && companionCfg.token.trim().length >= 16) {
        sendMain(Main.WEBSOCKET_START, {
            port: get(ports).companion || 5505,
            allowLan: companionCfg.allowLan === true,
            token: companionCfg.token.trim()
        })
    }
    await ensureBundledNepaliBibleRegistered()
    checkStartupActions()
    startTracking()
    void maybePromptAnalyticsConsent()

    // custom alert
    // if (Math.random() < 0.01) {
    //     alertMessage.set("")
    //     activePopup.set("alert")
    // }

    await wait(2000)
    autoBackup()
    await wait(3000)
    unsavedUpdater()
    cameraManager.initializeCameraWarming()

    // CHECK LISTENERS
    // console.log(window.api.getListeners())

    // RAM MONITOR (every 10 minutes)
    setTimeout(() => checkRamUsage(), 10000)
    setInterval(() => checkRamUsage(), 600000)
}

async function maybePromptAnalyticsConsent() {
    // Wait until SETTINGS store has arrived so first-run "initialize" popup (if any) opens first rather than clobbering analytics_consent
    await waitUntilValueIsDefined(() => get(loadedState).includes("settings"), 100, 6000)
    await wait(200)

    const privacy = await requestMain(Main.GET_PRIVACY_SETTINGS)
    if (!privacy || privacy.consent !== null) return

    if (!get(activePopup)) {
        activePopup.set("analytics_consent")
        return
    }

    const unsubscribe = activePopup.subscribe((current) => {
        if (current === null) {
            unsubscribe()
            setTimeout(() => {
                if (!get(activePopup)) activePopup.set("analytics_consent")
            }, 150)
        }
    })
}

async function ensureBundledNepaliBibleRegistered() {
    await waitUntilValueIsDefined(() => get(loadedState).includes("settings"), 80, 5000)
    const localBibles = (await requestMain(Main.READ_BIBLES_FOLDER)) || []
    const nepaliBible = localBibles.find((bible) => bible.name === "Nepali Bible")

    const sanitized = sanitizeAllowedScriptures(get(scriptures))
    let nepaliKey = Object.entries(sanitized).find(([, value]: any) => !value?.api && value?.name?.replace(/\.fsb$/i, "") === "Nepali Bible")?.[0] || ""

    if (!nepaliKey && nepaliBible) {
        nepaliKey = uid()
        sanitized[nepaliKey] = { name: nepaliBible.name, id: nepaliKey }
    }

    scriptures.set(sanitized)

    const fallbackKey = nepaliKey || Object.keys(sanitized)[0] || ""
    if (fallbackKey) {
        drawerTabsData.update((tabs) => {
            const currentActive = tabs.scripture?.activeSubTab || ""
            const validActive = currentActive && sanitized[currentActive] ? currentActive : fallbackKey
            tabs.scripture = { ...(tabs.scripture || { enabled: true }), activeSubTab: validActive }
            return tabs
        })
    }
}

async function checkRamUsage() {
    const ram = await requestMain(Main.CHECK_RAM_USAGE)
    if (ram && (ram.performanceMode ? ram.performanceMode !== get(special).optimizedMode : get(special).optimizedMode)) special.set({ ...get(special), optimizedMode: ram.performanceMode })
}

function autoBackup() {
    const interval = get(special).autoBackup || "weekly"
    if (interval === "never" || get(activePopup) === "initialize") return

    const now = Date.now()
    const lastBackup = get(special).autoBackupPrevious || 0
    const minTimeToBackup = getTimeFromInterval(interval)

    if (now - lastBackup > minTimeToBackup) {
        special.update((a) => {
            // subtract one hour from time to keep it relatively the same with each backup
            a.autoBackupPrevious = now - 3600000
            return a
        })

        // 20% chance of backing up all shows as well (just in case)
        save(false, { backup: true, isAutoBackup: true, backupShows: Math.random() < 0.2 })
    }
}

export function contentProviderSync() {
    // External content providers and cloud sync are disabled in Aradhana Studio.
}

function getMainData() {
    requestMainMultiple({
        [Main.VERSION]: (a) => (a ? version.set(a) : null),
        [Main.IS_DEV]: (a) => isDev.set(a || false),
        [Main.GET_OS]: (a) => (a ? os.set(a) : null),
        [Main.GET_CACHE_PATH]: (a) => cachePath.set(a || ""),
        [Main.DEVICE_ID]: (a) => deviceId.set(a || ""),
        [Main.MAXIMIZED]: (a) => windowState.set({ ...get(windowState), maximized: a ?? false })
    })
}

async function getStoredData() {
    sendMainMultiple([Main.SYNCED_SETTINGS, Main.STAGE, Main.PROJECTS, Main.OVERLAYS, Main.TEMPLATES, Main.EVENTS, Main.MEDIA, Main.THEMES, Main.DRIVE_API_KEY, Main.HISTORY, Main.CACHE, Main.USAGE])

    await waitUntilValueIsDefined(() => get(loadedState).includes("synced_settings"), 200, 8000)
    sendMain(Main.SETTINGS)

    // LOAD SHOWS FROM FOLDER
    sendMain(Main.SHOWS)

    getDefaultElements()
}

async function startupOutput() {
    setLanguage() // this is only needed for the context menu (and stage display)
    receive(OUTPUT, receiveOUTPUTasOUTPUT)

    // wait a bit on slow computers
    await wait(200)

    send(OUTPUT, ["REQUEST_DATA_MAIN"])
}
