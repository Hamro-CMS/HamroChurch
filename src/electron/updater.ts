import { spawn } from "child_process"
import { createHash, timingSafeEqual } from "crypto"
import { app, shell } from "electron"
import axios from "axios"
import fs from "fs"
import os from "os"
import path from "path"
import { Transform } from "stream"
import { pipeline } from "stream/promises"
import type { AppUpdateAsset, AppUpdateDownloadResult, AppUpdateInfo } from "../types/Main"
import { config } from "./data/store"

const RELEASES_LATEST_URL = "https://api.github.com/repos/Hamro-CMS/HamroChurch/releases/latest"
const RELEASES_PAGE_URL = "https://github.com/Hamro-CMS/HamroChurch/releases"
const RELEASE_DOWNLOAD_BASE_URL = "https://github.com/Hamro-CMS/HamroChurch/releases/download"
const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000
const ALLOWED_UPDATE_EXTENSIONS = new Set([".exe", ".dmg", ".zip", ".appimage", ".deb", ".rpm"])
const SAFE_TAG_RE = /^[A-Za-z0-9._+-]+$/

function isTrustedGitHubAssetUrl(rawUrl: unknown): boolean {
    if (typeof rawUrl !== "string" || !rawUrl.trim()) return false

    try {
        const parsed = new URL(rawUrl.trim())
        if (parsed.protocol !== "https:") return false

        const host = parsed.hostname.toLowerCase()
        if (host === "objects.githubusercontent.com" || host === "release-assets.githubusercontent.com") {
            return true
        }

        if (host === "github.com") {
            return parsed.pathname.toLowerCase().startsWith("/hamro-cms/hamrochurch/")
        }

        return false
    } catch {
        return false
    }
}

function sanitizeReleasePageUrl(rawUrl: unknown): string {
    if (typeof rawUrl !== "string" || !rawUrl.trim()) return RELEASES_PAGE_URL

    try {
        const parsed = new URL(rawUrl.trim())
        if (parsed.protocol === "https:" && parsed.hostname.toLowerCase() === "github.com" && parsed.pathname.toLowerCase().startsWith("/hamro-cms/hamrochurch")) {
            return parsed.toString()
        }
    } catch {
        // Fall through to official releases page
    }

    return RELEASES_PAGE_URL
}

type GitHubRelease = {
    tag_name?: string
    body?: string
    html_url?: string
    prerelease?: boolean
    published_at?: string
    assets?: Array<{
        name?: string
        browser_download_url?: string
        content_type?: string
        digest?: string | null
    }>
}

let cachedUpdateInfo: AppUpdateInfo | null = null

export function initializeUpdater() {
    // Updates download into isolated per-run temporary directories via fs.mkdtempSync
}

function normalizeVersion(value: string) {
    return String(value || "")
        .trim()
        .replace(/^v/i, "")
}

function parseSemver(value: string) {
    const normalized = normalizeVersion(value)
    const [core, prerelease = ""] = normalized.split("-")
    const [major = "0", minor = "0", patch = "0"] = core.split(".")

    return {
        major: Number(major) || 0,
        minor: Number(minor) || 0,
        patch: Number(patch) || 0,
        prerelease
    }
}

function compareVersions(left: string, right: string) {
    const a = parseSemver(left)
    const b = parseSemver(right)

    if (a.major !== b.major) return a.major - b.major
    if (a.minor !== b.minor) return a.minor - b.minor
    if (a.patch !== b.patch) return a.patch - b.patch

    if (!a.prerelease && b.prerelease) return 1
    if (a.prerelease && !b.prerelease) return -1
    return a.prerelease.localeCompare(b.prerelease)
}

function githubHeaders() {
    return {
        Accept: "application/vnd.github+json",
        "User-Agent": `Aradhana Studio/${app.getVersion()}`
    }
}

function scoreAsset(assetName: string) {
    const name = assetName.toLowerCase()
    let score = 0

    if (process.arch === "x64") {
        if (name.includes("x64") || name.includes("amd64")) score += 20
        if (name.includes("arm64") || name.includes("aarch64")) score -= 20
    } else if (process.arch === "arm64") {
        if (name.includes("arm64") || name.includes("aarch64")) score += 20
        if (name.includes("x64") || name.includes("amd64")) score -= 20
    }

    if (process.platform === "win32" && /\.exe$/i.test(name)) score += 10
    if (process.platform === "darwin" && /\.dmg$/i.test(name)) score += 10
    if (process.platform === "linux" && /\.appimage$/i.test(name)) score += 10

    return score
}

