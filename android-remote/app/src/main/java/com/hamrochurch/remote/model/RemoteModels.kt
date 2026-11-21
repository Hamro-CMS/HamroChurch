package com.hamrochurch.remote.model

import org.json.JSONArray
import org.json.JSONObject

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class MainTab {
    LIVE,
    HYMNS,
    BIBLE,
    SCHEDULE,
    STAGE
}

data class DiscoveredChurchPc(
    val name: String,
    val ip: String,
    val ips: List<String>,
    val wsPort: Int = 5505,
    val restPort: Int = 5506,
    val pinEnabled: Boolean = true
)

data class ChurchPcProfile(
    val id: String,
    val name: String,
    val ips: List<String>,
    val preferredIp: String,
    val wsPort: Int = 5505,
    val restPort: Int = 5506,
    val token: String,
    val lastConnectedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ips", JSONArray(ips))
        put("preferredIp", preferredIp)
        put("wsPort", wsPort)
        put("restPort", restPort)
        put("token", token)
        put("lastConnectedAt", lastConnectedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChurchPcProfile {
            val ipsArr = obj.optJSONArray("ips")
            val ips = mutableListOf<String>()
            if (ipsArr != null) {
                for (i in 0 until ipsArr.length()) {
                    val v = ipsArr.optString(i, "").trim()
                    if (v.isNotEmpty()) ips.add(v)
                }
            }
            val pref = obj.optString("preferredIp", ips.firstOrNull() ?: "")
            if (pref.isNotEmpty() && pref !in ips) ips.add(0, pref)
            return ChurchPcProfile(
                id = obj.optString("id", ""),
                name = obj.optString("name", "Aradhana Studio PC"),
                ips = ips,
                preferredIp = pref,
                wsPort = obj.optInt("wsPort", 5505),
                restPort = obj.optInt("restPort", 5506),
                token = obj.optString("token", ""),
                lastConnectedAt = obj.optLong("lastConnectedAt", 0L)
            )
        }
    }
}

data class ClearedState(
    val all: Boolean = true,
    val background: Boolean = true,
    val slide: Boolean = true,
    val overlays: Boolean = true,
    val audio: Boolean = true,
    val slideTimers: Boolean = true
)

data class SlideSummary(
    val index: Int,
    val id: String,
    val group: String,
    val color: String,
    val text: String,
    val notes: String = "",
    val disabled: Boolean = false
)

data class TimerSummary(
    val id: String,
    val name: String,
    val type: String,
    val start: Double,
    val end: Double,
    val currentTime: Double?,
    val isActive: Boolean,
    val paused: Boolean
)

data class StageAlertState(
    val text: String = "",
    val target: String = "stage",
    val visible: Boolean = false,
    val updatedAt: Long = 0L
)

data class LiveState(
    val isLocked: Boolean = false,
    val outputActive: Boolean = false,
    val cleared: ClearedState = ClearedState(),
    val mode: String = "none",
    val activeShowId: String = "",
    val activeShowName: String = "",
    val activeLayoutId: String = "",
    val currentSlideIndex: Int = -1,
    val totalSlides: Int = 0,
    val currentSlideText: String = "",
    val currentGroup: String = "",
    val currentSlideNotes: String = "",
    val nextSlideIndex: Int = -1,
    val nextSlideText: String = "",
    val nextGroup: String = "",
    val slides: List<SlideSummary> = emptyList(),
    val timers: List<TimerSummary> = emptyList(),
    val stageMessage: StageAlertState = StageAlertState(),
    val timestamp: Long = 0L
)

data class HymnCategoryCounts(
    val all: Int = 0,
    val bhajan: Int = 0,
    val chorus: Int = 0,
    val children: Int = 0,
    val newSongs: Int = 0
)

data class HymnSummary(
    val id: String,
    val number: String,
    val title: String,
    val titleEn: String,
    val transliteration: String,
    val categoryId: String,
    val slidesCount: Int,
    val firstLine: String
)

data class HymnVerse(
    val index: Int,
    val group: String,
    val text: String
)

data class HymnDetail(
    val id: String,
    val number: String,
    val title: String,
    val titleEn: String,
    val transliteration: String,
    val categoryId: String,
    val authors: String,
    val verses: List<HymnVerse>
)

data class BibleInfo(
    val id: String,
    val name: String,
    val isApi: Boolean,
    val isCollection: Boolean
)

