package app.kholo.reader.office

import app.kholo.reader.core.file.SafeZip
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

private const val HEADER_SIZE = 512
private const val ENTRY_SIZE = 128
private const val HEADER_DIFAT_SLOTS = 109
private const val HEADER_DIFAT_AT = 76
private const val SECTOR_SHIFT_AT = 30
private const val MINI_SHIFT_AT = 32
private const val FAT_COUNT_AT = 44
private const val DIRECTORY_AT = 48
private const val MINI_CUTOFF_AT = 56
private const val MINI_FAT_AT = 60
private const val DIFAT_AT = 68
private const val SHIFT_V3 = 9
private const val SHIFT_V4 = 12
private const val END_OF_CHAIN = -2
private const val FREE_SECTOR = -1
private const val NO_ENTRY = -1
private const val NAME_LENGTH_AT = 64
private const val TYPE_AT = 66
private const val LEFT_AT = 68
private const val RIGHT_AT = 72
private const val CHILD_AT = 76
private const val START_AT = 116
private const val SIZE_AT = 120
private const val TYPE_STREAM = 2
private const val INT_BYTES = 4
private const val MAX_NAME_BYTES = 64

/** Ek stream ki memory me aane ki seema. Isse badi purani Office file phone par khulni nahi chahiye. */
const val MAX_STREAM_BYTES = 96 * 1024 * 1024

private class Entry(
    val name: String,
    val type: Int,
    val left: Int,
    val right: Int,
    val child: Int,
    val start: Int,
    val size: Long,
)

/**
 * OLE2 (Compound File) reader: purani DOC, XLS, PPT aur password wali Office files yahi format hain.
 * Apache POI app ko bahut bhaari karta, yeh kuch sau lines hain. File par bharosa nahi: har chain ki
 * lambai aur cycle jaanchi jati hai, aur root ke seedhe streams hi dikhte hain.
 */
