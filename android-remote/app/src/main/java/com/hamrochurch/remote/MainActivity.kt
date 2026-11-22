package com.hamrochurch.remote

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.ui.theme.WarnAmber
import com.hamrochurch.remote.viewmodel.RemoteUiState
import com.hamrochurch.remote.viewmodel.RemoteViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
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

            val qrLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val qrText = result.data?.getStringExtra(QrCaptureActivity.EXTRA_QR_CONTENT)
                    if (!qrText.isNullOrBlank()) {
                        viewModel.handleQrScanResult(qrText)
                    }
                }
            }

            HamroChurchRemoteTheme {
                HamroRemoteRoot(
                    state = state,
                    onLaunchQrScanner = {
                        val scanIntent = Intent(this@MainActivity, QrCaptureActivity::class.java).apply {
                            putExtra(QrCaptureActivity.EXTRA_NEPALI, state.nepaliLanguage)
                        }
                        qrLauncher.launch(scanIntent)
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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                MobilePromotionOverlay(
                    promotions = state.promotions,
                    nepaliLanguage = ne,
                    hasBottomNavBar = false,
                    onDismissAll = viewModel::dismissPromotions,
                    onImpression = viewModel::recordPromotionImpression,
                    onClickPromotion = viewModel::recordPromotionClick
                )
            }
        ) { innerPadding ->
            ConnectScreen(
                state = state,
                onToggleLanguage = viewModel::toggleLanguage,
                onLaunchQrScanner = onLaunchQrScanner,
                onConnectManual = viewModel::connectManual,
                onConnectProfile = viewModel::connectWithProfile,
                onDeleteProfile = viewModel::deleteSavedProfile,
                onRefreshNearbyPcs = viewModel::refreshNearbyPcs,
                onPairDiscoveredPc = viewModel::pairDiscoveredPcWithPin,
                onShowError = viewModel::showToast,
                modifier = Modifier.padding(innerPadding)
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = state.pcHostName.ifBlank { AppStrings.appTitle(ne) },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val hostShort = state.connectedHost.substringBefore(':')
                                if (hostShort.isNotBlank()) {
                                    Text(
                                        text = if (state.connectionStatus == ConnectionStatus.RECONNECTING) {
                                            " · ${AppStrings.reconnecting(ne)}"
                                        } else {
                                            " · $hostShort"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = viewModel::toggleLanguage,
                                    color = SanctuaryCard,
                                    border = BorderStroke(1.dp, SanctuaryBorder),
                                    shape = RoundedCornerShape(7.dp)
                                ) {
                                    Text(
                                        text = if (ne) "EN" else "ने",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HamroPink,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    onClick = { viewModel.setFullScreenPrompter(true) },
                                    color = Color.Transparent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Fullscreen,
                                            contentDescription = "Prompter",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = viewModel::disconnect,
                                    color = Color.Transparent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.LinkOff,
                                            contentDescription = AppStrings.disconnect(ne),
                                            tint = TextSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.7.dp, color = SanctuaryBorder.copy(alpha = 0.65f))
                    }
                }
            },
            bottomBar = {
                Surface(color = SanctuarySurface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        MobilePromotionOverlay(
                            promotions = state.promotions,
                            nepaliLanguage = ne,
                            hasBottomNavBar = true,
                            onDismissAll = viewModel::dismissPromotions,
                            onImpression = viewModel::recordPromotionImpression,
                            onClickPromotion = viewModel::recordPromotionClick
                        )
                        HorizontalDivider(thickness = 0.7.dp, color = SanctuaryBorder.copy(alpha = 0.7f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                Triple(MainTab.LIVE, AppStrings.tabLive(ne), Icons.Default.PresentToAll),
                                Triple(MainTab.HYMNS, AppStrings.tabHymns(ne), Icons.Default.MusicNote),
                                Triple(MainTab.BIBLE, AppStrings.tabBible(ne), Icons.AutoMirrored.Filled.MenuBook),
                                Triple(MainTab.SCHEDULE, AppStrings.tabSchedule(ne), Icons.AutoMirrored.Filled.QueueMusic),
                                Triple(MainTab.STAGE, AppStrings.tabStage(ne), Icons.Default.Tune)
                            )

                            tabs.forEach { (tab, label, icon) ->
                                val selected = state.activeTab == tab
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.selectTab(tab) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) HamroPink.copy(alpha = 0.2f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (selected) HamroPink else TextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) HamroPink else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
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

                AnimatedVisibility(
                    visible = !state.statusToast.isNullOrBlank(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                ) {
                    Surface(
                        color = SanctuaryCard,
                        border = BorderStroke(1.dp, SanctuaryBorder),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = state.statusToast.orEmpty(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
