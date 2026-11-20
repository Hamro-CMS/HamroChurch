package com.hamrochurch.remote.nepali

/**
 * Offline internal Nepali phonetic transliteration & multi-candidate engine
 * mirroring Desktop Aradhana Studio (`src/lib/nepaliTyping.ts` + `src/lib/googleNepaliTyping.ts`).
 *
 * Supports:
 * - High-frequency Christian / worship Nepali dictionary + automatic Nepali case-suffix inflection
 *   (e.g. "yeshule" -> ["येशूले", "येशुले", "येसुले", "yeshule"], "stutiko" -> ["स्तुतिको", "स्तुतीको", ...])
 * - Full phonetic Devanagari conversion with smart vowel-length, sibilant, and nasal variants.
 */
object NepaliPhoneticEngine {

    private const val HALANT = "\u094D"

    private val INSTANT_DICTIONARY: Map<String, List<String>> = mapOf(
        "yeshu" to listOf("येशू", "येशु", "येसु"),
        "yesu" to listOf("येशू", "येशु", "येसु"),
        "yeshuko" to listOf("येशूको", "येशुको", "येसुको"),
        "yeshule" to listOf("येशूले", "येशुले", "येसुले"),
        "yeshulai" to listOf("येशूलाई", "येशुलाई", "येसुलाई"),
        "yeshuma" to listOf("येशूमा", "येशुमा", "येसुमा"),
        "khrist" to listOf("ख्रीष्ट", "ख्रिष्ट", "क्रिस्ट", "ख्रीस्त"),
        "khrista" to listOf("ख्रीष्ट", "ख्रिष्ट", "ख्रीष्टको"),
        "khristiya" to listOf("ख्रीष्टीय", "ख्रिष्टीय", "क्रिस्चियन"),
        "parmeshwar" to listOf("परमेश्वर", "परमेशवर", "परमेस्वर"),
        "parmeshwor" to listOf("परमेश्वर", "परमेश्वोर", "परमेस्वर"),
        "parameshwar" to listOf("परमेश्वर", "परमेशवर", "परमेस्वर"),
        "prabhu" to listOf("प्रभु", "प्रभू"),
        "pita" to listOf("पिता", "पीता"),
        "putra" to listOf("पुत्र", "पूत्र"),
        "pavitra" to listOf("पवित्र", "पबित्र"),
        "pawitra" to listOf("पवित्र", "पबित्र"),
        "aatma" to listOf("आत्मा", "अत्मा"),
        "atma" to listOf("आत्मा", "अत्मा"),
        "mahima" to listOf("महिमा", "महीमा"),
        "stuti" to listOf("स्तुति", "स्तुती", "सतुति"),
        "stutiko" to listOf("स्तुतिको", "स्तुतीको", "सतुतिको"),
        "prashamsa" to listOf("प्रशंसा", "प्रसंशा"),
        "hallelujah" to listOf("हल्लेलूयाह", "हलेलुया", "हल्लेलुयाह"),
        "halleluya" to listOf("हल्लेलूयाह", "हलेलुया", "हल्लेलुयाह"),
        "hosanna" to listOf("होसन्ना", "होशन्ना", "होसना"),
        "amen" to listOf("आमेन", "आमिन", "अमेन"),
        "krus" to listOf("क्रूस", "क्रुस"),
        "kroos" to listOf("क्रूस", "क्रुस"),
        "mukti" to listOf("मुक्ति", "मुक्ती"),
        "muktidata" to listOf("मुक्तिदाता", "मुक्तीदाता"),
        "uddhar" to listOf("उद्धार", "उद्दार"),
        "anugraha" to listOf("अनुग्रह", "अनूग्रह"),
        "prem" to listOf("प्रेम", "परेम"),
        "shanti" to listOf("शान्ति", "शान्ती", "सान्ति"),
        "ashish" to listOf("आशिष्", "आशिष", "आशीष"),
        "aashish" to listOf("आशिष्", "आशिष", "आशीष"),
        "dhanyabad" to listOf("धन्यवाद", "धन्यबाद"),
        "dhanyawad" to listOf("धन्यवाद", "धन्यबाद"),
        "prarthana" to listOf("प्रार्थना", "प्रथना"),
        "aaradhana" to listOf("आराधना", "अराधना"),
        "aradhana" to listOf("आराधना", "अराधना"),
        "bhajan" to listOf("भजन", "भजनहरू"),
        "mandali" to listOf("मण्डली", "मंडली", "मन्डली"),
        "maranatha" to listOf("मारानाथा", "मरानाथा"),
        "church" to listOf("चर्च", "चर्चमा"),
        "baibal" to listOf("बाइबल", "बाईबल"),
        "bible" to listOf("बाइबल", "बाईबल"),
        "bachan" to listOf("वचन", "बचन"),
        "wachan" to listOf("वचन", "बचन"),
        "swarga" to listOf("स्वर्ग", "स्वर्गमा"),
        "swarg" to listOf("स्वर्ग", "स्वर्गमा"),
        "raja" to listOf("राजा", "राजाको"),
        "jiwan" to listOf("जीवन", "जिवन"),
        "jeevan" to listOf("जीवन", "जिवन"),
        "jiban" to listOf("जीवन", "जिवन"),
        "hridaya" to listOf("हृदय", "हृदयमा"),
        "bishwas" to listOf("विश्वास", "बिश्वास"),
        "vishwas" to listOf("विश्वास", "बिश्वास"),
        "baptisma" to listOf("बप्तिस्मा", "बाप्तिस्मा"),
        "sangati" to listOf("सङ्गति", "संगति", "संगती"),
        "pastor" to listOf("पास्टर", "पाष्टर"),
        "sewa" to listOf("सेवा", "सेबा"),
        "sewak" to listOf("सेवक", "सेबक"),
        "bheti" to listOf("भेटी", "भेटि"),
        "dashamsha" to listOf("दशांश", "दशांस"),
        "nepali" to listOf("नेपाली", "नेपालि"),
        "nepal" to listOf("नेपाल", "नेपालमा"),
        "namaste" to listOf("नमस्ते", "नमस्कार"),
        "jaymasih" to listOf("जयमसीह", "जय मसीह"),
        "masih" to listOf("मसीह", "मसिह"),
        "hamro" to listOf("हाम्रो", "हाम्रा"),
        "hami" to listOf("हामी", "हामीले"),
        "mero" to listOf("मेरो", "मेरा", "मेरी"),
        "ma" to listOf("म", "मा", "मै"),
        "tapai" to listOf("तपाईं", "तपाइँ", "तपाई"),
        "tapain" to listOf("तपाईं", "तपाइँ"),
        "tapaiko" to listOf("तपाईंको", "तपाइँको"),
        "tapailai" to listOf("तपाईंलाई", "तपाइँलाई"),
        "sadhai" to listOf("सधैं", "सधैँ", "सदैव"),
        "sada" to listOf("सदा", "सादा"),
        "chha" to listOf("छ", "छन्"),
        "chhan" to listOf("छन्", "छन"),
        "gaun" to listOf("गाऊँ", "गाउँ", "गाऔं"),
        "gauchhu" to listOf("गाउँछु", "गाउछु"),
        "aau" to listOf("आऊ", "आउ", "आऊँ"),
        "aaun" to listOf("आऊँ", "आउँ"),
        "yuhanna" to listOf("यूहन्ना", "युहन्ना"),
        "matti" to listOf("मत्ती", "मत्ति"),
        "markus" to listOf("मर्कूस", "मर्कुस"),
        "luka" to listOf("लूका", "लुका"),
        "utpatti" to listOf("उत्पत्ति", "उत्पत्ती"),
        "bhajansangraha" to listOf("भजनसंग्रह", "भजनसङ्ग्रह")
    )

