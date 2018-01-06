package com.ripple.client.subscriptions.ledger;

import com.ripple.core.types.ledger.LedgerHeader;
import com.ripple.core.types.shamap.TransactionTree;

public class ClosedLedger {
    public ClosedLedger(LedgerHeader header, TransactionTree transactions) {
        this.header = header;
        this.transactionTree = transactions;
    }
    public final LedgerHeader header;
    public final TransactionTree transactionTree;
}
