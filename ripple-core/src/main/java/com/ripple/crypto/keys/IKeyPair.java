package com.ripple.crypto.keys;

import java.math.BigInteger;

public interface IKeyPair extends IVerifyingKey {
    BigInteger privateKey();
    byte[] signMessage(byte[] message);
}
