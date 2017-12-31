package com.ripple.core.types.shamap;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class AccountStateTest {

    @Test
    public void loadFromLedgerDump() throws IOException {
        File file = new File("/Users/ndudfield/ripple/rippled/ledger.json");

        if (file.exists()) {
            System.out.println(new Date());
            AccountState state = AccountState.loadFromLedgerDump(file.getPath());
            assertEquals(
                    "F47142281FEC506E179E110044626662DA05AC72CBEB16632B8F52A3E3971548",
                    state.hash().toHex());
            System.out.println(new Date());
        }
    }
}