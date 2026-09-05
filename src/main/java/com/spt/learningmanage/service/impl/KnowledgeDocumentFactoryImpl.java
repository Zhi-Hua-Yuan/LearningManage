package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.constant.WeeklyReviewVisibilityScopeEnum;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.knowledge.KnowledgeTextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeDocumentFactoryImpl implements KnowledgeDocumentFactory {

    private final TaskMapper taskMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final KnowledgeTextNormalizer normalizer;

    public KnowledgeDocumentFactoryImpl(TaskMapper taskMapper,
                                        WeeklyReviewMapper weeklyReviewMapper,
                                        ProjectMapper projectMapper,
                                        TeamMapper teamMapper,
                                        UserMapper userMapper,
                                        PermissionService permissionService,
                                        KnowledgeTextNormalizer normalizer) {
        this.taskMapper = taskMapper;
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.projectMapper = projectMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.normalizer = normalizer;
    }

    @Override
    public List<KnowledgeDocumentProjection> buildDesiredDocuments(KnowledgeSourceRef source) {
        return switch (source.sourceType()) {
            case TASK -> buildTask(source.sourceId());
            case WEEKLY_REVIEW -> buildWeeklyReview(source.sourceId());
        };
    }

    private List<KnowledgeDocumentProjection> buildTask(Long taskId) {
        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getIsDelete, 0)
                .last("limit 1"));
        if (task == null) {
            return List.of();
        }
        Project project = activeProject(task.getProjectId());
        if (project == null || project.getUserId() == null) {
            return List.of();
        }
        boolean teamDocument = project.getTeamId() != null;
        if (teamDocument && activeTeam(project.getTeamId()) == null) {
            return List.of();
        }
        KnowledgeVisibilityTypeEnum visibility = teamDocument
                ? KnowledgeVisibilityTypeEnum.TEAM : KnowledgeVisibilityTypeEnum.PRIVATE;
        Long ownerUserId = teamDocument ? task.getCreatedByUserId() : project.getUserId();
        if (ownerUserId == null) {
            return List.of();
        }
        String prefix = normalizer.normalize("任务标题: " + task.getTitle());
        String body = StringUtils.hasText(task.getDescription())
                ? normalizer.normalize("任务描述: " + task.getDescription()) : "";
        Map<String, Object> payload = basePayload("TASK", task.getId(), project,
                ownerUserId, visibility);
        put(payload, "status", task.getStatus());
        put(payload, "priority", task.getPriority());
        put(payload, "dueDate", task.getDueDate());
        put(payload, "assigneeUserId", task.getAssigneeUserId());
        put(payload, "sourceUpdatedAt", task.getUpdateTime());
        return List.of(new KnowledgeDocumentProjection(
                key("TASK", task.getId(), visibility, project.getId()),
                KnowledgeSourceTypeEnum.TASK, task.getId(), project.getId(), project.getTeamId(),
                ownerUserId, visibility, prefix, body, payload
        ));
    }

    private List<KnowledgeDocumentProjection> buildWeeklyReview(Long reviewId) {
        WeeklyReview review = weeklyReviewMapper.selectById(reviewId);
        if (review == null || review.getFocusProjectId() == null || review.getUserId() == null) {
            return List.of();
        }
        Project project = activeProject(review.getFocusProjectId());
        if (project == null) {
            return List.of();
        }
        List<KnowledgeDocumentProjection> documents = new ArrayList<>(2);
        if (authorCanViewProject(review.getUserId(), project.getId())) {
            Map<String, Object> payload = basePayload("WEEKLY_REVIEW", review.getId(), project,
                    review.getUserId(), KnowledgeVisibilityTypeEnum.PRIVATE);
            reviewPayload(payload, review);
            documents.add(new KnowledgeDocumentProjection(
                    key("WEEKLY_REVIEW", review.getId(), KnowledgeVisibilityTypeEnum.PRIVATE, project.getId()),
                    KnowledgeSourceTypeEnum.WEEKLY_REVIEW, review.getId(), project.getId(), project.getTeamId(),
                    review.getUserId(), KnowledgeVisibilityTypeEnum.PRIVATE,
                    reviewPrefix(review), privateReviewBody(review), payload
            ));
        }

        if (WeeklyReviewVisibilityScopeEnum.TEAM.getValue().equals(review.getVisibilityScope())
                && project.getTeamId() != null
                && project.getTeamId().equals(review.getTeamId())
                && StringUtils.hasText(review.getSharedSummary())
                && activeTeam(project.getTeamId()) != null) {
            Map<String, Object> payload = basePayload("WEEKLY_REVIEW", review.getId(), project,
                    review.getUserId(), KnowledgeVisibilityTypeEnum.TEAM);
            reviewPayload(payload, review);
            documents.add(new KnowledgeDocumentProjection(
                    key("WEEKLY_REVIEW", review.getId(), KnowledgeVisibilityTypeEnum.TEAM, project.getId()),
                    KnowledgeSourceTypeEnum.WEEKLY_REVIEW, review.getId(), project.getId(), project.getTeamId(),
                    review.getUserId(), KnowledgeVisibilityTypeEnum.TEAM,
                    normalizer.normalize(reviewPrefix(review) + " 共享摘要"),
                    normalizer.normalize(review.getSharedSummary()), payload
            ));
        }
        return List.copyOf(documents);
    }

    private boolean authorCanViewProject(Long userId, Long projectId) {
        User user = userMapper.selectById(userId);
        return user != null && permissionService.resolveProjectScopes(userId, List.of(projectId))
                .containsKey(projectId);
    }

    private Project activeProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .last("limit 1"));
    }

    private Team activeTeam(Long teamId) {
        return teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getId, teamId)
                .eq(Team::getIsDelete, 0)
                .last("limit 1"));
    }

    private Map<String, Object> basePayload(String sourceType,
                                            Long sourceId,
                                            Project project,
                                            Long ownerUserId,
                                            KnowledgeVisibilityTypeEnum visibility) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", sourceType);
        payload.put("sourceId", sourceId);
        payload.put("projectId", project.getId());
        put(payload, "teamId", project.getTeamId());
        payload.put("ownerUserId", ownerUserId);
        payload.put("visibilityType", visibility.name());
        return payload;
    }

    private void reviewPayload(Map<String, Object> payload, WeeklyReview review) {
        put(payload, "year", review.getYear());
        put(payload, "weekNo", review.getWeekNo());
        put(payload, "sourceUpdatedAt", review.getUpdateTime());
    }

    private String reviewPrefix(WeeklyReview review) {
        return normalizer.normalize("周复盘: " + review.getYear() + "-W" + review.getWeekNo());
    }

    private String privateReviewBody(WeeklyReview review) {
        List<String> fields = new ArrayList<>();
        if (StringUtils.hasText(review.getReflection())) {
            fields.add("反思: " + review.getReflection());
        }
        if (StringUtils.hasText(review.getNextPlan())) {
            fields.add("下周计划: " + review.getNextPlan());
        }
        return normalizer.normalize(String.join("\n", fields));
    }

    private String key(String sourceType,
                       Long sourceId,
                       KnowledgeVisibilityTypeEnum visibility,
                       Long projectId) {
        return sourceType + ":" + sourceId + ":" + visibility.name() + ":" + projectId;
    }

    private void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value.toString());
        }
    }
}
