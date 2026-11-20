package com.hamrochurch.remote

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.model.ConnectionStatus
import com.hamrochurch.remote.model.MainTab
import com.hamrochurch.remote.ui.QrCaptureActivity
import com.hamrochurch.remote.ui.components.MobilePromotionOverlay
import com.hamrochurch.remote.ui.screens.BibleScreen
import com.hamrochurch.remote.ui.screens.ConnectScreen
import com.hamrochurch.remote.ui.screens.HymnsScreen
import com.hamrochurch.remote.ui.screens.LiveControlScreen
import com.hamrochurch.remote.ui.screens.ScheduleScreen
import com.hamrochurch.remote.ui.screens.StagePrompterOverlay
import com.hamrochurch.remote.ui.screens.StageToolsScreen
import com.hamrochurch.remote.ui.theme.HamroChurchRemoteTheme
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.ui.theme.WarnAmber
import com.hamrochurch.remote.viewmodel.RemoteUiState
import com.hamrochurch.remote.viewmodel.RemoteViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.keepScreenAwake) {
                if (state.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                if (!result.contents.isNullOrBlank()) {
                    viewModel.handleQrScanResult(result.contents)
                }
            }

            HamroChurchRemoteTheme {
                HamroRemoteRoot(
                    state = state,
                    onLaunchQrScanner = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(AppStrings.scanQr(state.nepaliLanguage))
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                            setCaptureActivity(QrCaptureActivity::class.java)
                        }
                        qrLauncher.launch(options)
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun shouldInterceptVolumeKey(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (currentFocus is EditText) return false
        val st = viewModel.uiState.value
        return st.connectionStatus == ConnectionStatus.CONNECTED && st.volumeNavEnabled && !st.prompterTouchLocked
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (shouldInterceptVolumeKey(keyCode)) {
            if (event?.repeatCount == 0) {
                viewModel.handleVolumeKey(isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (shouldInterceptVolumeKey(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun HamroRemoteRoot(
    state: RemoteUiState,
    onLaunchQrScanner: () -> Unit,
    viewModel: RemoteViewModel
) {
    val ne = state.nepaliLanguage
    val isActiveSession = state.connectionStatus == ConnectionStatus.CONNECTED ||
        state.connectionStatus == ConnectionStatus.RECONNECTING

    if (!isActiveSession) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConnectScreen(
                state = state,
                onToggleLanguage = viewModel::toggleLanguage,
                onLaunchQrScanner = onLaunchQrScanner,
                onConnectManual = viewModel::connectManual,
                onConnectProfile = viewModel::connectWithProfile,
                onDeleteProfile = viewModel::deleteSavedProfile,
                onShowError = viewModel::showToast
            )
            MobilePromotionOverlay(
                promotions = state.promotions,
                nepaliLanguage = ne,
                hasBottomNavBar = false,
                onDismissAll = viewModel::dismissPromotions,
                onImpression = viewModel::recordPromotionImpression,
                onClickPromotion = viewModel::recordPromotionClick
            )
        }
        return
    }

    if (state.fullScreenPrompter) {
        StagePrompterOverlay(
            state = state,
            onClose = { viewModel.setFullScreenPrompter(false) },
            onToggleTouchLock = viewModel::togglePrompterTouchLock,
            onPrevSlide = viewModel::prevSlide,
            onNextSlide = viewModel::nextSlide
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = SanctuarySurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val dotColor = if (state.connectionStatus == ConnectionStatus.CONNECTED) LiveGreen else WarnAmber
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = state.pcHostName.ifBlank { AppStrings.appTitle(ne) },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (state.connectionStatus == ConnectionStatus.RECONNECTING) {
                                    "${AppStrings.reconnecting(ne)} (${state.connectedHost})"
                                } else {
                                    state.connectedHost
                                },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::toggleLanguage) {
                            Text(if (ne) "EN" else "ने", fontWeight = FontWeight.Bold, color = HamroPink)
                        }
                        IconButton(onClick = { viewModel.setFullScreenPrompter(true) }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Prompter", tint = Color.White)
                        }
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = AppStrings.disconnect(ne), tint = TextSecondary)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = SanctuarySurface) {
                val tabs = listOf(
                    Triple(MainTab.LIVE, AppStrings.tabLive(ne), Icons.Default.PresentToAll),
                    Triple(MainTab.HYMNS, AppStrings.tabHymns(ne), Icons.Default.MusicNote),
                    Triple(MainTab.BIBLE, AppStrings.tabBible(ne), Icons.AutoMirrored.Filled.MenuBook),
                    Triple(MainTab.SCHEDULE, AppStrings.tabSchedule(ne), Icons.AutoMirrored.Filled.QueueMusic),
                    Triple(MainTab.STAGE, AppStrings.tabStage(ne), Icons.Default.Tune)
                )

                tabs.forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = HamroPink,
                            indicatorColor = HamroPink.copy(alpha = 0.25f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                MainTab.LIVE -> LiveControlScreen(
                    state = state,
                    onNextSlide = viewModel::nextSlide,
                    onPrevSlide = viewModel::prevSlide,
                    onSelectSlide = viewModel::selectSlide,
                    onClearLayer = viewModel::clearLayer,
                    onToggleLock = viewModel::toggleOutputLock,
                    onToggleOutputWindow = viewModel::toggleOutputWindow,
                    onToggleSwipePad = viewModel::toggleSwipePadMode
                )

                MainTab.HYMNS -> HymnsScreen(
                    state = state,
                    onQueryChange = viewModel::updateHymnQuery,
                    onToggleNepaliTyping = viewModel::toggleNepaliTyping,
                    onSelectCategory = viewModel::selectHymnCategory,
                    onDialDigit = viewModel::appendDialDigit,
                    onDialBackspace = viewModel::backspaceDialDigit,
                    onDialClear = viewModel::clearDialNumber,
                    onOpenHymnDetail = viewModel::openHymnDetail,
                    onDismissHymnDetail = viewModel::dismissHymnDetail,
                    onProjectHymn = viewModel::projectHymn
                )

                MainTab.BIBLE -> BibleScreen(
                    state = state,
                    onSelectBible = viewModel::selectBible,
                    onSelectBook = viewModel::selectBibleBook,
                    onSelectChapter = viewModel::selectBibleChapter,
                    onToggleMultiVerseMode = viewModel::toggleMultiVerseMode,
                    onToggleVerse = viewModel::toggleVerseSelection,
                    onProjectSelected = { addToProj -> viewModel.projectScripture(addToProject = addToProj) },
                    onProjectDirect = { book, chapter, verse ->
                        viewModel.projectScripture(book = book, chapter = chapter, verses = listOf(verse))
                    },
                    onStepScripture = viewModel::stepScripture,
                    onSetSearchMode = viewModel::setBibleSearchMode,
                    onSearchQueryChange = viewModel::updateBibleSearchQuery,
                    onToggleNepaliTyping = viewModel::toggleNepaliTyping
                )

                MainTab.SCHEDULE -> ScheduleScreen(
                    state = state,
                    onSetLibraryMode = viewModel::setScheduleLibraryMode,
                    onSelectProject = viewModel::selectProject,
                    onRefreshProjects = viewModel::refreshProjects,
                    onOpenProjectItem = { idx -> viewModel.openProjectItem(idx, true) },
                    onSavedShowsQueryChange = viewModel::updateSavedShowsQuery,
                    onToggleNepaliTyping = viewModel::toggleNepaliTyping,
                    onOpenSavedShow = viewModel::openSavedShow
                )

                MainTab.STAGE -> StageToolsScreen(
                    state = state,
                    onOpenPrompter = { viewModel.setFullScreenPrompter(true) },
                    onSendStageAlert = viewModel::sendStageAlert,
                    onClearStageAlert = viewModel::clearStageAlert,
                    onToggleNepaliTyping = viewModel::toggleNepaliTyping,
                    onStartCountdown = viewModel::startQuickCountdown,
                    onTimerControl = viewModel::controlTimer,
                    onSetPointerTool = viewModel::setPointerTool,
                    onMovePointer = { x, y -> viewModel.movePointer(x, y) },
                    onReleasePointer = viewModel::releasePointer,
                    onSetVolumeNav = viewModel::setVolumeNavEnabled,
                    onSetKeepAwake = viewModel::setKeepScreenAwake
                )
            }

            // Floating Toast Feedback Banner
            AnimatedVisibility(
                visible = !state.statusToast.isNullOrBlank(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Surface(
                    color = SanctuaryCard,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = state.statusToast.orEmpty(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    MobilePromotionOverlay(
        promotions = state.promotions,
        nepaliLanguage = ne,
        hasBottomNavBar = true,
        onDismissAll = viewModel::dismissPromotions,
        onImpression = viewModel::recordPromotionImpression,
        onClickPromotion = viewModel::recordPromotionClick
    )
    }
}
