package com.spt.learningmanage.service.knowledge;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class KnowledgeTextNormalizer {

    public static final String VERSION = "norm-v1";

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean horizontalWhitespace = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '\n') {
                trimTrailingSpace(result);
                if (result.isEmpty() || result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                horizontalWhitespace = false;
            } else if (current == ' ' || current == '\t' || current == '\f') {
                horizontalWhitespace = true;
            } else {
                if (horizontalWhitespace && !result.isEmpty()
                        && result.charAt(result.length() - 1) != '\n') {
                    result.append(' ');
                }
                result.append(current);
                horizontalWhitespace = false;
            }
        }
        return result.toString().trim();
    }

    private void trimTrailingSpace(StringBuilder value) {
        while (!value.isEmpty() && value.charAt(value.length() - 1) == ' ') {
            value.deleteCharAt(value.length() - 1);
        }
    }
}
