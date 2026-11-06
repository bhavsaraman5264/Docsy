package app.kholo.reader.core.file

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import app.kholo.reader.open.DocumentType
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipInputStream

private const val HEAD_BYTES = 1100
private const val MAX_SNIFF_ENTRIES = 50

data class FileInfo(val name: String?, val size: Long?, val mime: String?, val head: ByteArray) {
    override fun equals(other: Any?): Boolean = other is FileInfo && name == other.name && size == other.size

    override fun hashCode(): Int = name.hashCode() * 31 + size.hashCode()
}

/** Content URI se file ki jaankari padhta hai. Doosri app ka provider fail ho sakta hai, isliye sab sambhala hai. */
class UriReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun inspect(uri: Uri): DocResult<FileInfo> = try {
        val head = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(HEAD_BYTES)
            val read = input.readNBytesCompat(buffer)
            buffer.copyOf(read)
        }
        if (head == null) {
            DocResult.Failure(AppError.FileGone)
        } else {
            val (name, size) = queryNameAndSize(uri)
            DocResult.Success(FileInfo(name ?: uri.lastPathSegment, size, resolver.getType(uri), head))
        }
    } catch (_: FileNotFoundException) {
        DocResult.Failure(AppError.FileGone)
    } catch (_: SecurityException) {
        DocResult.Failure(AppError.NeedsAccess)
    } catch (_: IOException) {
        DocResult.Failure(AppError.FileGone)
    } catch (_: IllegalArgumentException) {
        DocResult.Failure(AppError.FileGone)
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = resolver.query(uri, columns, null, null, null) ?: return null to null
        return cursor.use {
            if (it.moveToFirst()) readRow(it) else null to null
        }
    }

    private fun readRow(row: Cursor): Pair<String?, Long?> {
        val nameColumn = row.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeColumn = row.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameColumn >= 0) row.getString(nameColumn) else null
        val size = if (sizeColumn >= 0 && !row.isNull(sizeColumn)) row.getLong(sizeColumn) else null
        return name to size
    }

    /** Naam ke bina aayi ZIP (jaise WhatsApp se) kis tarah ki Office file hai. */
    fun zipType(uri: Uri): DocumentType = try {
        resolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                generateSequence { zip.nextEntry }
                    .take(MAX_SNIFF_ENTRIES)
                    .map { DocumentType.fromZipEntry(it.name) }
                    .firstOrNull { it != DocumentType.Unknown }
            }
        } ?: DocumentType.Unknown
    } catch (_: IOException) {
        DocumentType.Unknown
    } catch (_: SecurityException) {
        DocumentType.Unknown
    } catch (_: IllegalArgumentException) {
        DocumentType.Unknown
    }

    /** File picker se mili access app restart ke baad bhi rahe, taaki recent list se file khul sake. */
    fun keepAccess(uri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    }

    fun hasKeptAccess(uri: Uri): Boolean = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    fun releaseAccess(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Access tha hi nahi, kuch karna nahi.
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}
