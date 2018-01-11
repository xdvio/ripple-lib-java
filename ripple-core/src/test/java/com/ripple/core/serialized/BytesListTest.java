package com.ripple.core.serialized;

import com.ripple.config.Config;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class BytesListTest {
    static {
        Config.initBouncy();
    }

    @Test
    public void testNested() throws Exception {

        BytesList ba1 = new BytesList();
        BytesList ba2 = new BytesList();

        ba1.add(new byte[]{'a', 'b', 'c'});
        ba1.add(new byte[]{'d', 'e'});

        ba2.add(new byte[]{'f', 'g'});
        ba2.add((byte) 'h');
        ba2.add(ba1);

        assertEquals(ba2.bytesLength(), 8);
        byte[] bytes = ba2.bytes();
        String ascii = new String(bytes, "ascii");

        assertEquals("fghabcde", ascii);
    }
}
