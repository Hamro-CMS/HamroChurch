package com.hamrochurch.remote.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.model.BibleBook
import com.hamrochurch.remote.ui.components.NepaliInputField
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.viewmodel.RemoteUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleScreen(
    state: RemoteUiState,
    onSelectBible: (String) -> Unit,
    onSelectBook: (BibleBook) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleMultiVerseMode: () -> Unit,
    onToggleVerse: (verseNum: Int, projectImmediately: Boolean) -> Unit,
    onProjectSelected: (addToProject: Boolean) -> Unit,
    onProjectDirect: (book: Int, chapter: Int, verse: Int) -> Unit,
    onStepScripture: (next: Boolean) -> Unit,
    onSetSearchMode: (Boolean) -> Unit,
    onSearchQueryChange: (displayQuery: String, effectiveQuery: String) -> Unit,
    onToggleNepaliTyping: () -> Unit
) {
    val ne = state.nepaliLanguage
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val verseListState = rememberLazyListState()

    var bibleMenuExpanded by remember { mutableStateOf(false) }
    var bookSheetOpen by remember { mutableStateOf(false) }
    var verseGridSheetOpen by remember { mutableStateOf(false) }

    val activeBibleName = state.bibles.firstOrNull { it.id == state.selectedBibleId }?.name ?: "Bible"

    fun pickVerseAndScroll(verseNumber: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleVerse(verseNumber, false)
        val idx = state.chapterVerses.indexOfFirst { it.number == verseNumber }
        if (idx >= 0) {
            coroutineScope.launch {
                verseListState.animateScrollToItem(idx)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Translation Picker + Browse / Search Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { bibleMenuExpanded = true },
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(0.9.dp, SanctuaryBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp), tint = HamroCyan)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = activeBibleName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = bibleMenuExpanded,
                    onDismissRequest = { bibleMenuExpanded = false }
                ) {
                    state.bibles.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            onClick = {
                                bibleMenuExpanded = false
                                onSelectBible(b.id)
                            }
                        )
                    }
                }
            }

            listOf(false to AppStrings.browseBooks(ne), true to AppStrings.searchScripture(ne)).forEach { (isSearch, label) ->
                val selected = state.bibleSearchMode == isSearch
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) HamroPink.copy(alpha = 0.22f) else SanctuaryCard)
                        .border(0.9.dp, if (selected) HamroPink else SanctuaryBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetSearchMode(isSearch) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
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

        if (state.bibleSearchMode) {
            NepaliInputField(
                value = state.bibleSearchQuery,
                onValueChange = onSearchQueryChange,
                nepaliTypingEnabled = state.nepaliTypingEnabled,
                onToggleNepaliTyping = onToggleNepaliTyping,
                placeholderText = AppStrings.searchBiblePlaceholder(ne),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.bibleLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HamroPink)
                }
            } else if (state.bibleSearchQuery.trim().length >= 2 && state.bibleSearchResults.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (ne) "कुनै मिल्दो बाइबल पद भेटिएन" else "No matching Bible verses found",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.bibleSearchResults, key = { "${it.book}_${it.chapter}_${it.verse}" }) { res ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                            border = BorderStroke(1.dp, SanctuaryBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onProjectDirect(res.book, res.chapter, res.verse)
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${res.bookName} ${res.chapter}:${res.verse}",
                                        fontWeight = FontWeight.Bold,
                                        color = HamroCyan,
                                        fontSize = 13.sp
                                    )
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HamroPink, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(res.text, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Row 1: Book Button + Chapter Horizontal Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { bookSheetOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SanctuaryCard, contentColor = Color.White),
                    border = BorderStroke(0.9.dp, SanctuaryBorder),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = state.selectedBookName.ifBlank { AppStrings.selectBook(ne) },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (chap in 1..state.selectedBookChapters.coerceAtLeast(1)) {
                        val selected = state.selectedChapter == chap
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) HamroPink else SanctuaryCard)
                                .border(0.8.dp, if (selected) HamroPink else SanctuaryBorder, RoundedCornerShape(7.dp))
                                .clickable { onSelectChapter(chap) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$chap",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            // Row 2: Dedicated Verse Number Selector Bar + Multi-Verse Toggle
            if (state.chapterVerses.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tap to open full Verse Number Grid sheet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanctuaryCard)
                            .border(1.dp, SanctuaryBorder, RoundedCornerShape(8.dp))
                            .clickable { verseGridSheetOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = HamroCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = AppStrings.verseShort(ne),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HamroCyan
                        )
                    }

                    // Horizontal Verse Number Chips (1..N)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.chapterVerses.forEach { v ->
                            val selected = v.number in state.selectedVerses
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) HamroPink else SanctuaryCard)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) HamroPink else SanctuaryBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { pickVerseAndScroll(v.number) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${v.number}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            }
                        }
                    }

                    // Multi-verse toggle chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.multiVerseMode) HamroCyan.copy(alpha = 0.2f) else SanctuaryCard)
                            .border(
                                width = 1.dp,
                                color = if (state.multiVerseMode) HamroCyan else SanctuaryBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleMultiVerseMode()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = if (state.multiVerseMode) HamroCyan else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = AppStrings.multiVerseToggle(ne),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.multiVerseMode) HamroCyan else TextSecondary
                        )
                    }
                }
            }

            // Verses List
            if (state.bibleLoading && state.chapterVerses.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HamroPink)
                }
            } else {
                LazyColumn(
                    state = verseListState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.chapterVerses, key = { it.number }) { verse ->
                        val isSelected = verse.number in state.selectedVerses
                        Surface(
                            color = if (isSelected) HamroPink.copy(alpha = 0.16f) else SanctuaryCard,
                            border = BorderStroke(
                                width = if (isSelected) 1.3.dp else 0.8.dp,
                                color = if (isSelected) HamroPink else SanctuaryBorder
                            ),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleVerse(verse.number, false)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${verse.number}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) HamroCyan else TextSecondary,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.width(26.dp)
                                )
                                Text(
                                    text = verse.text,
                                    fontSize = 13.sp,
                                    lineHeight = 18.5.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleVerse(verse.number, true)
                                    },
                                    color = HamroPink.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HamroPink, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Project Selected + Prev/Next Verse Stepper Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onStepScripture(false) },
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = AppStrings.prevVerse(ne), modifier = Modifier.size(20.dp))
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onProjectSelected(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    val sortedVerses = state.selectedVerses.sorted()
                    val vLabel = if (sortedVerses.size <= 4) {
                        sortedVerses.joinToString(",")
                    } else {
                        "${sortedVerses.first()}..${sortedVerses.last()} (${sortedVerses.size})"
                    }
                    Text(
                        text = AppStrings.projectVerseBtn(ne, vLabel.ifBlank { "1" }),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onProjectSelected(true) },
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppStrings.addToSchedule(ne), tint = HamroCyan, modifier = Modifier.size(18.dp))
                }

                OutlinedButton(
                    onClick = { onStepScripture(true) },
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = AppStrings.nextVerse(ne), modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // Book Picker Modal Sheet
    if (bookSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { bookSheetOpen = false },
            containerColor = SanctuarySurface
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(440.dp)
            ) {
                items(state.bibleBooks, key = { it.number }) { book ->
                    val selected = book.number == state.selectedBook
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) HamroPink.copy(alpha = 0.22f) else SanctuaryCard
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bookSheetOpen = false
                                onSelectBook(book)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${book.number}. ${book.name}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${book.chaptersCount} ch",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Verse Number Grid Picker Modal Sheet
    if (verseGridSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { verseGridSheetOpen = false },
            containerColor = SanctuarySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.selectedBookName} ${state.selectedChapter} — ${AppStrings.selectVerse(ne)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    FilterChip(
                        selected = state.multiVerseMode,
                        onClick = onToggleMultiVerseMode,
                        label = { Text(AppStrings.multiVerseToggle(ne), fontSize = 12.sp) }
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.height(340.dp)
                ) {
                    items(state.chapterVerses, key = { it.number }) { v ->
                        val selected = v.number in state.selectedVerses
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) HamroPink else SanctuaryCard)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) HamroPink else SanctuaryBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    pickVerseAndScroll(v.number)
                                    if (!state.multiVerseMode) {
                                        verseGridSheetOpen = false
                                    }
                                }
                        ) {
                            Text(
                                text = "${v.number}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
