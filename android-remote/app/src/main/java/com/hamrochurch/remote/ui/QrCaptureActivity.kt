package com.hamrochurch.remote.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings

/**
 * Dedicated offline ZXing QR scanner activity tuned for instant LCD/monitor scanning:
 * - Disables `isBarcodeSceneModeEnabled` (which overrides continuous autofocus & locks dark paper
 *   exposure on Xiaomi/MIUI/Samsung Camera1 HALs).
 * - Enables continuous picture autofocus + auto-exposure metering + QR-only `TRY_HARDER` decoder.
 */
class QrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        val barcodeView = super.initializeContent()
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(
            listOf(BarcodeFormat.QR_CODE),
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            ),
            "UTF-8",
            2
        )
        barcodeView.barcodeView.cameraSettings = CameraSettings().apply {
            isAutoFocusEnabled = true
            isContinuousFocusEnabled = true
            focusMode = CameraSettings.FocusMode.CONTINUOUS
            isMeteringEnabled = true
            isExposureEnabled = true
            isBarcodeSceneModeEnabled = false
        }
        return barcodeView
    }
}
