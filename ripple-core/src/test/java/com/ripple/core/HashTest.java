package com.ripple.core;

import com.ripple.core.coretypes.STObject;
import com.ripple.core.coretypes.hash.Hash128;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.encodings.common.B16;
import com.ripple.utils.Utils;
import org.junit.Test;

import static com.ripple.core.coretypes.hash.Hash256.Hash256Map;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HashTest{

    @Test(expected = IllegalArgumentException.class)
    public void testDoesNoImplicitPadding() {
        Hash128 hash128 = new Hash128(new byte[]{0});
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBoundsChecking() {
        Hash128 hash128 = new Hash128(new byte[32]);
    }

    @Test
    public void testTreeMapSuitability() throws Exception {
        Hash256 a = new Hash256(Utils.padTo256(B16.decode("0A")));
        Hash256 b = new Hash256(Utils.padTo256(B16.decode("0B")));
        Hash256 c = new Hash256(Utils.padTo256(B16.decode("0C")));

        Hash256 d = new Hash256(Utils.padTo256(B16.decode("0A")));

        STObject objectA = new STObject();
        STObject objectB = new STObject();
        STObject objectC = new STObject();

        Hash256Map<STObject> tree = new Hash256Map<>();
        tree.put(a, objectA);

        // There can be ONLY one
        assertTrue(tree.containsKey(d));

        tree.put(b, objectB);
        tree.put(c, objectC);

        assertSame(tree.get(a), objectA);
        assertSame(tree.get(b), objectB);
        assertSame(tree.get(c), objectC);
    }
}
