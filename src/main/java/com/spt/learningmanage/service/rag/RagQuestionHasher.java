package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.config.RagProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class RagQuestionHasher {
    private final RagProperties properties;

    public RagQuestionHasher(RagProperties properties) {
        this.properties = properties;
    }

    public String hmac(String question) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getQuestionHmacSecret()
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(question.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate RAG question HMAC", exception);
        }
    }
}
