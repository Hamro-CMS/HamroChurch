package com.hamrochurch.remote.ui

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.camera.CameraSettings

/**
 * Dedicated offline ZXing QR scanner activity tuned with continuous autofocus
 * and barcode scene optimization for fast, glare-resistant screen QR scanning.
 */
class QrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        val barcodeView = super.initializeContent()
        barcodeView.barcodeView.cameraSettings = CameraSettings().apply {
            isAutoFocusEnabled = true
            isContinuousFocusEnabled = true
            focusMode = CameraSettings.FocusMode.CONTINUOUS
            isBarcodeSceneModeEnabled = true
        }
        return barcodeView
    }
}
