package com.spt.learningmanage.service.impl.ai.draft;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationContext;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import com.spt.learningmanage.service.ai.draft.AiDraftHandlerRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAiDraftHandlerRegistry implements AiDraftHandlerRegistry {

    private final Map<String, AiDraftHandler<?>> handlers;

    public DefaultAiDraftHandlerRegistry(List<AiDraftHandler<?>> registeredHandlers) {
        Map<String, AiDraftHandler<?>> resolved = new LinkedHashMap<>();
        for (AiDraftHandler<?> handler : registeredHandlers) {
            if (handler == null || StrUtil.isBlank(handler.scene())) {
                throw new IllegalStateException("AI 草稿 Handler 场景不能为空");
            }
            if (handler.currentSchemaVersion() <= 0
                    || !handler.supportedSchemaVersions().contains(handler.currentSchemaVersion())) {
                throw new IllegalStateException("AI 草稿 Handler 版本配置非法: " + handler.scene());
            }
            AiDraftHandler<?> previous = resolved.putIfAbsent(handler.scene(), handler);
            if (previous != null) {
                throw new IllegalStateException("AI 草稿 Handler 场景重复: " + handler.scene());
            }
        }
        this.handlers = Map.copyOf(resolved);
    }

    @Override
    public AiDraftHandler<?> require(String scene, AiDraftConfirmationContext context) {
        AiDraftHandler<?> handler = handlers.get(scene);
        if (handler == null) {
            throw new BusinessException(ErrorCode.AI_DRAFT_NOT_CONFIRMABLE, "草稿场景不支持确认");
        }
        if (context == null || !handler.contextType().isInstance(context)) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿确认上下文类型不匹配");
        }
        return handler;
    }

    @Override
    public int currentSchemaVersion(String scene) {
        AiDraftHandler<?> handler = handlers.get(scene);
        if (handler == null) {
            throw new BusinessException(ErrorCode.AI_DRAFT_NOT_CONFIRMABLE, "草稿场景不支持创建");
        }
        return handler.currentSchemaVersion();
    }
}
