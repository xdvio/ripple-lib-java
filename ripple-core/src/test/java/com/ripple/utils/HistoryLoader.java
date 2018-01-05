package com.ripple.utils;

import com.ripple.core.binary.STReader;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.core.coretypes.uint.UInt32;
import com.ripple.core.types.known.tx.result.TransactionResult;
import com.ripple.core.types.ledger.LedgerHeader;
import com.ripple.core.types.shamap.AccountState;
import com.ripple.core.types.shamap.TransactionTree;

public class HistoryLoader {
    private AccountState _state;
    private TransactionTree _transactions;
    private LedgerHeader _ledger, _parentLedger;
    private final STReader _reader;

    public interface OnLedger {
        boolean onLedger(LedgerHeader header, AccountState state, TransactionTree txns);
    }

    public HistoryLoader(STReader reader) {
        _reader = reader;
    }

    public void Parse(OnLedger onLedger) {
        ParseIt(onLedger, true);
    }

    public void ParseFast(OnLedger onLedger) {
        ParseIt(onLedger, false);
    }

    private void ParseIt(OnLedger onLedger, boolean copyMap) {
        while (!_reader.end()) {
            ParseOneLedger();

            boolean continue_ = onLedger.onLedger(_ledger,
                    copyMap ? _state.copy() : _state,
                    _transactions);
            if (!continue_) {
                break;
            }
        }
    }

    private void ParseOneLedger() {
        _parentLedger = _ledger;
        _ledger = LedgerHeader.fromReader(_reader);

        if (NextFrame() == FrameType.AccountStateTree) {
            ParseAccountState();
        }

        ParseTransactions();
        CheckTransactionHash();
        ParseAndApplyAccountStateDiff();
        CheckStateHash();
        CheckHashChain();
    }

    private int NextFrame() {
        return _reader.parser().read(1)[0];
    }

    private void CheckHashChain() {
        if (_parentLedger != null) {
            AssertHashesEqual(_parentLedger.hash(), _ledger.previousLedger);
        }
    }

    private void CheckStateHash() {
        AssertHashesEqual(_state.hash(), _ledger.stateHash);

    }

    private void CheckTransactionHash() {
        AssertHashesEqual(_transactions.hash(), _ledger.transactionHash);
    }

    private static void AssertHashesEqual(Hash256 h1, Hash256 h2) {
        if (!h1.equals(h2)) {
            throw new AssertionError(h1 + " != " + h2);
        }
    }

    private void ParseTransactions() {
        _transactions = new TransactionTree();

        while (NextFrame() == FrameType.IndexedTransaction) {
            TransactionResult tr = _reader.readTransactionResult(_ledger.sequence);
            _transactions.addTransactionResult(tr);
            _reader.readOneInt();
            ParseAndApplyAccountStateDiff();
        }
    }

    private void ParseAccountState() {
        _state = new AccountState();

        while (NextFrame() == FrameType.IndexedLedgerEntry) {
            _state.addLE(_reader.readLE());
        }
    }

    private void ParseAndApplyAccountStateDiff() {
        UInt32 modded = _reader.uInt32();
        for (long i = 0; i < modded.longValue(); i++) {
            boolean b = _state.updateLE(_reader.readLE());
            if (!b) {
                throw new AssertionError();
            }
        }

        UInt32 deleted = _reader.uInt32();
        for (long i = 0; i < deleted.longValue(); i++) {
            boolean b = _state.removeLeaf(_reader.hash256());
            if (!b) {
                throw new AssertionError();
            }
        }

        UInt32 added = _reader.uInt32();
        for (long i = 0; i < added.longValue(); i++) {
            boolean b = _state.addLE(_reader.readLE());
            if (!b) {
                throw new AssertionError();
            }
        }
    }

    public static class FrameType {
        public static final int AccountStateTree = 0;
        public static final int AccountStateTreeEnd = 1;
        public static final int AccountStateDelta = 2;
        public static final int IndexedLedgerEntry = 3;
        public static final int IndexedTransaction = 4;
    }
}
