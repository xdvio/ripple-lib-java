package com.ripple.crypto.ecdsa;

import com.ripple.crypto.keys.IKeyPair;
import com.ripple.utils.HashUtils;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class K256KeyPair extends K256VerifyingKey implements IKeyPair {
    private BigInteger privateKey;
    private ECDSASigner signer;

    K256KeyPair(BigInteger privateKey, ECPoint pub, byte[] pubEncoded) {
        super(pub, pubEncoded);
        this.privateKey = privateKey;
        signer= new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKey, SECP256K1.params());
        signer.init(true, privKey);
    }

    @Override
    public BigInteger privateKey() {
        return privateKey;
    }

    @Override
    public byte[] signMessage(byte[] message) {
        byte[] hash = HashUtils.halfSha512(message);
        return signHash(hash);
    }

    public byte[] signHash(byte[] bytes) {
        ECDSASignature sig = K256.createECDSASignature(bytes, signer);
        byte[] der = sig.encodeToDER();
        if (!ECDSASignature.isStrictlyCanonical(der)) {
            throw new IllegalStateException("Signature is not strictly canonical");
        }
        return der;
    }

}
