package com.hamrochurch.remote.ui.components

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamrochurch.remote.model.MobilePromotion
import com.hamrochurch.remote.model.PromotionSanitizer
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.HamroPinkDark
import com.hamrochurch.remote.ui.theme.LiveGreen
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import com.hamrochurch.remote.ui.theme.TextPrimary
import com.hamrochurch.remote.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MobileFlipAngle {
    FLIP_Y,
    FLIP_X,
    FLIP_DIAG_A,
    SWING_3D,
    FLIP_DIAG_B
}

private val MIX_ANGLES = listOf(
    MobileFlipAngle.FLIP_Y,
    MobileFlipAngle.FLIP_X,
    MobileFlipAngle.FLIP_DIAG_A,
    MobileFlipAngle.SWING_3D,
    MobileFlipAngle.FLIP_DIAG_B
)

@Composable
fun MobilePromotionOverlay(
    promotions: List<MobilePromotion>,
    nepaliLanguage: Boolean,
    hasBottomNavBar: Boolean,
    onDismissAll: (List<MobilePromotion>) -> Unit,
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

    // Tick once per second only while at least one campaign is waiting on delaySeconds
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
    var angleCounter by remember { mutableIntStateOf(0) }
    var currentAngle by remember { mutableStateOf(MobileFlipAngle.FLIP_Y) }
    var rotateElapsedMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
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

    fun pickNextAngle(style: String): MobileFlipAngle {
        return when (style) {
            "flip_y" -> MobileFlipAngle.FLIP_Y
            "flip_x" -> MobileFlipAngle.FLIP_X
            "swing_3d" -> MobileFlipAngle.SWING_3D
            "flip_diagonal" -> {
                val chosen = if (angleCounter % 2 == 0) MobileFlipAngle.FLIP_DIAG_A else MobileFlipAngle.FLIP_DIAG_B
                angleCounter += 1
                chosen
            }
            else -> {
                val chosen = MIX_ANGLES[angleCounter % MIX_ANGLES.size]
                angleCounter += 1
                chosen
            }
        }
    }

    suspend fun animateFlipTo(targetIdx: Int) {
        if (eligiblePromos.size <= 1 || isFlipping) return
        val total = eligiblePromos.size
        val normalized = ((targetIdx % total) + total) % total
        if (normalized == activeIndex) return

        isFlipping = true
        rotateElapsedMs = 0L
        currentAngle = pickNextAngle(promo.transitionStyle)

        try {
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            activeIndex = normalized
            flipAnim.snapTo(-1f)
            flipAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        } finally {
            flipAnim.snapTo(0f)
            isFlipping = false
        }
    }

    LaunchedEffect(eligiblePromos.size, promo.campaignKey, promo.rotateIntervalSeconds) {
        rotateElapsedMs = 0L
        if (eligiblePromos.size <= 1) return@LaunchedEffect

        val intervalMs = promo.rotateIntervalSeconds.coerceIn(3, 300) * 1000L
        while (isActive) {
            delay(250L)
            if (isDragging || isFlipping) continue
            rotateElapsedMs += 250L
            if (rotateElapsedMs >= intervalMs) {
                rotateElapsedMs = 0L
                animateFlipTo(activeIndex + 1)
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

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val bottomClearance = if (hasBottomNavBar) 88.dp else 18.dp
    val progressFraction = if (eligiblePromos.size > 1) {
        val intervalMs = (promo.rotateIntervalSeconds.coerceIn(3, 300) * 1000L).toFloat()
        (rotateElapsedMs.toFloat() / intervalMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val bottomPadPx = with(density) { bottomClearance.toPx() }
        val edgePadPx = with(density) { 12.dp.toPx() }

        val flipFraction = flipAnim.value
        val absFlip = kotlin.math.abs(flipFraction)

        Card(
            modifier = Modifier
                .align(
                    if (promo.position == "bottom_left") Alignment.BottomStart else Alignment.BottomEnd
                )
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = bottomClearance,
                    top = 12.dp
                )
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.92f)
                .onSizeChanged { cardSize = it }
                .offset {
                    IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt())
                }
                .pointerInput(maxWidthPx, maxHeightPx, cardSize) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val cardW = cardSize.width.takeIf { it > 0 }?.toFloat() ?: (maxWidthPx * 0.85f)
                            val cardH = cardSize.height.takeIf { it > 0 }?.toFloat() ?: 220f
                            val maxUp = -(maxHeightPx - cardH - bottomPadPx - edgePadPx).coerceAtLeast(0f)
                            val maxDown = (bottomPadPx - edgePadPx).coerceAtLeast(0f)
                            val maxHoriz = (maxWidthPx - cardW - edgePadPx * 2f).coerceAtLeast(0f)

                            dragOffsetX = if (promo.position == "bottom_left") {
                                (dragOffsetX + dragAmount.x).coerceIn(0f, maxHoriz)
                            } else {
                                (dragOffsetX + dragAmount.x).coerceIn(-maxHoriz, 0f)
                            }
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(maxUp, maxDown)
                        }
                    )
                }
                .graphicsLayer {
                    cameraDistance = 14f * density.density
                    scaleX = 1f - 0.08f * absFlip
                    scaleY = 1f - 0.08f * absFlip
                    alpha = (1f - 0.55f * absFlip).coerceIn(0.25f, 1f)
                    when (currentAngle) {
                        MobileFlipAngle.FLIP_Y -> {
                            rotationY = flipFraction * 85f
                        }
                        MobileFlipAngle.FLIP_X -> {
                            rotationX = -flipFraction * 85f
                        }
                        MobileFlipAngle.FLIP_DIAG_A -> {
                            rotationX = flipFraction * 55f
                            rotationY = flipFraction * 65f
                        }
                        MobileFlipAngle.FLIP_DIAG_B -> {
                            rotationX = -flipFraction * 55f
                            rotationY = flipFraction * 65f
                        }
                        MobileFlipAngle.SWING_3D -> {
                            rotationY = -flipFraction * 78f
                            rotationZ = -flipFraction * 7f
                        }
                    }
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SanctuaryCard.copy(alpha = 0.98f)),
            border = BorderStroke(1.2.dp, HamroPink.copy(alpha = 0.55f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Strip: Drag Handle + HamroCMS.com + Counter + Close (×)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = null,
                            tint = HamroPink.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "HamroCMS.com",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF85C8)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (eligiblePromos.size > 1) {
                            Text(
                                text = "${safeIndex + 1}/${eligiblePromos.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiveGreen
                            )
                        }

                        Surface(
                            onClick = { onDismissAll(promotions) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (nepaliLanguage) "बन्द गर्नुहोस्" else "Close advertisement",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Multi-ad progress strip
                if (eligiblePromos.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(2.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(HamroPink, HamroCyan)
                                    )
                                )
                        )
                    }
                }

                // Optional Verified Raster Banner Image
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = promo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }

                // Plain-text Title, Body & Action CTA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = promo.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!promo.body.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = promo.body,
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(9.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val safeUrl = PromotionSanitizer.sanitizeHttpsUrl(promo.ctaUrl, null)
                                onClickPromotion(promo)
                                if (safeUrl != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        // Ignore if device has no browser installed
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HamroPink,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = promo.ctaLabel?.takeIf { it.isNotBlank() }
                                    ?: if (nepaliLanguage) "थप जान्नुहोस्" else "Learn More",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color.White
                            )
                        }

                        if (eligiblePromos.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                eligiblePromos.forEachIndexed { idx, _ ->
                                    val selected = idx == safeIndex
                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .width(if (selected) 14.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) HamroPink else Color.White.copy(alpha = 0.28f)
                                            )
                                            .clickable {
                                                scope.launch { animateFlipTo(idx) }
                                            }
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                }
            }
        }
    }
}