    // Common Nepali grammatical case endings (postpositions) for stem+suffix dictionary matching
    private val CASE_SUFFIXES: List<Pair<String, String>> = listOf(
        "haruko" to "हरूको",
        "harulai" to "हरूलाई",
        "harule" to "हरूले",
        "haruma" to "हरूमा",
        "haru" to "हरू",
        "sanga" to "सँग",
        "dekhi" to "देखि",
        "bata" to "बाट",
        "lai" to "लाई",
        "le" to "ले",
        "ko" to "को",
        "ka" to "का",
        "ki" to "की",
        "ma" to "मा"
    )

    private val PHONETIC_CONSONANTS: List<Pair<String, String>> = listOf(
        "chh" to "छ",
        "ksh" to "क्ष",
        "shr" to "श्र",
        "gy" to "ज्ञ",
        "jn" to "ज्ञ",
        "kh" to "ख",
        "gh" to "घ",
        "ng" to "ङ",
        "ch" to "च",
        "jh" to "झ",
        "ny" to "ञ",
        "Th" to "ठ",
        "Dh" to "ढ",
        "th" to "थ",
        "dh" to "ध",
        "ph" to "फ",
        "bh" to "भ",
        "sh" to "श",
        "Sh" to "ष",
        "tr" to "त्र",
        "dr" to "द्र",
        "pr" to "प्र",
        "br" to "ब्र",
        "kr" to "क्र",
        "gr" to "ग्र",
        "sr" to "स्र",
        "k" to "क",
        "g" to "ग",
        "c" to "च",
        "j" to "ज",
        "T" to "ट",
        "D" to "ड",
        "N" to "ण",
        "t" to "त",
        "d" to "द",
        "n" to "न",
        "p" to "प",
        "f" to "फ",
        "b" to "ब",
        "m" to "म",
        "y" to "य",
        "r" to "र",
        "l" to "ल",
        "v" to "व",
        "w" to "व",
        "s" to "स",
        "h" to "ह",
        "z" to "ज",
        "q" to "क",
        "x" to "क्स"
    )

