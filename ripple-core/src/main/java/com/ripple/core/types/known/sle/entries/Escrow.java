package com.ripple.core.types.known.sle.entries;

import com.ripple.core.coretypes.AccountID;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.core.coretypes.hash.Index;
import com.ripple.core.coretypes.uint.UInt64;
import com.ripple.core.serialized.enums.LedgerEntryType;
import com.ripple.core.types.known.tx.Transaction;

import java.util.ArrayList;

public class Escrow extends IndexedLedgerEntry {
    public Escrow() {
        super(LedgerEntryType.Escrow);
    }

    @Override
    public void setDefaults() {
        super.setDefaults();
        if (multiParty()) {
            if (!has(UInt64.DestinationNode)) {
                put(UInt64.DestinationNode, UInt64.ZERO);
            }
        }
    }

    @Override
    public ArrayList<Hash256> ownerDirectoryIndexes(Transaction nullableContext) {
        ArrayList<Hash256> indexes = super.ownerDirectoryIndexes(nullableContext);
        if (multiParty()) {
            Hash256 destinationOwnerDir =
                    Index.ownerDirectory(destination());
            indexes.add(Index.directoryNode(
                    destinationOwnerDir, destinationNode()));
        }
        return indexes;
    }

    private UInt64 destinationNode() {
        return get(UInt64.DestinationNode);
    }

    private AccountID destination() {
        return get(AccountID.Destination);
    }

    private boolean multiParty() {
        return !get(AccountID.Account).equals(destination());
    }
}
