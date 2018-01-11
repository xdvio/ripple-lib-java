package com.ripple.encodings;

import com.ripple.core.TestFixtures;
import com.ripple.encodings.addresses.Addresses;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class B58IdentifierTest {
    @Test
    public void testDecodeFamilySeed() {
        Assert.assertArrayEquals(TestFixtures.master_seed_bytes,
                Addresses.decodeSeedToBytes(TestFixtures.master_seed));
    }
    @Test
    public void testEncodeFamilySeed() {
        String masterSeedStringRebuilt = Addresses.encodeSeedK256(TestFixtures.master_seed_bytes);
        assertEquals(TestFixtures.master_seed, masterSeedStringRebuilt);
    }
}
