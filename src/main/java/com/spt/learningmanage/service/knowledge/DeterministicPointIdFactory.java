package com.spt.learningmanage.service.knowledge;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
public class DeterministicPointIdFactory {

    private static final UUID NAMESPACE = UUID.fromString("a8bb8795-989f-5f9d-ae71-5aee44f331d2");

    public String pointId(String documentKey, int chunkIndex) {
        if (documentKey == null || documentKey.isBlank() || chunkIndex < 0) {
            throw new IllegalArgumentException("Point identity is invalid");
        }
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer namespaceBytes = ByteBuffer.allocate(16)
                    .putLong(NAMESPACE.getMostSignificantBits())
                    .putLong(NAMESPACE.getLeastSignificantBits());
            sha1.update(namespaceBytes.array());
            byte[] hash = sha1.digest((documentKey + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer uuidBytes = ByteBuffer.wrap(hash, 0, 16);
            return new UUID(uuidBytes.getLong(), uuidBytes.getLong()).toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }
}
