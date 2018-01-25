package com.ripple.utils

import com.ripple.core.binary.STReader
import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.fields.Field
import com.ripple.core.types.known.sle.entries.DirectoryNode
import com.ripple.core.types.known.sle.entries.OfferDirectory
import com.ripple.core.types.shamap.AccountState
import com.ripple.core.types.shamap.AccountStateBuilder
import com.ripple.core.types.shamap.ShaMapDiff
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.*

class HistoryLoaderTest {
    @Test
    fun testLoadTransactions() {

        val pack = System.getenv("HISTORY_PACK") ?:
                "history-11375000-11380000.bin"
        if (!File(pack).exists()) {
            return
        }

        val reader = STReader.fromFile(pack)
        val loader = HistoryLoader(reader)
        var stateBuilder: AccountStateBuilder? = null

        loader.Parse { header, state, transactions ->
            if (stateBuilder == null) {
                stateBuilder = AccountStateBuilder(state.copy(),
                                        header.sequence.toLong())
                stateBuilder!!.sortedDirectories = false
            } else {
                val builder = stateBuilder!!
                // Add them in order, TransactionResult's are sortable
                val set = transactions.toTreeSet()
                val unsortedOwnerDirectories = TreeSet<Hash256>()

                set.forEach {
                    builder.onTransaction(it)
                    unsortedOwnerDirectories.addAll(builder.directoriesWithIndexesOutOfOrder())
                }

                builder.onLedgerClose(header.sequence.toLong(),
                        header.stateHash,
                        header.previousLedger)

                val diff = ShaMapDiff(state, builder.state()).find()

                // There should not be any missing or added nodes
                assertEquals(0, diff.deleted.size)
                assertEquals(0, diff.added.size)

                // All the modified nodes should be only Owner nodes
                val modifiedOnlyOwnerDirectories =
                        diff.modified.all {
                            val le = state.getLE(it)
                            val b = le is DirectoryNode && le !is OfferDirectory
                            if (!b) {
                                println("issue: " + le.prettyJSON() +
                                        "\nwas: " +
                                        builder
                                                .state()
                                                .getLE(le.index()).prettyJSON())
                            }
                            b
                        }
                assert(modifiedOnlyOwnerDirectories)

                // Only the ordering should have changed
                val onlyOrdering = diff.modified.all {
                    diffOnlyIndexes(state, builder.state(), it)
                }
                assert(onlyOrdering)

                val unknown = diff.modified.filter {
                    !builder.ledgerModifiedEntries.contains(it)
                }

                // There should be no modified entries that weren't tracked
                // by the AccountStateBuilder
                assert(unknown.isEmpty())

                // We don't need to updated deleted entries
                unsortedOwnerDirectories.removeAll(builder.ledgerDeletedEntries)
                // With the current ledger amendments these directories should
                // be sorted, and not need to be retrieved
                unsortedOwnerDirectories.forEach( {
                    // Just get from the history pack, though normally this
                    // would be a Client operation, if it all (new SortedIndex)
                    // amendments ...
                    builder.state().updateLE(state.getLE(it))
                })

                // How many directories could we not infer?
                // println("owner directories: ${moreThanTwice.size}")

                // Reset the modified states
                builder.resetModified()
//                // Set a checkpoint, saving the previous ledger
//                builder.setStateCheckPoint()

                // Make sure the state tree hash() matches the target hash()
                assert(!builder.bad())
            }

            return@Parse true

            /*var totalOffers = 0
            var seenOffers = 0

            transactions.walkTransactions { totalTransactions++ }
            lookup[header.sequence] = Triple(header, state, transactions)
            state.walkEntries { entry ->
                if (entry is Offer) {
                    seenOffers++
                }
                if (entry is OfferDirectory) {
                    if (entry.isRootIndex) {
                        state.directoryIterator(entry)
                                .map { state.getLE(it) as Offer }
                                .forEach { offer ->
                                    totalOffers++

                                    // Using a shared unmodified node
                                    if (offer.has(Amount.taker_gets_funded)) {
                                        return@forEach
                                    }

                                    val fundingSource =
                                            offer.fundingSource() ?: return@forEach

                                    val funds = state.getLE(fundingSource)
                                    if (funds is AccountRoot) {
                                        val xrp = funds.balance().subtract(reserve)
                                        offer.put(Amount.taker_gets_funded, xrp)
                                    } else if (funds is RippleState) {
                                        val balance = funds.issuedTo(offer.account())
                                        offer.put(Amount.taker_gets_funded, balance)
                                    }
                                }
                    }
                }
            }
            assertEquals(totalOffers, seenOffers)
            true*/
        }
        println()
    }

    private fun diffOnlyIndexes(state: AccountState, state1: AccountState, index: Hash256): Boolean
    {
        val one = state.getLE(index)
        val two = state1.getLE(index)
        if (one.size() != two.size()) {
            return false
        }
        return one.none { one[it].toHex() != two[it].toHex() && it != Field.Indexes }
            && (getSortedIndexes(state, index) == getSortedIndexes(state1, index))
    }

    private fun getSortedIndexes(state: AccountState, it: Hash256?) =
            (state.getLE(it) as DirectoryNode).indexes().sorted()

}