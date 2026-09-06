package com.spt.learningmanage.interceptor;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.ai.governance.AiFeatureGate;
import com.spt.learningmanage.service.RateLimitService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final String POST_METHOD = "POST";

    private static final Map<String, String> AI_RATE_LIMIT_SCENE_MAP = Map.of(
            "/ai/breakdown", "task-breakdown",
            "/ai/breakdown/preview", "task-breakdown",
            "/ai/polish", "weekly-polish",
            "/ai/polish/preview", "weekly-polish",
            "/ai/today-order/recommend", "today-order",
            "/ai/daily-review/suggest-rename", "daily-review-rename",
            "/ai/list/replan/preview", "list-replan",
            "/ai/rag/ask", "rag-project-ask",
            "/ai/agent/project-risk", "project-risk-report",
            "/ai/agent/team-workload", "team-workload-report"
    );

    @Resource
    private RateLimitService rateLimitService;

    @Resource
    private AiFeatureGate aiFeatureGate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!POST_METHOD.equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String scene = resolveScene(request);
        if (scene == null) {
            return true;
        }
        if (!aiFeatureGate.isChatEnabled()) {
            throw new BusinessException(ErrorCode.AI_DISABLED);
        }

        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        rateLimitService.checkAiRateLimit(userId, scene);
        return true;
    }

    private String resolveScene(HttpServletRequest request) {
        String path = normalizePath(request);
        return AI_RATE_LIMIT_SCENE_MAP.get(path);
    }

    private String normalizePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestUri;
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            path = requestUri.substring(contextPath.length());
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