data class BibleBook(
    val number: Int,
    val name: String,
    val englishName: String,
    val chaptersCount: Int
)

data class BibleVerse(
    val number: Int,
    val text: String
)

data class BibleSearchResult(
    val book: Int,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

data class ProjectInfo(
    val id: String,
    val name: String,
    val itemsCount: Int
)

data class ProjectItem(
    val index: Int,
    val id: String,
    val type: String,
    val name: String,
    val notes: String,
    val played: Boolean
)

data class SavedShowItem(
    val id: String,
    val name: String,
    val category: String,
    val number: String
)

object RemoteParsers {
    fun parseLiveState(obj: JSONObject?): LiveState {
        if (obj == null) return LiveState()

        val clearedObj = obj.optJSONObject("cleared")
        val cleared = ClearedState(
            all = clearedObj?.optBoolean("all", true) ?: true,
            background = clearedObj?.optBoolean("background", true) ?: true,
            slide = clearedObj?.optBoolean("slide", true) ?: true,
            overlays = clearedObj?.optBoolean("overlays", true) ?: true,
            audio = clearedObj?.optBoolean("audio", true) ?: true,
            slideTimers = clearedObj?.optBoolean("slideTimers", true) ?: true
        )

        val slidesList = mutableListOf<SlideSummary>()
        val slidesArr = obj.optJSONArray("slides")
        if (slidesArr != null) {
            for (i in 0 until slidesArr.length()) {
                val s = slidesArr.optJSONObject(i) ?: continue
                slidesList.add(
                    SlideSummary(
                        index = s.optInt("index", i),
                        id = s.optString("id", "$i"),
                        group = s.optString("group", ""),
                        color = s.optString("color", ""),
                        text = s.optString("text", ""),
                        notes = s.optString("notes", ""),
                        disabled = s.optBoolean("disabled", false)
                    )
                )
            }
        }

        val timersList = mutableListOf<TimerSummary>()
        val timersArr = obj.optJSONArray("timers")
        if (timersArr != null) {
            for (i in 0 until timersArr.length()) {
                val t = timersArr.optJSONObject(i) ?: continue
                val cur = if (t.has("currentTime") && !t.isNull("currentTime")) t.optDouble("currentTime") else null
                timersList.add(
                    TimerSummary(
                        id = t.optString("id", ""),
                        name = t.optString("name", "Timer"),
                        type = t.optString("type", "counter"),
                        start = t.optDouble("start", 300.0),
                        end = t.optDouble("end", 0.0),
                        currentTime = cur,
                        isActive = t.optBoolean("isActive", false),
                        paused = t.optBoolean("paused", false)
                    )
                )
            }
        }

        val stageObj = obj.optJSONObject("stageMessage")
        val stageAlert = StageAlertState(
            text = stageObj?.optString("text", "") ?: "",
            target = stageObj?.optString("target", "stage") ?: "stage",
            visible = stageObj?.optBoolean("visible", false) ?: false,
            updatedAt = stageObj?.optLong("updatedAt", 0L) ?: 0L
        )

        return LiveState(
            isLocked = obj.optBoolean("isLocked", false),
            outputActive = obj.optBoolean("outputActive", false),
            cleared = cleared,
            mode = obj.optString("mode", "none"),
            activeShowId = obj.optString("activeShowId", ""),
            activeShowName = obj.optString("activeShowName", ""),
            activeLayoutId = obj.optString("activeLayoutId", ""),
            currentSlideIndex = obj.optInt("currentSlideIndex", -1),
            totalSlides = obj.optInt("totalSlides", slidesList.size),
            currentSlideText = obj.optString("currentSlideText", ""),
            currentGroup = obj.optString("currentGroup", ""),
            currentSlideNotes = obj.optString("currentSlideNotes", ""),
            nextSlideIndex = obj.optInt("nextSlideIndex", -1),
            nextSlideText = obj.optString("nextSlideText", ""),
            nextGroup = obj.optString("nextGroup", ""),
            slides = slidesList,
            timers = timersList,
            stageMessage = stageAlert,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun parseHymnCounts(obj: JSONObject?): HymnCategoryCounts {
        if (obj == null) return HymnCategoryCounts()
        return HymnCategoryCounts(
            all = obj.optInt("all", 0),
            bhajan = obj.optInt("bhajan", 0),
            chorus = obj.optInt("chorus", 0),
            children = obj.optInt("children", 0),
            newSongs = obj.optInt("new", 0)
        )
    }

    fun parseHymns(arr: JSONArray?): List<HymnSummary> {
        if (arr == null) return emptyList()
        val list = ArrayList<HymnSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(
                HymnSummary(
                    id = item.optString("id", ""),
                    number = item.optString("number", ""),
                    title = item.optString("title", ""),
                    titleEn = item.optString("titleEn", ""),
                    transliteration = item.optString("transliteration", ""),
                    categoryId = item.optString("categoryId", "bhajan"),
                    slidesCount = item.optInt("slidesCount", 0),
                    firstLine = item.optString("firstLine", "")
                )
            )
        }
        return list
    }

    fun parseHymnDetail(obj: JSONObject?): HymnDetail? {
        if (obj == null) return null
        val versesArr = obj.optJSONArray("verses")
        val verses = mutableListOf<HymnVerse>()
        if (versesArr != null) {
            for (i in 0 until versesArr.length()) {
                val v = versesArr.optJSONObject(i) ?: continue
                verses.add(
                    HymnVerse(
                        index = v.optInt("index", i),
                        group = v.optString("group", "verse"),
                        text = v.optString("text", "")
                    )
                )
            }
        }
        return HymnDetail(
            id = obj.optString("id", ""),
            number = obj.optString("number", ""),
            title = obj.optString("title", ""),
            titleEn = obj.optString("titleEn", ""),
            transliteration = obj.optString("transliteration", ""),
            categoryId = obj.optString("categoryId", "bhajan"),
            authors = obj.optString("authors", ""),
            verses = verses
        )
    }

    fun parseBibles(arr: JSONArray?): List<BibleInfo> {
        if (arr == null) return emptyList()
        val list = ArrayList<BibleInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            list.add(
                BibleInfo(
                    id = b.optString("id", ""),
                    name = b.optString("name", "Bible"),
                    isApi = b.optBoolean("isApi", false),
                    isCollection = b.optBoolean("isCollection", false)
                )
            )
        }
        return list
    }

    fun parseBibleBooks(arr: JSONArray?): List<BibleBook> {
        if (arr == null) return emptyList()
        val list = ArrayList<BibleBook>(arr.length())
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            list.add(
                BibleBook(
                    number = b.optInt("number", i + 1),
                    name = b.optString("name", ""),
                    englishName = b.optString("englishName", ""),
                    chaptersCount = b.optInt("chaptersCount", 1)
                )
            )
        }
        return list
    }

    fun parseBibleVerses(arr: JSONArray?): List<BibleVerse> {
        if (arr == null) return emptyList()
        val list = ArrayList<BibleVerse>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            list.add(
                BibleVerse(
                    number = v.optInt("number", i + 1),
                    text = v.optString("text", "")
                )
            )
        }
        return list
    }

    fun parseBibleSearchResults(arr: JSONArray?): List<BibleSearchResult> {
        if (arr == null) return emptyList()
        val list = ArrayList<BibleSearchResult>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            list.add(
                BibleSearchResult(
                    book = r.optInt("book", 1),
                    bookName = r.optString("bookName", ""),
                    chapter = r.optInt("chapter", 1),
                    verse = r.optInt("verse", 1),
                    text = r.optString("text", "")
                )
            )
        }
        return list
    }

    fun parseProjects(arr: JSONArray?): List<ProjectInfo> {
        if (arr == null) return emptyList()
        val list = ArrayList<ProjectInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                ProjectInfo(
                    id = p.optString("id", ""),
                    name = p.optString("name", "Project"),
                    itemsCount = p.optInt("itemsCount", 0)
                )
            )
        }
        return list
    }

    fun parseProjectItems(arr: JSONArray?): List<ProjectItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<ProjectItem>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(
                ProjectItem(
                    index = item.optInt("index", i),
                    id = item.optString("id", ""),
                    type = item.optString("type", "show"),
                    name = item.optString("name", ""),
                    notes = item.optString("notes", ""),
                    played = item.optBoolean("played", false)
                )
            )
        }
        return list
    }

    fun parseSavedShows(arr: JSONArray?): List<SavedShowItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<SavedShowItem>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            list.add(
                SavedShowItem(
                    id = s.optString("id", ""),
                    name = s.optString("name", ""),
                    category = s.optString("category", ""),
                    number = s.optString("number", "")
                )
            )
        }
        return list
    }
}
