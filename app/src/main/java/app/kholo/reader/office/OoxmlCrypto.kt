package app.kholo.reader.office

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val AGILE_VERSION = 4
private const val STANDARD_MINOR = 2
private const val HEADER_SIZE_AT = 8
private const val HEADER_START = 12
private const val AGILE_XML_AT = 8
private const val HEADER_ALG_AT = 8
private const val HEADER_KEY_BITS_AT = 16
private const val SALT_LENGTH = 16
private const val VERIFIER_LENGTH = 16
private const val HASH_LENGTH = 32
private const val SHA1_LENGTH = 20
private const val STANDARD_SPIN = 50_000
private const val SEGMENT = 4096
private const val SIZE_PREFIX = 8
private const val BITS_PER_BYTE = 8
private const val PAD_INNER = 0x36
private const val PAD_OUTER = 0x5C
private const val HMAC_BLOCK = 64
private const val MAX_SPIN = 10_000_000
private const val AES_128 = 0x660E
private const val AES_256 = 0x6610
private const val AES_BLOCK = 16
private const val PAD_BYTE = 0x36.toByte()

private val VERIFIER_INPUT = hex("FEA7D2763B4B9E79")
private val VERIFIER_VALUE = hex("D7AA0F6D3061344E")
private val KEY_VALUE = hex("146E0BE7ABACD0D6")

private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()

/** Sahi password se bani chaabi, jo `EncryptedPackage` ko asli ZIP me badalti hai. */
fun interface PackageKey {
    fun decrypt(source: InputStream, target: OutputStream)
}

/**
 * MS-OFFCRYPTO: password wali DOCX, XLSX aur PPTX asal me ek OLE2 file me bandh ZIP hain.
 * Office 2010 ke baad Agile (XML descriptor, SHA-512), Office 2007 me Standard (AES-ECB, SHA-1).
 */
object OoxmlCrypto {
    /** Galat password par null. Certificate ya kisi anjaan tareeke par [UnsupportedOffice]. */
    fun key(info: ByteArray, password: String): PackageKey? {
        val major = info.u16(0)
        val minor = info.u16(2)
        return try {
            when {
                major == AGILE_VERSION && minor == AGILE_VERSION -> agile(info, password)
                minor == STANDARD_MINOR -> standard(info, password)
                else -> throw UnsupportedOffice()
            }
        } catch (_: GeneralSecurityException) {
            throw UnsupportedOffice()
        }
    }

    private fun attributes(xml: String, tag: String, needed: String): Map<String, String>? =
        Regex("<(?:\\w+:)?$tag\\b([^>]*)>").findAll(xml)
            .map { match ->
                Regex("(\\w+)=\"([^\"]*)\"").findAll(match.groupValues[1])
                    .associate { it.groupValues[1] to it.groupValues[2] }
            }
            .firstOrNull { needed in it }

    private fun Map<String, String>.required(name: String): String = get(name) ?: throw UnsupportedOffice()

    private fun Map<String, String>.bytes(name: String): ByteArray = try {
        Base64.getDecoder().decode(required(name))
    } catch (_: IllegalArgumentException) {
        throw IOException("bad base64")
    }

    private fun Map<String, String>.number(name: String): Int =
        required(name).toIntOrNull()?.takeIf { it > 0 } ?: throw IOException("bad number")

    private fun digest(name: String): MessageDigest = MessageDigest.getInstance(
        when (name.uppercase().replace("-", "")) {
            "SHA1" -> "SHA-1"
            "SHA256" -> "SHA-256"
            "SHA384" -> "SHA-384"
            "SHA512" -> "SHA-512"
            else -> throw UnsupportedOffice()
        },
    )

    private fun agile(info: ByteArray, password: String): PackageKey? {
        val xml = String(info, AGILE_XML_AT, info.size - AGILE_XML_AT, Charsets.UTF_8)
        val data = attributes(xml, "keyData", "saltValue") ?: throw UnsupportedOffice()
        // Certificate wale encryptor me spinCount nahi hota, isliye password wala isi se pehchana jata hai.
        val encryptor = attributes(xml, "encryptedKey", "spinCount") ?: throw UnsupportedOffice()
        if (!encryptor.required("cipherChaining").contains("CBC", ignoreCase = true) ||
            !encryptor.required("cipherAlgorithm").equals("AES", ignoreCase = true)
        ) {
            throw UnsupportedOffice()
        }
        val hash = digest(encryptor.required("hashAlgorithm"))
        val salt = encryptor.bytes("saltValue")
        val keyBytes = encryptor.number("keyBits") / BITS_PER_BYTE
        val blockSize = encryptor.number("blockSize")
        val spin = encryptor.number("spinCount")
        if (spin > MAX_SPIN) throw IOException("spin too high")
        val base = spunHash(hash, salt, password, spin)
        val open = { block: ByteArray, value: ByteArray ->
            val key = fit(hash.digestOf(base, block), keyBytes)
            aesCbc(key, fit(salt, blockSize), value)
        }
        val input = open(VERIFIER_INPUT, encryptor.bytes("encryptedVerifierHashInput"))
        val value = open(VERIFIER_VALUE, encryptor.bytes("encryptedVerifierHashValue"))
        val expected = hash.digest(input)
        if (!expected.contentEquals(value.copyOf(expected.size))) return null
        val packageKey = open(KEY_VALUE, encryptor.bytes("encryptedKeyValue")).copyOf(keyBytes)
        return agilePackage(packageKey, data)
    }

