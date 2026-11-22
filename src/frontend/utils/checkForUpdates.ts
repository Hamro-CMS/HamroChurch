import { get } from "svelte/store"
import { Main } from "../../types/IPC/Main"
import type { AppUpdateDownloadResult, AppUpdateInfo } from "../../types/Main"
import { requestMain, sendMain } from "../IPC/main"
import { activePopup, alertUpdates, isDev, language, outputs, popupData, special, version } from "./../stores"
import { newToast } from "./common"

const RELEASES_PAGE_URL = "https://hamrocms.com/download"
const BACKGROUND_CHECK_INTERVAL_MS = 20 * 60 * 1000

let autoInstallInFlight = false
let backgroundTimerStarted = false
let pendingOutputUnsubscribe: (() => void) | null = null

export function isAutoUpdateActive(): boolean {
    return autoInstallInFlight
}

function isLiveProjectionActive(): boolean {
    const allOutputs = get(outputs) || {}
    return Object.values(allOutputs).some((out: any) => !out?.stageOutput && out?.enabled !== false && Boolean(out?.out?.slide || out?.out?.background || out?.out?.overlays?.length))
}

export function cleanReleaseNotes(rawNotes: unknown): string {
    if (typeof rawNotes !== "string" || !rawNotes.trim()) return ""
    return rawNotes
        .split(/\r?\n/)
        .filter((line) => {
            const lower = line.trim().toLowerCase()
            if (!lower) return true
            if (lower.includes("full changelog") || lower.includes("github.com")) return false
            if (/^##\s*what'?s changed/i.test(lower)) return false
            return true
        })
        .map((line) =>
            line
                .replace(/\s+by\s+@[A-Za-z0-9_-]+\s+in\s+https?:\/\/[^\s)]+/gi, "")
                .replace(/https?:\/\/(?:www\.)?github\.com[^\s)]*/gi, "")
                .replace(/\*\*([^*]+)\*\*/g, "$1")
                .trimEnd()
        )
        .join("\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim()
}

function includeBetaUpdates() {
    const currentVersion = get(version)
    return currentVersion.includes("-beta") || get(special).betaVersionAlert
}

function fallbackResult(error?: string): AppUpdateInfo {
    const currentVersion = get(version)

    return {
        checked: true,
        hasUpdate: false,
        currentVersion,
        latestVersion: currentVersion,
        releaseNotes: "",
        releaseUrl: RELEASES_PAGE_URL,
        error
    }
}

export async function getUpdateData(manual = false): Promise<AppUpdateInfo> {
    const result = await requestMain(Main.CHECK_FOR_UPDATES, { manual, includeBeta: includeBetaUpdates() }, undefined, manual ? 30000 : 20000)
    return result || fallbackResult("Timed out while checking for updates.")
}

export async function downloadAndInstallUpdate(): Promise<AppUpdateDownloadResult> {
    if (autoInstallInFlight) {
        return { success: true, launched: true }
    }
    autoInstallInFlight = true
    try {
        const result = await requestMain(Main.DOWNLOAD_UPDATE, { includeBeta: includeBetaUpdates() }, undefined, 10 * 60 * 1000)
        return result || { success: false, launched: false, error: "Timed out while downloading the update." }
    } finally {
        autoInstallInFlight = false
    }
}

export function openReleasePage(url?: string) {
    sendMain(Main.URL, url || RELEASES_PAGE_URL)
}

async function performSilentAutoInstall(updateData: AppUpdateInfo) {
    if (autoInstallInFlight || !updateData.hasUpdate || !updateData.asset) return

    const isNe = get(language) === "ne"
    newToast(
        isNe
            ? `नयाँ संस्करण v${updateData.latestVersion} स्वचालित रूपमा डाउनलोड र इन्स्टल हुँदैछ...`
            : `Downloading and installing update v${updateData.latestVersion} automatically...`
    )

    const result = await downloadAndInstallUpdate()
    if (!result.success && get(alertUpdates) !== false && get(activePopup) === null) {
        popupData.set(updateData)
        activePopup.set("new_update")
    }
}

export function checkForUpdates(_currentVersion?: string) {
    if (get(isDev)) return

    const autoEnabled = get(special).autoUpdates !== false
    const alertEnabled = get(alertUpdates) !== false
    if (!autoEnabled && !alertEnabled) return

    if (!backgroundTimerStarted) {
        backgroundTimerStarted = true
        setInterval(() => {
            checkForUpdates(get(version))
        }, BACKGROUND_CHECK_INTERVAL_MS)
    }

    getUpdateData(false)
        .then((updateData) => {
            if (updateData.error) {
                console.warn(updateData.error)
                return
            }

            if (!updateData.hasUpdate) return

            if (get(special).autoUpdates !== false && updateData.asset) {
                if (!isLiveProjectionActive()) {
                    void performSilentAutoInstall(updateData)
                } else if (!pendingOutputUnsubscribe) {
                    // Defer auto-restart until live projection screen is cleared so worship is never interrupted
                    pendingOutputUnsubscribe = outputs.subscribe(() => {
                        if (!isLiveProjectionActive()) {
                            if (pendingOutputUnsubscribe) {
                                pendingOutputUnsubscribe()
                                pendingOutputUnsubscribe = null
                            }
                            if (get(special).autoUpdates !== false) {
                                void performSilentAutoInstall(updateData)
                            }
                        }
                    })
                }
                return
            }

            if (get(alertUpdates) !== false && get(activePopup) === null) {
                popupData.set(updateData)
                activePopup.set("new_update")
            }
        })
        .catch((error) => {
            console.warn(error)
        })
}

export function notifyManualUpdateResult(updateData: AppUpdateInfo) {
    if (updateData.error) {
        newToast("updates.unable_to_check")
        return
    }

    if (!updateData.hasUpdate) newToast("updates.latest")
}
