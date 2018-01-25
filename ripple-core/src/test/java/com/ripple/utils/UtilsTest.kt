package com.ripple.utils

import com.ripple.encodings.common.B16
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MemberVisibilityCanPrivate")
class UtilsTest {

    val longNonZero =
               "FFE8A3E6508A7BE5FDE2097B2CF46638A3C34208B0AB09967022B4CE3A3150BC5A"
    val ok =     "E8A3E6508A7BE5FDE2097B2CF46638A3C34208B0AB09967022B4CE3A3150BC5A"
    val long = "00E8A3E6508A7BE5FDE2097B2CF46638A3C34208B0AB09967022B4CE3A3150BC5A"
    val short =    "A3E6508A7BE5FDE2097B2CF46638A3C34208B0AB09967022B4CE3A3150BC5A"
    val veryLong =
            "0000000000000000" +
                 "E8A3E6508A7BE5FDE2097B2CF46638A3C34208B0AB09967022B4CE3A3150BC5A"

    @Test
    fun leadingZeroesTrimmedOrPaddedToRightSize() {
        createTest(ok)
    }

    @Test
    fun leadingZeroesTrimmedOrPaddedToLong() {
        createTest(long)
    }
    @Test
    fun leadingZeroesTrimmedOrPaddedToVeryLong() {
        createTest(veryLong)
    }

    @Test(expected = IllegalArgumentException::class)
    fun leadingZeroesTrimmedOrPaddedToLongNonZero() {
        createTest(longNonZero)
    }

    @Test
    fun leadingZeroesTrimmedOrPaddedToShort() {
        createTest(short, expected = "00" + ok.substring(2))
    }

    private fun createTest(input: String, expected: String?=ok) {
        val arr = B16.decode(input)
        val padded = Utils.leadingZeroesTrimmedOrPaddedTo(32, arr)
        assertEquals(32, padded.size)
        if (expected != null) {
            assertEquals(expected, B16.encode(padded))
        }
    }
}