function selectAsset(release: GitHubRelease): AppUpdateAsset | null {
    const assets = Array.isArray(release.assets) ? release.assets : []
    const platformMatchers = process.platform === "win32" ? [/\.exe$/i] : process.platform === "darwin" ? [/\.dmg$/i, /\.zip$/i] : [/\.AppImage$/i, /\.deb$/i, /\.rpm$/i]

    const matchingAssets = assets.filter((asset) => {
        const name = String(asset.name || "")
        return platformMatchers.some((matcher) => matcher.test(name))
    })

    if (!matchingAssets.length) return null

    const asset = matchingAssets.sort((left, right) => scoreAsset(String(right.name || "")) - scoreAsset(String(left.name || "")))[0]
    if (!asset?.name || !asset.browser_download_url || !isTrustedGitHubAssetUrl(asset.browser_download_url)) return null

    return {
        name: path.basename(asset.name),
        url: asset.browser_download_url,
        contentType: asset.content_type,
        digest: asset.digest || null,
        tagName: typeof release.tag_name === "string" ? release.tag_name.trim() : undefined
    }
}

async function fetchLatestRelease() {
    const response = await axios.get<GitHubRelease>(RELEASES_LATEST_URL, {
        timeout: 15000,
        headers: githubHeaders(),
        validateStatus: (status) => status >= 200 && status < 300
    })

    return response.data
}

function createBaseResult(currentVersion: string, checkedAt: number): AppUpdateInfo {
    return {
        checked: true,
        hasUpdate: false,
        currentVersion,
        latestVersion: currentVersion,
        releaseNotes: "",
        releaseUrl: RELEASES_PAGE_URL,
        checkedAt
    }
}

export async function checkForUpdates(options: { manual?: boolean; includeBeta?: boolean } = {}): Promise<AppUpdateInfo> {
    const manual = options.manual === true
    const includeBeta = options.includeBeta === true
    const currentVersion = normalizeVersion(app.getVersion())
    const checkedAt = Date.now()

    if (!manual && !app.isPackaged) {
        return { ...createBaseResult(currentVersion, checkedAt), checked: false, skipped: true }
    }

    const lastCheck = Number(config.get("lastUpdateCheck") || 0)
    if (!manual && lastCheck && checkedAt - lastCheck < CHECK_INTERVAL_MS && cachedUpdateInfo) {
        return { ...cachedUpdateInfo, checked: false, skipped: true }
    }

    config.set("lastUpdateCheck", checkedAt)

    try {
        const release = await fetchLatestRelease()
        const latestVersion = normalizeVersion(release.tag_name || currentVersion)
        const isPrerelease = Boolean(release.prerelease || parseSemver(latestVersion).prerelease)
        const hasUpdate = compareVersions(latestVersion, currentVersion) > 0 && (!isPrerelease || includeBeta)

        const result: AppUpdateInfo = {
            checked: true,
            hasUpdate,
            currentVersion,
            latestVersion,
            releaseNotes: String(release.body || ""),
            releaseUrl: sanitizeReleasePageUrl(release.html_url),
            publishedAt: release.published_at,
            asset: hasUpdate ? selectAsset(release) : null,
            checkedAt
        }

        cachedUpdateInfo = result
        return result
    } catch (error) {
        const result: AppUpdateInfo = {
            ...createBaseResult(currentVersion, checkedAt),
            error: error instanceof Error ? error.message : String(error)
        }
        cachedUpdateInfo = result
        return result
    }
}

function getExpectedSha256Buffer(asset: AppUpdateAsset | null | undefined): Buffer | null {
    const digest = String(asset?.digest || "").trim()
    if (!/^sha256:[0-9a-fA-F]{64}$/.test(digest)) return null
    return Buffer.from(digest.slice("sha256:".length), "hex")
}

