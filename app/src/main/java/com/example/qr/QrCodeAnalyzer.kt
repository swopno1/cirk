package com.example.qr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.EnumMap

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
        }
        setHints(hints)
    }

    private var isScanningActive = true

    fun stop() {
        isScanningActive = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningActive) {
            imageProxy.close()
            return
        }

        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height

            val source = PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            if (result != null && result.text.isNotBlank()) {
                isScanningActive = false
                onQrCodeScanned(result.text)
            }
        } catch (_: NotFoundException) {
            // Normal when QR code is not in frame
        } catch (_: Exception) {
            // Ignore frame analysis errors
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}
