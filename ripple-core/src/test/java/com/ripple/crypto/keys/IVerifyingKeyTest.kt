package com.ripple.crypto.keys

import com.ripple.crypto.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IVerifyingKeyTest {
    @Test
    fun fromEd25519Test() {
        val seed = Seed.fromPassPhrase("niq")
                              .setEd25519()
        helper(seed, { key ->
            assertEquals(0xED.toByte(), key.canonicalPubBytes()[0])
        })
    }
    @Test
    fun fromK256Test() {
        val seed = Seed.fromPassPhrase("niq")
        helper(seed, { key ->
            assertEquals(0x2.toByte(), key.canonicalPubBytes()[0])
        })
    }

    private fun helper(seed: Seed, block: ((verifier: IVerifyingKey) -> Unit)? = null) {
        val pair = seed.keyPair()
        val message = byteArrayOf(0xb, 0xe, 0xe, 0xf)
        val signature = pair.signMessage(message)
        val verifier = IVerifyingKey.from(pair.canonicalPubBytes())
        assertTrue(verifier.verify(message, signature))
        if (block != null) {
            block(verifier)
        }
    }
}