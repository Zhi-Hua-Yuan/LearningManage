package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.ProjectQueryRequest;
import com.spt.learningmanage.model.dto.project.ProjectReorderRequest;
import com.spt.learningmanage.model.dto.project.ProjectUpdateRequest;
import com.spt.learningmanage.model.dto.project.TeamProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.TeamProjectQueryRequest;
import com.spt.learningmanage.model.vo.project.ProjectVo;

import java.util.List;

public interface ProjectService {
    /**
     * 创建项目，返回项目ID。
     */
    Long create(ProjectCreateRequest projectCreateRequest);

    /**
     * 创建团队项目，返回项目ID。
     */
    Long createTeamProject(TeamProjectCreateRequest teamProjectCreateRequest);

    /**
     * 根据ID查询项目详情。
     */
    ProjectVo getById(Long id);

    /**
     * 分页查询项目列表。
     */
    Page<ProjectVo> list(ProjectQueryRequest projectQueryRequest);

    /**
     * 按团队范围分页查询团队项目列表。
     */
    Page<ProjectVo> listTeamProjects(TeamProjectQueryRequest teamProjectQueryRequest);

    /**
     * 更新项目信息。
     */
    void update(ProjectUpdateRequest projectUpdateRequest);

    /**
     * 调整项目排序。
     */
    void reorder(List<ProjectReorderRequest> reorderRequests);

    /**
     * 归档项目。
     */
    void archive(List<Long> ids);

    /**
     * 删除项目。
     */
    void delete(Long id);

    /**
     * 恢复项目。
     */
    void recover(Long id);
}
