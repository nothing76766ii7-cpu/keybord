package com.keybord.engine.base32

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin RFC 4648 Base32 Codec, Cryptographic Token Generator,
 * and RFC 6238 TOTP (HMAC-SHA1) 6-digit code generator.
 * Zero network dependencies; completely offline.
 */
object Base32Engine {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { idx, ch ->
            this[ch.code] = idx
            this[ch.lowercaseChar().code] = idx
        }
    }

    data class TotpResult(
        val code: String,
        val remainingSeconds: Int,
        val periodSeconds: Int,
        val normalizedSecret: String
    )

    /**
     * Generates a cryptographically random Base32 string backed by bit-aligned random bytes
     * (or exact Base32 character count when custom lengths are chosen).
     */
    fun generateRandom(
        charCount: Int = 16,
        pad: Boolean = false,
        lowercase: Boolean = false,
        grouped: Boolean = false,
        random: SecureRandom = SecureRandom()
    ): String {
        val count = charCount.coerceIn(8, 64)
        val raw = when (count) {
            16 -> encodeBytes(ByteArray(10).also { random.nextBytes(it) }, pad = pad, lowercase = lowercase)
            26 -> encodeBytes(ByteArray(16).also { random.nextBytes(it) }, pad = pad, lowercase = lowercase)
            32 -> encodeBytes(ByteArray(20).also { random.nextBytes(it) }, pad = pad, lowercase = lowercase)
            else -> {
                val sb = StringBuilder(count)
                for (i in 0 until count) {
                    val ch = ALPHABET[random.nextInt(32)]
                    sb.append(if (lowercase) ch.lowercaseChar() else ch)
                }
                if (pad) {
                    while (sb.length % 8 != 0) sb.append('=')
                }
                sb.toString()
            }
        }
        return if (grouped) groupFourChars(raw) else raw
    }

    fun groupFourChars(input: String): String {
        val clean = input.replace(" ", "")
        return clean.chunked(4).joinToString(" ")
    }

    fun encodeText(text: String, pad: Boolean = true, lowercase: Boolean = false): String {
        return encodeBytes(text.toByteArray(StandardCharsets.UTF_8), pad = pad, lowercase = lowercase)
    }

    fun encodeHex(hexInput: String, pad: Boolean = true, lowercase: Boolean = false): Result<String> {
        val cleaned = hexInput
            .trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .replace(Regex("[\\s:-]"), "")
        if (cleaned.isEmpty()) return Result.success("")
        if (cleaned.length % 2 != 0) {
            return Result.failure(IllegalArgumentException("Hex input must have an even number of characters"))
        }
        val bytes = ByteArray(cleaned.length / 2)
        for (i in bytes.indices) {
            val hi = cleaned[i * 2].digitToIntOrNull(16)
            val lo = cleaned[i * 2 + 1].digitToIntOrNull(16)
            if (hi == null || lo == null) {
                return Result.failure(IllegalArgumentException("Invalid hex character at index ${i * 2}"))
            }
            bytes[i] = ((hi shl 4) or lo).toByte()
        }
        return Result.success(encodeBytes(bytes, pad = pad, lowercase = lowercase))
    }

    /**
     * RFC 4648 Base32 encoding of raw bytes.
     */
    fun encodeBytes(data: ByteArray, pad: Boolean = true, lowercase: Boolean = false): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder(((data.size + 4) / 5) * 8)
        var buffer = 0
        var bitsLeft = 0

        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1F
                val ch = ALPHABET[idx]
                out.append(if (lowercase) ch.lowercaseChar() else ch)
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            val ch = ALPHABET[idx]
            out.append(if (lowercase) ch.lowercaseChar() else ch)
        }
        if (pad) {
            while (out.length % 8 != 0) {
                out.append('=')
            }
        }
        return out.toString()
    }

    /**
     * RFC 4648 Base32 decoding ignoring whitespace, dashes, and trailing '=' padding.
     */
    fun decodeToBytes(input: String): Result<ByteArray> {
        val cleaned = input.replace(Regex("[\\s-]"), "").trimEnd('=')
        if (cleaned.isEmpty()) return Result.success(ByteArray(0))

        val out = ArrayList<Byte>((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0

        for (i in cleaned.indices) {
            val code = cleaned[i].code
            val value = if (code < DECODE_TABLE.size) DECODE_TABLE[code] else -1
            if (value < 0) {
                return Result.failure(IllegalArgumentException("Invalid Base32 char '${cleaned[i]}' (valid: A-Z, 2-7)"))
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return Result.success(out.toByteArray())
    }

    fun decodeToText(input: String): Result<String> {
        return decodeToBytes(input).map { bytes -> String(bytes, StandardCharsets.UTF_8) }
    }

    fun decodeToHex(input: String): Result<String> {
        return decodeToBytes(input).map { bytes ->
            bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }
    }

    /**
     * Computes RFC 6238 HMAC-SHA1 TOTP code for the given Base32 secret.
     */
    fun generateTotp(
        base32Secret: String,
        epochSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30,
        digits: Int = 6
    ): Result<TotpResult> {
        val normalized = base32Secret.replace(Regex("[\\s-]"), "").trimEnd('=').uppercase()
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a Base32 secret key (e.g. JBSWY3DPEHPK3PXP)"))
        }
        val keyBytesResult = decodeToBytes(normalized)
        val keyBytes = keyBytesResult.getOrElse { return Result.failure(it) }
        if (keyBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Base32 secret decoded to 0 bytes"))
        }

        val stepSeconds = periodSeconds.coerceAtLeast(1)
        val counter = epochSeconds / stepSeconds
        val msg = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(msg)

        val offset = hash[hash.lastIndex].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        var mod = 1
        repeat(digits.coerceIn(6, 8)) { mod *= 10 }
        val otp = (binary % mod).toString().padStart(digits.coerceIn(6, 8), '0')
        val remaining = (stepSeconds - (epochSeconds % stepSeconds)).toInt()

        return Result.success(
            TotpResult(
                code = otp,
                remainingSeconds = remaining,
                periodSeconds = stepSeconds,
                normalizedSecret = normalized
            )
        )
    }
}
