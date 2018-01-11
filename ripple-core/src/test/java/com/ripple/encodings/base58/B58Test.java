package com.ripple.encodings.base58;

import com.ripple.encodings.addresses.Addresses;
import com.ripple.encodings.common.B16;
import org.bouncycastle.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class B58Test {

    @Test
    public void testFindsEdPrefix() throws Exception {
        String prefix = "sEd";
        byte[] versionBytes = Addresses.codec.findPrefix(16, prefix);
        testStability(16, prefix, versionBytes);
        assertEncodesTo("01E14B", versionBytes);
    }

    @Test
    public void testFindsecp256k1Prefix() throws Exception {
        String prefix = "secp256k1";
        byte[] versionBytes = Addresses.codec.findPrefix(16, prefix);
        testStability(16, prefix, versionBytes);
        assertEncodesTo("13984B20F2BD93", versionBytes);
    }

    private void testStability(@SuppressWarnings("SameParameterValue")
                                       int length, String prefix, byte[] versionBytes) {
        B58.Version version = new B58.Version(versionBytes, "test", length);
        testStabilityWithAllByteValuesAtIx(0, length, prefix, version);
        testStabilityWithAllByteValuesAtIx(length -1, length, prefix, version);
    }

    private void testStabilityWithAllByteValuesAtIx(int ix,
                                                    int length,
                                                    String prefix,
                                                    B58.Version version) {
        byte[] sample = new byte[length];
        Arrays.fill(sample, (byte) 0xff);

        for (int i = 0; i < 256; i++) {
            sample[ix] = (byte) i;
            String encoded = Addresses.encode(sample, version);
            assertEquals(prefix, encoded.substring(0, prefix.length()));
        }
    }

    public void assertEncodesTo(String expected, byte[] actual) {
        assertEquals(expected, B16.encode(actual));
    }
}
