(function () {
    const apiUrl = "https://api.github.com/repos/Hamro-CMS/HamroChurch/releases/latest"
    const fallbackUrl = "https://github.com/Hamro-CMS/HamroChurch/releases"
    const downloadTrackBase = "https://hamrocms.com/api/v1/software/download/"

    const pageLinks = document.querySelectorAll("[data-release-page]")
    const windowsLinks = document.querySelectorAll("[data-release-windows]")
    const macLinks = document.querySelectorAll("[data-release-macos]")
    const linuxLinks = document.querySelectorAll("[data-release-linux]")
    const versionNodes = document.querySelectorAll("[data-release-version]")
    const summaryNodes = document.querySelectorAll("[data-release-summary]")

    if (!pageLinks.length && !windowsLinks.length && !macLinks.length && !linuxLinks.length) return

    function trackedDownloadUrl(platform, versionTag) {
        return downloadTrackBase + platform + "?source=hamrochurch-website&version=" + encodeURIComponent(versionTag || "latest")
    }

    function setLinkGroup(nodes, href) {
        nodes.forEach((node) => node.setAttribute("href", href))
    }

    function setTextGroup(nodes, value) {
        nodes.forEach((node) => {
            node.textContent = value
        })
    }

    function findAsset(assets, matcher) {
        return assets.find((asset) => matcher.test(asset.name || "")) || null
    }

    setLinkGroup(pageLinks, fallbackUrl)
    setLinkGroup(windowsLinks, trackedDownloadUrl("windows", "latest"))
    setLinkGroup(macLinks, trackedDownloadUrl("mac", "latest"))
    setLinkGroup(linuxLinks, trackedDownloadUrl("linux", "latest"))

    fetch(apiUrl, { headers: { Accept: "application/vnd.github+json" } })
        .then((response) => {
            if (!response.ok) throw new Error("Could not load the latest release")
            return response.json()
        })
        .then((release) => {
            const assets = Array.isArray(release.assets) ? release.assets : []
            const releaseUrl = release.html_url || fallbackUrl
            const version = release.tag_name || "latest"

            const windowsAsset = findAsset(assets, /\.exe$/i)
            const macAsset = findAsset(assets, /\.dmg$/i)
            const linuxAsset = findAsset(assets, /\.AppImage$/i) || findAsset(assets, /\.deb$/i)

            setLinkGroup(pageLinks, releaseUrl)
            setTextGroup(versionNodes, version)
            setTextGroup(summaryNodes, "Latest stable release is " + version + ".")

            setLinkGroup(windowsLinks, windowsAsset ? trackedDownloadUrl("windows", version) : releaseUrl)
            setLinkGroup(macLinks, macAsset ? trackedDownloadUrl("mac", version) : releaseUrl)
            setLinkGroup(linuxLinks, linuxAsset ? trackedDownloadUrl("linux", version) : releaseUrl)
        })
        .catch(() => {
            setTextGroup(summaryNodes, "Failed to fetch latest release metadata. Direct download links remain active.")
        })
})()