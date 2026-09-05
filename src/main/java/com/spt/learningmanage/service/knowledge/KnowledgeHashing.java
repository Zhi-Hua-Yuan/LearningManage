package com.spt.learningmanage.service.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Component
public class KnowledgeHashing {

    private final ObjectMapper objectMapper;

    public KnowledgeHashing(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String contentHash(String canonicalText) {
        return sha256(KnowledgeTextNormalizer.VERSION + "\n"
                + KnowledgeChunker.VERSION + "\n" + canonicalText);
    }

    public String payloadHash(Map<String, Object> payload) {
        try {
            return sha256(objectMapper.writeValueAsString(new TreeMap<>(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to canonicalize knowledge payload", exception);
        }
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
