package com.bopis.associate.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.delay

// Embeds zxing's scanning surface directly (BarcodeView/CaptureManager building blocks)
// instead of launching its separate full-screen Activity via ScanContract. That previous
// flow could decode successfully with no feedback the associate could actually see, so a
// working scan looked identical to a failed one. Here, a decode is confirmed with a vibration
// + on-screen banner, then moves on automatically - same destination ("Is Empty"/"Is Nearly
// Empty") as manually picking the shelf from the dropdown.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(onDecoded: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Scan shelf QR code") },
            navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
    }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text("Camera permission is needed to scan a shelf QR code.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant camera permission")
                    }
                }
                return@Box
            }

            var decodedText by remember { mutableStateOf<String?>(null) }
            val onDecodedState = rememberUpdatedState(onDecoded)

            val decoratedBarcodeView = remember {
                DecoratedBarcodeView(context).apply {
                    barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                    setStatusText("Point the camera at the shelf's QR code")
                }
            }

            AndroidView(factory = { decoratedBarcodeView }, modifier = Modifier.fillMaxSize())

            DisposableEffect(decoratedBarcodeView) {
                decoratedBarcodeView.resume()
                decoratedBarcodeView.decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        if (decodedText != null) return
                        decodedText = result.text
                        decoratedBarcodeView.pause()
                        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                        vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                    }

                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
                })
                onDispose { decoratedBarcodeView.pause() }
            }

            decodedText?.let { qr ->
                LaunchedEffect(qr) {
                    delay(350)
                    onDecodedState.value(qr)
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Scanned \u2713",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
