package com.ripple.client.subscriptions.ledger;

import com.ripple.client.Client;
import com.ripple.client.subscriptions.ServerInfo;
import com.ripple.client.subscriptions.SubscriptionManager;
import com.ripple.client.subscriptions.TransactionSubscriptionManager;
import com.ripple.core.types.known.tx.result.TransactionResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes care of dropouts when subscribing to a ledger.
 */
public class LedgerSubscriber implements TransactionSubscriptionManager {
    public static final Logger logger = Logger.getLogger(LedgerSubscriber.class.getName());
    public static void log(String fmt, Object... args) {
        logger.log(Level.FINE, fmt, args);
    }

    Client client;
    PendingLedgers ledgers;

    public void notifyTransactionResult(TransactionResult tr) {
        ledgers.notifyTransactionResult(tr);
    }

    public LedgerSubscriber(final Client client) {
        this.client = client;
        ledgers = new PendingLedgers(client);
        subscribeToTransactions();
        bindLedgerCloseHandler();
    }

    private void subscribeToTransactions() {
        client.subscriptions.addStream(SubscriptionManager.Stream.transactions);
    }

    private void bindLedgerCloseHandler() {
        client.onLedgerClosed(serverInfo -> {
            final long ledger_index = serverInfo.ledger_index;
            // We can see how many transactions are pending
            ledgers.logPendingLedgers();

            PendingLedger ledger = ledgers.getOrAddLedger(ledger_index);
            ledger.expectedTxns = serverInfo.txn_count;

            ledgers.trackMissingLedgersInClearedLedgerHistory();

            for (Long stalledOrGapLedger : ledgers.pendingLedgerIndexes()) {
                PendingLedger stalled = ledgers.getOrAddLedger(stalledOrGapLedger);
                if (stalled.status == PendingLedger.Status.pending
                        // give the transaction stream a chance
                        && stalled.ledger_index != ledger_index) {
                    ledgers.checkHeader(stalled);
                    break;
                }
            }
        });
    }
}
