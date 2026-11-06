package app.kholo.reader.office

import java.io.IOException

private const val BYTE_MASK = 0xFF
private const val BITS_8 = 8
private const val BITS_16 = 16
private const val BITS_24 = 24
private const val BITS_32 = 32
private const val INT_MASK = 0xFFFFFFFFL

/** File sahi hai par aisa format hai jo hum nahi padhte, jaise Word 6 ya Excel 95. */
class UnsupportedOffice : IOException()

/** Binary Office formats little-endian hain. File par bharosa nahi, galat offset yahan exception deta hai. */
internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and BYTE_MASK

internal fun ByteArray.u16(at: Int): Int = u8(at) or (u8(at + 1) shl BITS_8)

internal fun ByteArray.i32(at: Int): Int =
    u8(at) or (u8(at + 1) shl BITS_8) or (u8(at + 2) shl BITS_16) or (u8(at + 3) shl BITS_24)

internal fun ByteArray.u32(at: Int): Long = i32(at).toLong() and INT_MASK

internal fun ByteArray.i64(at: Int): Long = u32(at) or (u32(at + 4) shl BITS_32)

internal fun littleEndian(value: Int): ByteArray = byteArrayOf(
    value.toByte(),
    (value shr BITS_8).toByte(),
    (value shr BITS_16).toByte(),
    (value shr BITS_24).toByte(),
)

/** Lambi file ki length Int me na aaye to kharab maano, warna negative offset se galat data padha jata. */
internal fun Long.toSafeInt(): Int {
    if (this < 0 || this > Int.MAX_VALUE) throw IOException("bad length")
    return toInt()
}
