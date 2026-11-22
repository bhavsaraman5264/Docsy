package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

private const val MAX_PHOTO_SIDE = 2200
internal const val FULL_TURN = 360
internal const val QUARTER_TURN = 90
private const val HALF_TURN = 180

/** Photo ko sahi disha me, [maxSide] tak chhoti karke padhta hai. */
internal fun loadPhoto(source: () -> InputStream?, maxSide: Int = MAX_PHOTO_SIDE): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    source()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val decoded = source()?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    val degrees = source()?.use { exifRotation(it) } ?: 0
    return if (decoded == null || degrees == 0) {
        decoded
    } else {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}

private fun exifRotation(stream: InputStream): Int =
    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
        ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
        ExifInterface.ORIENTATION_ROTATE_270 -> FULL_TURN - QUARTER_TURN
        else -> 0
    }

/** JPEG me transparency nahi hoti, aur transparent hissa kaala ban jata hai. Isliye safed par rakhte hain. */
internal fun Bitmap.flattened(): Bitmap {
    if (!hasAlpha()) return this
    val flat = createBitmap(width, height)
    val canvas = Canvas(flat)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(this, 0f, 0f, null)
    return flat
}

internal class EncodedPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

/** PDF me lagane ke liye photo ka JPEG. Bitmap yahin mit jata hai, sirf bytes bachte hain. */
internal fun encodePhoto(source: () -> InputStream?, quality: Int, maxSide: Int = MAX_PHOTO_SIDE): EncodedPhoto {
    val decoded = loadPhoto(source, maxSide) ?: throw IOException("unreadable photo")
    val photo = decoded.flattened()
    if (photo !== decoded) decoded.recycle()
    return try {
        val jpeg = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        EncodedPhoto(jpeg.toByteArray(), photo.width, photo.height)
    } finally {
        photo.recycle()
    }
}
