package app.kholo.reader.table.csv

import java.io.InputStream

private const val BUFFER_SIZE = 256 * 1024
private const val MAX_RECORD_BYTES = 1 shl 20
private const val QUOTE = '"'.code.toByte()
private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

/**
 * CSV ko raw bytes me padhta hai. Indexer aur dikhane wala code yahi use karte hain, taaki dono ke
 * record boundary hamesha same hon. Band na hua quote 1 MB ke baad chhod diya jata hai.
 */
class CsvRecords(private val input: InputStream, private val delimiter: Byte, start: Long = 0L) {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var length = 0
    private var index = 0

    /** Agle record ka byte offset. */
    var position: Long = start
        private set

    private fun next(): Int {
        if (index == length) {
            length = input.read(buffer)
            index = 0
            if (length <= 0) {
                length = 0
                return -1
            }
        }
        position++
        return buffer[index++].toInt() and 0xFF
    }

    private fun peek(): Int {
        if (index == length) {
            length = input.read(buffer)
            index = 0
            if (length <= 0) {
                length = 0
                return -1
            }
        }
        return buffer[index].toInt() and 0xFF
    }

    /** Record chhod kar aage badho. False matlab file khatam. */
    fun skip(): Boolean = read(null)

    /** Agla record [fields] me. False matlab file khatam. */
    fun read(fields: MutableList<ByteArray>?): Boolean {
        var inQuotes = false
        var size = 0
        var any = false
        val field = if (fields != null) java.io.ByteArrayOutputStream() else null
        while (true) {
            val value = next()
            if (value < 0) {
                if (!any) return false
                break
            }
            any = true
            size++
            val byte = value.toByte()
            if (inQuotes) {
                if (size > MAX_RECORD_BYTES) inQuotes = false
                if (byte == QUOTE) {
                    if (peek() == QUOTE.toInt()) {
                        next()
                        field?.write(value)
                    } else {
                        inQuotes = false
                    }
                } else if (size <= MAX_RECORD_BYTES) {
                    field?.write(value)
                }
                continue
            }
            when (byte) {
                QUOTE -> inQuotes = true
                delimiter -> {
                    fields?.add(field?.toByteArray() ?: ByteArray(0))
                    field?.reset()
                }
                CR -> {
                    if (peek() == LF.toInt()) next()
                    break
                }
                LF -> break
                else -> if (size <= MAX_RECORD_BYTES) field?.write(value)
            }
        }
        fields?.add(field?.toByteArray() ?: ByteArray(0))
        return true
    }
}
