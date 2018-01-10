package com.ripple.client.transactions;

import com.ripple.client.Client;
import com.ripple.client.enums.Command;
import com.ripple.client.pubsub.CallbackContext;
import com.ripple.client.pubsub.Publisher;
import com.ripple.client.requests.Request;
import com.ripple.client.responses.Response;
import com.ripple.client.subscriptions.TrackedAccountRoot;
import com.ripple.core.coretypes.AccountID;
import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.core.coretypes.uint.UInt32;
import com.ripple.core.serialized.enums.EngineResult;
import com.ripple.core.types.known.tx.Transaction;
import com.ripple.core.types.known.tx.result.TransactionResult;
import com.ripple.core.types.known.tx.txns.AccountSet;
import com.ripple.crypto.keys.IKeyPair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class takes care of resubmitting transactions in a manner resilient to
 * network failures. It does NOT handle the case where the process exits
 * prematurely as it only persists the hashes, submission and last valid ledger
 * sequence in memory, rather than to disk, where recovery can be made.
 *
 * This was written long ago when the author had relatively little experience
 * with ripple, and was never used internally at ripple for *anything*.
 *
 * This class needs:
 *  - Documentation of the existing logic
 *  - Refactoring for better testability without a [live/test] net
 *  - RangeSet and logic for handling ledger history gaps
 *      - ensure that each and every ledger index has been checked
 *  - Handling new fee escalation logic
 *     - Transactions with higher fees are prioritized
 *  - Handling new transaction queue result codes
 *     - Upper limits on the number of transactions submitted per txn per acct
 *  - Adding hooks for submission persistence
 *  - Testing on the test net
 *
 */
public class TransactionManager extends Publisher<TransactionManager.events> {
    public interface events<T> extends Publisher.Callback<T> {}
    // This event is emitted with the Sequence of the AccountRoot
    private interface OnValidatedSequence extends events<UInt32> {}

    private Client client;
    // TODO: Using a shared mutable cache ... fix ...
    private TrackedAccountRoot accountRoot;
    private AccountID accountID;
    private IKeyPair keyPair;
    private AccountTxPager txnPager;

    private static final int RESUBMIT_PENDING_AFTER_N_LEDGERS = 5;

    /**
     * These are transactions that we have either put on the wire, or we have
     * received a successful provisional result for.
     */
    private ArrayList<ManagedTxn> pending = new ArrayList<>();

    /**
     * These transactions *may actually still clear*, but had a provisional
     * failure. As a rule we keep a look out for ALL transactions that we have
     * submitted to the network.
     *
     * We wait until we have seen the `LastLedgerSequence` exceeded, along with
     * some safety margin, and then emit the last submissions result as an error
     * or failure as a ManagedTxn event.
     *
     * {@link ManagedTxn.OnSubmitError}
     * {@link ManagedTxn.OnSubmitFailure}
     *
     * It's possible and more convenient to bind to both in
     * one call: {@link ManagedTxn#onError}
     */
    private ArrayList<ManagedTxn> failedTransactions = new ArrayList<>();

    public TransactionManager(Client client,
                              final TrackedAccountRoot accountRoot,
                              AccountID accountID,
                              IKeyPair keyPair) {
        this.client = client;
        this.accountRoot = accountRoot;
        this.accountID = accountID;
        this.keyPair = keyPair;

        // We'd be subscribed yeah ;)
        // TODO: audit this for ledgerClosed -> transaction -> transaction ...
        // stream of events from the server.
        this.client.onLedgerClosed(serverInfo -> {
            checkAccountTransactions(serverInfo.ledger_index);
            clearFailed(serverInfo.ledger_index);

            if (!canSubmit() || getPending().isEmpty()) {
                return;
            }
            ArrayList<ManagedTxn> sorted = pendingSequenceSorted();

            ManagedTxn first = sorted.get(0);
            Submission previous = first.lastSubmission();

            if (previous != null) {
                long ledgersClosed = serverInfo.ledger_index - previous.ledgerSequence;
                if (ledgersClosed > RESUBMIT_PENDING_AFTER_N_LEDGERS) {
                    resubmitWithSameSequence(first);
                }
            }
        });
    }

