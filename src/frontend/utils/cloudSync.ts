import { get } from "svelte/store"
import { Main } from "../../types/IPC/Main"
import { isLocalFile } from "../components/helpers/media"
import { sendMain } from "../IPC/main"
import { cloudSyncData, cloudUsers, special } from "../stores"
import { isMainWindow } from "./common"

// Cloud sync and ChurchApps team presence are disabled in Hamro Church.

export async function setupCloudSync(_auto: boolean = false) {
    return
}

export async function changeTeam() {
    return
}

export async function chooseTeam(_team: { id: string; churchId: string; name: string; count?: number }) {
    return
}

export async function syncWithCloud(_initialize: boolean = false, _isClosing: boolean = false) {
    return false
}

// copy media files to one media sync folder for easy syncing (local folder only)
export function addToMediaFolder(filePath: string) {
    if (get(cloudSyncData).enabled === false || !get(cloudSyncData).id || !get(special).cloudSyncMediaFolder) return
    if (!isLocalFile(filePath)) return
    if (filePath.includes("freeshow-cache") || filePath.includes("media-cache")) return
    if (!isMainWindow()) return

    sendMain(Main.MEDIA_FOLDER_COPY, { paths: [filePath] })
}

export async function socketDisconnect() {
    cloudUsers.set([])
}

export function getCloudUsers(_updater = get(cloudUsers)): any[] {
    return []
}

export function isActiveShowInUseByCloudUser(_updater: any = null) {
    return false
}

export async function cloudSyncMessage(_id: string = "", _data: { [key: string]: any } = {}) {
    return
}
