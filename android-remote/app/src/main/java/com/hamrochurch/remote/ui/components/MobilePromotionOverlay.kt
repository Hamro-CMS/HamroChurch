package com.hamrochurch.remote.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.model.MobilePromotion
import com.hamrochurch.remote.model.PromotionSanitizer
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.SanctuaryBorder
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.SanctuarySurface
import com.hamrochurch.remote.ui.theme.TextPrimary
import com.hamrochurch.remote.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Slim, non-intrusive bottom-docked promotion strip for Aradhana Studio Remote.
 * Docked inside Scaffold's bottomBar so it never covers buttons, inputs, or slide controls,
 * and never overflows behind the Android system navigation bar.
 */
@Composable
fun MobilePromotionOverlay(
    promotions: List<MobilePromotion>,
    nepaliLanguage: Boolean,
    hasBottomNavBar: Boolean,
    onImpression: (MobilePromotion) -> Unit,
    onClickPromotion: (MobilePromotion) -> Unit
) {
    if (promotions.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val appOpenedAt = remember { System.currentTimeMillis() }
    val firstSeenByKey = remember { mutableStateMapOf<String, Long>() }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(promotions) {
        val now = System.currentTimeMillis()
        promotions.forEach { p ->
            if (p.campaignKey.isNotEmpty() && !firstSeenByKey.containsKey(p.campaignKey)) {
                firstSeenByKey[p.campaignKey] = now
            }
        }
    }

    fun isDelayUnlocked(promo: MobilePromotion, tick: Long): Boolean {
        val delayMs = promo.delaySeconds.coerceAtLeast(0) * 1000L
        if (delayMs <= 0L) return true
        val firstSeen = firstSeenByKey[promo.campaignKey] ?: appOpenedAt
        val anchor = if (firstSeen - appOpenedAt <= 15_000L) appOpenedAt else firstSeen
        return tick - anchor >= delayMs
    }

    LaunchedEffect(promotions) {
        while (isActive) {
            val current = System.currentTimeMillis()
            nowTick = current
            val anyPending = promotions.any { !isDelayUnlocked(it, current) }
            if (!anyPending) break
            delay(1000L)
        }
    }

    val eligiblePromos = remember(promotions, nowTick) {
        promotions.filter { isDelayUnlocked(it, nowTick) }
    }
    if (eligiblePromos.isEmpty()) return

    var activeIndex by remember { mutableIntStateOf(0) }
    var rotateElapsedMs by remember { mutableLongStateOf(0L) }
    var isFlipping by remember { mutableStateOf(false) }
    val flipAnim = remember { Animatable(0f) }

    val safeIndex = if (activeIndex in eligiblePromos.indices) activeIndex else 0
    if (safeIndex != activeIndex) {
        activeIndex = safeIndex
    }
    val promo = eligiblePromos[safeIndex]

    LaunchedEffect(promo.campaignKey) {
        onImpression(promo)
    }

    suspend fun animateSlideTo(targetIdx: Int) {
        if (eligiblePromos.size <= 1 || isFlipping) return
        val total = eligiblePromos.size
        val normalized = ((targetIdx % total) + total) % total
        if (normalized == activeIndex) return

        isFlipping = true
        rotateElapsedMs = 0L
        try {
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            activeIndex = normalized
            flipAnim.snapTo(-1f)
            flipAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
            )
        } finally {
            flipAnim.snapTo(0f)
            isFlipping = false
        }
    }

    LaunchedEffect(eligiblePromos.size, promo.campaignKey, promo.rotateIntervalSeconds) {
        rotateElapsedMs = 0L
        if (eligiblePromos.size <= 1) return@LaunchedEffect

        val intervalMs = promo.rotateIntervalSeconds.coerceIn(4, 300) * 1000L
        while (isActive) {
            delay(250L)
            if (isFlipping) continue
            rotateElapsedMs += 250L
            if (rotateElapsedMs >= intervalMs) {
                rotateElapsedMs = 0L
                animateSlideTo(activeIndex + 1)
            }
        }
    }

    val decodedBitmap = remember(promo.campaignKey, promo.imageBytes) {
        val bytes = promo.imageBytes
        if (bytes != null && bytes.isNotEmpty()) {
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val progressFraction = if (eligiblePromos.size > 1) {
        val intervalMs = (promo.rotateIntervalSeconds.coerceIn(4, 300) * 1000L).toFloat()
        (rotateElapsedMs.toFloat() / intervalMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val currentPromo by rememberUpdatedState(promo)

    fun triggerOpenUrl() {
        val activePromo = currentPromo
        val safeUrl = PromotionSanitizer.sanitizeHttpsUrl(activePromo.ctaUrl, null)
        onClickPromotion(activePromo)
        if (safeUrl != null) {
            openPromotionDestination(context, safeUrl)
        }
    }

    val flipFraction = flipAnim.value
    val absFlip = kotlin.math.abs(flipFraction)

    val containerModifier = if (hasBottomNavBar) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .background(SanctuarySurface)
            .navigationBarsPadding()
    }

    Surface(
        modifier = containerModifier,
        color = SanctuarySurface,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, HamroCyan.copy(alpha = 0.36f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF112240), Color(0xFF0A1428))
                            )
                        )
                ) {
                    if (eligiblePromos.size > 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(Color.White.copy(alpha = 0.10f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(2.5.dp)
                                    .background(Brush.horizontalGradient(listOf(HamroPink, HamroCyan)))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { triggerOpenUrl() }
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                            .graphicsLayer {
                                cameraDistance = 12f * density.density
                                rotationX = -flipFraction * 65f
                                alpha = (1f - 0.65f * absFlip).coerceIn(0.2f, 1f)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (decodedBitmap != null) {
                            Image(
                                bitmap = decodedBitmap,
                                contentDescription = promo.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(98.dp)
                                    .height(66.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(SanctuaryCard)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            val isStutiSong = promo.isStutiSong || promo.campaignKey.startsWith("stuti-")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (isStutiSong) "Stuti Media" else "HamroCMS.com",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isStutiSong) Color(0xFFF87171) else Color(0xFFFBBF24),
                                        maxLines = 1
                                    )
                                    if (eligiblePromos.size > 1) {
                                        Text(
                                            text = "• ${safeIndex + 1}/${eligiblePromos.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary,
                                            modifier = Modifier.clickable {
                                                scope.launch { animateSlideTo(activeIndex + 1) }
                                            }
                                        )
                                    }
                                }
                            }

                            Text(
                                text = promo.title,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = if (promo.body.isNullOrBlank()) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = promo.body?.takeIf { it.isNotBlank() }
                                        ?: if (isStutiSong) {
                                            if (nepaliLanguage) "स्तुति मिडिया नयाँ आराधना भजन" else "Stuti Media • New Worship Song"
                                        } else {
                                            if (nepaliLanguage) "आराधना र चर्च सेवा सामग्री" else "Worship & church ministry updates"
                                        },
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                )

                                Button(
                                    onClick = { triggerOpenUrl() },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HamroCyan,
                                        contentColor = Color(0xFF04111F)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 27.dp)
                                ) {
                                    val rawLabel = promo.ctaLabel?.trim().orEmpty()
                                    val compactLabel = when {
                                        rawLabel.isEmpty() -> if (isStutiSong) {
                                            if (nepaliLanguage) "भजन सुन्नुहोस्" else "Watch Song"
                                        } else {
                                            if (nepaliLanguage) "हेर्नुहोस्" else "Open"
                                        }
                                        rawLabel.length > 14 -> if (nepaliLanguage) "भजन सुन्नुहोस्" else "Explore"
                                        else -> rawLabel
                                    }
                                    Text(
                                        text = compactLabel,
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFF04111F)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

private fun extractYouTubeVideoId(rawUrl: String): String? {
    return try {
        val uri = Uri.parse(rawUrl.trim())
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val candidate = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com" -> {
                val queryVid = uri.getQueryParameter("v")
                if (!queryVid.isNullOrBlank()) {
                    queryVid
                } else {
                    val segments = uri.pathSegments
                    if (segments.size >= 2 && segments[0] in setOf("shorts", "live", "embed", "v")) {
                        segments[1]
                    } else {
                        null
                    }
                }
            }
            else -> null
        }?.trim()

        if (candidate != null && YOUTUBE_VIDEO_ID_REGEX.matches(candidate)) candidate else null
    } catch (_: Exception) {
        null
    }
}

private fun openPromotionDestination(context: Context, safeUrl: String) {
    val videoId = extractYouTubeVideoId(safeUrl)
    if (videoId != null) {
        // Launch YouTube's dedicated video player directly via vnd.youtube:<videoId>
        // without FLAG_ACTIVITY_NEW_TASK from Activity context so YouTube never drops onto its Home feed.
        try {
            val ytVideoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                putExtra("VIDEO_ID", videoId)
                putExtra("force_fullscreen", false)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(ytVideoIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to standard HTTPS video watch URL
        } catch (_: Exception) {
            // Fall through to standard HTTPS video watch URL
        }

        try {
            val webWatchIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=$videoId")
            ).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(webWatchIntent)
        } catch (_: Exception) {
            // Ignore if device has no browser installed
        }
        return
    }

    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(browserIntent)
    } catch (_: Exception) {
        // Ignore if device has no browser installed
    }
}
