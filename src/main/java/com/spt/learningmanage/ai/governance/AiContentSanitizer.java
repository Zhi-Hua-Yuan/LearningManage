package com.spt.learningmanage.ai.governance;

public interface AiContentSanitizer {

    AiSanitizedContent sanitizeForProvider(String content);

    AiSanitizedContent sanitizeForLog(String content, boolean errorContent);
}
