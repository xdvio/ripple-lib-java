package com.ripple.core.coretypes.uint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UIntTest {

    @Test
    public void testLTE() {
        UInt64 n = new UInt64(34);
        UInt32 n2 = new UInt32(400);
        assertTrue(n.lte(n2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUInt32Negative() {
        new UInt32(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUInt8TooBig() {
        try {
            new UInt8(374);
        } catch (Exception e) {
            assertEquals("value `374` is illegal for UInt8",
                    e.getMessage());
            throw e;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUInt16TooBig() {
        try {
            new UInt16(1 << 20);
        } catch (Exception e) {
            assertEquals("value `1048576` is illegal for UInt16",
                    e.getMessage());
            throw e;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUInt32TooBig() {
        try {
            new UInt32(((long) 1) << 32);
        } catch (Exception e) {
            assertEquals("value `4294967296` is illegal for UInt32",
                    e.getMessage());
            throw e;
        }
    }
}
