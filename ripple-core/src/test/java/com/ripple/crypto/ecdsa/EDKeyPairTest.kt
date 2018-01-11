package com.ripple.crypto.ecdsa

import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.STObject
import com.ripple.core.types.known.tx.Transaction
import com.ripple.crypto.Seed
import com.ripple.crypto.ed25519.EDKeyPair
import com.ripple.encodings.common.B16
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EDKeyPairTest {
    @Language("JSON")
    private val fixturesJson = """{
      "tx_json": {
        "Account": "rJZdUusLDtY9NEsGea7ijqhVrXv98rYBYN",
        "Amount": "1000",
        "Destination": "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
        "Fee": "10",
        "Flags": 2147483648,
        "Sequence": 1,
        "SigningPubKey": "EDD3993CDC6647896C455F136648B7750723B011475547AF60691AA3D7438E021D",
        "TransactionType": "Payment"
      },
      "expected_sig": "C3646313B08EED6AF4392261A31B961F10C66CB733DB7F6CD9EAB079857834C8B0334270A2C037E63CDCCC1932E0832882B7B7066ECD2FAEDEB4A83DF8AE6303"
    }"""
    private var edKeyPair: EDKeyPair
    private val fixtures = JSONObject(fixturesJson)
    private val expectedSig = fixtures.getString("expected_sig")
    private val txJson = fixtures.getJSONObject("tx_json")
    private val tx = STObject.fromJSONObject(txJson) as Transaction

    private val message = tx.signingData()

    init {
        val seedBytes = Seed.passPhraseToSeedBytes("niq")
        edKeyPair = EDKeyPair.from128Seed(seedBytes)
    }

    @Test
    fun testAccountIDGeneration() {
        assertEquals("rJZdUusLDtY9NEsGea7ijqhVrXv98rYBYN",
                AccountID.fromKeyPair(edKeyPair).toString())
    }

    @Test
    fun testSigning() {
        val bytes = edKeyPair.signMessage(message)
        assertEquals(fixtures.getString("expected_sig"),
                B16.encode(bytes))
    }

    @Test
    fun testVerifying() {
        assertTrue(edKeyPair.verify(message, B16.decode(expectedSig)))
    }
}