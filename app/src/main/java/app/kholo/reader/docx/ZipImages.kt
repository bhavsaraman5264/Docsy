package app.kholo.reader.docx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.readUpTo
import java.io.IOException

private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

/** Office file ke andar ki tasveer, [maxSide] tak chhoti karke. Na padhi jaye to null, poora kaam nahi girta. */
fun SafeZip.decodeImage(path: String, maxSide: Int): Bitmap? = try {
    val bytes = open(path)?.use { it.readUpTo(MAX_IMAGE_BYTES) }
    if (bytes == null) {
        null
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
