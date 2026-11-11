package com.keybord.engine

import com.keybord.engine.base32.Base32Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Base32EngineTest {

    @Test
    fun testRfc4648VectorsEncodeAndDecode() {
        val vectors = listOf(
            "" to "",
            "f" to "MY======",
            "fo" to "MZXQ====",
            "foo" to "MZXW6===",
            "foob" to "MZXW6YQ=",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI======"
        )
        for ((plain, expected) in vectors) {
            assertEquals(expected, Base32Engine.encodeText(plain, pad = true))
            val decoded = Base32Engine.decodeToText(expected).getOrThrow()
            assertEquals(plain, decoded)
            // Unpadded & spaced/lowercase variants must also decode identically
            val messy = expected.trimEnd('=').lowercase().chunked(2).joinToString(" ")
            assertEquals(plain, Base32Engine.decodeToText(messy).getOrThrow())
        }
    }

    @Test
    fun testHexRoundTripAndValidation() {
        val hex = "48656C6C6F" // "Hello"
        val encoded = Base32Engine.encodeHex("0x$hex", pad = true).getOrThrow()
        assertEquals("JBSWY3DP", encoded)
        assertEquals("Hello", Base32Engine.decodeToText(encoded).getOrThrow())
        assertEquals(hex, Base32Engine.decodeToHex(encoded).getOrThrow())

        assertTrue(Base32Engine.decodeToBytes("INVALID0189").isFailure)
        assertTrue(Base32Engine.encodeHex("ABC").isFailure)
    }

    @Test
    fun testRandomGeneratorBitAlignedAndAlphabetValid() {
        val k16 = Base32Engine.generateRandom(charCount = 16, pad = false)
        val k32 = Base32Engine.generateRandom(charCount = 32, pad = false)
        assertEquals(16, k16.length)
        assertEquals(32, k32.length)
        assertTrue(k16.all { it in 'A'..'Z' || it in '2'..'7' })
        assertEquals(10, Base32Engine.decodeToBytes(k16).getOrThrow().size)
        assertEquals(20, Base32Engine.decodeToBytes(k32).getOrThrow().size)

        val another = Base32Engine.generateRandom(charCount = 32, pad = false)
        assertNotEquals(k32, another)
    }

    @Test
    fun testRfc6238Sha1TotpOfficialVector() {
        // RFC 6238 SHA-1 secret = "12345678901234567890" -> Base32 "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val resAt59 = Base32Engine.generateTotp(secret, epochSeconds = 59L, periodSeconds = 30, digits = 6).getOrThrow()
        assertEquals("287082", resAt59.code)
        assertEquals(1, resAt59.remainingSeconds)

        // Common demo secret "JBSWY3DPEHPK3PXP" returns 6-digit padded code cleanly
        val demo = Base32Engine.generateTotp("JBSW Y3DP EHPK 3PXP", epochSeconds = 1_700_000_000L).getOrThrow()
        assertEquals(6, demo.code.length)
        assertTrue(demo.code.all { it.isDigit() })
        assertTrue(demo.remainingSeconds in 1..30)
    }
}
