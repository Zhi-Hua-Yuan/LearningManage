package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.task.TaskAssignmentHistoryQueryRequest;
import com.spt.learningmanage.model.query.task.TaskAssignmentHistoryRow;
import com.spt.learningmanage.model.vo.task.TaskAssignmentHistoryVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentHistoryServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private TaskAssigneePolicy taskAssigneePolicy;

    @InjectMocks
    private TaskAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        UserHolder.set(9L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void listHistoryUsesDefaultPageAndMapsAllPublicFields() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, null, null);
        Page<TaskAssignmentHistoryRow> source = page(1, 50, 1,
                row(101L, 62001L, "REASSIGN", 11L, "alice", 12L, "bob",
                        9L, "owner", "handoff"));
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(source);

        Page<TaskAssignmentHistoryVO> result = service.listAssignmentHistory(request);

        verify(permissionService).requireTaskAssignmentHistoryView(9L, 62001L);
        verify(taskAssignmentLogMapper).selectAssignmentHistoryPage(
                any(), eq(62001L));
        assertEquals(1L, result.getCurrent());
        assertEquals(50L, result.getSize());
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        TaskAssignmentHistoryVO vo = result.getRecords().get(0);
        assertEquals(101L, vo.getId());
        assertEquals(62001L, vo.getTaskId());
        assertEquals("REASSIGN", vo.getAction());
        assertEquals("handoff", vo.getReason());
        assertEquals("bob", vo.getToAssignee().getUsername());
        assertEquals("owner", vo.getAssignedBy().getUsername());
    }

    @Test
    void explicitPageValuesArePassedToMapperAndRetained() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 3L, 2L);
        Page<TaskAssignmentHistoryRow> source = page(3, 2, 5,
                row(100L, 62001L, "ASSIGN", null, null, 12L, "bob",
                        9L, "owner", null));
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(source);

        Page<TaskAssignmentHistoryVO> result = service.listAssignmentHistory(request);

        verify(taskAssignmentLogMapper).selectAssignmentHistoryPage(
                org.mockito.ArgumentMatchers.argThat(page ->
                        page.getCurrent() == 3L && page.getSize() == 2L),
                eq(62001L));
        assertEquals(3L, result.getCurrent());
        assertEquals(2L, result.getSize());
        assertEquals(5L, result.getTotal());
        assertEquals(3L, result.getPages());
    }

    @Test
    void nullAssigneeAndDeletedUsersKeepFrozenSemantics() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        TaskAssignmentHistoryRow row = row(102L, 62001L, "ASSIGN",
                null, null, 77L, null, 88L, null, null);
        Page<TaskAssignmentHistoryRow> source = page(1, 50, 1, row);
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(source);

        TaskAssignmentHistoryVO vo = service.listAssignmentHistory(request)
                .getRecords().get(0);

        assertNull(vo.getFromAssignee());
        assertEquals(77L, vo.getToAssignee().getUserId());
        assertNull(vo.getToAssignee().getUsername());
        assertEquals(88L, vo.getAssignedBy().getUserId());
        assertNull(vo.getAssignedBy().getUsername());
        assertNull(vo.getReason());
    }

    @Test
    void permissionIsCheckedBeforeMapper() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        BusinessException denied = new BusinessException(
                com.spt.learningmanage.exception.ErrorCode.FORBIDDEN_ERROR,
                "禁止访问");
        org.mockito.Mockito.doThrow(denied)
                .when(permissionService).requireTaskAssignmentHistoryView(9L, 62001L);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertSame(denied, actual);
        verify(taskAssignmentLogMapper, never())
                .selectAssignmentHistoryPage(any(), anyLong());
    }

    @Test
    void invalidRequestDoesNotCallPermissionOrMapper() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 101L);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(40000, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void nullRequestIsRejected() {
        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(null));

        assertEquals(40000, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void invalidTaskIdIsRejected() {
        TaskAssignmentHistoryQueryRequest request = request(0L, 1L, 50L);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(40000, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void nullCurrentIsRejected() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        request.setCurrent(null);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(40000, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void nullSizeIsRejected() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        request.setSize(null);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(40000, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void missingLoginDoesNotCallAnything() {
        UserHolder.remove();

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request(62001L, 1L, 50L)));

        assertEquals(40100, actual.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskAssignmentLogMapper);
    }

    @Test
    void unknownActionFailsClosed() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        Page<TaskAssignmentHistoryRow> source = page(1, 50, 1,
                row(101L, 62001L, "UNKNOWN", 11L, "alice", 12L, "bob",
                        9L, "owner", "bad"));
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(source);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(50000, actual.getErrorCode().getCode());
    }

    @Test
    void nullMapperResultFailsClosed() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(null);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(50000, actual.getErrorCode().getCode());
    }

    @Test
    void emptyPageReturnsEmptyRecordsAndMetadata() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 2L, 2L);
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(page(2, 2, 3));

        Page<TaskAssignmentHistoryVO> result = service.listAssignmentHistory(request);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(3L, result.getTotal());
        assertEquals(2L, result.getPages());
    }

    @Test
    void rowForAnotherTaskFailsClosed() {
        TaskAssignmentHistoryQueryRequest request = request(62001L, 1L, 50L);
        when(taskAssignmentLogMapper.selectAssignmentHistoryPage(any(), eq(62001L)))
                .thenReturn(page(1, 50, 1,
                        row(101L, 62002L, "ASSIGN", null, null, 12L, "bob",
                                9L, "owner", null)));

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.listAssignmentHistory(request));

        assertEquals(50000, actual.getErrorCode().getCode());
    }

    private TaskAssignmentHistoryQueryRequest request(Long taskId, Long current, Long size) {
        TaskAssignmentHistoryQueryRequest request = new TaskAssignmentHistoryQueryRequest();
        request.setTaskId(taskId);
        if (current != null) {
            request.setCurrent(current);
        }
        if (size != null) {
            request.setSize(size);
        }
        return request;
    }

    private Page<TaskAssignmentHistoryRow> page(long current, long size, long total,
                                                 TaskAssignmentHistoryRow... rows) {
        Page<TaskAssignmentHistoryRow> page = new Page<>(current, size, total);
        page.setRecords(List.of(rows));
        return page;
    }

    private TaskAssignmentHistoryRow row(Long id, Long taskId, String action,
                                         Long fromId, String fromName,
                                         Long toId, String toName,
                                         Long actorId, String actorName,
                                         String reason) {
        TaskAssignmentHistoryRow row = new TaskAssignmentHistoryRow();
        row.setId(id);
        row.setTaskId(taskId);
        row.setAction(action);
        row.setFromAssigneeUserId(fromId);
        row.setFromAssigneeUsername(fromName);
        row.setToAssigneeUserId(toId);
        row.setToAssigneeUsername(toName);
        row.setAssignedByUserId(actorId);
        row.setAssignedByUsername(actorName);
        row.setReason(reason);
        row.setCreateTime(LocalDateTime.of(2026, 8, 29, 10, 30));
        return row;
    }

}
