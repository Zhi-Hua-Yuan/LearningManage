package com.spt.learningmanage.service.ai.support;

public interface AiJsonResponseSanitizer {
    String sanitizeArray(String content);

    String sanitizeObject(String content);
}
