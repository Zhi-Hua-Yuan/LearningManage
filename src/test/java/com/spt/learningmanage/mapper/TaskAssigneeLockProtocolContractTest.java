package com.spt.learningmanage.mapper;

import com.spt.learningmanage.service.impl.TaskAssignmentServiceImpl;
import com.spt.learningmanage.service.impl.TaskCreationServiceImpl;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAssigneeLockProtocolContractTest {

    @Test
    void assigneeLookupLocksTeamMemberAndHasNoCountFallback() throws Exception {
        Method lookup = TaskAssigneeQueryMapper.class
                .getMethod("selectActiveTeamAssigneeForUpdate", Long.class, Long.class);
        Select select = lookup.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).toUpperCase();
        assertTrue(sql.contains("FROM TEAM_MEMBER"));
        assertTrue(sql.contains("FOR UPDATE"));
        assertTrue(sql.contains("IS_DELETE = 0"));
        assertTrue(sql.contains("DELETED_AT IS NULL"));
        assertFalse(Arrays.stream(TaskAssigneeQueryMapper.class.getMethods())
                .anyMatch(method -> method.getName().equals("countActiveTeamAssignee")));
    }

    @Test
    void taskMutationEntryPointsRemainTransactional() throws Exception {
        assertRollbackOnCheckedException(TaskCreationServiceImpl.class, "createTask");
        assertRollbackOnCheckedException(TaskAssignmentServiceImpl.class, "assign");
    }

    private void assertRollbackOnCheckedException(Class<?> type, String methodName) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, type.getSimpleName() + " must be transactional");
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
    }
}
