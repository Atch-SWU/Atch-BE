package com.swu.atch.ai.service;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class AiJson {
    private final JsonMapper json;

    public AiJson(JsonMapper json) {
        this.json = json;
    }

    public String write(Object value) {
        return json.writeValueAsString(value);
    }

    public <T> T read(String value, Class<T> type) {
        return json.readValue(value, type);
    }

    public String hash(Object value) {
        return sha256(write(canonical(json.readValue(write(value), Object.class))));
    }

    private Object canonical(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            var sorted = new java.util.TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(key.toString(), canonical(item)));
            return sorted;
        }
        if (value instanceof java.util.List<?> list)
            return list.stream().map(this::canonical).toList();
        return value;
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String title(String value) {
        return value.trim();
    }

    public static String text(String value) {
        return value == null ? "" : value;
    }
}