    private fun agilePackage(key: ByteArray, data: Map<String, String>): PackageKey {
        val hash = digest(data.required("hashAlgorithm"))
        val salt = data.bytes("saltValue")
        val blockSize = data.number("blockSize")
        return PackageKey { source, target ->
            copySegments(source, target) { index, segment, length ->
                val iv = fit(hash.digestOf(salt, littleEndian(index)), blockSize)
                aesCbc(key, iv, segment.copyOf(length - length % blockSize))
            }
        }
    }

    private fun copySegments(source: InputStream, target: OutputStream, open: (Int, ByteArray, Int) -> ByteArray) {
        val prefix = ByteArray(SIZE_PREFIX)
        if (fill(source, prefix) < SIZE_PREFIX) throw IOException("no size")
        var left = prefix.i64(0)
        if (left < 0) throw IOException("bad size")
        val segment = ByteArray(SEGMENT)
        var index = 0
        while (left > 0) {
            val read = fill(source, segment)
            if (read == 0) throw IOException("package cut short")
            val plain = open(index++, segment, read)
            val keep = minOf(left, plain.size.toLong()).toInt()
            if (keep == 0) throw IOException("package cut short")
            target.write(plain, 0, keep)
            left -= keep
        }
    }

    private fun fill(source: InputStream, target: ByteArray): Int {
        var done = 0
        while (done < target.size) {
            val read = source.read(target, done, target.size - done)
            if (read < 0) break
            done += read
        }
        return done
    }

    private fun spunHash(hash: MessageDigest, salt: ByteArray, password: String, spin: Int): ByteArray {
        var value = hash.digestOf(salt, password.toByteArray(Charsets.UTF_16LE))
        for (round in 0 until spin) value = hash.digestOf(littleEndian(round), value)
        return value
    }

    private fun MessageDigest.digestOf(first: ByteArray, second: ByteArray): ByteArray {
        update(first)
        return digest(second)
    }

    private fun fit(value: ByteArray, size: Int): ByteArray {
        if (value.size >= size) return value.copyOf(size)
        return value + ByteArray(size - value.size) { PAD_BYTE }
    }

    private fun aesCbc(key: ByteArray, iv: ByteArray, value: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(value)
        }

    private fun standard(info: ByteArray, password: String): PackageKey? {
        val headerSize = info.i32(HEADER_SIZE_AT)
        if (headerSize < 0) throw IOException("bad header")
        val algorithm = info.i32(HEADER_START + HEADER_ALG_AT)
        if (algorithm !in AES_128..AES_256) throw UnsupportedOffice()
        val keyBytes = info.i32(HEADER_START + HEADER_KEY_BITS_AT) / BITS_PER_BYTE
        val saltAt = HEADER_START + headerSize + Int.SIZE_BYTES
        val salt = info.copyOfRange(saltAt, saltAt + SALT_LENGTH)
        val encryptedAt = saltAt + SALT_LENGTH
        val hashAt = encryptedAt + VERIFIER_LENGTH + Int.SIZE_BYTES
        val key = standardKey(salt, password, keyBytes)
        val plain = aesEcb(key, info.copyOfRange(encryptedAt, encryptedAt + VERIFIER_LENGTH))
        val expected = aesEcb(key, info.copyOfRange(hashAt, hashAt + HASH_LENGTH)).copyOf(SHA1_LENGTH)
        if (!MessageDigest.getInstance("SHA-1").digest(plain).contentEquals(expected)) return null
        return PackageKey { source, target ->
            copySegments(source, target) { _, segment, length ->
                aesEcb(key, segment.copyOf(length - length % AES_BLOCK))
            }
        }
    }

    private fun standardKey(salt: ByteArray, password: String, keyBytes: Int): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = spunHash(sha1, salt, password, STANDARD_SPIN)
        val final = sha1.digestOf(hash, littleEndian(0))
        val mix = { pad: Int ->
            val block = ByteArray(HMAC_BLOCK) { pad.toByte() }
            for (i in final.indices) block[i] = (block[i].toInt() xor final[i].toInt()).toByte()
            sha1.digest(block)
        }
        return (mix(PAD_INNER) + mix(PAD_OUTER)).copyOf(keyBytes)
    }

    private fun aesEcb(key: ByteArray, value: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        doFinal(value)
    }
}