    private void clearFailed(long ledger_index) {
        // TODO: make sure each and every ledger has been checked
        int safety = 1;

        for (ManagedTxn failed : failedTransactions) {
            int expired = 0;
            for (Submission submission : failed.submissions) {
                if (ledger_index - safety > submission.lastLedgerSequence.longValue()) {
                    expired++;
                }
            }
            if (expired == failed.submissions.size()) {
                // The last response our submissions
                Response response = failed.lastSubmission().request.response;

                if (response != null) {
                    if (response.rpcerr != null) {
                        failed.emit(ManagedTxn.OnSubmitError.class, response);
                    } else {
                        failed.emit(ManagedTxn.OnSubmitFailure.class, response);
                    }
                }
            }

        }
    }

    private Set<Long> seenValidatedSequences = new TreeSet<>();
    public long sequence = 0;

    private UInt32 locallyPreemptedSubmissionSequence() {
        if (!accountRoot.primed()) {
            throw new IllegalStateException("The AccountRoot hasn't been populated from the server");
        }
        long server = accountRoot.Sequence.longValue();
        if (server > sequence) {
            sequence = server;
        }
        return new UInt32(sequence++);
    }
    private boolean txnNotFinalizedAndSeenValidatedSequence(ManagedTxn txn) {
        return !txn.isFinalized() &&
               seenValidatedSequences.contains(txn.sequence().longValue());
    }

    public void queue(final ManagedTxn tx) {
        if (accountRoot.primed()) {
            queue(tx, locallyPreemptedSubmissionSequence());
        } else {
            accountRoot.once(TrackedAccountRoot.OnUpdate.class,
                    accountRoot -> queue(tx, locallyPreemptedSubmissionSequence()));
        }
    }

    // TODO: data structure that keeps txns in sequence sorted order
    public ArrayList<ManagedTxn> getPending() {
        return pending;
    }

    public ArrayList<ManagedTxn> pendingSequenceSorted() {
        ArrayList<ManagedTxn> queued = new ArrayList<>(getPending());
        queued.sort(Comparator.comparing(ManagedTxn::sequence));
        return queued;
    }

    public int txnsPending() {
        return getPending().size();
    }

    // TODO, maybe this is an instance configurable strategy parameter
    private static long LEDGERS_BETWEEN_ACCOUNT_TX = 15;
    private static long ACCOUNT_TX_TIMEOUT = 5;

    private long lastTxnRequesterUpdate = 0;
    private long lastLedgerCheckedAccountTxns = 0;
    private AccountTxPager.OnPage onTxnsPage = new AccountTxPager.OnPage() {
        @Override
        public void onPage(AccountTxPager.Page page) {
            lastTxnRequesterUpdate = client.serverInfo.ledger_index;

            // TODO: account for ledger gaps ...
            if (page.hasNext()) {
                page.requestNext();
            } else {
                lastLedgerCheckedAccountTxns = Math.max(lastLedgerCheckedAccountTxns, page.ledgerMax());
                txnPager = null;
            }

            for (TransactionResult tr : page.transactionResults()) {
                notifyTransactionResult(tr);
            }
        }
    };

    private void checkAccountTransactions(long currentLedgerIndex) {
        if (pending.size() == 0 && failedTransactions.size() == 0) {
            // TODO: this currently grows unbounded. Can use a RangeSet here
            // seenValidatedSequences.clear();
            lastLedgerCheckedAccountTxns = 0;
            return;
        }

        long ledgersPassed = currentLedgerIndex - lastLedgerCheckedAccountTxns;

        if ((lastLedgerCheckedAccountTxns == 0 || ledgersPassed >= LEDGERS_BETWEEN_ACCOUNT_TX)) {
            if (lastLedgerCheckedAccountTxns == 0) {
                lastLedgerCheckedAccountTxns = currentLedgerIndex;
                for (ManagedTxn txn : pending) {
                    for (Submission submission : txn.submissions) {
                        lastLedgerCheckedAccountTxns = Math.min(lastLedgerCheckedAccountTxns, submission.ledgerSequence);
                    }
                }
                for (ManagedTxn txn : failedTransactions) {
                    for (Submission submission : txn.submissions) {
                        lastLedgerCheckedAccountTxns = Math.min(lastLedgerCheckedAccountTxns, submission.ledgerSequence);
                    }
                }
                return; // and wait for next ledger close
            }
            if (txnPager != null) {
                if ((currentLedgerIndex - lastTxnRequesterUpdate) >= ACCOUNT_TX_TIMEOUT) {
                    txnPager.abort(); // no more OnPage
                    txnPager = null; // and wait for next ledger close
                }
                // else keep waiting ;)
            } else {
                lastTxnRequesterUpdate = currentLedgerIndex;
                txnPager = new AccountTxPager(client, accountID, onTxnsPage,
                                                        /* for good measure */
                                                        lastLedgerCheckedAccountTxns - 5);

                // Very important VVVVV
                txnPager.forward(true);
                txnPager.request();
            }
        }
    }

