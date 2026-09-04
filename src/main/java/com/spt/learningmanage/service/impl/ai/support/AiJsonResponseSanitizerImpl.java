package com.spt.learningmanage.service.impl.ai.support;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import org.springframework.stereotype.Service;

@Service
public class AiJsonResponseSanitizerImpl implements AiJsonResponseSanitizer {

    @Override
    public String sanitizeArray(String content) {
        return sanitize(content, '[', ']');
    }

    @Override
    public String sanitizeObject(String content) {
        return sanitize(content, '{', '}');
    }

    private String sanitize(String content, char opening, char closing) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
        }
        String cleaned = content.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        int startIndex = cleaned.indexOf(opening);
        int endIndex = cleaned.lastIndexOf(closing);
        return startIndex >= 0 && endIndex > startIndex
                ? cleaned.substring(startIndex, endIndex + 1)
                : cleaned;
    }
}
