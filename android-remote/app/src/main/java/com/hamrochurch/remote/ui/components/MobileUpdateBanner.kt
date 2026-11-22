package com.hamrochurch.remote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.network.MobileAppUpdateState
import com.hamrochurch.remote.network.MobileUpdatePhase
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.TextPrimary
import com.hamrochurch.remote.ui.theme.TextSecondary

@Composable
fun MobileUpdateBanner(
    updateState: MobileAppUpdateState,
    nepaliLanguage: Boolean,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (updateState.phase != MobileUpdatePhase.DOWNLOADING &&
        updateState.phase != MobileUpdatePhase.READY_TO_INSTALL
    ) {
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0E223D),
        border = BorderStroke(1.dp, HamroCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = HamroCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val title = when (updateState.phase) {
                            MobileUpdatePhase.DOWNLOADING ->
                                if (nepaliLanguage) {
                                    "नयाँ अपडेट v${updateState.latestVersion} डाउनलोड हुँदैछ (${updateState.progressPercent}%)"
                                } else {
                                    "Downloading update v${updateState.latestVersion} (${updateState.progressPercent}%)"
                                }
                            MobileUpdatePhase.READY_TO_INSTALL ->
                                if (nepaliLanguage) {
                                    "नयाँ भर्सन v${updateState.latestVersion} इन्स्टलको लागि तयार छ"
                                } else {
                                    "Update v${updateState.latestVersion} is ready to install"
                                }
                            else -> ""
                        }
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (updateState.phase == MobileUpdatePhase.READY_TO_INSTALL) {
                            Text(
                                text = if (nepaliLanguage) {
                                    "एक ट्यापमा एप अपडेट पूरा गर्नुहोस्"
                                } else {
                                    "Tap Install to finish updating the app"
                                },
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (updateState.phase == MobileUpdatePhase.READY_TO_INSTALL) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onInstallClick,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HamroCyan,
                            contentColor = Color(0xFF04111F)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 28.dp)
                    ) {
                        Text(
                            text = if (nepaliLanguage) "अपडेट गर्नुहोस्" else "Install",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (updateState.phase == MobileUpdatePhase.DOWNLOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    LinearProgressIndicator(
                        progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = HamroCyan,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}
