const ALLOWED_HTML_TAGS = new Set(["b", "i", "u", "s", "span", "p", "br", "div", "strong", "em", "ul", "ol", "li", "sub", "sup", "a"])

const DROP_SUBTREE_TAGS = new Set([
    "script",
    "style",
    "iframe",
    "frame",
    "frameset",
    "object",
    "embed",
    "applet",
    "meta",
    "link",
    "base",
    "form",
    "input",
    "button",
    "select",
    "textarea",
    "option",
    "foreignobject",
    "audio",
    "video",
    "source",
    "track",
    "portal",
    "template",
    "noscript",
    "math",
    "svg",
    "canvas",
    "img",
    "animate",
    "animatemotion",
    "animatetransform",
    "set",
    "mpath",
    "discard",
    "head",
    "title"
])

const ALLOWED_HTML_ATTRS = new Set(["class", "id", "style", "dir", "lang", "title", "data-title", "data-customtype", "data-sourcedynamickey", "data-chords"])

const ALLOWED_SVG_TAGS = new Set([
    "svg",
    "g",
    "path",
    "circle",
    "ellipse",
    "line",
    "polygon",
    "polyline",
    "rect",
    "defs",
    "lineargradient",
    "radialgradient",
    "stop",
    "clippath",
    "mask",
    "symbol",
    "title",
    "desc",
    "use"
])

const ALLOWED_SVG_ATTRS = new Set([
    "id",
    "class",
    "style",
    "xmlns",
    "version",
    "viewbox",
    "preserveaspectratio",
    "width",
    "height",
    "x",
    "y",
    "x1",
    "y1",
    "x2",
    "y2",
    "cx",
    "cy",
    "r",
    "rx",
    "ry",
    "d",
    "points",
    "transform",
    "gradientunits",
    "gradienttransform",
    "spreadmethod",
    "clippathunits",
    "maskunits",
    "maskcontentunits",
    "offset",
    "stop-color",
    "stop-opacity",
    "fill",
    "fill-opacity",
    "fill-rule",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "stroke-dasharray",
    "stroke-dashoffset",
    "stroke-opacity",
    "opacity",
    "color",
    "vector-effect",
    "clip-path",
    "clip-rule",
    "mask",
    "display",
    "visibility",
    "focusable",
    "aria-hidden",
    "role",
    "href",
    "xlink:href"
])

