package com.ripple.java8.example;

import com.ripple.client.Client;
import com.ripple.client.subscriptions.ServerInfo;
import com.ripple.client.subscriptions.ledger.LedgerSubscriber;
import com.ripple.client.transport.impl.JavaWebSocketTransportImpl;
import com.ripple.config.Config;
import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.STObject;
import com.ripple.core.types.known.sle.entries.Offer;
import com.ripple.core.types.known.tx.result.TransactionResult;
import com.ripple.java8.utils.ProcessHelper;

import static com.ripple.java8.utils.Print.print;

/**
 * This example subscribes to all transactions and prints executed
 * offers.
 */
public class OffersExecuted {
    static {
        Config.initBouncy();
    }

    public static void main(String[] args) {
        print("pid={0}", ProcessHelper.getPID());
        new Client(new JavaWebSocketTransportImpl())
                .connect(/*"ws://localhost:6006"*/
                        "wss://s2.ripple.com",
                        OffersExecuted::onceConnected);
    }

    private static void onceConnected(Client c) {
        c.transactionSubscriptionManager(new LedgerSubscriber(c));
        c.onLedgerClosed(OffersExecuted::onLedgerClosed)
                .onValidatedTransaction((tr) -> tr.meta.affectedNodes().forEach((an) -> {
                    if (an.isOffer() && an.wasPreviousNode()) {
                        printTrade(tr, (Offer) an.nodeAsPrevious(),
                                (Offer) an.nodeAsFinal());
                    }
                }));
    }

    private static void onLedgerClosed(ServerInfo serverInfo) {
        print("Ledger {0} closed @ {1} with {2} transactions. Server: {3}",
                serverInfo.ledger_index, serverInfo.date(),
                serverInfo.txn_count, serverInfo);
    }

    private static void printTrade(TransactionResult tr,
                                   Offer before,
                                   Offer after) {
        // Executed define non negative amount of Before - After
        STObject executed = after.executed(before);
        Amount takerGot = executed.get(Amount.TakerGets);

        // Only print trades that executed
        if (!takerGot.isZero()) {
            Amount takerPaid = executed.get(Amount.TakerPays);
            print("In {0} tx: {1}, Offer owner {2}, was paid: {3}, gave: {4} ",
                    tr.transactionType(), tr.hash, before.account(),
                    takerPaid.toTextFull(), takerGot.toTextFull());
        }
    }
}
