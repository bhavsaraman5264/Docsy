package app.kholo.reader.core.file

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

private const val MAX_ENTRIES = 10_000
private const val MAX_TOTAL_BYTES = 1L shl 30

/**
 * Office files ZIP hoti hain, aur file par bharosa nahi kiya ja sakta. Asli nikle bytes gine jate
 * hain, `entry.size` par bharosa nahi. Kuch bhi disk par extract nahi hota.
 */
class SafeZip(file: File) : Closeable {
    private val zip = ZipFile(file)
    private var total = 0L

    init {
        if (zip.size() > MAX_ENTRIES) {
            zip.close()
            throw TooBig()
        }
    }

    fun exists(name: String): Boolean = zip.getEntry(name) != null

    fun open(name: String): InputStream? {
        val entry = zip.getEntry(name) ?: return null
        return Counting(zip.getInputStream(entry))
    }

    override fun close() = zip.close()

    private inner class Counting(source: InputStream) : FilterInputStream(source) {
        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it >
                0
            ) {
                count(it)
            }
        }

        private fun count(bytes: Int) {
            total += bytes
            if (total > MAX_TOTAL_BYTES) throw TooBig()
        }
    }

    class TooBig : IOException()
}
