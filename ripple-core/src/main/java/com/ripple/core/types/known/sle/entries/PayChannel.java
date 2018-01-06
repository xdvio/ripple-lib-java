package com.ripple.core.types.known.sle.entries;

import com.ripple.core.serialized.enums.LedgerEntryType;

public class PayChannel extends IndexedLedgerEntry {
    public PayChannel() {
        super(LedgerEntryType.PayChannel);
    }

}
