package com.bopis.associate.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// Wraps zxing-android-embedded's ScanContract for use from Compose; returns a launcher
// function that opens the (Apache-2.0, fully offline) QR scanner and calls back with the
// decoded shelf QR code text, or null if the associate cancelled.
@Composable
fun rememberQrScanLauncher(onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        onResult(result.contents)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }
}
