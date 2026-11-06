package app.kholo.reader.office

import java.io.IOException
import java.security.MessageDigest

private const val RC4_STATE = 256
private const val BYTE_MASK = 0xFF
private const val SALT_LENGTH = 16
private const val VERIFIER_LENGTH = 16
private const val MD5_LENGTH = 16
private const val SHA1_LENGTH = 20
private const val TRUNCATED = 5
private const val REPEATS = 16
private const val KEY_40_BITS = 40
private const val KEY_128_BYTES = 16
private const val BITS_PER_BYTE = 8
private const val VERSION_RC4 = 1
private const val VERSION_LENGTH = 4
private const val CRYPTO_API_MINOR = 2
private const val HEADER_SIZE_AT = 8
private const val HEADER_START = 12
private const val HEADER_KEY_BITS_AT = 16

/** Word aur Excel 97-2003 ka RC4. Har block ki chaabi alag hoti hai, isliye har block par naya RC4. */
class LegacyKey internal constructor(private val keyFor: (Int) -> ByteArray) {
    /** `bytes` ke saare blocks jagah par hi khulte hain. */
    fun decrypt(bytes: ByteArray, blockSize: Int) {
        var block = 0
        var start = 0
        while (start < bytes.size) {
            rc4(keyFor(block++), bytes, start, minOf(blockSize, bytes.size - start))
            start += blockSize
        }
    }
}

internal fun rc4(key: ByteArray, bytes: ByteArray, offset: Int, length: Int) {
    val state = IntArray(RC4_STATE) { it }
    var j = 0
    for (i in 0 until RC4_STATE) {
        j = (j + state[i] + (key[i % key.size].toInt() and BYTE_MASK)) and BYTE_MASK
        state[i] = state[j].also { state[j] = state[i] }
    }
    var a = 0
    var b = 0
    for (index in offset until offset + length) {
        a = (a + 1) and BYTE_MASK
        b = (b + state[a]) and BYTE_MASK
        state[a] = state[b].also { state[b] = state[a] }
        val mask = state[(state[a] + state[b]) and BYTE_MASK]
        bytes[index] = (bytes[index].toInt() xor mask).toByte()
    }
}

/** MS-OFFCRYPTO 2.3.5 aur 2.3.6. Header Word ki table stream ya Excel ke FilePass se aata hai. */
object LegacyCrypto {
    /** Galat password par null. Header anjaan ho to [UnsupportedOffice]. */
    fun key(header: ByteArray, at: Int, password: String): LegacyKey? {
        val major = header.u16(at)
        val minor = header.u16(at + 2)
        return when {
            major == VERSION_RC4 && minor == VERSION_RC4 -> plainRc4(header, at + VERSION_LENGTH, password)
            minor == CRYPTO_API_MINOR -> cryptoApi(header, at, password)
            else -> throw UnsupportedOffice()
        }
    }

    private fun plainRc4(header: ByteArray, at: Int, password: String): LegacyKey? {
        val salt = header.copyOfRange(at, at + SALT_LENGTH)
        val md5 = MessageDigest.getInstance("MD5")
        val first = md5.digest(password.toByteArray(Charsets.UTF_16LE)).copyOf(TRUNCATED)
        var mixed = ByteArray(0)
        repeat(REPEATS) { mixed += first + salt }
        val second = md5.digest(mixed).copyOf(TRUNCATED)
        val keyOf = { block: Int -> md5.digest(second + littleEndian(block)) }
        val verifierAt = at + SALT_LENGTH
        val check = header.copyOfRange(verifierAt, verifierAt + VERIFIER_LENGTH + MD5_LENGTH)
        rc4(keyOf(0), check, 0, check.size)
        val hash = md5.digest(check.copyOf(VERIFIER_LENGTH))
        return LegacyKey(keyOf).takeIf { hash.contentEquals(check.copyOfRange(VERIFIER_LENGTH, check.size)) }
    }

    private fun cryptoApi(header: ByteArray, at: Int, password: String): LegacyKey? {
        val headerSize = header.i32(at + HEADER_SIZE_AT)
        if (headerSize < 0) throw IOException("bad header")
        val start = at + HEADER_START
        val bits = header.i32(start + HEADER_KEY_BITS_AT).let { if (it == 0) KEY_40_BITS else it }
        val saltAt = start + headerSize + Int.SIZE_BYTES
        val salt = header.copyOfRange(saltAt, saltAt + SALT_LENGTH)
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(salt)
        val base = sha1.digest(password.toByteArray(Charsets.UTF_16LE))
        val keyOf = { block: Int ->
            val hash = sha1.digest(base + littleEndian(block))
            // 40-bit chaabi 128-bit RC4 me zero ke saath chalti hai.
            if (bits == KEY_40_BITS) hash.copyOf(TRUNCATED).copyOf(KEY_128_BYTES) else hash.copyOf(bits / BITS_PER_BYTE)
        }
        val encryptedAt = saltAt + SALT_LENGTH
        val hashAt = encryptedAt + VERIFIER_LENGTH + Int.SIZE_BYTES
        val check = header.copyOfRange(encryptedAt, encryptedAt + VERIFIER_LENGTH) +
            header.copyOfRange(hashAt, hashAt + SHA1_LENGTH)
        rc4(keyOf(0), check, 0, check.size)
        val hash = sha1.digest(check.copyOf(VERIFIER_LENGTH))
        return LegacyKey(keyOf).takeIf { hash.contentEquals(check.copyOfRange(VERIFIER_LENGTH, check.size)) }
    }
}
