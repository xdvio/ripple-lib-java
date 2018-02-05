package com.ripple.client.integration

import com.ripple.core.coretypes.AccountID
import com.ripple.crypto.Seed

class TestAccount(name: String) {
    private val passPhrase = if (name == "root") "masterpassphrase" else name
    private val seed = Seed.fromPassPhrase(passPhrase)!!
    val keyPair = seed.keyPair()!!
    val id = AccountID(keyPair.id())
}