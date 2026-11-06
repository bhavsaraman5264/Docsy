package app.kholo.reader.text

import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

private const val CHUNK_BYTES = 8 * 1024
private const val BOUNDARY_WINDOW = 1024
private const val NEWLINE = '\n'.code.toByte()
private const val UTF8_CONTINUATION_MASK = 0xC0
private const val UTF8_CONTINUATION = 0x80

/**
 * Bahut badi text file: pehle se koi index nahi banta, isliye 1 GB ki file bhi turant khulti hai.
 * Har tukde ki seema uski tay jagah ke paas hi dhoondhte hain, aur wahi hissa padhte hain jo screen par aata hai.
 */
class TextPager(
    private val stream: FileInputStream,
    private val size: Long,
    private val charset: Charset,
    private val start: Long,
) : Closeable {
    private val channel: FileChannel = stream.channel
    private val window = ByteBuffer.allocate(BOUNDARY_WINDOW)

    val chunkCount: Int = countChunks()

    private fun countChunks(): Int {
        val body = (size - start).coerceAtLeast(0L)
        val nominal = ((body + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt().coerceAtLeast(1)
        // Aakhri seema file ke ant par gire to aakhri tukda khaali hota.
        return if (nominal > 1 && boundary(nominal - 1) >= size) nominal - 1 else nominal
    }

    private fun boundary(index: Int): Long = when {
        index <= 0 -> start
        else -> (start + index.toLong() * CHUNK_BYTES).let { if (it >= size) size else boundaryNear(it) }
    }

    /** Line par kaatna behtar hai, warna kam se kam aadhe akshar par nahi. */
    private fun boundaryNear(position: Long): Long {
        window.clear()
        val read = channel.read(window, position).coerceAtLeast(0)
        val bytes = window.array()
        val newline = (0 until read).firstOrNull { bytes[it] == NEWLINE }
        if (newline != null) return position + newline + 1
        var i = 0
        while (i < read && (bytes[i].toInt() and UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION) i++
        return position + i
    }

    @Synchronized
    fun chunk(index: Int): String {
        val from = boundary(index)
        val to = if (index + 1 >= chunkCount) size else boundary(index + 1)
        val buffer = ByteBuffer.allocate((to - from).toInt())
        var position = from
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read <= 0) break
            position += read
        }
        return String(buffer.array(), 0, buffer.position(), charset).trimEnd('\n', '\r')
    }

    override fun close() = stream.close()
}
