package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val MAX_PHOTO_SIDE = 2200
private const val PERCENT = 100
internal const val FULL_TURN = 360
internal const val QUARTER_TURN = 90
private const val HALF_TURN = 180

/** Pehle sirf quality ghatti hai, phir size. Target milte hi ruk jate hain, taaki photo jitni ho sake saaf rahe. */
private val SHRINK_STEPS = listOf(
    1f to 0.85f,
    1f to 0.7f,
    0.8f to 0.7f,
    0.65f to 0.65f,
    0.5f to 0.6f,
    0.4f to 0.55f,
    0.3f to 0.5f,
    0.2f to 0.45f,
    0.15f to 0.4f,
    0.1f to 0.35f,
)

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

/** Form ke liye photo chhoti karta hai. False ka matlab target tak nahi pahunchi, aur sabse chhoti file di gayi. */
fun shrinkPhoto(source: () -> InputStream?, targetBytes: Long, out: File): Boolean {
    val photo = loadPhoto(source)?.flattened() ?: throw IOException("unreadable photo")
    var best: ByteArray? = null
    for ((scale, quality) in SHRINK_STEPS) {
        val width = (photo.width * scale).toInt().coerceAtLeast(1)
        val height = (photo.height * scale).toInt().coerceAtLeast(1)
        val sized = if (scale < 1f) photo.scale(width, height) else photo
        val bytes = ByteArrayOutputStream().also {
            sized.compress(Bitmap.CompressFormat.JPEG, (quality * PERCENT).toInt(), it)
        }.toByteArray()
        if (sized !== photo) sized.recycle()
        if (bytes.size < (best?.size ?: Int.MAX_VALUE)) best = bytes
        if (bytes.size <= targetBytes) break
    }
    photo.recycle()
    val smallest = best ?: throw IOException("nothing written")
    out.writeBytes(smallest)
    return smallest.size <= targetBytes
}
