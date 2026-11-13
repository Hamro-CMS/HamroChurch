// Offline candidates for the Google-style Nepali typing (ported from HamroCMS_Desktop useNepaliTyping.js).
// Used for 0ms first suggestions and as the fallback when Google Input Tools is unreachable.

import { toUnicode } from "./nepaliTyping"

// High-frequency church / worship words -> preferred spellings first
export const INSTANT_DICTIONARY: Record<string, string[]> = {
    yeshu: ["येशू", "येशु", "येसु", "येशूको"],
    yesu: ["येशू", "येशु", "येसु", "येशूको"],
    yeshuko: ["येशूको", "येशुको", "येसुको"],
    khrist: ["ख्रीष्ट", "ख्रिष्ट", "क्रिस्ट", "ख्रीस्त"],
    khrista: ["ख्रीष्ट", "ख्रिष्ट", "ख्रीष्टको"],
    khristiya: ["ख्रीष्टीय", "ख्रिष्टीय", "क्रिस्चियन"],
    parmeshwar: ["परमेश्वर", "परमेशवर", "परमेस्वर", "परमेश्वरको"],
    parmeshwor: ["परमेश्वर", "परमेश्वोर", "परमेशवर", "परमेस्वर"],
    parameshwar: ["परमेश्वर", "परमेशवर", "परमेस्वर", "परमेश्वरको"],
    prabhu: ["प्रभु", "प्रभू", "प्रभुको", "प्रभुलाई"],
    prabhuko: ["प्रभुको", "प्रभूको", "प्रभुका"],
    pita: ["पिता", "पीता", "पिताको", "पितालाई"],
    putra: ["पुत्र", "पुत्रको", "पुत्रलाई"],
    pavitra: ["पवित्र", "पबित्र", "पवित्रको"],
    pawitra: ["पवित्र", "पबित्र", "पवित्रको"],
    aatma: ["आत्मा", "आत्माको", "आत्मामा"],
    atma: ["आत्मा", "आत्माको", "आत्मामा"],
    mahima: ["महिमा", "महीमा", "महिमाको"],
    stuti: ["स्तुति", "स्तुती", "स्तुतिको"],
    prashamsa: ["प्रशंसा", "प्रसंशा", "प्रशंसाको"],
    hallelujah: ["हल्लेलूयाह", "हलेलुया", "हल्लेलुयाह"],
    halleluya: ["हल्लेलूयाह", "हलेलुया", "हल्लेलुयाह"],
    hosanna: ["होसन्ना", "होशन्ना", "होसना"],
    amen: ["आमेन", "आमिन", "अमेन"],
    krus: ["क्रूस", "क्रुस", "क्रूसको"],
    kroos: ["क्रूस", "क्रुस", "क्रूसको"],
    mukti: ["मुक्ति", "मुक्ती", "मुक्तिको"],
    muktidata: ["मुक्तिदाता", "मुक्तिदाताको"],
    uddhar: ["उद्धार", "उद्दार", "उद्धारको"],
    anugraha: ["अनुग्रह", "अनूग्रह", "अनुग्रहको", "अनुग्रहमा"],
    prem: ["प्रेम", "परेम", "प्रेमको", "प्रेममा"],
    shanti: ["शान्ति", "शान्ती", "सान्ति", "सान्ती"],
    ashish: ["आशिष्", "आशिष", "अशिष", "आशीष"],
    aashish: ["आशिष्", "आशिष", "आशीष"],
    dhanyabad: ["धन्यवाद", "धन्यबाद", "धन्यवादको"],
    dhanyawad: ["धन्यवाद", "धन्यबाद", "धन्यवादको"],
    prarthana: ["प्रार्थना", "पर्थना", "प्रथना", "प्रार्थनामा"],
    aaradhana: ["आराधना", "अराधना", "आराधाना", "आराधनामा"],
    aradhana: ["आराधना", "अराधना", "आराधाना", "अराधाना"],
    bhajan: ["भजन", "भजनको", "भजनहरू"],
    mandali: ["मण्डली", "मंडली", "मन्डली", "मण्डलीको"],
    mandalika: ["मण्डलीका", "मंडलीका", "मण्डलिका"],
    maranatha: ["मारानाथा", "मरानाथा", "मरनाथा"],
    church: ["चर्च", "चर्चमा", "चर्चको"],
    baibal: ["बाइबल", "बाईबल", "बाइबलको"],
    bible: ["बाइबल", "बाईबल", "बाइबलको"],
    bachan: ["वचन", "बचन", "वचनको"],
    wachan: ["वचन", "बचन", "वचनको"],
    swarga: ["स्वर्ग", "स्वर्गमा", "स्वर्गको"],
    swarg: ["स्वर्ग", "स्वर्गमा", "स्वर्गको"],
    raja: ["राजा", "राजाको", "राजालाई"],
    jiwan: ["जीवन", "जिवन", "जीवनको"],
    jeevan: ["जीवन", "जिवन", "जीवनको"],
    jiban: ["जीवन", "जिवन", "जीवनको"],
    hridaya: ["हृदय", "हृदयको", "हृदयमा"],
    bishwas: ["विश्वास", "बिश्वास", "विश्वासको"],
    vishwas: ["विश्वास", "बिश्वास", "विश्वासको"],
    baptisma: ["बप्तिस्मा", "बाप्तिस्मा", "बप्तीस्मा"],
    sangati: ["सङ्गति", "संगति", "संगती", "सङ्गती"],
    pastor: ["पास्टर", "पाष्टर", "पास्टरको"],
    sewa: ["सेवा", "शेवा", "सेबा", "सेवामा"],
    sewak: ["सेवक", "शेवक", "सेबक", "सेवकहरू"],
    bheti: ["भेटी", "भेटि", "भेटीहरू"],
    dashamsha: ["दशांश", "दशांस", "दसांस"],
    nepali: ["नेपाली", "नेपालि", "नेपालीमा"],
    nepal: ["नेपाल", "नेपालमा", "नेपालको"],
    namaste: ["नमस्ते", "नमश्ते", "नमस्कार"],
    jaymasih: ["जयमसीह", "जय मसीह"],
    masih: ["मसीह", "मसिह", "मसीहको"],
    hamro: ["हाम्रो", "हम्रो", "हाम्रा"],
    hami: ["हामी", "हामि", "हामीले"],
    mero: ["मेरो", "मेरा", "मेरी"],
    ma: ["म", "मा", "मै"],
    tapai: ["तपाईं", "तपाइँ", "तपाई"],
    tapain: ["तपाईं", "तपाइँ", "तपाईंको"],
    tapaiko: ["तपाईंको", "तपाइँको", "तपाईको"],
    sadhai: ["सधैं", "सधैँ", "सदैव"],
    sadhain: ["सधैं", "सधैँ"],
    sada: ["सदा", "सादा", "शदा"],
    chha: ["छ", "छा", "छन्"],
    chhan: ["छन्", "छन", "छौं"],
    gaun: ["गाऊँ", "गाउँ", "गाऔं"],
    gauchhu: ["गाउँछु", "गाउछु", "गाऊँछु"],
    aau: ["आऊ", "आउ", "आऊँ"],
    aaun: ["आऊँ", "आउँ", "आऔं"]
}

