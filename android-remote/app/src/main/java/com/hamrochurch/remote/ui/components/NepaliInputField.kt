package com.hamrochurch.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.nepali.NepaliPhoneticEngine
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.TextSecondary

/**
 * Reusable text/search input with Aradhana Studio's built-in Nepali phonetic typing
 * and horizontal candidate bar (matching Desktop `GoogleTypingPopup.svelte` + `NepaliTypingControls.svelte`).
 */
@Composable
fun NepaliInputField(
    value: String,
    onValueChange: (displayValue: String, effectiveSearchValue: String) -> Unit,
    nepaliTypingEnabled: Boolean,
    onToggleNepaliTyping: () -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    maxLength: Int = 200,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    var tfv by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var dismissedRoman by remember { mutableStateOf<String?>(null) }

    // Keep internal TextFieldValue synced when external state resets (e.g. Clear button)
    LaunchedEffect(value) {
        if (value != tfv.text) {
            tfv = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    val activeToken = remember(tfv.text, tfv.selection, nepaliTypingEnabled, dismissedRoman) {
        if (!nepaliTypingEnabled || !tfv.selection.collapsed) {
            null
        } else {
            NepaliPhoneticEngine.detectActiveToken(tfv.text, tfv.selection.start)
                ?.takeIf { it.roman != dismissedRoman }
        }
    }

    fun emitUpdatedText(newText: String, newCursor: Int) {
        val clipped = newText.take(maxLength)
        val safeCursor = newCursor.coerceIn(0, clipped.length)
        tfv = TextFieldValue(text = clipped, selection = TextRange(safeCursor))
        val effective = if (nepaliTypingEnabled) {
            NepaliPhoneticEngine.previewDevanagariQuery(clipped)
        } else {
            clipped
        }
        onValueChange(clipped, effective)
    }

    fun commitCandidate(token: NepaliPhoneticEngine.ActiveRomanToken, chosen: String, appendSpace: Boolean) {
        val suffix = if (appendSpace) " " else ""
        val before = tfv.text.substring(0, token.start)
        val after = tfv.text.substring(token.end)
        val nextText = before + chosen + suffix + after
        val nextCursor = (before.length + chosen.length + suffix.length).coerceAtMost(nextText.length)
        dismissedRoman = null
        emitUpdatedText(nextText, nextCursor)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = tfv,
            onValueChange = { incoming ->
                val oldText = tfv.text
                val newText = incoming.text.take(maxLength)

                // Check if user just pressed Space right after an active Roman word in Nepali mode
                if (
                    nepaliTypingEnabled &&
                    activeToken != null &&
                    newText.length == oldText.length + 1 &&
                    incoming.selection.collapsed &&
                    incoming.selection.start == activeToken.end + 1 &&
                    newText.getOrNull(activeToken.end) == ' ' &&
                    newText.startsWith(oldText.substring(0, activeToken.end))
                ) {
                    val primaryCandidate = activeToken.candidates.firstOrNull()
                    if (!primaryCandidate.isNullOrEmpty()) {
                        val before = oldText.substring(0, activeToken.start)
                        val after = oldText.substring(activeToken.end)
                        val committed = "$before$primaryCandidate $after"
                        val cursor = before.length + primaryCandidate.length + 1
                        dismissedRoman = null
                        emitUpdatedText(committed, cursor)
                        return@OutlinedTextField
                    }
                }

                if (newText != oldText) {
                    dismissedRoman = null
                }
                tfv = incoming.copy(text = newText)
                val effective = if (nepaliTypingEnabled) {
                    NepaliPhoneticEngine.previewDevanagariQuery(newText)
                } else {
                    newText
                }
                onValueChange(newText, effective)
            },
            placeholder = {
                Text(
                    text = placeholderText,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            },
            leadingIcon = leadingIcon,
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    if (tfv.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                dismissedRoman = null
                                emitUpdatedText("", 0)
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }

                    // Inline ने | EN typing mode pill (mirrors Desktop NepaliTypingControls)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanctuaryCard)
                            .border(
                                width = 1.dp,
                                color = if (nepaliTypingEnabled) HamroPink.copy(alpha = 0.8f) else SanctuaryBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleNepaliTyping()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ने",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (nepaliTypingEnabled) HamroPink else TextSecondary
                        )
                        Text(
                            text = "/",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Text(
                            text = "EN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!nepaliTypingEnabled) HamroCyan else TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (nepaliTypingEnabled && activeToken != null) {
                        commitCandidate(activeToken, activeToken.candidates.first(), appendSpace = false)
                    }
                    onImeAction?.invoke()
                },
                onSearch = {
                    if (nepaliTypingEnabled && activeToken != null) {
                        commitCandidate(activeToken, activeToken.candidates.first(), appendSpace = false)
                    }
                    onImeAction?.invoke()
                }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Horizontal Nepali Candidate Bar (matches Desktop's horizontal line box)
        AnimatedVisibility(visible = activeToken != null && activeToken.candidates.isNotEmpty()) {
            val token = activeToken
            if (token != null) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFE2E8F0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = token.roman,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }

                        token.candidates.forEachIndexed { idx, candidate ->
                            val isPrimary = idx == 0
                            val isRomanOption = idx == token.candidates.lastIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPrimary) Color(0xFFDBEAFE) else Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = if (isPrimary) Color(0xFF60A5FA) else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        commitCandidate(token, candidate, appendSpace = true)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrimary) Color(0xFF1D4ED8) else Color(0xFF64748B)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = candidate,
                                    fontSize = if (isRomanOption) 12.sp else 14.sp,
                                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPrimary) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { dismissedRoman = token.roman }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss suggestions",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.width(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
