package com.hamrochurch.remote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamrochurch.remote.data.ProfileStore
import com.hamrochurch.remote.model.BibleBook
import com.hamrochurch.remote.model.BibleInfo
import com.hamrochurch.remote.model.BibleSearchResult
import com.hamrochurch.remote.model.BibleVerse
import com.hamrochurch.remote.model.ChurchPcProfile
import com.hamrochurch.remote.model.ConnectionStatus
import com.hamrochurch.remote.model.DiscoveredChurchPc
import com.hamrochurch.remote.model.HymnCategoryCounts
import com.hamrochurch.remote.model.HymnDetail
import com.hamrochurch.remote.model.HymnSummary
import com.hamrochurch.remote.model.LiveState
import com.hamrochurch.remote.model.MainTab
import com.hamrochurch.remote.model.MobilePromotion
import com.hamrochurch.remote.model.ProjectInfo
import com.hamrochurch.remote.model.ProjectItem
import com.hamrochurch.remote.model.PromotionSanitizer
import com.hamrochurch.remote.model.RemoteParsers
import com.hamrochurch.remote.model.SavedShowItem
import com.hamrochurch.remote.nepali.NepaliPhoneticEngine
import com.hamrochurch.remote.network.HamroRemoteClient
import com.hamrochurch.remote.network.PromotionClient
import com.hamrochurch.remote.security.LanSecurityValidator
import com.hamrochurch.remote.security.QrPairingParser
import com.hamrochurch.remote.security.QrParseResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RemoteUiState(
    val nepaliLanguage: Boolean = true,
    val nepaliTypingEnabled: Boolean = true,
    val volumeNavEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedHost: String = "",
    val pcHostName: String = "",
    val pcVersion: String = "",
    val errorMessage: String? = null,
    val statusToast: String? = null,
    val savedProfiles: List<ChurchPcProfile> = emptyList(),
    val discoveredPcs: List<DiscoveredChurchPc> = emptyList(),
    val isDiscoveringPcs: Boolean = false,
    val activeTab: MainTab = MainTab.LIVE,
    val swipePadMode: Boolean = false,
    val fullScreenPrompter: Boolean = false,
    val prompterTouchLocked: Boolean = false,

    // Live Output
    val live: LiveState = LiveState(),

    // Bhajan / Hymns
    val hymnCounts: HymnCategoryCounts = HymnCategoryCounts(),
    val hymnCategory: String = "all",
    val hymnQuery: String = "",
    val hymnDialNumber: String = "",
    val hymnLoading: Boolean = false,
    val hymnsTotal: Int = 0,
    val hymns: List<HymnSummary> = emptyList(),
    val selectedHymnDetail: HymnDetail? = null,

    // Bible / Scripture
    val bibles: List<BibleInfo> = emptyList(),
    val selectedBibleId: String = "",
    val bibleBooks: List<BibleBook> = emptyList(),
    val selectedBook: Int = 1,
    val selectedBookName: String = "",
    val selectedBookChapters: Int = 1,
    val selectedChapter: Int = 1,
    val chapterVerses: List<BibleVerse> = emptyList(),
    val selectedVerses: Set<Int> = setOf(1),
    val multiVerseMode: Boolean = false,
    val bibleSearchMode: Boolean = false,
    val bibleSearchQuery: String = "",
    val bibleSearchResults: List<BibleSearchResult> = emptyList(),
    val bibleLoading: Boolean = false,

    // Projects & Saved Shows
    val projects: List<ProjectInfo> = emptyList(),
    val activeProjectId: String = "",
    val projectItems: List<ProjectItem> = emptyList(),
    val savedShowsQuery: String = "",
    val savedShows: List<SavedShowItem> = emptyList(),
    val scheduleShowLibrary: Boolean = false,

    // Stage & Pointer
    val pointerTool: String = "off",
    val pointerX: Float = 0.5f,
    val pointerY: Float = 0.5f,

    // In-App Popup Promotions (Direct HTTPS + Offline PC LAN Relay)
    val promotions: List<MobilePromotion> = emptyList()
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val profileStore = ProfileStore(application)
    private val promotionClient = PromotionClient()

    private val sessionDismissedPromos = mutableSetOf<String>()
    private val recordedPromoImpressions = mutableSetOf<String>()
    private var latestDirectPromos: List<MobilePromotion> = emptyList()
    private var latestRelayedPromos: List<MobilePromotion> = emptyList()

    private val _uiState = MutableStateFlow(
        RemoteUiState(
            nepaliLanguage = profileStore.isNepaliLanguage(),
            nepaliTypingEnabled = profileStore.isNepaliTypingEnabled(),
            volumeNavEnabled = profileStore.isVolumeNavEnabled(),
            keepScreenAwake = profileStore.isKeepScreenAwake(),
            savedProfiles = profileStore.getProfiles()
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val client = HamroRemoteClient(
        context = application.applicationContext,
        onLivePush = { pushedLiveState ->
            lastLiveReceivedMs = System.currentTimeMillis()
            _uiState.update { it.copy(live = pushedLiveState) }
        },
        onPromotionsPush = { promosJson ->
            viewModelScope.launch {
                ingestRelayedPromotions(promosJson)
            }
        }
    )

    private var discoveryJob: Job? = null

    private var hymnSearchJob: Job? = null
    private var bibleSearchJob: Job? = null
    private var showSearchJob: Job? = null
    private var pointerJob: Job? = null
    private var toastClearJob: Job? = null
    private var lastPointerSentMs: Long = 0L
    private var lastLiveReceivedMs: Long = 0L

    init {
        viewModelScope.launch {
            client.status.collect { st ->
                _uiState.update { it.copy(connectionStatus = st) }
            }
        }
        viewModelScope.launch {
            client.connectedHost.collect { host ->
                _uiState.update { it.copy(connectedHost = host) }
            }
        }
        viewModelScope.launch {
            client.lastError.collect { err ->
                if (!err.isNullOrBlank()) {
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }

        // Background resilience loop: keeps live state fresh if running on HTTP fallback
        viewModelScope.launch {
            while (isActive) {
                delay(3500L)
                val status = _uiState.value.connectionStatus
                val stale = System.currentTimeMillis() - lastLiveReceivedMs > 3500L
                if ((status == ConnectionStatus.CONNECTED || status == ConnectionStatus.RECONNECTING) && stale) {
                    refreshLiveStateQuietly()
                }
            }
        }

        // Background software promotions poll (silent no-op when phone is offline on church hotspot)
        viewModelScope.launch {
            delay(2200L)
            refreshDirectPromotions()
            while (isActive) {
                delay(60_000L)
                refreshDirectPromotions()
            }
        }

        // Auto-discover Aradhana Studio PCs on Wi-Fi / Hotspot immediately on launch
        refreshNearbyPcs()
    }

    fun showToast(message: String) {
        toastClearJob?.cancel()
        _uiState.update { it.copy(statusToast = message) }
        toastClearJob = viewModelScope.launch {
            delay(2600L)
            _uiState.update { it.copy(statusToast = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleLanguage() {
        val next = !_uiState.value.nepaliLanguage
        profileStore.setNepaliLanguage(next)
        _uiState.update { it.copy(nepaliLanguage = next) }
    }

    fun toggleNepaliTyping() {
        val next = !_uiState.value.nepaliTypingEnabled
        profileStore.setNepaliTypingEnabled(next)
        _uiState.update { it.copy(nepaliTypingEnabled = next) }
    }

    fun setVolumeNavEnabled(enabled: Boolean) {
        profileStore.setVolumeNavEnabled(enabled)
        _uiState.update { it.copy(volumeNavEnabled = enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        profileStore.setKeepScreenAwake(enabled)
        _uiState.update { it.copy(keepScreenAwake = enabled) }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
        when (tab) {
            MainTab.HYMNS -> if (_uiState.value.hymns.isEmpty()) loadHymns()
            MainTab.BIBLE -> if (_uiState.value.bibleBooks.isEmpty()) loadBibleBooks(_uiState.value.selectedBibleId)
            MainTab.SCHEDULE -> {
                refreshProjects()
                if (_uiState.value.savedShows.isEmpty()) loadSavedShows("")
            }
            else -> Unit
        }
    }

    fun toggleSwipePadMode() {
        _uiState.update { it.copy(swipePadMode = !it.swipePadMode) }
    }

    fun setFullScreenPrompter(enabled: Boolean) {
        _uiState.update {
            it.copy(
                fullScreenPrompter = enabled,
                prompterTouchLocked = if (!enabled) false else it.prompterTouchLocked
            )
        }
    }

    fun togglePrompterTouchLock() {
        _uiState.update { it.copy(prompterTouchLocked = !it.prompterTouchLocked) }
    }

    // --- PAIRING & CONNECTION ---

    fun refreshNearbyPcs() {
        if (_uiState.value.isDiscoveringPcs) return
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _uiState.update { it.copy(isDiscoveringPcs = true) }
            val found = runCatching { client.discoverChurchPcs() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    discoveredPcs = found,
                    isDiscoveringPcs = false
                )
            }
        }
    }

    fun pairDiscoveredPcWithPin(pc: DiscoveredChurchPc, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, connectionStatus = ConnectionStatus.CONNECTING) }
            val res = client.pairWithPin(pc, pin)
            res.onSuccess { p ->
                val existingId = _uiState.value.savedProfiles.firstOrNull {
                    it.name.equals(p.name, ignoreCase = true) || it.ips.any { ip -> ip in p.ips }
                }?.id ?: UUID.randomUUID().toString()

                val profile = ChurchPcProfile(
                    id = existingId,
                    name = p.name,
                    ips = p.ips,
                    preferredIp = p.ips.first(),
                    wsPort = p.wsPort,
                    restPort = p.restPort,
                    token = p.token,
                    lastConnectedAt = System.currentTimeMillis()
                )
                connectWithProfile(profile)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        errorMessage = err.message ?: "PIN pairing failed"
                    )
                }
            }
        }
    }

    fun handleQrScanResult(rawQr: String?) {
        if (rawQr.isNullOrBlank()) return
        when (val parsed = QrPairingParser.parse(rawQr)) {
            is QrParseResult.Invalid -> {
                _uiState.update { it.copy(errorMessage = parsed.reason) }
            }
            is QrParseResult.Success -> {
                val p = parsed.payload
                val existingId = _uiState.value.savedProfiles.firstOrNull {
                    it.name.equals(p.name, ignoreCase = true) || it.ips.any { ip -> ip in p.ips }
                }?.id ?: UUID.randomUUID().toString()

                val profile = ChurchPcProfile(
                    id = existingId,
                    name = p.name,
                    ips = p.ips,
                    preferredIp = p.ips.first(),
                    wsPort = p.wsPort,
                    restPort = p.restPort,
                    token = p.token,
                    lastConnectedAt = System.currentTimeMillis()
                )
                connectWithProfile(profile)
            }
        }
    }

    fun connectManual(nameInput: String, ipInput: String, portInput: String, tokenInput: String) {
        val cleanIp = ipInput.trim()
        val cleanToken = tokenInput.trim()
        val wsPort = portInput.trim().toIntOrNull() ?: 5505
        val ne = _uiState.value.nepaliLanguage

        if (!LanSecurityValidator.isAllowedLocalHost(cleanIp)) {
            _uiState.update {
                it.copy(
                    errorMessage = if (ne) {
                        "सुरक्षा रोक: केवल चर्चको लोकल Wi-Fi वा Hotspot IP (जस्तै 192.168.x.x) मात्र मान्य छ।"
                    } else {
                        "Security block: Only private local Wi-Fi or Hotspot LAN IPs (e.g. 192.168.x.x) are allowed."
                    }
                )
            }
            return
        }

        if (!LanSecurityValidator.isValidPort(wsPort) || !LanSecurityValidator.isValidPort(wsPort + 1)) {
            _uiState.update {
                it.copy(errorMessage = if (ne) "अमान्य पोर्ट नम्बर (1025–65534 बीच राख्नुहोस्)।" else "Invalid port number (must be 1025–65534).")
            }
            return
        }

        if (!LanSecurityValidator.isValidToken(cleanToken)) {
            _uiState.update {
                it.copy(errorMessage = if (ne) "सुरक्षा टोकन कम्तीमा १६ अक्षरको हुनुपर्छ।" else "Security token must be at least 16 characters.")
            }
            return
        }

        val cleanName = nameInput.trim().ifEmpty { "Aradhana Studio ($cleanIp)" }.take(64)
        val existingId = _uiState.value.savedProfiles.firstOrNull {
            it.preferredIp == cleanIp && it.wsPort == wsPort
        }?.id ?: UUID.randomUUID().toString()

        val profile = ChurchPcProfile(
            id = existingId,
            name = cleanName,
            ips = listOf(cleanIp),
            preferredIp = cleanIp,
            wsPort = wsPort,
            restPort = wsPort + 1,
            token = cleanToken,
            lastConnectedAt = System.currentTimeMillis()
        )
        connectWithProfile(profile)
    }

    fun connectWithProfile(profile: ChurchPcProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = client.connect(profile)
            result.onSuccess { activeIp ->
                val updatedProfile = profile.copy(
                    preferredIp = activeIp,
                    lastConnectedAt = System.currentTimeMillis()
                )
                profileStore.saveProfile(updatedProfile)
                _uiState.update {
                    it.copy(
                        savedProfiles = profileStore.getProfiles(),
                        errorMessage = null
                    )
                }
                loadBootstrap()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Connection failed") }
            }
        }
    }

    fun deleteSavedProfile(profileId: String) {
        profileStore.deleteProfile(profileId)
        _uiState.update { it.copy(savedProfiles = profileStore.getProfiles()) }
    }

    fun disconnect() {
        if (_uiState.value.pointerTool != "off") {
            releasePointer()
        }
        client.disconnect()
        _uiState.update {
            it.copy(
                fullScreenPrompter = false,
                prompterTouchLocked = false,
                pointerTool = "off"
            )
        }
    }

    private suspend fun loadBootstrap() {
        val res = client.sendAction("get_mobile_bootstrap", timeoutMs = 6000L)
        res.onSuccess { data ->
            lastLiveReceivedMs = System.currentTimeMillis()
            val hostName = data.optString("hostName", "")
            val version = data.optString("version", "")
            val hymnCounts = RemoteParsers.parseHymnCounts(data.optJSONObject("hymnCounts"))
            val bibles = RemoteParsers.parseBibles(data.optJSONArray("bibles"))
            val activeBibleId = data.optString("activeBibleId", bibles.firstOrNull()?.id.orEmpty())
            val projects = RemoteParsers.parseProjects(data.optJSONArray("projects"))
            val activeProjectId = data.optString("activeProjectId", projects.firstOrNull()?.id.orEmpty())
            val projectItems = RemoteParsers.parseProjectItems(data.optJSONArray("activeProjectItems"))
            val live = RemoteParsers.parseLiveState(data.optJSONObject("live"))
            val relayedPromosArr = data.optJSONArray("promotions")

            _uiState.update {
                it.copy(
                    pcHostName = hostName,
                    pcVersion = version,
                    hymnCounts = hymnCounts,
                    bibles = bibles,
                    selectedBibleId = activeBibleId,
                    projects = projects,
                    activeProjectId = activeProjectId,
                    projectItems = projectItems,
                    live = live
                )
            }

            if (relayedPromosArr != null) {
                ingestRelayedPromotions(relayedPromosArr)
            }

            // Preload initial Hymn list & Bible books so tab switching is instant
            loadHymns()
            if (activeBibleId.isNotEmpty()) {
                loadBibleBooks(activeBibleId)
            }
        }
    }

    private suspend fun refreshLiveStateQuietly() {
        client.sendAction("get_mobile_live_state", timeoutMs = 2500L).onSuccess { data ->
            lastLiveReceivedMs = System.currentTimeMillis()
            _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
        }
    }

    // --- LIVE SLIDE CONTROLS ---

    fun nextSlide() {
        viewModelScope.launch {
            client.sendAction("mobile_next_slide").onSuccess { data ->
                lastLiveReceivedMs = System.currentTimeMillis()
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun prevSlide() {
        viewModelScope.launch {
            client.sendAction("mobile_prev_slide").onSuccess { data ->
                lastLiveReceivedMs = System.currentTimeMillis()
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun selectSlide(slideIndex: Int) {
        val currentLive = _uiState.value.live
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("slideIndex", slideIndex)
                if (currentLive.activeShowId.isNotEmpty() && currentLive.activeShowId != "temp") {
                    put("showId", currentLive.activeShowId)
                }
                if (currentLive.activeLayoutId.isNotEmpty()) {
                    put("layoutId", currentLive.activeLayoutId)
                }
            }
            client.sendAction("mobile_select_slide", params).onSuccess { data ->
                lastLiveReceivedMs = System.currentTimeMillis()
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun clearLayer(layer: String) {
        viewModelScope.launch {
            val params = JSONObject().put("layer", layer)
            client.sendAction("mobile_clear", params).onSuccess { data ->
                lastLiveReceivedMs = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        live = RemoteParsers.parseLiveState(data),
                        pointerTool = if (layer == "all" || layer == "drawing") "off" else it.pointerTool
                    )
                }
            }
        }
    }

    fun toggleOutputLock() {
        val nextLocked = !_uiState.value.live.isLocked
        viewModelScope.launch {
            val params = JSONObject().put("locked", nextLocked)
            client.sendAction("mobile_toggle_lock", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun toggleOutputWindow() {
        val nextEnabled = !_uiState.value.live.outputActive
        viewModelScope.launch {
            val params = JSONObject().put("enabled", nextEnabled)
            client.sendAction("mobile_toggle_output_window", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean {
        val state = _uiState.value
        if (state.connectionStatus != ConnectionStatus.CONNECTED || !state.volumeNavEnabled) {
            return false
        }
        if (isVolumeUp) prevSlide() else nextSlide()
        return true
    }

    // --- BHAJAN / HYMNS ---

    fun updateHymnQuery(displayQuery: String, effectiveQuery: String = displayQuery) {
        _uiState.update { it.copy(hymnQuery = displayQuery) }
        hymnSearchJob?.cancel()
        hymnSearchJob = viewModelScope.launch {
            delay(180L)
            loadHymns(effectiveQuery)
        }
    }

    fun selectHymnCategory(category: String) {
        _uiState.update { it.copy(hymnCategory = category) }
        loadHymns()
    }

    fun loadHymns(overrideQuery: String? = null) {
        val raw = overrideQuery ?: _uiState.value.hymnQuery
        val query = if (_uiState.value.nepaliTypingEnabled) {
            NepaliPhoneticEngine.previewDevanagariQuery(raw)
        } else {
            raw
        }
        val category = _uiState.value.hymnCategory
        viewModelScope.launch {
            _uiState.update { it.copy(hymnLoading = true) }
            val params = JSONObject().apply {
                put("query", query)
                put("category", category)
                put("offset", 0)
                put("limit", 100)
            }
            client.sendAction("get_mobile_hymns", params, timeoutMs = 5000L).onSuccess { data ->
                val items = RemoteParsers.parseHymns(data.optJSONArray("items"))
                val counts = RemoteParsers.parseHymnCounts(data.optJSONObject("categoryCounts"))
                val total = data.optInt("total", items.size)
                _uiState.update {
                    it.copy(
                        hymnLoading = false,
                        hymns = items,
                        hymnsTotal = total,
                        hymnCounts = counts
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(hymnLoading = false) }
            }
        }
    }

    fun appendDialDigit(digit: String) {
        _uiState.update {
            if (it.hymnDialNumber.length >= 5) it else it.copy(hymnDialNumber = it.hymnDialNumber + digit)
        }
    }

    fun backspaceDialDigit() {
        _uiState.update {
            it.copy(hymnDialNumber = it.hymnDialNumber.dropLast(1))
        }
    }

    fun clearDialNumber() {
        _uiState.update { it.copy(hymnDialNumber = "") }
    }

    fun openHymnDetail(id: String? = null, number: String? = null) {
        viewModelScope.launch {
            val params = JSONObject().apply {
                if (!id.isNullOrBlank()) put("id", id)
                if (!number.isNullOrBlank()) put("number", number)
                put("category", _uiState.value.hymnCategory)
            }
            client.sendAction("get_mobile_hymn_detail", params).onSuccess { data ->
                if (data.optBoolean("found", false)) {
                    val detail = RemoteParsers.parseHymnDetail(data.optJSONObject("hymn"))
                    _uiState.update { it.copy(selectedHymnDetail = detail) }
                } else {
                    val ne = _uiState.value.nepaliLanguage
                    showToast(if (ne) "यो नम्बरको भजन भेटिएन" else "Hymn number not found")
                }
            }
        }
    }

    fun dismissHymnDetail() {
        _uiState.update { it.copy(selectedHymnDetail = null) }
    }

    fun projectHymn(
        id: String? = null,
        number: String? = null,
        slideIndex: Int = 0,
        addToProject: Boolean = false,
        goLive: Boolean = true
    ) {
        viewModelScope.launch {
            val params = JSONObject().apply {
                if (!id.isNullOrBlank()) put("id", id)
                if (!number.isNullOrBlank()) put("number", number)
                put("category", _uiState.value.hymnCategory)
                put("slideIndex", slideIndex)
                put("addToProject", addToProject)
                put("goLive", goLive)
            }
            client.sendAction("mobile_project_hymn", params, timeoutMs = 5000L).onSuccess { data ->
                if (data.optBoolean("ok", false)) {
                    val liveObj = data.optJSONObject("live")
                    val title = data.optString("title", "")
                    val ne = _uiState.value.nepaliLanguage
                    _uiState.update {
                        it.copy(
                            live = if (liveObj != null) RemoteParsers.parseLiveState(liveObj) else it.live,
                            hymnDialNumber = if (!number.isNullOrBlank()) "" else it.hymnDialNumber
                        )
                    }
                    if (!goLive && addToProject) {
                        showToast(if (ne) "सूचीमा थपियो: $title" else "Added to schedule: $title")
                    } else {
                        showToast(if (ne) "लाइभ: $title" else "Live: $title")
                    }
                    if (addToProject) refreshProjects()
                } else {
                    val ne = _uiState.value.nepaliLanguage
                    showToast(if (ne) "भजन भेटिएन" else "Hymn not found")
                }
            }
        }
    }

    // --- BIBLE / SCRIPTURE ---

    fun selectBible(bibleId: String) {
        if (bibleId.isBlank() || bibleId == _uiState.value.selectedBibleId) return
        _uiState.update { it.copy(selectedBibleId = bibleId) }
        loadBibleBooks(bibleId)
    }

    fun loadBibleBooks(bibleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(bibleLoading = true) }
            val params = JSONObject().put("bibleId", bibleId)
            client.sendAction("get_mobile_bible_books", params, timeoutMs = 6000L).onSuccess { data ->
                val books = RemoteParsers.parseBibleBooks(data.optJSONArray("books"))
                val firstBook = books.firstOrNull()
                _uiState.update {
                    it.copy(
                        bibleLoading = false,
                        bibleBooks = books,
                        selectedBook = firstBook?.number ?: 1,
                        selectedBookName = firstBook?.name.orEmpty(),
                        selectedBookChapters = firstBook?.chaptersCount ?: 1,
                        selectedChapter = 1,
                        selectedVerses = setOf(1)
                    )
                }
                if (firstBook != null) {
                    loadBibleChapter(bibleId, firstBook.number, 1)
                }
            }.onFailure {
                _uiState.update { it.copy(bibleLoading = false) }
            }
        }
    }

    fun selectBibleBook(book: BibleBook) {
        _uiState.update {
            it.copy(
                selectedBook = book.number,
                selectedBookName = book.name,
                selectedBookChapters = book.chaptersCount,
                selectedChapter = 1,
                selectedVerses = setOf(1)
            )
        }
        loadBibleChapter(_uiState.value.selectedBibleId, book.number, 1)
    }

    fun selectBibleChapter(chapter: Int) {
        _uiState.update { it.copy(selectedChapter = chapter, selectedVerses = setOf(1)) }
        loadBibleChapter(_uiState.value.selectedBibleId, _uiState.value.selectedBook, chapter)
    }

    private fun loadBibleChapter(bibleId: String, book: Int, chapter: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(bibleLoading = true) }
            val params = JSONObject().apply {
                put("bibleId", bibleId)
                put("book", book)
                put("chapter", chapter)
            }
            client.sendAction("get_mobile_bible_chapter", params, timeoutMs = 5000L).onSuccess { data ->
                val verses = RemoteParsers.parseBibleVerses(data.optJSONArray("verses"))
                val bookName = data.optString("bookName", _uiState.value.selectedBookName)
                val chapCount = data.optInt("chaptersCount", _uiState.value.selectedBookChapters)
                _uiState.update {
                    it.copy(
                        bibleLoading = false,
                        selectedBookName = bookName,
                        selectedBookChapters = chapCount.coerceAtLeast(1),
                        chapterVerses = verses,
                        selectedVerses = if (verses.isNotEmpty()) setOf(verses.first().number) else emptySet()
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(bibleLoading = false) }
            }
        }
    }

    fun toggleMultiVerseMode() {
        _uiState.update { state ->
            val nextMulti = !state.multiVerseMode
            val normalizedSelected = if (!nextMulti && state.selectedVerses.size > 1) {
                setOf(state.selectedVerses.minOrNull() ?: 1)
            } else {
                state.selectedVerses
            }
            state.copy(multiVerseMode = nextMulti, selectedVerses = normalizedSelected)
        }
    }

    fun toggleVerseSelection(verseNum: Int, projectImmediately: Boolean = false) {
        if (projectImmediately) {
            _uiState.update { it.copy(selectedVerses = setOf(verseNum), multiVerseMode = false) }
            projectScripture(verses = listOf(verseNum), addToProject = false)
            return
        }

        _uiState.update { state ->
            if (!state.multiVerseMode) {
                // Single-verse mode (default): cleanly switch directly to the chosen verse number!
                state.copy(selectedVerses = setOf(verseNum))
            } else {
                // Multi-verse mode: toggle multiple verses freely
                val next = state.selectedVerses.toMutableSet()
                if (verseNum in next && next.size > 1) {
                    next.remove(verseNum)
                } else {
                    next.add(verseNum)
                }
                state.copy(selectedVerses = next.sorted().toSet())
            }
        }
    }

    fun projectScripture(
        book: Int = _uiState.value.selectedBook,
        chapter: Int = _uiState.value.selectedChapter,
        verses: List<Int> = _uiState.value.selectedVerses.sorted().ifEmpty { listOf(1) },
        addToProject: Boolean = false
    ) {
        viewModelScope.launch {
            val versesArr = JSONArray()
            verses.forEach { versesArr.put(it) }
            val params = JSONObject().apply {
                put("bibleId", _uiState.value.selectedBibleId)
                put("book", book)
                put("chapter", chapter)
                put("verses", versesArr)
                put("addToProject", addToProject)
            }
            client.sendAction("mobile_project_scripture", params, timeoutMs = 5000L).onSuccess { data ->
                val liveObj = data.optJSONObject("live")
                if (liveObj != null) {
                    lastLiveReceivedMs = System.currentTimeMillis()
                    _uiState.update { it.copy(live = RemoteParsers.parseLiveState(liveObj)) }
                }
                val ne = _uiState.value.nepaliLanguage
                showToast(if (ne) "वचन स्क्रिनमा देखाइयो" else "Scripture projected live")
                if (addToProject) refreshProjects()
            }
        }
    }

    fun stepScripture(next: Boolean) {
        viewModelScope.launch {
            val params = JSONObject().put("direction", if (next) "next" else "previous")
            client.sendAction("mobile_scripture_step", params).onSuccess { data ->
                lastLiveReceivedMs = System.currentTimeMillis()
                val parsedLive = RemoteParsers.parseLiveState(data)
                val scObj = data.optJSONObject("scripture")
                val scBook = scObj?.optInt("book", -1) ?: -1
                val scChap = scObj?.optInt("chapter", -1) ?: -1
                val scVersesArr = scObj?.optJSONArray("verses")
                val scVerses = buildSet {
                    if (scVersesArr != null) {
                        for (i in 0 until scVersesArr.length()) {
                            val v = scVersesArr.optInt(i, -1)
                            if (v > 0) add(v)
                        }
                    }
                }
                _uiState.update { st ->
                    val sameBookAndChap = scBook == st.selectedBook && scChap == st.selectedChapter
                    st.copy(
                        live = parsedLive,
                        selectedVerses = if (sameBookAndChap && scVerses.isNotEmpty()) scVerses else st.selectedVerses
                    )
                }
            }
        }
    }

    fun setBibleSearchMode(enabled: Boolean) {
        _uiState.update { it.copy(bibleSearchMode = enabled) }
    }

    fun updateBibleSearchQuery(displayQuery: String, effectiveQuery: String = displayQuery) {
        _uiState.update { it.copy(bibleSearchQuery = displayQuery) }
        bibleSearchJob?.cancel()
        val searchTerm = effectiveQuery.trim()
        if (searchTerm.length < 2) {
            _uiState.update { it.copy(bibleSearchResults = emptyList()) }
            return
        }
        bibleSearchJob = viewModelScope.launch {
            delay(220L)
            _uiState.update { it.copy(bibleLoading = true) }
            val params = JSONObject().apply {
                put("bibleId", _uiState.value.selectedBibleId)
                put("query", searchTerm)
                put("limit", 40)
            }
            client.sendAction("get_mobile_bible_search", params, timeoutMs = 6000L).onSuccess { data ->
                val results = RemoteParsers.parseBibleSearchResults(data.optJSONArray("results"))
                _uiState.update { it.copy(bibleLoading = false, bibleSearchResults = results) }
            }.onFailure {
                _uiState.update { it.copy(bibleLoading = false) }
            }
        }
    }

    // --- PROJECTS & SAVED SHOWS ---

    fun setScheduleLibraryMode(showLibrary: Boolean) {
        _uiState.update { it.copy(scheduleShowLibrary = showLibrary) }
        if (showLibrary && _uiState.value.savedShows.isEmpty()) {
            loadSavedShows(_uiState.value.savedShowsQuery)
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            client.sendAction("get_mobile_projects").onSuccess { data ->
                val projects = RemoteParsers.parseProjects(data.optJSONArray("projects"))
                val activeId = data.optString("activeProjectId", "")
                val items = RemoteParsers.parseProjectItems(data.optJSONArray("activeItems"))
                _uiState.update {
                    it.copy(
                        projects = projects,
                        activeProjectId = activeId,
                        projectItems = items
                    )
                }
            }
        }
    }

    fun selectProject(projectId: String) {
        viewModelScope.launch {
            val params = JSONObject().put("projectId", projectId)
            client.sendAction("mobile_select_project", params).onSuccess { data ->
                val projects = RemoteParsers.parseProjects(data.optJSONArray("projects"))
                val activeId = data.optString("activeProjectId", projectId)
                val items = RemoteParsers.parseProjectItems(data.optJSONArray("activeItems"))
                val liveObj = data.optJSONObject("live")
                _uiState.update {
                    it.copy(
                        projects = projects,
                        activeProjectId = activeId,
                        projectItems = items,
                        live = if (liveObj != null) RemoteParsers.parseLiveState(liveObj) else it.live
                    )
                }
            }
        }
    }

    fun openProjectItem(index: Int, goLive: Boolean = true) {
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("index", index)
                put("goLive", goLive)
            }
            client.sendAction("mobile_open_project_item", params).onSuccess { data ->
                val liveObj = data.optJSONObject("live")
                if (liveObj != null) {
                    lastLiveReceivedMs = System.currentTimeMillis()
                    _uiState.update { it.copy(live = RemoteParsers.parseLiveState(liveObj)) }
                }
            }
        }
    }

    fun updateSavedShowsQuery(displayQuery: String, effectiveQuery: String = displayQuery) {
        _uiState.update { it.copy(savedShowsQuery = displayQuery) }
        showSearchJob?.cancel()
        showSearchJob = viewModelScope.launch {
            delay(200L)
            loadSavedShows(effectiveQuery)
        }
    }

    private fun loadSavedShows(query: String) {
        val effective = if (_uiState.value.nepaliTypingEnabled) {
            NepaliPhoneticEngine.previewDevanagariQuery(query)
        } else {
            query
        }
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("query", effective.trim())
                put("limit", 80)
            }
            client.sendAction("get_mobile_shows", params).onSuccess { data ->
                val items = RemoteParsers.parseSavedShows(data.optJSONArray("items"))
                _uiState.update { it.copy(savedShows = items) }
            }
        }
    }

    fun openSavedShow(showId: String) {
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("showId", showId)
                put("goLive", true)
                put("slideIndex", 0)
            }
            client.sendAction("mobile_open_show", params).onSuccess { data ->
                val liveObj = data.optJSONObject("live")
                if (liveObj != null) {
                    lastLiveReceivedMs = System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            live = RemoteParsers.parseLiveState(liveObj),
                            activeTab = MainTab.LIVE
                        )
                    }
                }
            }
        }
    }

    // --- STAGE PROMPTER, ALERTS, TIMERS & POINTER ---

    fun sendStageAlert(text: String, target: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("text", trimmed)
                put("target", target)
                put("visible", true)
            }
            client.sendAction("mobile_stage_message", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
                val ne = _uiState.value.nepaliLanguage
                showToast(if (ne) "सन्देश पठाइयो" else "Alert displayed")
            }
        }
    }

    fun clearStageAlert() {
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("text", "")
                put("target", "both")
                put("visible", false)
            }
            client.sendAction("mobile_stage_message", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun startQuickCountdown(minutes: Int) {
        viewModelScope.launch {
            val ne = _uiState.value.nepaliLanguage
            val params = JSONObject().apply {
                put("action", "quick_countdown")
                put("seconds", minutes * 60)
                put("name", if (ne) "प्रचार समय ($minutes मि.)" else "Preaching ($minutes min)")
            }
            client.sendAction("mobile_timer_action", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
                showToast(if (ne) "$minutes मिनेट काउन्टडाउन सुरु भयो" else "$minutes min countdown started")
            }
        }
    }

    fun controlTimer(action: String, timerId: String? = null) {
        viewModelScope.launch {
            val params = JSONObject().apply {
                put("action", action)
                if (!timerId.isNullOrBlank()) put("timerId", timerId)
            }
            client.sendAction("mobile_timer_action", params).onSuccess { data ->
                _uiState.update { it.copy(live = RemoteParsers.parseLiveState(data)) }
            }
        }
    }

    fun setPointerTool(tool: String) {
        _uiState.update { it.copy(pointerTool = tool) }
        if (tool == "off") {
            releasePointer()
        } else {
            movePointer(_uiState.value.pointerX, _uiState.value.pointerY, forceImmediate = true)
        }
    }

    fun movePointer(normX: Float, normY: Float, forceImmediate: Boolean = false) {
        val x = normX.coerceIn(0f, 1f)
        val y = normY.coerceIn(0f, 1f)
        val tool = _uiState.value.pointerTool
        _uiState.update { it.copy(pointerX = x, pointerY = y) }
        if (tool == "off") return

        val now = System.currentTimeMillis()
        if (!forceImmediate && now - lastPointerSentMs < 40L) return
        lastPointerSentMs = now

        pointerJob?.cancel()
        pointerJob = viewModelScope.launch {
            val params = JSONObject().apply {
                put("tool", tool)
                put("x", x.toDouble())
                put("y", y.toDouble())
            }
            client.sendAction("mobile_pointer", params, timeoutMs = 1200L)
        }
    }

    fun releasePointer() {
        pointerJob?.cancel()
        viewModelScope.launch {
            client.sendAction("mobile_pointer", JSONObject().put("tool", "off"), timeoutMs = 1500L)
        }
    }

    // --- POPUP PROMOTIONS (DIRECT HTTPS + OFFLINE LAN RELAY) ---

    private suspend fun refreshDirectPromotions() {
        promotionClient.fetchActivePromotions().onSuccess { fetched ->
            latestDirectPromos = fetched
            recomputeVisiblePromotions()
        }
    }

    private suspend fun ingestRelayedPromotions(promosJson: JSONArray) {
        val parsed = PromotionSanitizer.parsePromotionsResponse(promosJson)
        latestRelayedPromos = promotionClient.enrichRelayedPromotions(parsed)
        recomputeVisiblePromotions()
    }

    private fun recomputeVisiblePromotions() {
        val mergedByKey = LinkedHashMap<String, MobilePromotion>()

        // Direct HTTPS promotions take precedence; relayed PC promotions fill in when phone is offline
        for (p in latestDirectPromos) {
            mergedByKey[p.campaignKey] = p
        }
        for (relayed in latestRelayedPromotionsList()) {
            val existing = mergedByKey[relayed.campaignKey]
            if (existing == null) {
                mergedByKey[relayed.campaignKey] = relayed
            } else if (existing.imageBytes == null && relayed.imageBytes != null) {
                mergedByKey[relayed.campaignKey] = existing.copy(
                    imageBytes = relayed.imageBytes,
                    imageMime = relayed.imageMime ?: existing.imageMime
                )
            }
        }

        val visible = mergedByKey.values.sortedBy { it.sortOrder }

        _uiState.update { it.copy(promotions = visible) }
    }

    private fun latestRelayedPromotionsList(): List<MobilePromotion> = latestRelayedPromos

    fun dismissPromotions(items: List<MobilePromotion>) {
        if (items.isEmpty()) return
        val persisted = profileStore.getDismissedPromotions().toMutableMap()
        val today = PromotionSanitizer.todayLocalStamp()
        var dirty = false

        for (promo in items) {
            val key = promo.campaignKey.trim()
            if (key.isEmpty()) continue
            sessionDismissedPromos.add(key)
            when (promo.dismissMode) {
                "once_per_campaign" -> {
                    persisted[key] = "forever"
                    dirty = true
                }
                "once_per_day" -> {
                    persisted[key] = today
                    dirty = true
                }
            }
        }

        if (dirty) {
            profileStore.saveDismissedPromotions(persisted)
        }
        recomputeVisiblePromotions()
    }

    fun recordPromotionImpression(promo: MobilePromotion) {
        val key = promo.campaignKey.trim()
        if (key.isEmpty() || promo.id <= 0) return
        if (!recordedPromoImpressions.add(key)) return

        viewModelScope.launch {
            val ok = promotionClient.recordEvent(promo.id, "impression", promo.eventUrl)
            if (!ok && _uiState.value.connectionStatus == ConnectionStatus.CONNECTED) {
                val params = JSONObject().apply {
                    put("id", promo.id)
                    put("campaignKey", key)
                    put("event", "impression")
                    if (!promo.eventUrl.isNullOrBlank()) put("eventUrl", promo.eventUrl)
                }
                client.sendAction("mobile_promotion_event", params, timeoutMs = 2000L)
            }
        }
    }

    fun recordPromotionClick(promo: MobilePromotion) {
        if (promo.id <= 0) return
        viewModelScope.launch {
            val ok = promotionClient.recordEvent(promo.id, "click", promo.eventUrl)
            if (!ok && _uiState.value.connectionStatus == ConnectionStatus.CONNECTED) {
                val params = JSONObject().apply {
                    put("id", promo.id)
                    put("campaignKey", promo.campaignKey)
                    put("event", "click")
                    if (!promo.eventUrl.isNullOrBlank()) put("eventUrl", promo.eventUrl)
                }
                client.sendAction("mobile_promotion_event", params, timeoutMs = 2000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnect()
    }
}