const SVG_REF_TAGS = new Set(["use", "lineargradient", "radialgradient"])
const LOCAL_FRAGMENT_RE = /^#[A-Za-z0-9_.:-]+$/
const SAFE_LOCAL_URL_RE = /^url\(\s*['"]?#[A-Za-z0-9_.:-]+['"]?\s*\)$/i
const DANGEROUS_CSS_RE = /expression\s*\(|javascript\s*:|vbscript\s*:|data\s*:\s*text\/html|-moz-binding|behavior\s*:|@import/i

const MAX_CACHE_ENTRIES = 1000
const htmlCache = new Map<string, string>()
const svgCache = new Map<string, string>()

export function escapeHtml(input: string): string {
    if (!input) return ""
    return String(input)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
}

function decodeCssEscapes(css: string): string {
    return css
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/\\([0-9a-fA-F]{1,6})\s?/g, (_m, hex) => {
            const code = parseInt(hex, 16)
            return Number.isFinite(code) && code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : ""
        })
        .replace(/\\(.)/g, "$1")
}

function splitCssDeclarations(styleText: string): string[] {
    const parts: string[] = []
    let current = ""
    let depth = 0
    let quote = ""

    for (let i = 0; i < styleText.length; i++) {
        const ch = styleText[i]
        if (quote) {
            current += ch
            if (ch === "\\" && i + 1 < styleText.length) {
                current += styleText[++i]
            } else if (ch === quote) {
                quote = ""
            }
            continue
        }
        if (ch === '"' || ch === "'") {
            quote = ch
            current += ch
            continue
        }
        if (ch === "(") depth++
        else if (ch === ")" && depth > 0) depth--

        if (ch === ";" && depth === 0) {
            parts.push(current)
            current = ""
        } else {
            current += ch
        }
    }
    if (current) parts.push(current)
    return parts
}

export function sanitizeCssStyle(rawStyle: string, allowLocalFragmentUrl = false): string {
    if (!rawStyle || typeof rawStyle !== "string") return ""

    const cleanedDecls: string[] = []
    const declarations = splitCssDeclarations(rawStyle)

    for (const decl of declarations) {
        const trimmed = decl.trim()
        if (!trimmed) continue

        const colonIdx = trimmed.indexOf(":")
        if (colonIdx <= 0) continue

        const prop = trimmed.slice(0, colonIdx).trim()
        const val = trimmed.slice(colonIdx + 1).trim()
        if (!prop || !val) continue

        if (!/^(--[A-Za-z0-9_-]+|-?[A-Za-z][A-Za-z0-9-]*)$/.test(prop)) continue

        const decoded = decodeCssEscapes(`${prop}:${val}`).replace(/[\x00-\x20]+/g, " ")
        if (DANGEROUS_CSS_RE.test(decoded)) continue

        if (/url\s*\(/i.test(decoded)) {
            if (!allowLocalFragmentUrl) continue
            const urlMatches = decoded.match(/url\s*\([^)]*\)/gi) || []
            if (!urlMatches.length || urlMatches.some((u) => !SAFE_LOCAL_URL_RE.test(u.trim()))) {
                continue
            }
        }

        cleanedDecls.push(`${prop}: ${val}`)
    }

    return cleanedDecls.length ? cleanedDecls.join("; ") + ";" : ""
}

function isSafeAnchorHref(rawHref: string): boolean {
    // eslint-disable-next-line no-control-regex
    const normalized = rawHref.replace(/[\x00-\x20]+/g, "")
    if (!normalized) return false
    if (normalized.startsWith("#")) return true
    return /^(https?:|mailto:)/i.test(normalized)
}

function sanitizeHtmlNode(node: Node, doc: Document) {
    const children = Array.from(node.childNodes)
    for (const child of children) {
        if (child.nodeType === Node.COMMENT_NODE) {
            node.removeChild(child)
            continue
        }

        if (child.nodeType !== Node.ELEMENT_NODE) {
            continue
        }

        const el = child as Element
        const tag = el.tagName.toLowerCase()

        if (DROP_SUBTREE_TAGS.has(tag)) {
            node.removeChild(el)
            continue
        }

        // Sanitize children first so unwrapped elements keep sanitized text/markup
        sanitizeHtmlNode(el, doc)

        if (!ALLOWED_HTML_TAGS.has(tag)) {
            while (el.firstChild) {
                node.insertBefore(el.firstChild, el)
            }
            node.removeChild(el)
            continue
        }

        const attrs = Array.from(el.attributes)
        for (const attr of attrs) {
            const name = attr.name.toLowerCase()
            const val = attr.value

            if (name.startsWith("on")) {
                el.removeAttribute(attr.name)
                continue
            }

            if (tag === "a" && (name === "href" || name === "target" || name === "rel")) {
                if (name === "href") {
                    if (!isSafeAnchorHref(val)) {
                        el.removeAttribute(attr.name)
                    }
                } else if (name === "target") {
                    if (val !== "_blank" && val !== "_self") {
                        el.removeAttribute(attr.name)
                    }
                }
                continue
            }

            if (!ALLOWED_HTML_ATTRS.has(name)) {
                el.removeAttribute(attr.name)
                continue
            }

            if (name === "style") {
                const safeStyle = sanitizeCssStyle(val, false)
                if (safeStyle) el.setAttribute("style", safeStyle)
                else el.removeAttribute(attr.name)
            }
        }

        if (tag === "a" && el.hasAttribute("href")) {
            el.setAttribute("rel", "noopener noreferrer")
        }
    }
}

/**
 * Sanitizes untrusted HTML for Svelte {@html} rendering while preserving
 * legitimate slide, lyric, chord, and note formatting.
 */
export function sanitizeHtml(dirty: string | null | undefined): string {
    if (!dirty || typeof dirty !== "string") return ""
    if (dirty === "<br>" || dirty === "<br/>" || dirty === "<br />") return "<br>"
    if (!dirty.includes("<")) return dirty

    const cached = htmlCache.get(dirty)
    if (cached !== undefined) return cached

    if (typeof DOMParser === "undefined") {
        return escapeHtml(dirty)
    }

    const doc = new DOMParser().parseFromString(dirty, "text/html")
    sanitizeHtmlNode(doc.body, doc)
    const clean = doc.body.innerHTML

    if (htmlCache.size >= MAX_CACHE_ENTRIES) {
        const oldestKey = htmlCache.keys().next().value
        if (oldestKey !== undefined) htmlCache.delete(oldestKey)
    }
    htmlCache.set(dirty, clean)
    return clean
}

function sanitizeSvgElement(el: Element) {
    const children = Array.from(el.childNodes)
    for (const child of children) {
        if (child.nodeType === Node.COMMENT_NODE) {
            el.removeChild(child)
            continue
        }
        if (child.nodeType !== Node.ELEMENT_NODE) {
            continue
        }

        const childEl = child as Element
        const tag = childEl.tagName.toLowerCase()
        if (!ALLOWED_SVG_TAGS.has(tag)) {
            el.removeChild(childEl)
            continue
        }

        sanitizeSvgElement(childEl)
    }

    const tag = el.tagName.toLowerCase()
    const attrs = Array.from(el.attributes)
    for (const attr of attrs) {
        const name = attr.name.toLowerCase()
        const val = attr.value.trim()

        if (name.startsWith("on") || !ALLOWED_SVG_ATTRS.has(name)) {
            el.removeAttribute(attr.name)
            continue
        }

        if (name === "href" || name === "xlink:href") {
            if (!SVG_REF_TAGS.has(tag) || !LOCAL_FRAGMENT_RE.test(val)) {
                el.removeAttribute(attr.name)
            }
            continue
        }

        if (name === "style") {
            const safeStyle = sanitizeCssStyle(val, true)
            if (safeStyle) el.setAttribute("style", safeStyle)
            else el.removeAttribute(attr.name)
            continue
        }

        // eslint-disable-next-line no-control-regex
        const normalizedVal = decodeCssEscapes(val).replace(/[\x00-\x20]+/g, "")
        if (/^(javascript|vbscript|data):/i.test(normalizedVal)) {
            el.removeAttribute(attr.name)
            continue
        }

        if (/url\(/i.test(normalizedVal) && !SAFE_LOCAL_URL_RE.test(val)) {
            el.removeAttribute(attr.name)
            continue
        }
    }
}

/**
 * Sanitizes untrusted SVG strings for custom icons and vector slide items.
 */
export function sanitizeSvg(dirty: string | null | undefined): string {
    if (!dirty || typeof dirty !== "string") return ""

    const cached = svgCache.get(dirty)
    if (cached !== undefined) return cached

    if (typeof DOMParser === "undefined") {
        return ""
    }

    const hasOuterSvg = /<svg[\s>]/i.test(dirty)
    const wrapped = hasOuterSvg ? dirty : `<svg xmlns="http://www.w3.org/2000/svg">${dirty}</svg>`
    const doc = new DOMParser().parseFromString(wrapped, "text/html")

    let clean = ""
    if (hasOuterSvg) {
        const svgEls = Array.from(doc.body.querySelectorAll("svg")).filter((svg) => !svg.parentElement?.closest("svg"))
        for (const svg of svgEls) {
            sanitizeSvgElement(svg)
        }
        clean = svgEls.map((svg) => svg.outerHTML).join("")
    } else {
        const wrapperSvg = doc.body.querySelector("svg")
        if (wrapperSvg) {
            sanitizeSvgElement(wrapperSvg)
            clean = wrapperSvg.innerHTML
        }
    }

    if (svgCache.size >= MAX_CACHE_ENTRIES) {
        const oldestKey = svgCache.keys().next().value
        if (oldestKey !== undefined) svgCache.delete(oldestKey)
    }
    svgCache.set(dirty, clean)
    return clean
}
