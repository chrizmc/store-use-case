package com.bopis.associate.ui.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

// Fallback to the live camera scanner: decodes a QR code from a picked gallery image
// (e.g. a photo of a shelf's QR code, or one of the generated /qrcodes/*.png files).
fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        QRCodeReader().decode(binaryBitmap).text
    } catch (e: ReaderException) {
        null
    }
}
