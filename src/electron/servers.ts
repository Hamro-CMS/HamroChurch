import type { Main, MainSendPayloads } from "../types/IPC/Main"
import type { ServerData } from "../types/Socket"
import { unpublishPorts } from "./data/bonjour"

type ServerName = "REMOTE" | "STAGE" | "CONTROLLER" | "OUTPUT_STREAM"

const serverDataStore: { [key in ServerName]?: ServerData } = {}

// Remote/Stage/Controller/OutputStream servers are disabled in Hamro Church for now.
export function startServers(_payload: MainSendPayloads[Main.START]) {
    closeServers()
}

export function updateServerData(data: { [key: string]: ServerData }) {
    const serverList: ServerName[] = ["REMOTE", "STAGE", "CONTROLLER", "OUTPUT_STREAM"]
    serverList.forEach((id) => {
        serverDataStore[id] = data[id.toLowerCase()] || {}
    })
}

export function getServerData(id: ServerName): ServerData {
    return serverDataStore[id] || {}
}

export function closeServers() {
    unpublishPorts()
}

export function toServer(_id: ServerName, _msg: any) {
    // No-op while remote servers are disabled
}

export function getConnections(_id: ServerName) {
    return 0
}

const device: { [key: string]: RegExp } = {
    "Generic Linux": /Linux/i,
    Android: /Android/i,
    BlackBerry: /BlackBerry/i,
    Bluebird: /EF500/i,
    "Chrome OS": /CrOS/i,
    Datalogic: /DL-AXIS/i,
    Honeywell: /CT50/i,
    iPad: /iPad/i,
    iPhone: /iPhone/i,
    iPod: /iPod/i,
    macOS: /Macintosh/i,
    Windows: /IEMobile|Windows/i,
    Zebra: /TC70|TC55/i
}

export function getOS(ua: string) {
    return Object.keys(device).find((v) => ua.match(device[v])) || "Unknown"
}
