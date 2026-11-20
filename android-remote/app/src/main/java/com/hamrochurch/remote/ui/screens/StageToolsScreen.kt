package com.hamrochurch.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.nepali.NepaliPhoneticEngine
import com.hamrochurch.remote.ui.components.NepaliInputField
import com.hamrochurch.remote.ui.theme.DangerRed
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryBg
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.ui.theme.WarnAmber
import com.hamrochurch.remote.viewmodel.RemoteUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun formatSecondsClock(seconds: Double?): String {
    val total = (seconds ?: 0.0).toInt()
    val sign = if (total < 0) "-" else ""
    val absSec = abs(total)
    val mins = absSec / 60
    val secs = absSec % 60
    return "%s%02d:%02d".format(sign, mins, secs)
}

@Composable
fun StageToolsScreen(
    state: RemoteUiState,
    onOpenPrompter: () -> Unit,
    onSendStageAlert: (text: String, target: String) -> Unit,
    onClearStageAlert: () -> Unit,
    onToggleNepaliTyping: () -> Unit,
    onStartCountdown: (minutes: Int) -> Unit,
    onTimerControl: (action: String, timerId: String?) -> Unit,
    onSetPointerTool: (String) -> Unit,
    onMovePointer: (Float, Float) -> Unit,
    onReleasePointer: () -> Unit,
    onSetVolumeNav: (Boolean) -> Unit,
    onSetKeepAwake: (Boolean) -> Unit
) {
    val ne = state.nepaliLanguage
    var alertText by rememberSaveable { mutableStateOf("") }
    var alertTarget by rememberSaveable { mutableStateOf("stage") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Full-Screen Stage Prompter Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.5.dp, HamroPink.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = AppStrings.stagePrompterDesc(ne),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenPrompter,
                    colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.openFullStagePrompter(ne), fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Stage & Projector Alert Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, SanctuaryBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = WarnAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.stageAlertTitle(ne), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                NepaliInputField(
                    value = alertText,
                    onValueChange = { display, _ -> alertText = display.take(200) },
                    nepaliTypingEnabled = state.nepaliTypingEnabled,
                    onToggleNepaliTyping = onToggleNepaliTyping,
                    placeholderText = AppStrings.stageAlertPlaceholder(ne),
                    maxLength = 200,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = alertTarget == "stage",
                        onClick = { alertTarget = "stage" },
                        label = { Text(AppStrings.targetStageOnly(ne), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = alertTarget == "output",
                        onClick = { alertTarget = "output" },
                        label = { Text(AppStrings.targetOutputOnly(ne), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = alertTarget == "both",
                        onClick = { alertTarget = "both" },
                        label = { Text(AppStrings.targetBoth(ne), fontSize = 12.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onClearStageAlert,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(AppStrings.hideAlert(ne))
                    }
                    Button(
                        onClick = {
                            val finalMsg = if (state.nepaliTypingEnabled) {
                                NepaliPhoneticEngine.previewDevanagariQuery(alertText)
                            } else {
                                alertText
                            }
                            alertText = finalMsg
                            onSendStageAlert(finalMsg, alertTarget)
                        },
                        enabled = alertText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = WarnAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(AppStrings.showAlert(ne), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Preaching Countdown Timers
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, SanctuaryBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = HamroCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.preachingTimerTitle(ne), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                val activeTimer = state.live.timers.firstOrNull { it.isActive }
                if (activeTimer != null) {
                    Surface(
                        color = SanctuarySurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(activeTimer.name, fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    text = formatSecondsClock(activeTimer.currentTime),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LiveGreen
                                )
                            }
                            Row {
                                IconButton(onClick = { onTimerControl("pause", activeTimer.id) }) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = WarnAmber)
                                }
                                IconButton(onClick = { onTimerControl("stop", activeTimer.id) }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = DangerRed)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 30, 45).forEach { mins ->
                        OutlinedButton(
                            onClick = { onStartCountdown(mins) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SanctuaryBorder)
                        ) {
                            Text("${mins}m", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. 16:9 Laser Pointer & Spotlight Touchpad
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, SanctuaryBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(AppStrings.pointerTitle(ne), fontWeight = FontWeight.Bold, fontSize = 15.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "off" to AppStrings.pointerOff(ne),
                        "pointer" to AppStrings.pointerLaser(ne),
                        "focus" to AppStrings.pointerFocus(ne),
                        "zoom" to AppStrings.pointerZoom(ne)
                    ).forEach { (toolId, label) ->
                        FilterChip(
                            selected = state.pointerTool == toolId,
                            onClick = { onSetPointerTool(toolId) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                if (state.pointerTool != "off") {
                    Text(AppStrings.pointerTouchHint(ne), fontSize = 12.sp, color = HamroCyan)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SanctuaryBg)
                            .pointerInput(state.pointerTool) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        onMovePointer(nx, ny)
                                        tryAwaitRelease()
                                    }
                                )
                            }
                            .pointerInput(state.pointerTool) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        onMovePointer(nx, ny)
                                    },
                                    onDragEnd = {
                                        if (state.pointerTool == "pointer") onReleasePointer()
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val px = state.pointerX * size.width
                            val py = state.pointerY * size.height
                            drawCircle(
                                color = HamroPink.copy(alpha = 0.35f),
                                radius = 34.dp.toPx(),
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = HamroPink,
                                radius = 10.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }

        // 5. Device Preferences
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, SanctuaryBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.volumeKeyToggle(ne), fontSize = 14.sp)
                    Switch(checked = state.volumeNavEnabled, onCheckedChange = onSetVolumeNav)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.keepScreenOnToggle(ne), fontSize = 14.sp)
                    Switch(checked = state.keepScreenAwake, onCheckedChange = onSetKeepAwake)
                }
            }
        }
    }
}

@Composable
fun StagePrompterOverlay(
    state: RemoteUiState,
    onClose: () -> Unit,
    onToggleTouchLock: () -> Unit,
    onPrevSlide: () -> Unit,
    onNextSlide: () -> Unit
) {
    val ne = state.nepaliLanguage
    val live = state.live
    var clockText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(1000L)
        }
    }

    val activeTimer = live.timers.firstOrNull { it.isActive }
    val timerSec = activeTimer?.currentTime
    val timerColor = when {
        timerSec == null -> LiveGreen
        timerSec <= 30.0 -> DangerRed
        timerSec <= 120.0 -> WarnAmber
        else -> LiveGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070C))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Clock + Countdown + Touch Lock + Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(clockText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                if (activeTimer != null) {
                    Surface(
                        color = timerColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, timerColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = formatSecondsClock(timerSec),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = timerColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTouchLock) {
                    Icon(
                        imageVector = if (state.prompterTouchLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Touch Lock",
                        tint = if (state.prompterTouchLocked) WarnAmber else TextSecondary
                    )
                }
                if (!state.prompterTouchLocked) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // Urgent Stage Alert Banner
        if (live.stageMessage.visible && live.stageMessage.text.isNotBlank()) {
            Surface(
                color = WarnAmber,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = live.stageMessage.text,
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Giant Current Lyrics + Chords/Notes
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (live.currentGroup.isNotBlank()) {
                Text(
                    text = live.currentGroup.uppercase(),
                    color = HamroPink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = live.currentSlideText.ifBlank { AppStrings.noActiveSlide(ne) },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp
            )

            if (live.currentSlideNotes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = live.currentSlideNotes,
                    color = WarnAmber,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Next Slide Preview + Controls
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (live.nextSlideText.isNotBlank()) {
                Surface(
                    color = SanctuaryCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${AppStrings.nextSlideLabel(ne)} (${live.nextGroup})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HamroCyan
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = live.nextSlideText,
                            fontSize = 18.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 2
                        )
                    }
                }
            }

            if (state.prompterTouchLocked) {
                Text(
                    text = AppStrings.touchLocked(ne),
                    color = WarnAmber,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrevSlide,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                        Text(AppStrings.prevBtn(ne), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onNextSlide,
                        colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f).height(56.dp)
                    ) {
                        Text(AppStrings.nextBtn(ne), fontWeight = FontWeight.ExtraBold)
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    }
                }
            }
        }
    }
}
