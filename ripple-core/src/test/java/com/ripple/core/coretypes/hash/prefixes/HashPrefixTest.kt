package com.ripple.core.coretypes.hash.prefixes

import com.ripple.core.coretypes.uint.UInt32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashPrefixTest {
    private fun charToUint32(chars: CharArray): UInt32 {
        return UInt32.fromBytes(byteArrayOf(chars[0].toByte(),
                chars[1].toByte(),
                chars[2].toByte()) +
                byteArrayOf(0))
    }

    @Test
    fun existingTo() {
        testSame(HashPrefix.transactionID, charArrayOf('T', 'X', 'N'))
        testSame(HashPrefix.txNode, charArrayOf('S', 'N', 'D'))
        testSame(HashPrefix.leafNode, charArrayOf('M', 'L', 'N'))
        testSame(HashPrefix.innerNode, charArrayOf('M', 'I', 'N'))
        testSame(HashPrefix.ledgerMaster, charArrayOf('L', 'W', 'R'))
        testSame(HashPrefix.txSign, charArrayOf('S', 'T', 'X'))
        testSame(HashPrefix.validation, charArrayOf('V', 'A', 'L'))
        testSame(HashPrefix.proposal, charArrayOf('P', 'R', 'P'))
        testSame(HashPrefix.innerNodeV2, charArrayOf('I', 'N', 'R'))
        testSame(HashPrefix.txMultiSign, charArrayOf('S', 'M', 'T'))
        testSame(HashPrefix.manifest, charArrayOf('M', 'A', 'N'))
        testSame(HashPrefix.paymentChannelClaim, charArrayOf('C', 'L', 'M'))
    }

    private fun testSame(prefix: HashPrefix, chars: CharArray) {
        val fromChars = charToUint32(chars)
        assertEquals(String(chars), prefix.chars())
        assertEquals(0, fromChars.compareTo(prefix.uInt32()))
        assertTrue(prefix.bytes() is ByteArray)
    }
}