const PHONETIC_CONSONANTS: [string, string][] = [
    ["chh", "छ"],
    ["ksh", "क्ष"],
    ["shr", "श्र"],
    ["gy", "ज्ञ"],
    ["jn", "ज्ञ"],
    ["kh", "ख"],
    ["gh", "घ"],
    ["ng", "ङ"],
    ["ch", "च"],
    ["jh", "झ"],
    ["ny", "ञ"],
    ["Th", "ठ"],
    ["Dh", "ढ"],
    ["th", "थ"],
    ["dh", "ध"],
    ["ph", "फ"],
    ["bh", "भ"],
    ["sh", "श"],
    ["Sh", "ष"],
    ["tr", "त्र"],
    ["dr", "द्र"],
    ["pr", "प्र"],
    ["br", "ब्र"],
    ["kr", "क्र"],
    ["gr", "ग्र"],
    ["sr", "स्र"],
    ["k", "क"],
    ["g", "ग"],
    ["c", "च"],
    ["j", "ज"],
    ["T", "ट"],
    ["D", "ड"],
    ["N", "ण"],
    ["t", "त"],
    ["d", "द"],
    ["n", "न"],
    ["p", "प"],
    ["f", "फ"],
    ["b", "ब"],
    ["m", "म"],
    ["y", "य"],
    ["r", "र"],
    ["l", "ल"],
    ["v", "व"],
    ["w", "व"],
    ["s", "स"],
    ["h", "ह"],
    ["z", "ज"],
    ["q", "क"],
    ["x", "क्स"]
]

