package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;

import java.util.Collection;

/** Serializes team-owned writes before lower-level rows are mutated. */
public interface TeamWriteLockService {

    void lockTeam(Long teamId);

    /** Locks the project scope and validates team membership with a current locking read. */
    void lockProjectScope(ProjectAccessScope scope);

    void lockOwningTeam(Long projectId);

    void lockOwningTeams(Collection<Long> projectIds);
}
