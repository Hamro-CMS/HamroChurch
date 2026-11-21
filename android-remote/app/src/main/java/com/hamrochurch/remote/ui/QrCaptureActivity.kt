package com.hamrochurch.remote.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.hamrochurch.remote.i18n.AppStrings
import com.hamrochurch.remote.security.QrPairingParser
import com.hamrochurch.remote.security.QrParseResult
import com.hamrochurch.remote.ui.theme.HamroChurchRemoteTheme
import com.hamrochurch.remote.ui.theme.HamroCyan
import com.hamrochurch.remote.ui.theme.HamroPink
import com.hamrochurch.remote.ui.theme.SanctuaryCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class QrCaptureActivity : ComponentActivity() {

    private val delivered = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nepali = intent.getBooleanExtra(EXTRA_NEPALI, true)

        setContent {
            HamroChurchRemoteTheme {
                CameraXQrScannerScreen(
                    nepali = nepali,
                    onQrDetected = { rawQr -> deliverResult(rawQr) },
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun deliverResult(rawQr: String) {
        if (!delivered.compareAndSet(false, true)) return
        playScanFeedback()
        val data = Intent().putExtra(EXTRA_QR_CONTENT, rawQr)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun playScanFeedback() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45L)
            }
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    companion object {
        const val EXTRA_NEPALI = "extra_nepali"
        const val EXTRA_QR_CONTENT = "extra_qr_content"
    }
}

@Composable
private fun CameraXQrScannerScreen(
    nepali: Boolean,
    onQrDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var selectedZoom by remember { mutableFloatStateOf(1.3f) }
    var feedbackHint by remember { mutableStateOf<String?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mlKitScanner = remember {
        runCatching {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
        }.getOrNull()
    }

    val zxingReader = remember { QRCodeReader() }
    val zxingHints = remember {
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeQrFromUri(
                context = context,
                uri = uri,
                mlKitScanner = mlKitScanner,
                zxingReader = zxingReader,
                zxingHints = zxingHints,
                onSuccess = onQrDetected,
                onError = { msg -> feedbackHint = msg }
            )
        }
    }

    // Periodically meter focus & exposure on the center viewfinder so bright laptop LCDs never blow out
    LaunchedEffect(cameraRef, previewViewRef) {
        val cam = cameraRef ?: return@LaunchedEffect
        val pv = previewViewRef ?: return@LaunchedEffect
        while (isActive) {
            runCatching {
                if (pv.width > 0 && pv.height > 0) {
                    val centerPoint = pv.meteringPointFactory.createPoint(pv.width / 2f, pv.height / 2f)
                    val action = FocusMeteringAction.Builder(
                        centerPoint,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
            }
            delay(2800L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mlKitScanner?.close() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef = previewView

                val scaleDetector = ScaleGestureDetector(
                    ctx,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val cam = cameraRef ?: return false
                            val zoomState = cam.cameraInfo.zoomState.value ?: return false
                            val next = (zoomState.zoomRatio * detector.scaleFactor)
                                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio.coerceAtMost(5f))
                            selectedZoom = next
                            cam.cameraControl.setZoomRatio(next)
                            return true
                        }
                    }
                )

                previewView.setOnTouchListener { view, event ->
                    scaleDetector.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                        val cam = cameraRef
                        if (cam != null) {
                            val pt = previewView.meteringPointFactory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(
                                pt,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cam.cameraControl.startFocusAndMetering(action)
                        }
                        view.performClick()
                    }
                    true
                }

                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener(
                    {
                        val cameraProvider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener

                        val resolutionSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()

                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeCameraFrame(
                                imageProxy = imageProxy,
                                mlKitScanner = mlKitScanner,
                                zxingReader = zxingReader,
                                zxingHints = zxingHints,
                                onValidQr = onQrDetected,
                                onInvalidQr = { reason -> feedbackHint = reason }
                            )
                        }

                        runCatching {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraRef = cam
                            hasTorch = cam.cameraInfo.hasFlashUnit()

                            // Slight 1.3x default zoom lets modern 48MP/108MP phone lenses stay comfortably
                            // outside their minimum focus distance while keeping monitor QR pixels tack-sharp
                            val zoomState = cam.cameraInfo.zoomState.value
                            if (zoomState != null) {
                                val initialZoom = 1.3f.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                selectedZoom = initialZoom
                                cam.cameraControl.setZoomRatio(initialZoom)
                            }
                        }
                    },
                    ContextCompat.getMainExecutor(ctx)
                )

                previewView
            }
        )

        // Viewfinder Cutout + Corner Brackets
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color(0x99050B14))

            val boxSize = (size.minDimension * 0.68f).coerceAtMost(310.dp.toPx())
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2.25f
            val rect = Rect(left, top, left + boxSize, top + boxSize)

            drawRoundRect(
                color = Color.Transparent,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(24.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            val cornerLen = 32.dp.toPx()
            val strokePx = 4.dp.toPx()
            val accent = HamroCyan

            // Top-Left
            drawLine(accent, Offset(rect.left, rect.top + cornerLen), Offset(rect.left, rect.top), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(rect.left, rect.top), Offset(rect.left + cornerLen, rect.top), strokePx, StrokeCap.Round)
            // Top-Right
            drawLine(accent, Offset(rect.right - cornerLen, rect.top), Offset(rect.right, rect.top), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(rect.right, rect.top), Offset(rect.right, rect.top + cornerLen), strokePx, StrokeCap.Round)
            // Bottom-Left
            drawLine(accent, Offset(rect.left, rect.bottom - cornerLen), Offset(rect.left, rect.bottom), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(rect.left, rect.bottom), Offset(rect.left + cornerLen, rect.bottom), strokePx, StrokeCap.Round)
            // Bottom-Right
            drawLine(accent, Offset(rect.right - cornerLen, rect.bottom), Offset(rect.right, rect.bottom), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(rect.right, rect.bottom - cornerLen), Offset(rect.right, rect.bottom), strokePx, StrokeCap.Round)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Top Bar (Back + Title + Torch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x990B1324))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = AppStrings.scanQr(nepali),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (hasTorch) {
                IconButton(
                    onClick = {
                        val next = !torchEnabled
                        torchEnabled = next
                        cameraRef?.cameraControl?.enableTorch(next)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (torchEnabled) HamroPink else Color(0x990B1324))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // Bottom Controls (Zoom Pills + Instruction + Gallery QR Picker)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Optical/Sensor Zoom Selector Pills (1x / 1.5x / 2x)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC0B1324))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1.0f to "1x", 1.5f to "1.5x", 2.0f to "2x").forEach { (zoomVal, label) ->
                    val isActiveZoom = kotlin.math.abs(selectedZoom - zoomVal) < 0.26f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isActiveZoom) HamroPink else Color.Transparent)
                            .clickable {
                                val cam = cameraRef ?: return@clickable
                                val zs = cam.cameraInfo.zoomState.value
                                val clamped = if (zs != null) {
                                    zoomVal.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                                } else {
                                    zoomVal
                                }
                                selectedZoom = clamped
                                cam.cameraControl.setZoomRatio(clamped)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!feedbackHint.isNullOrBlank()) {
                Surface(
                    color = SanctuaryCard.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = feedbackHint.orEmpty(),
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = AppStrings.scannerAlignHint(nepali),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = HamroCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.scanFromGallery(nepali), color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeCameraFrame(
    imageProxy: ImageProxy,
    mlKitScanner: BarcodeScanner?,
    zxingReader: QRCodeReader,
    zxingHints: Map<DecodeHintType, Any>,
    onValidQr: (String) -> Unit,
    onInvalidQr: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    if (mlKitScanner != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        mlKitScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val raw = barcode.rawValue?.trim()
                    if (!raw.isNullOrEmpty()) {
                        when (val parsed = QrPairingParser.parse(raw)) {
                            is QrParseResult.Success -> {
                                onValidQr(raw)
                                return@addOnSuccessListener
                            }
                            is QrParseResult.Invalid -> {
                                onInvalidQr(parsed.reason)
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                // Fallback to ZXing Y-plane decode if native ML Kit encounters an unexpected error
                val zxingText = decodeWithZxingYPlane(imageProxy, zxingReader, zxingHints)
                if (!zxingText.isNullOrEmpty()) {
                    if (QrPairingParser.parse(zxingText) is QrParseResult.Success) {
                        onValidQr(zxingText)
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        try {
            val zxingText = decodeWithZxingYPlane(imageProxy, zxingReader, zxingHints)
            if (!zxingText.isNullOrEmpty()) {
                when (val parsed = QrPairingParser.parse(zxingText)) {
                    is QrParseResult.Success -> onValidQr(zxingText)
                    is QrParseResult.Invalid -> onInvalidQr(parsed.reason)
                }
            }
        } finally {
            imageProxy.close()
        }
    }
}

private fun decodeWithZxingYPlane(
    imageProxy: ImageProxy,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    buffer.rewind()
    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    val yBytes = ByteArray(width * height)
    if (rowStride == width && pixelStride == 1) {
        buffer.get(yBytes, 0, yBytes.size.coerceAtMost(buffer.remaining()))
    } else {
        val rowBuf = ByteArray(rowStride)
        for (row in 0 until height) {
            val toRead = if (row == height - 1) buffer.remaining().coerceAtMost(rowStride) else rowStride
            if (toRead <= 0) break
            buffer.get(rowBuf, 0, toRead)
            var outOffset = row * width
            for (col in 0 until width) {
                val srcIdx = col * pixelStride
                if (srcIdx < toRead) {
                    yBytes[outOffset++] = rowBuf[srcIdx]
                }
            }
        }
    }

    val source = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)
    runCatching {
        reader.reset()
        return reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }
    runCatching {
        reader.reset()
        return reader.decode(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source))), hints).text
    }
    return null
}

private fun decodeQrFromUri(
    context: Context,
    uri: Uri,
    mlKitScanner: BarcodeScanner?,
    zxingReader: QRCodeReader,
    zxingHints: Map<DecodeHintType, Any>,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (mlKitScanner != null) {
        val img = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (img != null) {
            mlKitScanner.process(img)
                .addOnSuccessListener { list ->
                    val match = list.firstNotNullOfOrNull { b ->
                        val raw = b.rawValue?.trim()
                        if (!raw.isNullOrEmpty() && QrPairingParser.parse(raw) is QrParseResult.Success) raw else null
                    }
                    if (match != null) {
                        onSuccess(match)
                    } else {
                        val fallback = decodeBitmapWithZxing(context, uri, zxingReader, zxingHints)
                        if (fallback != null && QrPairingParser.parse(fallback) is QrParseResult.Success) {
                            onSuccess(fallback)
                        } else {
                            onError("No valid Aradhana Studio QR code found in image")
                        }
                    }
                }
                .addOnFailureListener {
                    val fallback = decodeBitmapWithZxing(context, uri, zxingReader, zxingHints)
                    if (fallback != null && QrPairingParser.parse(fallback) is QrParseResult.Success) {
                        onSuccess(fallback)
                    } else {
                        onError("Could not read QR image")
                    }
                }
            return
        }
    }

    val fallback = decodeBitmapWithZxing(context, uri, zxingReader, zxingHints)
    if (fallback != null && QrPairingParser.parse(fallback) is QrParseResult.Success) {
        onSuccess(fallback)
    } else {
        onError("No valid Aradhana Studio QR code found in image")
    }
}

private fun decodeBitmapWithZxing(
    context: Context,
    uri: Uri,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    val bmp = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: return null
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val src = RGBLuminanceSource(w, h, pixels)
    return runCatching {
        reader.reset()
        reader.decode(BinaryBitmap(HybridBinarizer(src)), hints).text
    }.getOrNull()
}
