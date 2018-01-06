package com.ripple.kotlin.example

import com.ripple.client.Client
import com.ripple.client.subscriptions.ServerInfo
import com.ripple.client.subscriptions.SubscriptionManager
import com.ripple.client.subscriptions.ledger.LedgerSubscriber
import com.ripple.client.transport.impl.JavaWebSocketTransportImpl
import com.ripple.core.coretypes.Amount
import com.ripple.core.types.known.sle.entries.Offer
import com.ripple.core.types.known.tx.result.AffectedNode
import com.ripple.core.types.known.tx.result.TransactionResult

fun AffectedNode.offerBeforeAndAft() =
        Pair(this.nodeAsPrevious() as Offer, nodeAsFinal() as Offer)

fun AffectedNode.previousOffer() =
        this.isOffer && this.wasPreviousNode()

/**
 * This example subscribes to all transactions and prints executed
 * offers.
 */
object OffersExecuted {
    @JvmStatic
    fun main(args: Array<String>) {
        Client(JavaWebSocketTransportImpl())
                .connect("wss://s2.ripple.com", this::onceConnected)
    }

    private fun onceConnected(c: Client) {
        c.subscriptions.addStream(SubscriptionManager.Stream.transactions)
        c.transactionSubscriptionManager(LedgerSubscriber(c))
        c.onLedgerClosed(this::onLedgerClosed)
                .onValidatedTransaction { tr ->
                    tr.meta.affectedNodes()
                            .filter(AffectedNode::previousOffer)
                            .forEach { an ->
                                printTrade(tr, an)
                            }
                }
    }

    private fun onLedgerClosed(serverInfo: ServerInfo) {
        println("$serverInfo")
        println("Ledger `${serverInfo.ledger_index}` closed " +
               "@ `${serverInfo.date()}` " +
               "with `${serverInfo.txn_count}` transactions.")
    }

    private fun printTrade(tr: TransactionResult,
                           affectedNode: AffectedNode) {
        val (before, after) = affectedNode.offerBeforeAndAft()
        // Executed define non negative amount of Before - After
        val executed = after.executed(before)
        val takerGot = executed.get(Amount.TakerGets)

        // Only print trades that executed
        if (!takerGot.isZero) {
            val takerPaid = executed.get(Amount.TakerPays)
            println("In ${tr.transactionType()} tx: ${tr.hash}, " +
                    "Offer owner ${before.account()}, " +
                    "was paid: ${takerPaid.toTextFull()}, " +
                    "gave: ${takerGot.toTextFull()} ")
        }
    }
}
