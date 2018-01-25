package com.ripple.core.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ripple.encodings.json.JSON;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ValueTest {

    private JsonNode jsonNode = parseJson("{\n" +
            "  \"works\" : true,\n" +
            "  \"object\" : {\n" +
            "    \"yes\" : \"no\"\n" +
            "  }\n" +
            "}");

    @Test
    public void testJacksonBoolean() {
        Value works = Value.typeOf(jsonNode.path("works"));
        assertEquals(Value.JACKSON_BOOLEAN, works);
    }

    @Test
    public void testJacksonObject() {
        Value works = Value.typeOf(jsonNode.path("object"));
        assertEquals(Value.JACKSON_OBJECT, works);
    }

    private JsonNode parseJson(String content) {
        return JSON.parse(content);
    }
}