class OleFile(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val sectorSize: Int
    private val miniSize: Int
    private val miniCutoff: Long
    private val wide: Boolean
    private val fat: IntArray
    private val miniFat: IntArray
    private val root: Entry
    private val streams: Map<String, Entry>
    private var miniStream: ByteArray? = null

    init {
        try {
            val header = ByteArray(HEADER_SIZE)
            raf.readFully(header)
            if (!header.copyOf(MAGIC.size).contentEquals(MAGIC)) throw IOException("not ole2")
            val shift = header.u16(SECTOR_SHIFT_AT)
            if (shift != SHIFT_V3 && shift != SHIFT_V4) throw IOException("bad sector size")
            sectorSize = 1 shl shift
            wide = shift == SHIFT_V4
            miniSize = 1 shl header.u16(MINI_SHIFT_AT).coerceIn(1, SHIFT_V4)
            miniCutoff = header.u32(MINI_CUTOFF_AT)
            fat = readFat(header)
            miniFat = readInts(chain(header.i32(MINI_FAT_AT)))
            val entries = readEntries(header.i32(DIRECTORY_AT))
            root = entries.firstOrNull() ?: throw IOException("no root")
            streams = rootStreams(entries)
        } catch (e: IOException) {
            raf.close()
            throw e
        } catch (e: IndexOutOfBoundsException) {
            raf.close()
            throw IOException(e)
        }
    }

    private fun sectorCount(): Int = ((raf.length() + sectorSize - 1) / sectorSize).toSafeInt()

    private fun readFat(header: ByteArray): IntArray {
        val limit = sectorCount()
        val perSector = sectorSize / INT_BYTES
        val declared = header.i32(FAT_COUNT_AT)
        if (declared < 0 || declared > limit) throw IOException("bad fat")
        val sectors = ArrayList<Int>(declared)
        for (slot in 0 until HEADER_DIFAT_SLOTS) {
            val sector = header.i32(HEADER_DIFAT_AT + slot * INT_BYTES)
            if (sector >= 0 && sectors.size < declared) sectors += sector
        }
        var next = header.i32(DIFAT_AT)
        var hops = 0
        while (next >= 0 && sectors.size < declared) {
            if (++hops > limit) throw IOException("difat loop")
            val block = readSector(next)
            for (slot in 0 until perSector - 1) {
                val sector = block.i32(slot * INT_BYTES)
                if (sector >= 0 && sectors.size < declared) sectors += sector
            }
            next = block.i32((perSector - 1) * INT_BYTES)
        }
        return readInts(sectors)
    }

    private fun readInts(sectors: List<Int>): IntArray {
        val perSector = sectorSize / INT_BYTES
        val values = IntArray(sectors.size * perSector)
        sectors.forEachIndexed { index, sector ->
            val block = readSector(sector)
            for (slot in 0 until perSector) values[index * perSector + slot] = block.i32(slot * INT_BYTES)
        }
        return values
    }

    private fun readSector(sector: Int): ByteArray {
        val block = ByteArray(sectorSize)
        readAt(offsetOf(sector), block, 0, sectorSize)
        return block
    }

    private fun offsetOf(sector: Int): Long = (sector.toLong() + 1) * sectorSize

    /** Aakhri sector file ke ant se pehle kat sakta hai, isliye jitna mile utna hi lautta hai. */
    @Synchronized
    private fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || position >= raf.length()) throw IOException("sector outside file")
        raf.seek(position)
        var done = 0
        while (done < length) {
            val read = raf.read(target, offset + done, length - done)
            if (read < 0) break
            done += read
        }
        return done
    }

    private fun chain(start: Int): List<Int> = follow(start, fat)

    private fun follow(start: Int, table: IntArray): List<Int> {
        val sectors = ArrayList<Int>()
        var sector = start
        while (sector != END_OF_CHAIN && sector != FREE_SECTOR) {
            if (sector < 0 || sector >= table.size || sectors.size >= table.size) throw IOException("bad chain")
            sectors += sector
            sector = table[sector]
        }
        return sectors
    }

    private fun readEntries(start: Int): List<Entry> {
        val entries = ArrayList<Entry>()
        for (sector in chain(start)) {
            val block = readSector(sector)
            for (at in 0 until sectorSize step ENTRY_SIZE) entries += readEntry(block, at)
        }
        return entries
    }

    private fun readEntry(block: ByteArray, at: Int): Entry {
        val nameBytes = block.u16(at + NAME_LENGTH_AT).coerceIn(2, MAX_NAME_BYTES) - 2
        return Entry(
            name = String(block, at, nameBytes, Charsets.UTF_16LE),
            type = block.u8(at + TYPE_AT),
            left = block.i32(at + LEFT_AT),
            right = block.i32(at + RIGHT_AT),
            child = block.i32(at + CHILD_AT),
            start = block.i32(at + START_AT),
            // Version 3 me upar ke 4 bytes kachra ho sakte hain.
            size = if (wide) block.i64(at + SIZE_AT) else block.u32(at + SIZE_AT),
        )
    }

    private fun rootStreams(entries: List<Entry>): Map<String, Entry> {
        val found = HashMap<String, Entry>()
        val seen = BooleanArray(entries.size)
        val pending = ArrayDeque<Int>()
        pending += entries[0].child
        while (pending.isNotEmpty()) {
            val index = pending.removeLast()
            if (index == NO_ENTRY || index !in entries.indices || seen[index]) continue
            seen[index] = true
            val entry = entries[index]
            if (entry.type == TYPE_STREAM) found[entry.name.lowercase()] = entry
            pending += entry.left
            pending += entry.right
        }
        return found
    }

    fun has(name: String): Boolean = name.lowercase() in streams

    private fun entry(name: String): Entry = streams[name.lowercase()] ?: throw IOException("no stream")

    /** Poori stream memory me. Badi file ke liye [open] use karo. */
    fun read(name: String, max: Int = MAX_STREAM_BYTES): ByteArray {
        val entry = entry(name)
        if (entry.size > max) throw SafeZip.TooBig()
        return open(name).use { stream -> stream.readExactly(entry.size.toSafeInt()) }
    }

    fun open(name: String): InputStream {
        val entry = entry(name)
        return if (entry.size < miniCutoff) {
            ByteArrayInputStream(readMini(entry))
        } else {
            ChainStream(chain(entry.start), entry.size)
        }
    }

    private fun InputStream.readExactly(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var done = 0
        while (done < size) {
            val read = read(bytes, done, size - done)
            if (read < 0) throw IOException("stream cut short")
            done += read
        }
        return bytes
    }

    private fun readMini(entry: Entry): ByteArray {
        val container = miniStream ?: readRootStream().also { miniStream = it }
        val size = entry.size.toSafeInt()
        val bytes = ByteArray(size)
        var done = 0
        for (sector in follow(entry.start, miniFat)) {
            if (done >= size) break
            val count = minOf(miniSize, size - done)
            System.arraycopy(container, sector * miniSize, bytes, done, count)
            done += count
        }
        if (done < size) throw IOException("mini stream cut short")
        return bytes
    }

    private fun readRootStream(): ByteArray {
        if (root.size > MAX_STREAM_BYTES) throw SafeZip.TooBig()
        return ChainStream(chain(root.start), root.size).use { it.readExactly(root.size.toSafeInt()) }
    }

    override fun close() = raf.close()

    private inner class ChainStream(private val sectors: List<Int>, size: Long) : InputStream() {
        private var left = size
        private var position = 0L

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single.u8(0)
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (left <= 0) return -1
            val index = (position / sectorSize).toSafeInt()
            if (index >= sectors.size) throw IOException("chain shorter than stream")
            val inSector = (position % sectorSize).toInt()
            val wanted = minOf(length.toLong(), left, (sectorSize - inSector).toLong()).toInt()
            val read = readAt(offsetOf(sectors[index]) + inSector, target, offset, wanted)
            if (read <= 0) throw IOException("stream cut short")
            position += read
            left -= read
            return read
        }
    }

    private companion object {
        val MAGIC = byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11,
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A,
            0xE1.toByte(),
        )
    }
}