    private void queue(final ManagedTxn txn, final UInt32 sequence) {
        getPending().add(txn);
        makeSubmitRequest(txn, sequence);
    }

    private boolean canSubmit() {
        return client.connected  &&
               client.serverInfo.primed() &&
                // ledger close could have given us
               client.serverInfo.fee_base != 0 &&
               client.serverInfo.load_factor < (768 * 1000 ) &&
               accountRoot.primed();
    }

    private void makeSubmitRequest(final ManagedTxn txn, final UInt32 sequence) {
        if (canSubmit()) {
            doSubmitRequest(txn, sequence);
        }
        else {
            // If we have submitted again, before this gets to execute
            // we should just bail out early, and not submit again.
            final int n = txn.submissions.size();
            client.on(Client.OnStateChange.class, new CallbackContext() {
                @Override
                public boolean shouldExecute() {
                    return canSubmit() && !shouldRemove();
                }

                @Override
                public boolean shouldRemove() {
                    // The next state change should cause this to remove
                    return txn.isFinalized() || n != txn.submissions.size();
                }
            }, client -> doSubmitRequest(txn, sequence));
        }
    }

    private Request doSubmitRequest(final ManagedTxn txn, UInt32 sequence) {
        // Compute the fee for the current load_factor
        // TODO: handle new fee escalation rules
        Amount fee = client.serverInfo.transactionFee(txn.txn);
        // Inside prepare we check if Fee and Sequence are the same, and if so
        // we don't recreate tx_blob, or resign ;)


        long currentLedgerIndex = client.serverInfo.ledger_index;
        // TODO: make 8 ledgers timeout configurable
        UInt32 lastLedgerSequence = new UInt32(currentLedgerIndex + 8);
        Submission submission = txn.lastSubmission();
        if (submission != null) {
            if (currentLedgerIndex - submission.lastLedgerSequence.longValue() < 8) {
                lastLedgerSequence = submission.lastLedgerSequence;
            }
        }

        txn.prepare(keyPair, fee, sequence, lastLedgerSequence);

        final Request req = client.newRequest(Command.submit);
        // tx_blob is a hex string, right o' the bat
        req.json("tx_blob", txn.tx_blob);

        req.onceSuccess(response -> handleSubmitSuccess(txn, response));
        req.onceError(response -> handleSubmitError(txn, response));

        // Keep track of the submission, including the hash submitted
        // to the network, and the ledger_index at that point in time.
        txn.trackSubmitRequest(req, client.serverInfo.ledger_index);
        req.request();
        return req;
    }

    private void handleSubmitError(final ManagedTxn txn, Response res) {
        if (txn.finalizedOrResponseIsToPriorSubmission(res)) {
            return;
        }
        switch (res.rpcerr) {
            case noNetwork:
                client.schedule(500, () -> resubmitWithSameSequence(txn));
                break;
            default:
                // TODO, what other cases should this retry?
                awaitLastLedgerSequenceExpiry(txn);
                break;
        }
    }

