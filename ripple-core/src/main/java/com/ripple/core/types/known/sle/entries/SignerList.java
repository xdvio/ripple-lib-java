package com.ripple.core.types.known.sle.entries;

import com.ripple.core.coretypes.uint.UInt32;
import com.ripple.core.fields.Field;
import com.ripple.core.serialized.enums.LedgerEntryType;

public class SignerList extends IndexedLedgerEntry {
    public SignerList() {
        super(LedgerEntryType.SignerList);
    }

    @Override
    public void setDefaults() {
        super.setDefaults();
        if (!has(Field.SignerListID)) {
            put(UInt32.SignerListID, UInt32.ZERO);
        }
    }
}
