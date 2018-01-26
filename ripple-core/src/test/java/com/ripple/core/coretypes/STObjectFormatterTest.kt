package com.ripple.core.coretypes

import com.ripple.core.fields.Field
import com.ripple.core.serialized.enums.LedgerEntryType
import com.ripple.core.serialized.enums.TransactionType
import com.ripple.core.types.known.sle.LedgerEntry
import com.ripple.core.types.known.tx.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class STObjectFormatterTest {
    @Test
    fun testLedgerEntryTypes() {
        LedgerEntryType.values().forEach { let ->
            val so = STObject()
            so.put(Field.LedgerEntryType, let)
            val leSo = STObjectFormatter.format(so)
            assertTrue(leSo is LedgerEntry)
            val le = leSo as LedgerEntry
            assertEquals(let, le.ledgerEntryType())
        }
    }

    @Test
    fun testTransactionTypes() {
        TransactionType.values().forEach { tt ->
            val so = STObject()
            so.put(Field.TransactionType, tt)
            val txSo = STObjectFormatter.format(so)
            assertTrue(txSo is Transaction)
            val tx = txSo as Transaction
            assertEquals(tt, tx.transactionType())
        }
    }
}