    private data class VowelPair(val ind: String, val mat: String)

    private val PHONETIC_VOWELS: List<Pair<String, VowelPair>> = listOf(
        "aa" to VowelPair("आ", "ा"),
        "ai" to VowelPair("ऐ", "ै"),
        "au" to VowelPair("औ", "ौ"),
        "ee" to VowelPair("ई", "ी"),
        "ii" to VowelPair("ई", "ी"),
        "oo" to VowelPair("ऊ", "ू"),
        "uu" to VowelPair("ऊ", "ू"),
        "ri" to VowelPair("ऋ", "ृ"),
        "A" to VowelPair("आ", "ा"),
        "I" to VowelPair("ई", "ी"),
        "U" to VowelPair("ऊ", "ू"),
        "a" to VowelPair("अ", ""),
        "i" to VowelPair("इ", "ि"),
        "u" to VowelPair("उ", "ु"),
        "e" to VowelPair("ए", "े"),
        "o" to VowelPair("ओ", "ो")
    )

    private enum class NasalStyle { CLUSTER, ANUSVARA, RETROFLEX }

    private fun <T> matchPrefix(value: String, index: Int, table: List<Pair<String, T>>): Pair<String, T>? {
        for (entry in table) {
            if (value.startsWith(entry.first, startIndex = index)) {
                return entry
            }
        }
        return null
    }

    private fun convertWithRules(
        inputWord: String,
        nasalStyle: NasalStyle = NasalStyle.CLUSTER,
        longEndI: Boolean = true,
        longU: Boolean = false,
        preferW: Boolean = false
    ): String {
        val out = StringBuilder()
        var index = 0
        var lastWasConsonant = false

        while (index < inputWord.length) {
            val ch = inputWord[index]
            if ((ch == 'n' || ch == 'm') && index + 1 < inputWord.length && inputWord[index + 1] in "dtkgjpbDTKGJPB") {
                val next = inputWord[index + 1].lowercaseChar()
                if (nasalStyle == NasalStyle.ANUSVARA) {
                    out.append("ं")
                    lastWasConsonant = false
                    index += 1
                    continue
                }
                if (next == 'd' || next == 't') {
                    if (lastWasConsonant) out.append(HALANT)
                    if (nasalStyle == NasalStyle.RETROFLEX) {
                        out.append("ण").append(HALANT).append(if (next == 'd') "ड" else "ट")
                    } else {
                        out.append("न").append(HALANT).append(if (next == 'd') "द" else "त")
                    }
                    lastWasConsonant = true
                    index += 2
                    continue
                }
            }

            val consonant = matchPrefix(inputWord, index, PHONETIC_CONSONANTS)
            val vowel = matchPrefix(inputWord, index, PHONETIC_VOWELS)

            if (consonant != null && (vowel == null || consonant.first.length >= vowel.first.length)) {
                if (lastWasConsonant) out.append(HALANT)
                var mapped = consonant.second
                if (preferW && (consonant.first == "b" || consonant.first == "v")) {
                    mapped = if (consonant.first == "b") "व" else "ब"
                }
                out.append(mapped)
                index += consonant.first.length
                lastWasConsonant = true
                continue
            }

            if (vowel != null) {
                val key = vowel.first
                val pair = vowel.second
                val atEnd = index + key.length == inputWord.length
                if (lastWasConsonant) {
                    val matra = when {
                        key == "i" && atEnd && longEndI && inputWord.length > 2 -> "ी"
                        key == "u" && longU -> "ू"
                        else -> pair.mat
                    }
                    out.append(matra)
                } else {
                    out.append(pair.ind)
                }
                index += key.length
                lastWasConsonant = false
                continue
            }

            out.append(ch)
            index += 1
            lastWasConsonant = false
        }

        return out.toString()
    }