const PHONETIC_VOWELS: [string, { ind: string; mat: string }][] = [
    ["aa", { ind: "आ", mat: "ा" }],
    ["ai", { ind: "ऐ", mat: "ै" }],
    ["au", { ind: "औ", mat: "ौ" }],
    ["ee", { ind: "ई", mat: "ी" }],
    ["oo", { ind: "ऊ", mat: "ू" }],
    ["ri", { ind: "ऋ", mat: "ृ" }],
    ["A", { ind: "आ", mat: "ा" }],
    ["I", { ind: "ई", mat: "ी" }],
    ["U", { ind: "ऊ", mat: "ू" }],
    ["a", { ind: "अ", mat: "" }],
    ["i", { ind: "इ", mat: "ि" }],
    ["u", { ind: "उ", mat: "ु" }],
    ["e", { ind: "ए", mat: "े" }],
    ["o", { ind: "ओ", mat: "ो" }]
]

const HALANT = "\u094D"

function matchPrefix<T>(value: string, index: number, table: [string, T][]): [string, T] | null {
    for (const entry of table) {
        if (value.startsWith(entry[0], index)) return entry
    }
    return null
}

type RuleOptions = { nasalStyle?: "cluster" | "anusvara" | "retroflex"; longEndI?: boolean; preferW?: boolean }

function convertWithRules(inputWord: string, { nasalStyle = "cluster", longEndI = true, preferW = false }: RuleOptions = {}) {
    let out = ""
    let index = 0
    let lastWasConsonant = false

    while (index < inputWord.length) {
        const char = inputWord[index]
        if ((char === "n" || char === "m") && index + 1 < inputWord.length && /[dtkgjpbDTKGJPB]/.test(inputWord[index + 1])) {
            const next = inputWord[index + 1].toLowerCase()
            if (nasalStyle === "anusvara") {
                out += "ं"
                lastWasConsonant = false
                index += 1
                continue
            }
            if (next === "d" || next === "t") {
                if (lastWasConsonant) out += HALANT
                if (nasalStyle === "retroflex") out += "ण" + HALANT + (next === "d" ? "ड" : "ट")
                else out += "न" + HALANT + (next === "d" ? "द" : "त")
                lastWasConsonant = true
                index += 2
                continue
            }
        }

        const consonant = matchPrefix(inputWord, index, PHONETIC_CONSONANTS)
        const vowel = matchPrefix(inputWord, index, PHONETIC_VOWELS)

        if (consonant && (!vowel || consonant[0].length >= vowel[0].length)) {
            if (lastWasConsonant) out += HALANT
            let value = consonant[1]
            if (preferW && (consonant[0] === "b" || consonant[0] === "v")) value = consonant[0] === "b" ? "व" : "ब"
            out += value
            index += consonant[0].length
            lastWasConsonant = true
            continue
        }

        if (vowel) {
            const [key, value] = vowel
            const atEnd = index + key.length === inputWord.length
            if (lastWasConsonant) out += key === "i" && atEnd && longEndI && inputWord.length > 2 ? "ी" : value.mat
            else out += value.ind
            index += key.length
            lastWasConsonant = false
            continue
        }

        out += char
        index += 1
        lastWasConsonant = false
    }

    return out
}

/** Preferred church spellings (e.g. येशू, ख्रीष्ट, परमेश्वर) shown before Google's suggestions. */
export function dictionaryCandidates(rawWord: string): string[] {
    return [
        ...(INSTANT_DICTIONARY[
            String(rawWord || "")
                .trim()
                .toLowerCase()
        ] || [])
    ]
}

/** Whole-word phonetic transliteration + natural spelling variants (offline). */
export function generateLocalCandidates(rawWord: string, max = 4): string[] {
    const word = String(rawWord || "").trim()
    if (!word) return []

    const lower = word.toLowerCase()
    const variants = [
        ...(INSTANT_DICTIONARY[lower] || []),
        // the same engine the existing "ने" typing uses, so results feel familiar (without a dangling final halant)
        toUnicode(word).replace(/\u094D$/, ""),
        convertWithRules(lower, { nasalStyle: "cluster", longEndI: true }),
        convertWithRules(lower, { nasalStyle: "anusvara", longEndI: true }),
        convertWithRules(lower, { nasalStyle: "cluster", longEndI: false }),
        convertWithRules(word, { nasalStyle: "cluster", longEndI: true, preferW: true }),
        convertWithRules(lower, { nasalStyle: "retroflex", longEndI: true })
    ]

    return uniqueCandidates(variants, word).slice(0, max)
}

export function uniqueCandidates(values: string[], romanWord = ""): string[] {
    const seen = new Set<string>()
    const result: string[] = []
    for (const value of values) {
        const trimmed = String(value || "").trim()
        // skip empty, duplicates, the roman word itself and half-converted values that still contain latin letters
        if (!trimmed || seen.has(trimmed) || trimmed === romanWord || /[a-zA-Z]/.test(trimmed)) continue
        seen.add(trimmed)
        result.push(trimmed)
    }
    return result
}
