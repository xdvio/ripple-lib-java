package com.ripple.core.types.known.generic

import com.ripple.core.coretypes.STObject
import com.ripple.utils.noSpace
import org.intellij.lang.annotations.Language
import com.ripple.utils.normalizeJSON
import com.ripple.utils.normalizedJSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private val validationHex =
            """
       22800000012602208FB22921E76C2451690C9EFEB2229BC2A26FEFFBD7954489
       E8B3DC1A447EDDAF4F993230B7EE72AF732102ACAA0A6AB8C6BAD6495DF58C1A
       5ADB9BC3054304743DEEA5F68B6B5560CCD15E7647304502210092522C2DC4F3
       0E5C8D7A611544772071C6F5BB8AE2230162297169EE480B853E0220012ECA5F
       382172D48BA9E6B6C8C5354678F7DF6675B3CAEE5C044B7D9CBADA0D""".noSpace()

    @Test
    fun testParsingBlob() {
        @Language("JSON")
        val expected ="""
             {
              "LedgerHash": "690C9EFEB2229BC2A26FEFFBD7954489E8B3DC1A447EDDAF4F993230B7EE72AF",
              "LedgerSequence": 35688370,
              "SigningPubKey": "02ACAA0A6AB8C6BAD6495DF58C1A5ADB9BC3054304743DEEA5F68B6B5560CCD15E",
              "SigningTime": 568814628,
              "Signature": "304502210092522C2DC4F30E5C8D7A611544772071C6F5BB8AE2230162297169EE480B853E0220012ECA5F382172D48BA9E6B6C8C5354678F7DF6675B3CAEE5C044B7D9CBADA0D",
              "Flags": 2147483649
            }
        """
        val so = STObject.fromHex(validationHex)
        assertEquals(expected.normalizeJSON(), so.normalizedJSON())
        assertTrue(so is Validation)
    }
}