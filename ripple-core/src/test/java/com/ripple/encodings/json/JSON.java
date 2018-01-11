package com.ripple.encodings.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.Reader;

public class JSON {

    public static class ParseError extends RuntimeException {
        ParseError(Exception e) {
            super(e);
        }
    }

    public static ObjectMapper mapper = new ObjectMapper();
    public static JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new ParseError(e);
        }
    }

    public static JsonNode parse(Reader reader) {
        try {
            return mapper.readTree(reader);
        } catch (IOException e) {
            throw new ParseError(e);
        }
    }

    public static ObjectNode parseObject(String json) {
        return (ObjectNode) parse(json);
    }

    public static ObjectNode parseObject(Reader json) {
        return (ObjectNode) parse(json);
    }
}
