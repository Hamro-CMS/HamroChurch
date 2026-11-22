import { get } from "svelte/store"
import { Main } from "../../types/IPC/Main"
import type { AppUpdateDownloadResult, AppUpdateInfo } from "../../types/Main"
import { requestMain, sendMain } from "../IPC/main"
import { activePopup, alertUpdates, isDev, popupData, special, version } from "./../stores"
import { newToast } from "./common"

const RELEASES_PAGE_URL = "https://hamrocms.com/download"

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
    const result = await requestMain(Main.DOWNLOAD_UPDATE, { includeBeta: includeBetaUpdates() }, undefined, 10 * 60 * 1000)
    return result || { success: false, launched: false, error: "Timed out while downloading the update." }
}

export function openReleasePage(url?: string) {
    sendMain(Main.URL, url || RELEASES_PAGE_URL)
}

export function checkForUpdates(_currentVersion: string) {
    if (get(isDev) || get(alertUpdates) === false || get(special).autoUpdates === false) return

    getUpdateData(false)
        .then((updateData) => {
            if (updateData.error) {
                console.warn(updateData.error)
                return
            }

            if (get(activePopup) !== null || !updateData.hasUpdate) return

            popupData.set(updateData)
            activePopup.set("new_update")
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
