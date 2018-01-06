package com.ripple.crypto;

import com.ripple.config.Config;
import com.ripple.crypto.ed25519.EDKeyPair;
import com.ripple.crypto.ecdsa.K256;
import com.ripple.crypto.keys.IKeyPair;
import com.ripple.encodings.B58IdentiferCodecs;
import com.ripple.encodings.base58.B58;
import com.ripple.utils.Sha512;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static com.ripple.config.Config.getB58IdentiferCodecs;

public class Seed {
    public static byte[] VER_K256 = new byte[]{(byte) B58IdentiferCodecs.VER_FAMILY_SEED};
    public static byte[] VER_ED25519 = new byte[]{(byte) 0x1, (byte) 0xe1, (byte) 0x4b};

    private final byte[] seedBytes;
    private byte[] version;

    public Seed(byte[] seedBytes) {
        this(VER_K256, seedBytes);
    }
    public Seed(byte[] version, byte[] seedBytes) {
        this.seedBytes = seedBytes;
        this.version = version;
    }

    @Override
    public String toString() {
        return Config.getB58().encodeToStringChecked(seedBytes, version);
    }

    public byte[] bytes() {
        return seedBytes;
    }

    public byte[] version() {
        return version;
    }

    public Seed setEd25519() {
        this.version = VER_ED25519;
        return this;
    }

    public IKeyPair keyPair() {
        return keyPair(0);
    }

    public IKeyPair rootKeyPair() {
        return keyPair(-1);
    }

    public IKeyPair keyPair(int account) {
        if (Arrays.equals(version, VER_ED25519)) {
            if (account != 0) throw new AssertionError();
            return EDKeyPair.from128Seed(seedBytes);
        }  else {
            return K256.createKeyPair(seedBytes, account);
        }

    }
    public static Seed fromBase58(String b58) {
        B58.Decoded decoded = Config.getB58().decodeMulti(b58, 16, VER_K256, VER_ED25519);
        return new Seed(decoded.version, decoded.payload);
    }

    public static Seed fromPassPhrase(String passPhrase) {
        return new Seed(passPhraseToSeedBytes(passPhrase));
    }

    public static byte[] passPhraseToSeedBytes(String phrase) {
        try {
            return new Sha512(phrase.getBytes("utf-8")).finish128();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static IKeyPair createKeyPair(byte[] seedBytes) {
        return K256.createKeyPair(seedBytes, 0);
    }

    public static IKeyPair getKeyPair(byte[] seedBytes) {
        return K256.createKeyPair(seedBytes, 0);
    }

    public static IKeyPair getKeyPair(String b58) {
        return getKeyPair(getB58IdentiferCodecs().decodeFamilySeed(b58));
    }
}


