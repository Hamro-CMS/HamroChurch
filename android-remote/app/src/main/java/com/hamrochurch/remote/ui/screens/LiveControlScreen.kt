package com.hamrochurch.remote.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.ui.theme.DangerRed
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.ui.theme.WarnAmber
import com.hamrochurch.remote.viewmodel.RemoteUiState

private fun parseSlideColor(hex: String): Color {
    if (hex.isBlank()) return HamroPink
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        HamroPink
    }
}

@Composable
fun LiveControlScreen(
    state: RemoteUiState,
    onNextSlide: () -> Unit,
    onPrevSlide: () -> Unit,
    onSelectSlide: (Int) -> Unit,
    onClearLayer: (String) -> Unit,
    onToggleLock: () -> Unit,
    onToggleOutputWindow: () -> Unit,
    onToggleSwipePad: () -> Unit
) {
    val ne = state.nepaliLanguage
    val live = state.live
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    LaunchedEffect(live.currentSlideIndex, live.activeShowId) {
        if (live.currentSlideIndex >= 0 && live.currentSlideIndex < live.slides.size) {
            listState.animateScrollToItem(live.currentSlideIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Live Output Monitor Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(
                1.5.dp,
                if (!live.cleared.slide && live.currentSlideText.isNotBlank()) LiveGreen.copy(alpha = 0.7f) else SanctuaryBorder
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (!live.cleared.all) LiveGreen else TextSecondary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = live.activeShowName.ifBlank { AppStrings.liveOutput(ne) },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (live.currentGroup.isNotBlank()) {
                        Surface(
                            color = HamroPink.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = live.currentGroup,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7DD3FC),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = live.currentSlideText.ifBlank { AppStrings.noActiveSlide(ne) },
                    fontSize = 16.sp,
                    fontWeight = if (live.currentSlideText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                    color = if (live.currentSlideText.isNotBlank()) Color.White else TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                if (live.nextSlideText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${AppStrings.nextSlideLabel(ne)}: ${live.nextSlideText.replace('\n', ' ')}",
                        fontSize = 12.sp,
                        color = HamroCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Output Controls & Mode Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = live.isLocked,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLock()
                },
                label = { Text(if (live.isLocked) AppStrings.outputLocked(ne) else AppStrings.outputUnlocked(ne), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = if (live.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WarnAmber.copy(alpha = 0.25f),
                    selectedLabelColor = WarnAmber
                )
            )

            FilterChip(
                selected = live.outputActive,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleOutputWindow()
                },
                label = { Text(if (live.outputActive) AppStrings.projectorOn(ne) else AppStrings.projectorOff(ne), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = if (live.outputActive) Icons.Default.Tv else Icons.Default.TvOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LiveGreen.copy(alpha = 0.22f),
                    selectedLabelColor = LiveGreen
                )
            )

            FilterChip(
                selected = state.swipePadMode,
                onClick = onToggleSwipePad,
                label = { Text(if (state.swipePadMode) AppStrings.slideListMode(ne) else AppStrings.swipePadMode(ne), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.swipePadMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.Swipe,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        // 3. Quick Clear Layer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearLayer("all")
                },
                label = { Text(AppStrings.clearAll(ne), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed) },
                colors = AssistChipDefaults.assistChipColors(containerColor = DangerRed.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f))
            )

            val clearActions = listOf(
                "restore" to AppStrings.clearRestore(ne),
                "slide" to AppStrings.clearSlide(ne),
                "background" to AppStrings.clearBg(ne),
                "overlays" to AppStrings.clearOverlays(ne),
                "audio" to AppStrings.clearAudio(ne),
                "timers" to AppStrings.clearTimers(ne)
            )

            clearActions.forEach { (layerKey, label) ->
                AssistChip(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearLayer(layerKey)
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    border = BorderStroke(1.dp, SanctuaryBorder)
                )
            }
        }

        // 4. Main Slide Deck OR Blind Swipe Pad
        Box(modifier = Modifier.weight(1f)) {
            if (state.swipePadMode) {
                var dragDelta by remember { mutableFloatStateOf(0f) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SanctuarySurface),
                    border = BorderStroke(1.5.dp, HamroPink.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNextSlide()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragDelta = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragDelta += dragAmount },
                                onDragEnd = {
                                    if (dragDelta < -70f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNextSlide()
                                    } else if (dragDelta > 70f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPrevSlide()
                                    }
                                    dragDelta = 0f
                                }
                            )
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Swipe,
                                contentDescription = null,
                                tint = HamroPink,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = AppStrings.swipeHint(ne),
                                textAlign = TextAlign.Center,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(live.slides, key = { "${it.index}_${it.id}" }) { slide ->
                        val isCurrent = slide.index == live.currentSlideIndex && !live.cleared.slide
                        val accent = parseSlideColor(slide.color)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) HamroPink.copy(alpha = 0.2f) else SanctuaryCard
                            ),
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) HamroPink else SanctuaryBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !slide.disabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectSlide(slide.index)
                                }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(68.dp)
                                        .background(accent)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${slide.index + 1}. ${slide.group}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) Color(0xFF7DD3FC) else TextSecondary
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = "LIVE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = LiveGreen
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = slide.text.ifBlank { "—" },
                                        fontSize = 14.sp,
                                        color = if (slide.disabled) TextSecondary.copy(alpha = 0.5f) else Color.White,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Giant Tactile PREV / NEXT Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevSlide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SanctuaryCard, contentColor = Color.White),
                border = BorderStroke(1.dp, SanctuaryBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(4.dp))
                Text(AppStrings.prevBtn(ne), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNextSlide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HamroPink, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
            ) {
                Text(AppStrings.nextBtn(ne), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}
