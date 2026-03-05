package eu.tutorials.hangiatik

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class YuvToRgbConverter {

    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val width = image.width
        val height = image.height

        val argb = IntArray(width * height)

        var yp = 0
        for (j in 0 until height) {
            val yRow = yRowStride * j
            val uvRow = uvRowStride * (j / 2)

            for (i in 0 until width) {
                val y = 0xff and yBuffer.get(yRow + i).toInt()
                val uvOffset = uvRow + (i / 2) * uvPixelStride

                val u = (0xff and uBuffer.get(uvOffset).toInt()) - 128
                val v = (0xff and vBuffer.get(uvOffset).toInt()) - 128

                val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

                argb[yp++] =
                    (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(argb, 0, width, 0, 0, width, height)
    }
}
