package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.config.AgentProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class BusinessDataVersionServiceImpl implements BusinessDataVersionService {
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final AgentProperties agentProperties;

    public BusinessDataVersionServiceImpl(ProjectMapper projectMapper,
                                          TeamMapper teamMapper,
                                          AgentProperties agentProperties) {
        this.projectMapper = projectMapper;
        this.teamMapper = teamMapper;
        this.agentProperties = agentProperties;
    }

    @Override
    public long projectVersion(Long projectId) {
        Long value = projectMapper.selectDataVersion(projectId);
        if (value == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return value;
    }

    @Override
    public long teamVersion(Long teamId) {
        Long value = teamMapper.selectDataVersion(teamId);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在");
        }
        return value;
    }

    @Override
    public void incrementProject(Long projectId) {
        try {
            if (projectMapper.incrementDataVersion(projectId) != 1) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
        } catch (DataAccessException exception) {
            toleratePreV7Schema(exception);
        }
    }

    @Override
    public void incrementTeam(Long teamId) {
        try {
            if (teamMapper.incrementDataVersion(teamId) != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在");
            }
        } catch (DataAccessException exception) {
            toleratePreV7Schema(exception);
        }
    }

    @Override
    public void incrementProjectAndOwningTeam(Long projectId) {
        try {
            Project project = projectMapper.selectById(projectId);
            incrementProject(projectId);
            if (project != null && project.getTeamId() != null) {
                incrementTeam(project.getTeamId());
            }
        } catch (DataAccessException exception) {
            toleratePreV7Schema(exception);
        }
    }

    private void toleratePreV7Schema(DataAccessException exception) {
        if (agentProperties.isEnabled()) {
            throw exception;
        }
        // Compatibility for test/deployment nodes where V7 is intentionally not active yet.
    }
}