function parseSha512ToBuffer(rawSha512: string): Buffer | null {
    const cleaned = rawSha512.trim().replace(/^['"]|['"]$/g, "")
    if (!cleaned) return null

    if (/^[0-9a-fA-F]{128}$/.test(cleaned)) {
        return Buffer.from(cleaned, "hex")
    }

    if (/^[A-Za-z0-9+/=]{86,90}$/.test(cleaned)) {
        const buf = Buffer.from(cleaned, "base64")
        if (buf.length === 64) return buf
    }

    return null
}

function normalizeAssetKey(fileName: string): string {
    try {
        fileName = decodeURIComponent(fileName)
    } catch {
        // ignore decode error
    }
    return path
        .basename(fileName.trim())
        .toLowerCase()
        .replace(/[\s._-]+/g, "-")
}

function extractSha512FromLatestYml(ymlContent: string, expectedFileName: string): Buffer | null {
    if (!ymlContent || typeof ymlContent !== "string") return null

    const targetExact = expectedFileName.toLowerCase()
    const targetNormalized = normalizeAssetKey(expectedFileName)

    const entries: Array<{ name: string; sha512: string }> = []
    let currentEntryName = ""
    let topLevelPath = ""
    let topLevelSha512 = ""

    for (const rawLine of ymlContent.split(/\r?\n/)) {
        const trimmed = rawLine.trim()
        if (!trimmed || trimmed.startsWith("#")) continue

        const urlMatch = trimmed.match(/^-?\s*url\s*:\s*(.+)$/)
        if (urlMatch) {
            currentEntryName = urlMatch[1].trim().replace(/^['"]|['"]$/g, "")
            continue
        }

        const entryShaMatch = rawLine.match(/^\s+sha512\s*:\s*(.+)$/)
        if (entryShaMatch && currentEntryName) {
            entries.push({
                name: currentEntryName,
                sha512: entryShaMatch[1].trim().replace(/^['"]|['"]$/g, "")
            })
            currentEntryName = ""
            continue
        }

        const topPathMatch = rawLine.match(/^path\s*:\s*(.+)$/)
        if (topPathMatch) {
            topLevelPath = topPathMatch[1].trim().replace(/^['"]|['"]$/g, "")
            continue
        }

        const topShaMatch = rawLine.match(/^sha512\s*:\s*(.+)$/)
        if (topShaMatch) {
            topLevelSha512 = topShaMatch[1].trim().replace(/^['"]|['"]$/g, "")
        }
    }

    if (topLevelPath && topLevelSha512) {
        entries.push({ name: topLevelPath, sha512: topLevelSha512 })
    }

    const matched =
        entries.find((e) => path.basename(e.name).toLowerCase() === targetExact) ||
        entries.find((e) => normalizeAssetKey(e.name) === targetNormalized)

    if (!matched) return null
    return parseSha512ToBuffer(matched.sha512)
}

function resolveReleaseTag(asset: AppUpdateAsset): string | null {
    if (asset.tagName && SAFE_TAG_RE.test(asset.tagName) && asset.tagName !== "." && asset.tagName !== "..") {
        return asset.tagName
    }

    // Fallback: extract tag from official GitHub release download URL if present
    try {
        const parsed = new URL(asset.url)
        if (parsed.protocol === "https:" && parsed.hostname.toLowerCase() === "github.com") {
            const parts = parsed.pathname.split("/").filter(Boolean)
            // /Hamro-CMS/HamroChurch/releases/download/<tag>/<file>
            if (parts.length >= 6 && parts[0].toLowerCase() === "hamro-cms" && parts[1].toLowerCase() === "hamrochurch" && parts[2] === "releases" && parts[3] === "download") {
                const decodedTag = decodeURIComponent(parts[4])
                if (SAFE_TAG_RE.test(decodedTag) && decodedTag !== "." && decodedTag !== "..") {
                    return decodedTag
                }
            }
        }
    } catch {
        // ignore
    }

    return null
}

async function fetchExpectedSha512FromReleaseYml(asset: AppUpdateAsset, safeFileName: string): Promise<Buffer | null> {
    const tag = resolveReleaseTag(asset)
    if (!tag) return null

    const ymlCandidates = process.platform === "darwin" ? ["latest-mac.yml", "latest.yml"] : process.platform === "linux" ? ["latest-linux.yml", "latest.yml"] : ["latest.yml"]

    for (const ymlName of ymlCandidates) {
        const ymlUrl = `${RELEASE_DOWNLOAD_BASE_URL}/${encodeURIComponent(tag)}/${ymlName}`
        if (!isTrustedGitHubAssetUrl(ymlUrl)) continue

        try {
            const response = await axios.get<string>(ymlUrl, {
                responseType: "text",
                timeout: 15000,
                headers: {
                    "User-Agent": `Aradhana Studio/${app.getVersion()}`
                },
                validateStatus: (status) => status >= 200 && status < 300
            })

            const finalUrl = response.request?.res?.responseUrl
            if (finalUrl && !isTrustedGitHubAssetUrl(finalUrl)) {
                throw new Error("Blocked untrusted redirect while fetching release checksum manifest.")
            }

            const parsedSha512 = extractSha512FromLatestYml(String(response.data || ""), safeFileName)
            if (parsedSha512) return parsedSha512
        } catch {
            // Try next candidate if available; fail-closed caller rejects if none succeed
        }
    }

    return null
}

async function cleanupUpdateTempDir(tempDir: string) {
    if (!tempDir) return
    try {
        await fs.promises.rm(tempDir, { recursive: true, force: true })
    } catch {
        // ignore cleanup failure
    }
}

async function downloadAsset(asset: AppUpdateAsset) {
    if (!isTrustedGitHubAssetUrl(asset.url)) {
        throw new Error("Blocked untrusted update download origin.")
    }

    const safeFileName = path.basename(String(asset.name || "").trim())
    const ext = path.extname(safeFileName).toLowerCase()
    if (!safeFileName || safeFileName === "." || safeFileName === ".." || !ALLOWED_UPDATE_EXTENSIONS.has(ext)) {
        throw new Error("Blocked invalid update installer filename.")
    }

    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "hamro-church-update-"))
    const resolvedTempRoot = path.resolve(tempDir)
    const targetPath = path.resolve(resolvedTempRoot, safeFileName)
    if (!targetPath.startsWith(resolvedTempRoot + path.sep)) {
        await cleanupUpdateTempDir(tempDir)
        throw new Error("Blocked unsafe update destination path.")
    }

    try {
        const response = await axios.get(asset.url, {
            responseType: "stream",
            timeout: 120000,
            headers: githubHeaders(),
            validateStatus: (status) => status >= 200 && status < 300
        })

        const finalAssetUrl = response.request?.res?.responseUrl
        if (finalAssetUrl && !isTrustedGitHubAssetUrl(finalAssetUrl)) {
            throw new Error("Blocked untrusted redirect during update download.")
        }

        const sha256Hash = createHash("sha256")
        const sha512Hash = createHash("sha512")
        const hashStream = new Transform({
            transform(chunk, _encoding, callback) {
                sha256Hash.update(chunk)
                sha512Hash.update(chunk)
                callback(null, chunk)
            }
        })

        await pipeline(response.data, hashStream, fs.createWriteStream(targetPath, { mode: 0o600 }))

        const actualSha256Buf = sha256Hash.digest()
        const actualSha512Buf = sha512Hash.digest()

        const expectedSha256Buf = getExpectedSha256Buffer(asset)
        if (expectedSha256Buf) {
            if (actualSha256Buf.length !== expectedSha256Buf.length || !timingSafeEqual(actualSha256Buf, expectedSha256Buf)) {
                throw new Error("Downloaded update file failed SHA-256 integrity validation.")
            }
        } else {
            const expectedSha512Buf = await fetchExpectedSha512FromReleaseYml(asset, safeFileName)
            if (!expectedSha512Buf) {
                throw new Error("Update integrity verification failed: neither GitHub asset digest nor official release latest.yml SHA-512 checksum could be verified.")
            }
            if (actualSha512Buf.length !== expectedSha512Buf.length || !timingSafeEqual(actualSha512Buf, expectedSha512Buf)) {
                throw new Error("Downloaded update file failed SHA-512 integrity validation against latest.yml.")
            }
        }

        return targetPath
    } catch (error) {
        await cleanupUpdateTempDir(tempDir)
        throw error
    }
}

async function launchDetachedExecutable(command: string, args: string[] = []) {
    await new Promise<void>((resolve, reject) => {
        const child = spawn(command, args, { detached: true, stdio: "ignore", windowsHide: true })
        let settled = false

        const finish = (error?: Error) => {
            if (settled) return
            settled = true
            if (error) reject(error)
            else resolve()
        }

        child.once("error", finish)
        child.unref()
        setTimeout(() => finish(), 500)
    })
}

async function launchInstaller(filePath: string) {
    const extension = path.extname(filePath).toLowerCase()

    if (process.platform === "win32") {
        // Prefer shell launch so elevation/UAC prompts are handled by Windows.
        const openResult = await shell.openPath(filePath)
        if (openResult) {
            await launchDetachedExecutable(filePath)
        }

        setTimeout(() => app.quit(), 1000)
        return
    }

    if (process.platform === "darwin") {
        const child = spawn("open", [filePath], { detached: true, stdio: "ignore" })
        child.unref()
        setTimeout(() => app.quit(), 1000)
        return
    }

    if (process.platform === "linux" && extension === ".appimage") {
        await fs.promises.chmod(filePath, 0o755)
        const child = spawn(filePath, [], { detached: true, stdio: "ignore" })
        child.unref()
        setTimeout(() => app.quit(), 1000)
        return
    }

    if (process.platform === "linux" && [".deb", ".rpm"].includes(extension)) {
        const child = spawn("xdg-open", [filePath], { detached: true, stdio: "ignore" })
        child.unref()
        setTimeout(() => app.quit(), 1000)
        return
    }

    const result = await shell.openPath(filePath)
    if (result) throw new Error(result)
    setTimeout(() => app.quit(), 1000)
}

export async function downloadAndInstallUpdate(options: { includeBeta?: boolean } = {}): Promise<AppUpdateDownloadResult> {
    const current = cachedUpdateInfo?.hasUpdate ? cachedUpdateInfo : await checkForUpdates({ manual: true, includeBeta: options.includeBeta })
    if (!current.hasUpdate || !current.asset) {
        return { success: false, launched: false, error: "No downloadable update is available for this platform." }
    }

    try {
        const filePath = await downloadAsset(current.asset)
        await launchInstaller(filePath)
        return { success: true, launched: true, filePath }
    } catch (error) {
        return {
            success: false,
            launched: false,
            error: error instanceof Error ? error.message : String(error)
        }
    }
}
