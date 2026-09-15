package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;

import java.util.Collection;

/** 在修改底层数据行前，对团队内写操作进行串行化。 */
public interface TeamWriteLockService {

    void lockTeam(Long teamId);

    /** 锁定项目作用域，并通过当前加锁读取校验团队成员关系。 */
    void lockProjectScope(ProjectAccessScope scope);

    void lockOwningTeam(Long projectId);

    void lockOwningTeams(Collection<Long> projectIds);
}
