package app.kholo.reader.core.file

import android.content.Context
import android.net.Uri
import app.kholo.reader.core.AppError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

private const val DIR = "open"
private const val SPACE_MARGIN = 2L
internal const val COPY_BUFFER = 64 * 1024

/** Sirf kholi hui file ke liye private cache. Viewer band hote hi delete, aur app start par safai. */
class TempFiles(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, DIR)

    fun newFile(suffix: String): File {
        dir.mkdirs()
        return File.createTempFile("doc", suffix, dir)
    }

    /** ZipFile aur seek ke liye asli file chahiye. */
    fun copy(uri: Uri, suffix: String, knownSize: Long?): File {
        checkSpace(knownSize)
        val file = newFile(suffix)
        val copied = try {
            appContext.contentResolver.openInputStream(uri)?.use { source ->
                file.outputStream().use { source.copyTo(it, COPY_BUFFER) }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        if (copied == null) throw FileNotFoundException()
        return file
    }

    private fun checkSpace(knownSize: Long?) {
        val free = dir.parentFile?.usableSpace ?: return
        if (knownSize != null && free < knownSize * SPACE_MARGIN) throw OutOfSpace()
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    class OutOfSpace : IOException()
}

/** Zyada se zyada [max] bytes. `readNBytes` Android 13 se pehle nahi hai. */
fun InputStream.readUpTo(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER)
    var left = max
    while (left > 0) {
        val read = read(buffer, 0, minOf(buffer.size, left))
        if (read < 0) break
        out.write(buffer, 0, read)
        left -= read
    }
    return out.toByteArray()
}

/** File read ke aam failures ko user ke message me badalta hai. */
fun Throwable.toAppError(): AppError = when (this) {
    is TempFiles.OutOfSpace -> AppError.NoSpace
    is SafeZip.TooBig -> AppError.TooLarge
    is app.kholo.reader.office.UnsupportedOffice -> AppError.Unsupported
    is SecurityException -> AppError.NeedsAccess
    is java.io.FileNotFoundException -> if (isDiskFull()) AppError.NoSpace else AppError.FileGone
    is IOException -> if (isDiskFull()) AppError.NoSpace else AppError.Corrupt
    else -> AppError.Corrupt
}

/** Android disk bhar jane ka alag exception nahi deta, sirf message me ENOSPC aata hai. */
private fun Throwable.isDiskFull(): Boolean = message.orEmpty().contains("ENOSPC")
