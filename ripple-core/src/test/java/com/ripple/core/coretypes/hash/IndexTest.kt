package com.ripple.core.coretypes.hash

import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.uint.UInt32
import org.junit.Assert.assertEquals
import org.junit.Test

val address = AccountID::fromAddress
val hash256 = Hash256::fromHex

class IndexTest {
    @Test
    fun paymentChannel() {
        val account = address("rDx69ebzbowuqztksVDmZXjizTd12BVr4x")
        val sequence = UInt32(84)
        val expected = hash256("61E8E8ED53FA2CEBE192B23897071E9A75217BF5A410E9CB5B45AAB7AECA567A")
        val actual = Index.escrow(account, sequence)
        assertEquals(expected, actual)
    }

    @Test
    fun escrow() {
        val account = address("rDx69ebzbowuqztksVDmZXjizTd12BVr4x")
        val destination = address("rLFtVprxUEfsH54eCWKsZrEQzMDsx1wqso")
        val sequence = UInt32(82)
        val expected = hash256("E35708503B3C3143FB522D749AAFCC296E8060F0FB371A9A56FAE0B1ED127366")
        val actual = Index.paymentChannel(account, destination, sequence)
        assertEquals(expected, actual)
    }
}