    private fun stemInflectedDictionaryMatches(lowerWord: String): List<String> {
        val results = mutableListOf<String>()
        INSTANT_DICTIONARY[lowerWord]?.let { results.addAll(it) }

        for ((romSuffix, devSuffix) in CASE_SUFFIXES) {
            if (lowerWord.length > romSuffix.length + 1 && lowerWord.endsWith(romSuffix)) {
                val stem = lowerWord.removeSuffix(romSuffix)
                val stemMatches = INSTANT_DICTIONARY[stem]
                if (stemMatches != null) {
                    for (devStem in stemMatches) {
                        results.add(devStem.removeSuffix(HALANT) + devSuffix)
                    }
                }
            }
        }
        return results
    }

    /**
     * Generates up to [maxNepali] unique Devanagari suggestions for a single Roman word,
     * followed by the original [rawWord] as the final option (matching Desktop's horizontal bar).
     */
    fun getCandidatesForWord(rawWord: String, maxNepali: Int = 4): List<String> {
        val word = rawWord.trim()
        if (word.isEmpty() || !word.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            return emptyList()
        }

        val lower = word.lowercase()
        val rawVariants = buildList {
            addAll(stemInflectedDictionaryMatches(lower))
            add(convertWithRules(word, nasalStyle = NasalStyle.CLUSTER, longEndI = false))
            add(convertWithRules(lower, nasalStyle = NasalStyle.CLUSTER, longEndI = true))
            add(convertWithRules(lower, nasalStyle = NasalStyle.CLUSTER, longEndI = false, longU = true))
            add(convertWithRules(lower, nasalStyle = NasalStyle.ANUSVARA, longEndI = true))
            add(convertWithRules(word, nasalStyle = NasalStyle.CLUSTER, longEndI = true, preferW = true))
            add(convertWithRules(lower, nasalStyle = NasalStyle.RETROFLEX, longEndI = true))
        }

        val uniqueNepali = LinkedHashSet<String>()
        for (variant in rawVariants) {
            val cleaned = variant.trim()
            if (cleaned.isNotEmpty() && cleaned != word && cleaned.none { it in 'a'..'z' || it in 'A'..'Z' }) {
                uniqueNepali.add(cleaned)
                if (uniqueNepali.size >= maxNepali) break
            }
        }

        if (uniqueNepali.isEmpty()) return emptyList()
        return uniqueNepali.toList() + word
    }

    data class ActiveRomanToken(
        val start: Int,
        val end: Int,
        val roman: String,
        val candidates: List<String>
    )

    private val ROMAN_TAIL_REGEX = Regex("[a-zA-Z]+$")

    /**
     * Finds the active Roman word immediately before [cursor] in [text] (if any)
     * and computes horizontal bar candidates.
     */
    fun detectActiveToken(text: String, cursor: Int): ActiveRomanToken? {
        val safeCursor = cursor.coerceIn(0, text.length)
        // Do not trigger mid-Roman-word if cursor has ASCII letters right after it
        if (safeCursor < text.length && text[safeCursor].let { it in 'a'..'z' || it in 'A'..'Z' }) {
            return null
        }
        val before = text.substring(0, safeCursor)
        val match = ROMAN_TAIL_REGEX.find(before) ?: return null
        val roman = match.value
        val start = safeCursor - roman.length
        if (start > 0 && before[start - 1] in "@/:\\") return null

        val candidates = getCandidatesForWord(roman)
        if (candidates.isEmpty()) return null
        return ActiveRomanToken(start = start, end = safeCursor, roman = roman, candidates = candidates)
    }

    /**
     * Converts any uncommitted Roman words inside [query] to their top Devanagari candidate
     * so live search immediately finds Nepali Bible verses & Bhajans even before Space is pressed!
     */
    fun previewDevanagariQuery(query: String): String {
        if (query.isBlank() || query.none { it in 'a'..'z' || it in 'A'..'Z' }) return query
        return Regex("[a-zA-Z]+").replace(query) { matchResult ->
            val word = matchResult.value
            getCandidatesForWord(word, maxNepali = 1).firstOrNull()?.takeIf { it != word } ?: word
        }
    }
}
