package com.hamrochurch.remote.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.hamrochurch.remote.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.model.ChurchPcProfile
import com.hamrochurch.remote.model.ConnectionStatus
import com.hamrochurch.remote.model.DiscoveredChurchPc
import com.hamrochurch.remote.ui.theme.DangerRed
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.TextSecondary
import com.hamrochurch.remote.viewmodel.RemoteUiState

@Composable
fun ConnectScreen(
    state: RemoteUiState,
    onToggleLanguage: () -> Unit,
    onLaunchQrScanner: () -> Unit,
    onConnectManual: (name: String, ip: String, port: String, token: String) -> Unit,
    onConnectProfile: (ChurchPcProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onRefreshNearbyPcs: () -> Unit = {},
    onPairDiscoveredPc: (DiscoveredChurchPc, String) -> Unit = { _, _ -> },
    onShowError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ne = state.nepaliLanguage
    val context = LocalContext.current
    val isConnecting = state.connectionStatus == ConnectionStatus.CONNECTING

    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    var pcName by rememberSaveable { mutableStateOf("") }
    var ipAddress by rememberSaveable { mutableStateOf("192.168.") }
    var port by rememberSaveable { mutableStateOf("5505") }
    var token by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }

    var pinDialogTarget by remember { mutableStateOf<DiscoveredChurchPc?>(null) }
    var pinInput by rememberSaveable { mutableStateOf("") }

    if (pinDialogTarget != null) {
        val targetPc = pinDialogTarget!!
        AlertDialog(
            onDismissRequest = {
                pinDialogTarget = null
                pinInput = ""
            },
            title = {
                Text(AppStrings.enterPinDialogTitle(ne), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.enterPinDialogHint(ne, "${targetPc.name} (${targetPc.ip})"),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        label = { Text("6-Digit PIN (e.g. 482915)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pinInput.length == 6 && !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                    onClick = {
                        val entered = pinInput
                        pinDialogTarget = null
                        pinInput = ""
                        onPairDiscoveredPc(targetPc, entered)
                    }
                ) {
                    Text(AppStrings.connectNow(ne), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pinDialogTarget = null
                        pinInput = ""
                    }
                ) {
                    Text(AppStrings.cancelBtn(ne))
                }
            }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onLaunchQrScanner()
        } else {
            manualExpanded = true
            onShowError(AppStrings.cameraPermissionDenied(ne))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Official Logo + Language Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hamro_logo_banner),
                    contentDescription = "Aradhana Studio (आराधना स्टुडियो)",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(44.dp)
                )
            }

            OutlinedButton(
                onClick = onToggleLanguage,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SanctuaryBorder)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (ne) "EN" else "नेपाली", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Error Banner
        if (!state.errorMessage.isNullOrBlank()) {
            Surface(
                color = DangerRed.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.errorMessage,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // QR Scan Primary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, HamroPink.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            onLaunchQrScanner()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = HamroPink),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.connecting(ne), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.scanQr(ne), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = AppStrings.scanQrHint(ne),
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Auto-Discovered Church PCs on Active Wi-Fi / Hotspot (Zero-Camera 1-Tap / 6-Digit PIN Pairing)
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(
                1.dp,
                if (state.discoveredPcs.isNotEmpty()) LiveGreen.copy(alpha = 0.5f) else SanctuaryBorder
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = if (state.discoveredPcs.isNotEmpty()) LiveGreen else HamroCyan,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = AppStrings.nearbyPcsTitle(ne),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    TextButton(
                        enabled = !state.isDiscoveringPcs && !isConnecting,
                        onClick = onRefreshNearbyPcs
                    ) {
                        if (state.isDiscoveringPcs) {
                            CircularProgressIndicator(
                                color = HamroCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.scanningNetwork(ne), fontSize = 12.sp, color = HamroCyan)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = HamroCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(AppStrings.refreshSearch(ne), fontSize = 12.sp, color = HamroCyan)
                        }
                    }
                }

                if (state.discoveredPcs.isEmpty() && !state.isDiscoveringPcs) {
                    Text(
                        text = if (ne) {
                            "कम्प्युटरमा Aradhana Studio खोलेर Mobile आइकन क्लिक गर्नुहोस् र एउटै Wi-Fi मा जोड्नुहोस्।"
                        } else {
                            "Open Aradhana Studio on PC, click the Mobile icon, and join the same Wi-Fi."
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                } else {
                    state.discoveredPcs.forEach { pc ->
                        val matchingSaved = state.savedProfiles.firstOrNull { saved ->
                            saved.preferredIp == pc.ip || pc.ip in saved.ips
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, LiveGreen.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isConnecting) {
                                    if (matchingSaved != null) {
                                        onConnectProfile(matchingSaved.copy(preferredIp = pc.ip))
                                    } else {
                                        pinInput = ""
                                        pinDialogTarget = pc
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(LiveGreen)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = pc.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (matchingSaved != null) {
                                                "${pc.ip}:${pc.wsPort} • ${AppStrings.tapToReconnect(ne)}"
                                            } else {
                                                "${pc.ip}:${pc.wsPort} • ${AppStrings.pairWithPinBtn(ne)}"
                                            },
                                            fontSize = 12.sp,
                                            color = HamroCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Church PCs
        if (state.savedProfiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.savedChurches(ne),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                state.savedProfiles.forEach { profile ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
                        border = BorderStroke(1.dp, SanctuaryBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isConnecting) { onConnectProfile(profile) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(LiveGreen)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = profile.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${profile.preferredIp}:${profile.wsPort} • ${AppStrings.tapToReconnect(ne)}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual Pairing Accordion
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard),
            border = BorderStroke(1.dp, SanctuaryBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { manualExpanded = !manualExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = HamroCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = AppStrings.manualConnect(ne),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Icon(
                        imageVector = if (manualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = manualExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pcName,
                            onValueChange = { pcName = it },
                            label = { Text(AppStrings.pcNameLabel(ne)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it },
                                label = { Text(AppStrings.ipAddressLabel(ne)) },
                                placeholder = { Text("192.168.1.25") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                                label = { Text(AppStrings.portLabel(ne)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text(AppStrings.tokenLabel(ne)) },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { onConnectManual(pcName, ipAddress, port, token) },
                            enabled = !isConnecting,
                            colors = ButtonDefaults.buttonColors(containerColor = HamroCyan, contentColor = Color(0xFF042F36)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(AppStrings.connectNow(ne), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Offline Hotspot Tip Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, HamroCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = HamroCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = AppStrings.hotspotTipTitle(ne),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = HamroCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = AppStrings.hotspotTipBody(ne),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Stuti Media YouTube",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/@stutimedianepal?sub_confirmation=1")
                            )
                        )
                    }
                }
            )
            Text(
                text = "  ·  ",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Text(
                text = "hamrocms.com",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://hamrocms.com/?utm_source=aradhana_remote&utm_medium=connect_dock")
                            )
                        )
                    }
                }
            )
        }
    }
}