    /**
     * We handle various transaction engine results specifically
     * and then by class of result.
     */
    private void handleSubmitSuccess(final ManagedTxn txn, final Response res) {
        if (txn.finalizedOrResponseIsToPriorSubmission(res)) {
            return;
        }
        EngineResult ter = res.engineResult();
        final UInt32 submitSequence = res.getSubmitSequence();
        switch (ter) {
            case tesSUCCESS:
                txn.emit(ManagedTxn.OnSubmitSuccess.class, res);
                return;
            case tefPAST_SEQ:
                // TODO: make this configurable
                resubmitWithNewSequence(txn);
                break;
            case tefMAX_LEDGER:
                resubmit(txn, submitSequence);
                break;
            case terPRE_SEQ:
                on(OnValidatedSequence.class, new OnValidatedSequence() {
                    @Override
                    public void called(UInt32 sequence) {
                        if (txn.finalizedOrResponseIsToPriorSubmission(res)) {
                            removeListener(OnValidatedSequence.class, this);
                        } else {
                            if (sequence.equals(submitSequence)) {
                                // resubmit:
                                resubmit(txn, submitSequence);
                                removeListener(OnValidatedSequence.class, this);
                            }
                        }
                    }
                });
                break;
            case telINSUF_FEE_P:
                resubmit(txn, submitSequence);
                break;
            case tefALREADY:
                // We only get this if we are submitting with exact same transactionID
                // Do nothing, the transaction has already been submitted
                break;
            case terQUEUED:
                // terQUEUED == `Held until escalated fee drops.`
                // It must have been well formed to get this far and not `tef`
                // or `tel` so it should become `tec` or `tes` soon.
                // It will also be resubmitted with the same sequence once 5
                // ledgers have passed if we leave it as `pending`
                break;

            case telCAN_NOT_QUEUE:
            case telCAN_NOT_QUEUE_FEE:
            case telCAN_NOT_QUEUE_FULL:
                // Just wait the 5 or so ledgers and let it resubmit. We keep
                // an eye out for *ALL* transactions submitted to the network
                // so this *may* clear before then in the case of crazy queue
                // logic.
                break;
            case telCAN_NOT_QUEUE_BALANCE:
            case telCAN_NOT_QUEUE_BLOCKS:
            case telCAN_NOT_QUEUE_BLOCKED:
                // TODO: not sure what to do here. Just resubmit with same
                // sequence. See `RESUBMIT_PENDING_AFTER_N_LEDGERS`
                break;

            default:
                // For safety we *always* wait for LastLedgerSequence to timeout
                // the transaction.
                switch (ter.resultClass()) {
                    case tecCLAIM:
                        // Sequence was consumed and may even succeed,
                        // so do nothing and just wait until it clears or
                        // expires.
                        // TODO: could resubmit at least once, after the ledger
                        // closes.
                        awaitLastLedgerSequenceExpiry(txn);
                        break;
                    // OLD COMMENT: These are, according to the wiki, all of a final disposition
                    // ND2017: ^ The wiki was wrong, or misread ^
                    case temMALFORMED:
                        // See "For safety" comment above, though for a trusted
                        // server we should be able to immediately clear these.
                    case tefFAILURE:
                    case telLOCAL_ERROR:
                        // Assume failed and wait for it to either clear or
                        // timeout, but don't resubmit.
                    case terRETRY:
                        awaitLastLedgerSequenceExpiry(txn);
                        if (getPending().isEmpty()) //noinspection DanglingJavadoc
                        {
                            /**
                             * We take max(serverReported, locallyCalculated)
                             *
                             * {@link TransactionManager#locallyPreemptedSubmissionSequence}
                              */
                            sequence--;
                        } else {
                            // Plug a Sequence gap and preemptively resubmit some
                            // txns rather than waiting for `OnValidatedSequence`
                            // which will take quite some ledgers.
                            queueSequencePlugTxn(submitSequence);
                            resubmitGreaterThan(submitSequence);
                        }
                        break;
                }
                break;
        }
    }

    private void awaitLastLedgerSequenceExpiry(ManagedTxn txn) {
        finalizeTxnAndRemoveFromQueue(txn);
        failedTransactions.add(txn);
    }

    private void resubmitGreaterThan(UInt32 submitSequence) {
        for (ManagedTxn txn : getPending()) {
            if (txn.sequence().compareTo(submitSequence) > 0) {
                resubmitWithSameSequence(txn);
            }
        }
    }

    private void queueSequencePlugTxn(UInt32 sequence) {
        ManagedTxn plug = manage(new AccountSet());
        plug.setSequencePlug(true);
        queue(plug, sequence);
    }

