package com.spt.learningmanage.ai.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.config.AiProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultAiContentSanitizer implements AiContentSanitizer {

    private static final String BLOCKED_VALUE = "[BLOCKED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "proxy-authorization", "api-key", "apikey", "api_key",
            "accesskey", "access-key", "access_key", "accesskeysecret", "access-key-secret",
            "secret", "client-secret", "client_secret", "password", "passwd", "pwd",
            "token", "access-token", "access_token", "refresh-token", "refresh_token",
            "cookie", "set-cookie", "session", "sessionid", "session-id"
    );
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization)\\s*[:=]\\s*)(?:bearer|basic)\\s+[^\\s,;\\]}]+"
    );
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])"
    );
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)([\\\"']?(?:api[_-]?key|access[_-]?key(?:[_-]?secret)?|client[_-]?secret|secret|password|passwd|pwd|access[_-]?token|refresh[_-]?token|token|cookie|session(?:[_-]?id)?)[\\\"']?\\s*[:=]\\s*[\\\"']?)(?!\\[REDACTED:)([^\\\"'\\s,;}&\\]]+)"
    );
    private static final Pattern URI_CREDENTIALS = Pattern.compile(
            "(?i)(\\b(?:(?:jdbc:[a-z0-9]+:)|(?:[a-z][a-z0-9+.-]*:))?//[^:/@\\s]+:)([^@/\\s]+)(@)"
    );

    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public DefaultAiContentSanitizer(ObjectMapper objectMapper, AiProperties aiProperties) {
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public AiSanitizedContent sanitizeForProvider(String content) {
        return sanitize(content, Integer.MAX_VALUE);
    }

    @Override
    public AiSanitizedContent sanitizeForLog(String content, boolean errorContent) {
        int configuredLimit = errorContent
                ? aiProperties.getLogging().getMaxErrorChars()
                : aiProperties.getLogging().getMaxBodyChars();
        return sanitize(content, Math.max(configuredLimit, 1));
    }

    private AiSanitizedContent sanitize(String content, int maxChars) {
        if (content == null) {
            return new AiSanitizedContent(null, AiSanitizationStatus.CLEAN, false, null);
        }
        try {
            String structured = sanitizeJsonIfPossible(content);
            String sanitized = redactText(structured);
            boolean redacted = !content.equals(sanitized);
            String hash = sha256(sanitized);
            boolean truncated = sanitized.length() > maxChars;
            String stored = truncated ? sanitized.substring(0, maxChars) : sanitized;
            return new AiSanitizedContent(stored,
                    redacted ? AiSanitizationStatus.REDACTED : AiSanitizationStatus.CLEAN,
                    truncated, hash);
        } catch (RuntimeException exception) {
            return new AiSanitizedContent(BLOCKED_VALUE, AiSanitizationStatus.BLOCKED,
                    false, sha256(BLOCKED_VALUE));
        }
    }

    private String sanitizeJsonIfPossible(String content) {
        String trimmed = content.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return content;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root == null) {
                return content;
            }
            JsonNode copy = root.deepCopy();
            return redactNode(copy) ? objectMapper.writeValueAsString(copy) : content;
        } catch (Exception ignored) {
            return content;
        }
    }

    private boolean redactNode(JsonNode node) {
        boolean changed = false;
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    objectNode.put(field.getKey(), placeholder(field.getKey()));
                    changed = true;
                } else {
                    changed |= redactNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                changed |= redactNode(child);
            }
        }
        return changed;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }

    private String placeholder(String key) {
        String type = key == null ? "SECRET" : key.replaceAll("[^A-Za-z0-9]", "_")
                .toUpperCase(Locale.ROOT);
        return "[REDACTED:" + type + "]";
    }

    private String redactText(String text) {
        String value = replace(AUTHORIZATION, text, "$1[REDACTED:AUTHORIZATION]");
        value = replace(JWT, value, "[REDACTED:JWT]");
        value = replace(KEY_VALUE, value, "$1[REDACTED:SECRET]");
        return replace(URI_CREDENTIALS, value, "$1[REDACTED:PASSWORD]$3");
    }

    private String replace(Pattern pattern, String value, String replacement) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.replaceAll(replacement) : value;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
