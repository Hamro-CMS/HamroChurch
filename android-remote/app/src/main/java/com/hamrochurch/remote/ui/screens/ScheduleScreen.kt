package com.hamrochurch.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.viewmodel.RemoteUiState

@Composable
fun ScheduleScreen(
    state: RemoteUiState,
    onSetLibraryMode: (Boolean) -> Unit,
    onSelectProject: (String) -> Unit,
    onRefreshProjects: () -> Unit,
    onOpenProjectItem: (Int) -> Unit,
    onSavedShowsQueryChange: (displayQuery: String, effectiveQuery: String) -> Unit,
    onToggleNepaliTyping: () -> Unit,
    onOpenSavedShow: (String) -> Unit
) {
    val ne = state.nepaliLanguage
    val haptics = LocalHapticFeedback.current
    var projectDropdownOpen by remember { mutableStateOf(false) }

    val activeProjectName = state.projects.firstOrNull { it.id == state.activeProjectId }?.name ?: "Project"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Mode Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !state.scheduleShowLibrary,
                onClick = { onSetLibraryMode(false) },
                label = { Text(AppStrings.activeSchedule(ne), fontSize = 12.sp) }
            )
            FilterChip(
                selected = state.scheduleShowLibrary,
                onClick = { onSetLibraryMode(true) },
                label = { Text(AppStrings.savedLibrary(ne), fontSize = 12.sp) }
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefreshProjects, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        if (!state.scheduleShowLibrary) {
            // Project Selector
            if (state.projects.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { projectDropdownOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SanctuaryBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = HamroCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = activeProjectName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = projectDropdownOpen,
                        onDismissRequest = { projectDropdownOpen = false }
                    ) {
                        state.projects.forEach { proj ->
                            DropdownMenuItem(
                                text = { Text("${proj.name} (${proj.itemsCount})") },
                                onClick = {
                                    projectDropdownOpen = false
                                    onSelectProject(proj.id)
                                }
                            )
                        }
                    }
                }
            }

            if (state.projectItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(AppStrings.emptyProject(ne), color = TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.projectItems, key = { "${it.index}_${it.id}" }) { item ->
                        val isLive = item.id.isNotEmpty() && item.id == state.live.activeShowId
                        val icon = when (item.type) {
                            "audio" -> Icons.Default.MusicNote
                            "video" -> Icons.Default.VideoFile
                            "image" -> Icons.Default.Image
                            else -> Icons.Default.Slideshow
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLive) HamroPink.copy(alpha = 0.16f) else SanctuaryCard
                            ),
                            border = BorderStroke(if (isLive) 1.3.dp else 0.8.dp, if (isLive) HamroPink else SanctuaryBorder),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenProjectItem(item.index)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isLive) LiveGreen else HamroCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.index + 1}. ${item.name}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.notes.isNotBlank()) {
                                        Text(
                                            text = item.notes,
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HamroPink, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // Saved Shows Library
            NepaliInputField(
                value = state.savedShowsQuery,
                onValueChange = onSavedShowsQueryChange,
                nepaliTypingEnabled = state.nepaliTypingEnabled,
                onToggleNepaliTyping = onToggleNepaliTyping,
                placeholderText = AppStrings.searchShowsPlaceholder(ne),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.savedShows, key = { it.id }) { show ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                        border = BorderStroke(0.8.dp, SanctuaryBorder),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSavedShow(show.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (show.number.isNotBlank()) "#${show.number} — ${show.name}" else show.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (show.category.isNotBlank()) {
                                    Text(text = show.category, fontSize = 11.5.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HamroPink, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}