    private void finalizeTxnAndRemoveFromQueue(ManagedTxn transaction) {
        transaction.setFinalized();
        pending.remove(transaction);
    }

    private void resubmitFirstTransactionWithTakenSequence(UInt32 sequence) {
        for (ManagedTxn txn : getPending()) {
            if (txn.sequence().compareTo(sequence) == 0) {
                resubmitWithNewSequence(txn);
                break;
            }
        }
    }
    // We only EVER resubmit a txn with a new Sequence if we have actually
    // seen that the Sequence has been consumed by a transaction we didn't
    // submit ourselves.

    // TODO: This is arguably a security RED FLAG and should probably be
    // treated as such. This code followed the earlier versions of JavaScript
    // ripple-lib and tries to the handle the case of sequence contention.

    // This is the method that handles that,
    private void resubmitWithNewSequence(final ManagedTxn txn) {
        // A sequence plug's sole purpose is to plug a Sequence
        // so that transactions may clear.
        if (txn.isSequencePlug()) {
            // The sequence has already been plugged (somehow)
            // So:
            return; // without further ado.
        }

        // ONLY ONLY ONLY if we've actually seen the Sequence
        if (txnNotFinalizedAndSeenValidatedSequence(txn)) {
            resubmit(txn, locallyPreemptedSubmissionSequence());
        } else {
            // requesting account_tx now and then (as we do) should ensure that
            // this doesn't stall forever. We'll either finalize the transaction
            // or Sequence will be seen to have been consumed by another txn.
            on(OnValidatedSequence.class,
                new CallbackContext() {
                    @Override
                    public boolean shouldExecute() {
                        return !txn.isFinalized();
                    }

                    @Override
                    public boolean shouldRemove() {
                        return txn.isFinalized();
                    }
                },
                    uInt32 -> {
                        // Again, just to be safe.
                        if (txnNotFinalizedAndSeenValidatedSequence(txn)) {
                            resubmit(txn, locallyPreemptedSubmissionSequence());
                        }
                    });
        }
    }

    private void resubmit(ManagedTxn txn, UInt32 sequence) {
//        if (txn.abortedAwaitingFinal()) {
//            return;
//        }
        makeSubmitRequest(txn, sequence);
    }

    private void resubmitWithSameSequence(ManagedTxn txn) {
        UInt32 previouslySubmitted = txn.sequence();
        resubmit(txn, previouslySubmitted);
    }

    public ManagedTxn manage(Transaction tt) {
        ManagedTxn txn = new ManagedTxn(tt);
        tt.account(accountID);
        return txn;
    }

    public void notifyTransactionResult(TransactionResult tr) {
        if (!tr.validated || !(tr.initiatingAccount().equals(accountID))) {
            return;
        }
        UInt32 txnSequence = tr.txn.get(UInt32.Sequence);
        seenValidatedSequences.add(txnSequence.longValue());

        ManagedTxn txn = submittedTransactionForHash(tr.hash);
        if (txn != null) {
            finalizeTxnAndRemoveFromQueue(txn);
            failedTransactions.remove(txn);
            txn.emit(ManagedTxn.OnTransactionValidated.class, tr);
        } else //noinspection DanglingJavadoc
        {
            // TODO: Check for transaction malleability, by computing a signing
            // hash.
            // ND2017: With RFC 6979 and canonical signatures, this should not
            // be a problem.

            // Preempt the terPRE_SEQ
            resubmitFirstTransactionWithTakenSequence(txnSequence);
            // Some transactions are waiting on this event before resubmission
            // TODO: this assumes transactions will come in ordered
            /**
             * {@link com.ripple.client.subscriptions.ledger.LedgerSubscriber}
             */
            emit(OnValidatedSequence.class, txnSequence.add(new UInt32(1)));
        }
    }

    private ManagedTxn submittedTransactionForHash(Hash256 hash) {
        for (ManagedTxn pending : getPending()) {
            if (pending.wasSubmittedWith(hash)) {
                return pending;
            }
        }
        for (ManagedTxn markedAsFailed : failedTransactions) {
            if (markedAsFailed.wasSubmittedWith(hash)) {
                return markedAsFailed;
            }
        }
        return null;
    }
}
