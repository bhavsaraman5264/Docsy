package app.kholo.reader.table.xlsx

import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.xmlpull.v1.XmlPullParser

private const val INITIAL_SIZE = 4096

/** Shared strings ko Java heap me nahi rakhte: temp file me likh kar memory-map se padhte hain. */
class SharedStringsFile private constructor(
    private val file: File,
    private val offsets: IntArray,
    private val count: Int,
) : Closeable {
    private val access = RandomAccessFile(file, "r")
    private val mapped: MappedByteBuffer? =
        if (file.length() > 0) access.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()) else null

    /** Galat index par khaali text, crash nahi. */
    fun get(index: Int): String {
        val buffer = mapped
        if (buffer == null || index !in 0 until count) return ""
        val start = offsets[index]
        val end = offsets[index + 1]
        val bytes = ByteArray(end - start)
        buffer.duplicate().apply { position(start) }.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    override fun close() {
        access.close()
        file.delete()
    }

    companion object {
        fun read(zip: SafeZip, target: File): SharedStringsFile {
            var offsets = IntArray(INITIAL_SIZE)
            var count = 0
            var written = 0
            DataOutputStream(target.outputStream().buffered()).use { out ->
                zip.open("xl/sharedStrings.xml")?.use { stream ->
                    val parser = safeXml(stream)
                    val text = StringBuilder()
                    var inPhonetic = false
                    var inText = false
                    parser.walk { event ->
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "si" -> text.clear()
                                "rPh" -> inPhonetic = true
                                "t" -> inText = !inPhonetic
                            }
                            XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "t" -> inText = false
                                "rPh" -> inPhonetic = false
                                "si" -> {
                                    val bytes = decodeEscapes(text.toString()).toByteArray()
                                    out.write(bytes)
                                    written += bytes.size
                                    count++
                                    if (count + 1 >= offsets.size) offsets = offsets.copyOf(offsets.size * 2)
                                    offsets[count] = written
                                }
                            }
                        }
                    }
                }
            }
            return SharedStringsFile(target, offsets, count)
        }

        /** Excel `_x000D_` jaise escape likhta hai. */
        fun decodeEscapes(text: String): String = if ("_x" !in text) {
            text
        } else {
            Regex("_x([0-9A-Fa-f]{4})_").replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        }
    }
}
