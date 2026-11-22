package com.hamrochurch.remote.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Slim Single-Line Active Item Strip (no bulky duplicate live screen box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (!live.cleared.slide && live.currentSlideIndex >= 0) LiveGreen else TextSecondary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = live.activeShowName.ifBlank { AppStrings.noActiveSlide(ne) },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (live.activeShowName.isNotBlank()) Color.White else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (live.slides.isNotEmpty()) {
                val activeNum = if (live.currentSlideIndex >= 0) live.currentSlideIndex + 1 else 0
                Surface(
                    color = SanctuaryCard,
                    border = BorderStroke(0.8.dp, SanctuaryBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$activeNum / ${live.slides.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HamroCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 2. Unified Compact Control Pill Strip (single 28dp row instead of two huge 48dp chip rows)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactToolPill(
                label = AppStrings.clearAll(ne),
                icon = Icons.Default.ClearAll,
                tint = DangerRed,
                bgColor = DangerRed.copy(alpha = 0.13f),
                borderColor = DangerRed.copy(alpha = 0.45f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearLayer("all")
                }
            )

            CompactToolPill(
                label = if (live.outputActive) AppStrings.projectorOn(ne) else AppStrings.projectorOff(ne),
                icon = if (live.outputActive) Icons.Default.Tv else Icons.Default.TvOff,
                tint = if (live.outputActive) LiveGreen else TextSecondary,
                bgColor = if (live.outputActive) LiveGreen.copy(alpha = 0.15f) else SanctuaryCard,
                borderColor = if (live.outputActive) LiveGreen.copy(alpha = 0.45f) else SanctuaryBorder,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleOutputWindow()
                }
            )

            CompactToolPill(
                label = if (live.isLocked) AppStrings.outputLocked(ne) else AppStrings.outputUnlocked(ne),
                icon = if (live.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                tint = if (live.isLocked) WarnAmber else TextSecondary,
                bgColor = if (live.isLocked) WarnAmber.copy(alpha = 0.16f) else SanctuaryCard,
                borderColor = if (live.isLocked) WarnAmber.copy(alpha = 0.5f) else SanctuaryBorder,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLock()
                }
            )

            CompactToolPill(
                label = if (state.swipePadMode) AppStrings.slideListMode(ne) else AppStrings.swipePadMode(ne),
                icon = if (state.swipePadMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.Swipe,
                tint = if (state.swipePadMode) HamroCyan else TextSecondary,
                bgColor = if (state.swipePadMode) HamroCyan.copy(alpha = 0.16f) else SanctuaryCard,
                borderColor = if (state.swipePadMode) HamroCyan.copy(alpha = 0.5f) else SanctuaryBorder,
                onClick = onToggleSwipePad
            )

            val clearActions = listOf(
                "slide" to AppStrings.clearSlide(ne),
                "restore" to AppStrings.clearRestore(ne),
                "background" to AppStrings.clearBg(ne),
                "overlays" to AppStrings.clearOverlays(ne),
                "audio" to AppStrings.clearAudio(ne),
                "timers" to AppStrings.clearTimers(ne)
            )
            clearActions.forEach { (layerKey, label) ->
                CompactToolPill(
                    label = label,
                    icon = null,
                    tint = TextSecondary,
                    bgColor = SanctuaryCard,
                    borderColor = SanctuaryBorder,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearLayer(layerKey)
                    }
                )
            }
        }

        // 3. Clean, Spacious Slide List OR Swipe Pad
        Box(modifier = Modifier.weight(1f)) {
            if (state.swipePadMode) {
                var dragDelta by remember { mutableFloatStateOf(0f) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SanctuarySurface),
                    border = BorderStroke(1.dp, HamroPink.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            Icon(
                                imageVector = Icons.Default.Swipe,
                                contentDescription = null,
                                tint = HamroPink,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = AppStrings.swipeHint(ne),
                                textAlign = TextAlign.Center,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            } else if (live.slides.isEmpty()) {
                // If scripture/temp verse has live text without slide deck, show compact single card; else clean empty state
                if (live.currentSlideText.isNotBlank() && !live.cleared.slide) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                        border = BorderStroke(1.2.dp, HamroPink),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = live.currentGroup.ifBlank { AppStrings.liveOutput(ne) },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HamroCyan
                                )
                                Text(
                                    text = "LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LiveGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = live.currentSlideText,
                                fontSize = 13.5.sp,
                                color = Color.White,
                                lineHeight = 19.sp
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = AppStrings.noActiveSlide(ne),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(live.slides, key = { "${it.index}_${it.id}" }) { slide ->
                        val isCurrent = slide.index == live.currentSlideIndex && !live.cleared.slide
                        val accent = parseSlideColor(slide.color)
                        Surface(
                            color = if (isCurrent) HamroPink.copy(alpha = 0.16f) else SanctuaryCard,
                            border = BorderStroke(
                                width = if (isCurrent) 1.4.dp else 0.8.dp,
                                color = if (isCurrent) HamroPink else SanctuaryBorder
                            ),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(enabled = !slide.disabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectSlide(slide.index)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.5.dp)
                                        .fillMaxHeight()
                                        .background(if (isCurrent) LiveGreen else accent)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${slide.index + 1}. ${slide.group}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) HamroCyan else TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                color = LiveGreen.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "LIVE",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = LiveGreen,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = slide.text.ifBlank { "—" },
                                        fontSize = 13.sp,
                                        color = if (slide.disabled) TextSecondary.copy(alpha = 0.5f) else Color.White,
                                        maxLines = if (isCurrent) 3 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Sleek, Proportionate PREV / NEXT Bar (44dp instead of giant 62dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevSlide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SanctuaryCard, contentColor = Color.White),
                border = BorderStroke(1.dp, SanctuaryBorder),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(3.dp))
                Text(AppStrings.prevBtn(ne), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNextSlide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HamroPink, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
            ) {
                Text(AppStrings.nextBtn(ne), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CompactToolPill(
    label: String,
    icon: ImageVector?,
    tint: Color,
    bgColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (tint == TextSecondary) Color(0xFFE2E8F0) else tint,
            maxLines = 1
        )
    }
}
