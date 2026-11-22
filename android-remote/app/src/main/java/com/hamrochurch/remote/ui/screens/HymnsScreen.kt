package com.hamrochurch.remote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.ui.components.NepaliInputField
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.viewmodel.RemoteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HymnsScreen(
    state: RemoteUiState,
    onQueryChange: (displayQuery: String, effectiveQuery: String) -> Unit,
    onToggleNepaliTyping: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onDialDigit: (String) -> Unit,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onOpenHymnDetail: (id: String?, number: String?) -> Unit,
    onDismissHymnDetail: () -> Unit,
    onProjectHymn: (id: String?, number: String?, slideIndex: Int, addToProject: Boolean, goLive: Boolean) -> Unit
) {
    val ne = state.nepaliLanguage
    val haptics = LocalHapticFeedback.current
    var showDialer by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Search Bar + Quick Dialer Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            NepaliInputField(
                value = state.hymnQuery,
                onValueChange = onQueryChange,
                nepaliTypingEnabled = state.nepaliTypingEnabled,
                onToggleNepaliTyping = onToggleNepaliTyping,
                placeholderText = AppStrings.searchHymnPlaceholder(ne),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { showDialer = !showDialer },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showDialer) HamroPink else SanctuaryCard,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, if (showDialer) HamroPink else SanctuaryBorder),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp)
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = AppStrings.quickDialer(ne), modifier = Modifier.size(19.dp))
            }
        }

        // Collapsible Quick Hymn Number Keypad
        AnimatedVisibility(visible = showDialer) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                border = BorderStroke(1.dp, HamroPink.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.hymnDialNumber.isEmpty()) AppStrings.enterHymnNumber(ne) else "# ${state.hymnDialNumber}",
                            fontSize = if (state.hymnDialNumber.isEmpty()) 13.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.hymnDialNumber.isEmpty()) TextSecondary else HamroCyan
                        )
                        Row {
                            if (state.hymnDialNumber.isNotEmpty()) {
                                IconButton(onClick = onDialBackspace) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                }
                                IconButton(onClick = onDialClear) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    }

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("0")
                    )
                    rows.forEach { rowDigits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowDigits.forEach { digit ->
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDialDigit(digit)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SanctuaryBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(digit, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (state.hymnDialNumber.isNotBlank()) {
                                    onOpenHymnDetail(null, state.hymnDialNumber)
                                }
                            },
                            enabled = state.hymnDialNumber.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(AppStrings.previewBtn(ne))
                        }

                        Button(
                            onClick = {
                                if (state.hymnDialNumber.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onProjectHymn(null, state.hymnDialNumber, 0, false, true)
                                    showDialer = false
                                }
                            },
                            enabled = state.hymnDialNumber.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(AppStrings.goLiveNow(ne), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        val c = state.hymnCounts
        val categories = listOf(
            "all" to "${AppStrings.catAll(ne)} (${c.all})",
            "bhajan" to "${AppStrings.catBhajan(ne)} (${c.bhajan})",
            "chorus" to "${AppStrings.catChorus(ne)} (${c.chorus})",
            "children" to "${AppStrings.catChildren(ne)} (${c.children})",
            "new" to "${AppStrings.catNew(ne)} (${c.newSongs})"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { (catId, label) ->
                val selected = state.hymnCategory == catId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) HamroPink.copy(alpha = 0.22f) else SanctuaryCard)
                        .border(
                            width = 0.9.dp,
                            color = if (selected) HamroPink else SanctuaryBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectCategory(catId) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else TextSecondary
                    )
                }
            }
        }

        // Hymn List
        if (state.hymnLoading && state.hymns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HamroPink, modifier = Modifier.size(28.dp))
            }
        } else if (state.hymns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(AppStrings.noHymnsFound(ne), color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.hymns, key = { it.id }) { hymn ->
                    Surface(
                        color = SanctuaryCard,
                        border = BorderStroke(0.8.dp, SanctuaryBorder),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onOpenHymnDetail(hymn.id, null) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hymn.number.isNotBlank()) {
                                Surface(
                                    color = HamroPink.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = hymn.number,
                                        color = Color(0xFF7DD3FC),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hymn.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = hymn.transliteration.ifBlank { hymn.firstLine }
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onProjectHymn(hymn.id, null, 0, true, false)
                                },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = AppStrings.addToSchedule(ne), tint = HamroCyan, modifier = Modifier.size(18.dp))
                                }
                            }

                            Surface(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onProjectHymn(hymn.id, null, 0, false, true)
                                },
                                color = HamroPink.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = AppStrings.goLiveNow(ne), tint = HamroPink, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Hymn Detail & Verse-by-Verse Launcher BottomSheet
    val detail = state.selectedHymnDetail
    if (detail != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissHymnDetail,
            containerColor = SanctuarySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (detail.number.isNotBlank()) "#${detail.number} — ${detail.title}" else detail.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tapVerseToLive(ne),
                    fontSize = 12.sp,
                    color = HamroCyan
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            onProjectHymn(detail.id, null, 0, true, false)
                            onDismissHymnDetail()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(AppStrings.addToSchedule(ne))
                    }

                    Button(
                        onClick = {
                            onProjectHymn(detail.id, null, 0, true, true)
                            onDismissHymnDetail()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(AppStrings.goLiveNow(ne), fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(360.dp)
                ) {
                    items(detail.verses, key = { it.index }) { verse ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                            border = BorderStroke(1.dp, SanctuaryBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onProjectHymn(detail.id, null, verse.index, false, true)
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = verse.group.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HamroPink
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = verse.text,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
