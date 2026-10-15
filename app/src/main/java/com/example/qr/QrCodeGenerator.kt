package com.example.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeGenerator {
    /**
     * Generates an Android Bitmap for a QR Code from the given content string.
     */
    fun generateQrBitmap(
        content: String,
        widthPx: Int = 512,
        heightPx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            }
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                widthPx,
                heightPx,
                hints
            )
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (_: Exception) {
            null
        }
    }
}
