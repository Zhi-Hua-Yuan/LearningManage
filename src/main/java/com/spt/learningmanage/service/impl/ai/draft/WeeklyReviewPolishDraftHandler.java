package com.spt.learningmanage.service.impl.ai.draft;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.ai.draft.WeeklyReviewPolishConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class WeeklyReviewPolishDraftHandler implements AiDraftHandler<WeeklyReviewPolishConfirmationContext> {

    private final WeeklyReviewMapper weeklyReviewMapper;
    private final PermissionService permissionService;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public WeeklyReviewPolishDraftHandler(WeeklyReviewMapper weeklyReviewMapper,
                                          PermissionService permissionService,
                                          AiJsonResponseSanitizer jsonSanitizer) {
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.permissionService = permissionService;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Override
    public String scene() {
        return AiSceneEnum.WEEKLY_POLISH.getCode();
    }

    @Override
    public int currentSchemaVersion() {
        return 1;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    @Override
    public Class<WeeklyReviewPolishConfirmationContext> contextType() {
        return WeeklyReviewPolishConfirmationContext.class;
    }

    @Override
    public Long apply(AiDraft draft, WeeklyReviewPolishConfirmationContext context) {
        Long reviewId = context.reviewId();
        if (reviewId == null || reviewId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reviewId 不合法");
        }
        JSONObject payload = parsePayload(draft);
        JSONArray taskIds = payload.getJSONArray("taskIds");
        if (taskIds != null && !taskIds.isEmpty()) {
            List<Long> persistedTaskIds;
            try {
                persistedTaskIds = JSONUtil.toList(taskIds, Long.class);
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿任务引用损坏");
            }
            permissionService.requireAllTasksReadable(draft.getUserId(), persistedTaskIds);
        }
        WeeklyReview review = weeklyReviewMapper.selectByIdForUpdate(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "周总结不存在");
        }
        permissionService.requireWeeklyReviewUpdate(draft.getUserId(), reviewId);
        if (!Objects.equals(review.getUserId(), draft.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该周总结");
        }
        String reviewText = extractReviewText(payload.getStr("polished"));
        if (StrUtil.isBlank(reviewText)) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿内容为空");
        }
        review.setReflection(reviewText);
        if (weeklyReviewMapper.updateById(review) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结更新失败");
        }
        return reviewId;
    }

    private JSONObject parsePayload(AiDraft draft) {
        try {
            return JSONUtil.parseObj(draft.getPayloadJson());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿内容损坏，请重新生成");
        }
    }

    private String extractReviewText(String polished) {
        if (StrUtil.isBlank(polished)) {
            return "";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(jsonSanitizer.sanitizeObject(polished));
            return safeTrim(obj.getStr("review"));
        } catch (Exception exception) {
            return safeTrim(polished);
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
