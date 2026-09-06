package com.spt.learningmanage.service;

public interface BusinessDataVersionService {
    long projectVersion(Long projectId);

    long teamVersion(Long teamId);

    void incrementProject(Long projectId);

    void incrementTeam(Long teamId);

    void incrementProjectAndOwningTeam(Long projectId);
}
