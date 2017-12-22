package com.ripple.client.subscriptions.ledger;

import com.ripple.client.Client;
import com.ripple.client.enums.Command;
import com.ripple.client.requests.Request;
import com.ripple.client.responses.Response;
import com.ripple.core.types.known.tx.result.TransactionResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.TreeMap;

import static com.ripple.client.pubsub.Publisher.Callback;

public class PendingLedgers {
    private Client client;
    private TreeMap<Long, PendingLedger> ledgers = new TreeMap<Long, PendingLedger>();
    private ClearedLedgersSet clearedLedgers = new ClearedLedgersSet();

    PendingLedgers(Client clientInstance) {
        client = clientInstance;
    }

    public PendingLedger getOrAddLedger(long ledger_index) {
        PendingLedger ledger = ledgers.get(ledger_index);

        if (ledger == null) {
            ledger = constructAndAddLedger(ledger_index);
        }
        return ledger;
    }

    private PendingLedger constructAndAddLedger(long ledger_index) {
        if (clearedLedgers.contains(ledger_index)) throw new AssertionError();

        PendingLedger ledger = new PendingLedger(ledger_index, client);
        ledgers.put(ledger_index, ledger);
        return ledger;
    }

    private void clearLedger(long ledger_index, String from) {
        // We are using this to test our logic wrt, to when it's safe to
        // to clear the `clearedLedgers` set.
        clearedLedgers.clear(ledger_index);
        PendingLedger remove = ledgers.remove(ledger_index);
        if (remove == null) throw new AssertionError();
        remove.setStatus(PendingLedger.Status.cleared);

        if (ledgers.size() == 1) {
            clearedLedgers.clearIfNoGaps();
        }
    }

    public void notifyTransactionResult(TransactionResult tr) {
        long key = tr.ledgerIndex.longValue();
        if (clearedLedgers.contains(key)) {
            System.out.println("warning");
            return;
        }

        PendingLedger ledger = getOrAddLedger(key);
        ledger.notifyTransaction(tr);
        if (ledger.expectedTxns == ledger.clearedTransactions) {
            checkHeader(ledger);
        }
    }

    private void requestLedger(final long ledger_index, final boolean onlyHeader, final Callback<Response> cb) {
        client.requestLedger(ledger_index, new Request.Manager<JSONObject>() {
            @Override
            public void beforeRequest(Request r) {
                if (!onlyHeader) {
                    r.json("transactions", true);
                    r.json("expand", true);
                }
            }

            @Override
            public boolean retryOnUnsuccessful(Response r) {
                return true;
            }

            @Override
            public void cb(Response response, JSONObject jsonObject) throws JSONException {
                cb.called(response);
            }
        });
    }

    void checkHeader(final PendingLedger ledger) {
        final long ledger_index = ledger.ledger_index;
        ledger.setStatus(PendingLedger.Status.checkingHeader);

        requestLedger(ledger_index, true, response -> {
            JSONObject ledgerJSON = response.result.getJSONObject("ledger");
            final String transaction_hash = ledgerJSON.getString("transaction_hash");
            boolean correctHash = ledger.transactionHashEquals(transaction_hash);
            if (correctHash) {
                clearLedger(ledger_index, "checkHeader");
            } else {
                LedgerSubscriber.log("Missing transactions, need to fillInLedger: " + ledger);
                fillInLedger(ledger);
            }
        });
    }

    private void fillInLedger(final PendingLedger ledger) {
        final long ledger_index = ledger.ledger_index;
        ledger.setStatus(PendingLedger.Status.fillingIn);

        requestLedger(ledger_index, false, response -> {
            JSONObject ledgerJSON = response.result.getJSONObject("ledger");
            JSONArray transactions = ledgerJSON.getJSONArray("transactions");

            for (int i = 0; i < transactions.length(); i++) {
                JSONObject json = transactions.getJSONObject(i);
                // This is kind of nasty
                json.put("ledger_index", ledger_index);
                json.put("validated",    true);
                TransactionResult tr = TransactionResult.fromJSON(json);
                ledger.notifyTransaction(tr);
            }

            final String transaction_hash = ledgerJSON.getString("transaction_hash");
            boolean correctHash = ledger.transactionHashEquals(transaction_hash);
            if (!correctHash) throw new IllegalStateException("We don't handle invalid transactions yet");
            clearLedger(ledger_index, "fillInLedger");
        });
    }

    private boolean alreadyPending(long j) {
        return ledgers.containsKey(j);
    }

    /**
     * We can't just look for gaps in the ledgers keys as they are popped
     * off randomly as the ledgers clear.
     */
    void trackMissingLedgersInClearedLedgerHistory() {
        for (Long j : clearedLedgers.gaps()) {
            if (!alreadyPending(j)) {
                constructAndAddLedger(j);
            }
        }
    }

    Set<Long> pendingLedgerIndexes() {
        return ledgers.keySet();
    }

    public void logPendingLedgers() {
        for (PendingLedger pendingLedger : ledgers.values()) {
            System.out.println(pendingLedger);
        }
    }
}
