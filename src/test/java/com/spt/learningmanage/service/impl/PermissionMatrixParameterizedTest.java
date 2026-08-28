package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.SystemRoleEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.PermissionQueryMapper;
import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * S1-A-005 的冻结权限矩阵验收测试。
 *
 * <p>每一个矩阵单元都是一个参数化用例。拒绝用例只接受统一的
 * PermissionDeniedException；任何越权调用成功都会增加计数并使本类的
 * {@link #unauthorizedAllowedCount} 断言失败。</p>
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionMatrixParameterizedTest {

    private static final long PROJECT_ID = 100L;
    private static final long TASK_ID = 500L;
    private static final long TEAM_ID = 200L;
    private static final long REVIEW_ID = 900L;
    private static final long TEAM_OWNER_ID = 10L;
    private static final long AUTHOR_ID = 20L;
    private static final AtomicInteger unauthorizedAllowedCount = new AtomicInteger();

    @Mock
    private PermissionQueryMapper permissionQueryMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @BeforeEach
    void activeUserByDefault() {
        lenient().when(permissionQueryMapper.selectActorPermissionRow(any()))
                .thenAnswer(invocation -> actor(invocation.getArgument(0), SystemRoleEnum.USER));
    }

    @AfterAll
    void permissionMatrixHasNoUnauthorizedAllowances() {
        assertEquals(0, unauthorizedAllowedCount.get(), "unauthorizedAllowedCount");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("projectMatrix")
    void projectMatrix(String caseId, Subject subject, ProjectAction action, boolean expectedAllowed) {
        stubActor(subject);
        when(permissionQueryMapper.selectProjectPermissionRows(subject.actorId(), List.of(PROJECT_ID)))
                .thenReturn(List.of(project(subject)));

        assertOutcome(caseId, expectedAllowed, () -> action.invoke(permissionService, subject.actorId()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("taskMatrix")
    void taskMatrix(String caseId, Subject subject, TaskAction action, boolean expectedAllowed) {
        stubActor(subject);
        when(permissionQueryMapper.selectTaskPermissionRows(subject.actorId(), List.of(TASK_ID)))
                .thenReturn(List.of(task(subject)));

        assertOutcome(caseId, expectedAllowed, () -> action.invoke(permissionService, subject.actorId()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("teamMatrix")
    void teamMatrix(String caseId, TeamSubject subject, TeamAction action, boolean expectedAllowed) {
        stubActor(subject.subject());
        when(permissionQueryMapper.selectTeamMemberPermissionRow(
                subject.subject().actorId(), TEAM_ID, subject.targetId()))
                .thenReturn(teamMember(subject));

        assertOutcome(caseId, expectedAllowed,
                () -> action.invoke(permissionService, subject.subject().actorId(), subject.targetId()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reviewMatrix")
    void reviewMatrix(String caseId, ReviewSubject subject, ReviewAction action, boolean expectedAllowed) {
        stubActor(subject.subject());
        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(subject.subject().actorId(), List.of(REVIEW_ID)))
                .thenReturn(List.of(review(subject)));

        assertOutcome(caseId, expectedAllowed, () -> action.invoke(permissionService, subject.subject().actorId()));
    }

    private void assertOutcome(String caseId, boolean expectedAllowed, Runnable call) {
        if (expectedAllowed) {
            assertDoesNotThrow(call::run, caseId);
            return;
        }

        try {
            assertThrows(PermissionDeniedException.class, call::run, caseId);
        } catch (AssertionError error) {
            unauthorizedAllowedCount.incrementAndGet();
            throw error;
        }
    }

    private void stubActor(Subject subject) {
        when(permissionQueryMapper.selectActorPermissionRow(subject.actorId()))
                .thenReturn(actor(subject.actorId(), subject.systemRole()));
    }

    private static Stream<Arguments> projectMatrix() {
        List<Arguments> cases = new ArrayList<>();
        List<Subject> subjects = projectSubjects();
        for (ProjectAction action : ProjectAction.values()) {
            for (Subject subject : subjects) {
                boolean allowed = switch (action) {
                    case PROJECT_VIEW -> subject.personalOwner() || subject.teamRole() != null;
                    case TASK_CREATE, PROJECT_CREATE_TASK, PROJECT_UPDATE, PROJECT_ARCHIVE, PROJECT_DELETE ->
                            subject.personalOwner() || subject.teamRole() == TeamRoleEnum.OWNER
                                    || subject.teamRole() == TeamRoleEnum.ADMIN;
                    case PROJECT_MEMBER_LIST -> !subject.personal() && subject.teamRole() != null;
                };
                cases.add(Arguments.of(action.name() + "/" + subject.label(), subject, action, allowed));
            }
        }
        assertEquals(49, cases.size(), "project matrix case count");
        return cases.stream();
    }

    private static Stream<Arguments> taskMatrix() {
        List<Arguments> cases = new ArrayList<>();
        for (TaskAction action : TaskAction.values()) {
            for (Subject subject : taskSubjects()) {
                boolean allowed = switch (action) {
                    case TASK_VIEW -> subject.personalOwner() || subject.teamRole() != null;
                    case TASK_EDIT_CONTENT, TASK_CHANGE_STATUS -> subject.personalOwner()
                            || subject.teamRole() == TeamRoleEnum.OWNER
                            || subject.teamRole() == TeamRoleEnum.ADMIN
                            || subject.assignee();
                    case TASK_REORGANIZE, TASK_ASSIGN, TASK_DELETE -> subject.personalOwner()
                            || subject.teamRole() == TeamRoleEnum.OWNER
                            || subject.teamRole() == TeamRoleEnum.ADMIN;
                    case TASK_ASSIGNMENT_HISTORY_VIEW -> subject.personalOwner() || subject.teamRole() != null;
                };
                cases.add(Arguments.of(action.name() + "/" + subject.label(), subject, action, allowed));
            }
        }
        assertEquals(56, cases.size(), "task matrix case count");
        return cases.stream();
    }

    private static Stream<Arguments> teamMatrix() {
        List<Arguments> cases = new ArrayList<>();
        for (TeamAction action : TeamAction.values()) {
            if (action == TeamAction.TEAM_MEMBER_ROLE_UPDATE || action == TeamAction.TEAM_MEMBER_REMOVE) {
                for (TeamRoleEnum actorRole : TeamRoleEnum.values()) {
                    for (TeamRoleEnum targetRole : TeamRoleEnum.values()) {
                        long actorId = actorRole == TeamRoleEnum.OWNER ? TEAM_OWNER_ID : AUTHOR_ID;
                        long targetId = targetRole == TeamRoleEnum.OWNER ? TEAM_OWNER_ID
                                : targetRole == TeamRoleEnum.ADMIN ? 40L : 30L;
                        TeamSubject subject = new TeamSubject(
                                new Subject("actor-" + actorRole, actorId, SystemRoleEnum.USER,
                                        false, actorRole, false),
                                targetId, targetRole);
                        boolean allowed = action == TeamAction.TEAM_MEMBER_ROLE_UPDATE
                                ? actorRole == TeamRoleEnum.OWNER && targetRole != TeamRoleEnum.OWNER
                                : (actorRole == TeamRoleEnum.OWNER
                                && targetRole != TeamRoleEnum.OWNER)
                                || (actorRole == TeamRoleEnum.ADMIN && targetRole == TeamRoleEnum.MEMBER);
                        cases.add(Arguments.of(action.name() + "/" + actorRole + "->" + targetRole,
                                subject, action, allowed));
                    }
                }
            } else {
                for (Subject subject : teamSubjects()) {
                    boolean allowed = switch (action) {
                        case TEAM_VIEW, TEAM_MEMBER_LIST -> subject.teamRole() != null;
                        case TEAM_MANAGE_PROJECT -> subject.teamRole() == TeamRoleEnum.OWNER
                                || subject.teamRole() == TeamRoleEnum.ADMIN;
                        case TEAM_LEAVE -> subject.teamRole() == TeamRoleEnum.ADMIN
                                || subject.teamRole() == TeamRoleEnum.MEMBER;
                        default -> false;
                    };
                    cases.add(Arguments.of(action.name() + "/" + subject.label(),
                            new TeamSubject(subject, subject.actorId(), subject.teamRole()), action, allowed));
                }
            }
        }
        assertEquals(38, cases.size(), "team matrix case count");
        return cases.stream();
    }

    private static Stream<Arguments> reviewMatrix() {
        List<Arguments> cases = new ArrayList<>();
        for (ReviewAction action : ReviewAction.values()) {
            for (ReviewSubject subject : reviewSubjects(action)) {
                boolean allowed = switch (action) {
                    case REVIEW_FULL_VIEW, REVIEW_UPDATE, REVIEW_DELETE, PRIVATE_REVIEW_DISCOVER -> subject.author();
                    case TEAM_SUMMARY_VIEW -> subject.author() || subject.teamRole() != null;
                };
                cases.add(Arguments.of(action.name() + "/" + subject.label(), subject, action, allowed));
            }
        }
        assertEquals(30, cases.size(), "review matrix case count");
        return cases.stream();
    }

    private static List<Subject> projectSubjects() {
        return List.of(
                new Subject("personal-owner", AUTHOR_ID, SystemRoleEnum.USER, true, null, true),
                new Subject("personal-outsider", 30L, SystemRoleEnum.USER, true, null, false),
                new Subject("team-owner", TEAM_OWNER_ID, SystemRoleEnum.USER, false, TeamRoleEnum.OWNER, false),
                new Subject("team-admin", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.ADMIN, false),
                new Subject("team-member", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.MEMBER, false),
                new Subject("team-outsider", 30L, SystemRoleEnum.USER, false, null, false),
                new Subject("system-admin", 99L, SystemRoleEnum.SYSTEM_ADMIN, false, null, false)
        );
    }

    private static List<Subject> taskSubjects() {
        return List.of(
                new Subject("personal-owner", AUTHOR_ID, SystemRoleEnum.USER, true, null, true),
                new Subject("personal-outsider", 30L, SystemRoleEnum.USER, true, null, false),
                new Subject("team-owner", TEAM_OWNER_ID, SystemRoleEnum.USER, false, TeamRoleEnum.OWNER, false),
                new Subject("team-admin", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.ADMIN, false),
                new Subject("member-assignee", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.MEMBER, true),
                new Subject("member-non-assignee", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.MEMBER, false),
                new Subject("team-outsider", 30L, SystemRoleEnum.USER, false, null, false),
                new Subject("system-admin", 99L, SystemRoleEnum.SYSTEM_ADMIN, false, null, false)
        );
    }

    private static List<Subject> teamSubjects() {
        return List.of(
                new Subject("owner", TEAM_OWNER_ID, SystemRoleEnum.USER, false, TeamRoleEnum.OWNER, false),
                new Subject("admin", AUTHOR_ID, SystemRoleEnum.USER, false, TeamRoleEnum.ADMIN, false),
                new Subject("member", 30L, SystemRoleEnum.USER, false, TeamRoleEnum.MEMBER, false),
                new Subject("outsider", 40L, SystemRoleEnum.USER, false, null, false),
                new Subject("system-admin", 99L, SystemRoleEnum.SYSTEM_ADMIN, false, null, false)
        );
    }

    private static List<ReviewSubject> reviewSubjects(ReviewAction action) {
        boolean privateReview = action == ReviewAction.PRIVATE_REVIEW_DISCOVER;
        String scope = privateReview ? "PRIVATE" : "TEAM";
        Long teamId = privateReview ? null : TEAM_ID;
        return List.of(
                new ReviewSubject("author", new Subject("author", AUTHOR_ID, SystemRoleEnum.USER,
                        false, null, false), true, null, scope, teamId),
                new ReviewSubject("team-owner", new Subject("team-owner", TEAM_OWNER_ID, SystemRoleEnum.USER,
                        false, TeamRoleEnum.OWNER, false), false, TeamRoleEnum.OWNER, scope, teamId),
                new ReviewSubject("team-admin", new Subject("team-admin", 40L, SystemRoleEnum.USER,
                        false, TeamRoleEnum.ADMIN, false), false, TeamRoleEnum.ADMIN, scope, teamId),
                new ReviewSubject("team-member", new Subject("team-member", 30L, SystemRoleEnum.USER,
                        false, TeamRoleEnum.MEMBER, false), false, TeamRoleEnum.MEMBER, scope, teamId),
                new ReviewSubject("other-user", new Subject("other-user", 50L, SystemRoleEnum.USER,
                        false, null, false), false, null, scope, teamId),
                new ReviewSubject("system-admin", new Subject("system-admin", 99L, SystemRoleEnum.SYSTEM_ADMIN,
                        false, null, false), false, null, scope, teamId)
        );
    }

    private static ProjectPermissionRow project(Subject subject) {
        ProjectPermissionRow row = new ProjectPermissionRow();
        row.setProjectId(PROJECT_ID);
        row.setProjectOwnerUserId(subject.personalOwner() ? subject.actorId() : TEAM_OWNER_ID);
        row.setProjectIsDelete(0);
        if (!subject.personal()) {
            row.setTeamId(TEAM_ID);
            row.setTeamOwnerUserId(TEAM_OWNER_ID);
            row.setTeamIsDelete(0);
            if (subject.teamRole() != null) {
                row.setActorTeamMemberId(1L);
                row.setActorTeamRole(subject.teamRole().getValue());
                row.setActorMembershipIsDelete(0);
            }
        }
        return row;
    }

    private static TaskPermissionRow task(Subject subject) {
        TaskPermissionRow row = new TaskPermissionRow();
        row.setTaskId(TASK_ID);
        row.setTaskCreatorUserId(TEAM_OWNER_ID);
        row.setAssigneeUserId(subject.assignee() ? subject.actorId() : 50L);
        row.setProjectId(PROJECT_ID);
        row.setTaskStatus(0);
        row.setTaskIsDelete(0);
        row.setProjectOwnerUserId(subject.personalOwner() ? subject.actorId() : TEAM_OWNER_ID);
        row.setProjectIsDelete(0);
        if (!subject.personal()) {
            row.setTeamId(TEAM_ID);
            row.setTeamOwnerUserId(TEAM_OWNER_ID);
            row.setTeamIsDelete(0);
            if (subject.teamRole() != null) {
                row.setActorTeamMemberId(1L);
                row.setActorTeamRole(subject.teamRole().getValue());
                row.setActorMembershipIsDelete(0);
            }
        }
        return row;
    }

    private static TeamMemberPermissionRow teamMember(TeamSubject subject) {
        TeamMemberPermissionRow row = new TeamMemberPermissionRow();
        row.setTeamId(TEAM_ID);
        row.setTeamOwnerUserId(TEAM_OWNER_ID);
        row.setTeamIsDelete(0);
        Subject actor = subject.subject();
        row.setActorUserId(actor.actorId());
        if (actor.teamRole() != null) {
            row.setActorTeamMemberId(1L);
            row.setActorTeamRole(actor.teamRole().getValue());
            row.setActorMembershipIsDelete(0);
        }
        row.setTargetUserId(subject.targetId());
        if (subject.targetRole() != null) {
            row.setTargetTeamMemberId(2L);
            row.setTargetTeamRole(subject.targetRole().getValue());
            row.setTargetMembershipIsDelete(0);
        }
        return row;
    }

    private static WeeklyReviewPermissionRow review(ReviewSubject subject) {
        WeeklyReviewPermissionRow row = new WeeklyReviewPermissionRow();
        row.setReviewId(REVIEW_ID);
        row.setAuthorUserId(AUTHOR_ID);
        row.setVisibilityScope(subject.scope());
        row.setTeamId(subject.teamId());
        if (subject.teamId() != null) {
            row.setTeamOwnerUserId(TEAM_OWNER_ID);
            row.setTeamIsDelete(0);
            if (subject.teamRole() != null) {
                row.setActorTeamMemberId(1L);
                row.setActorTeamRole(subject.teamRole().getValue());
                row.setActorMembershipIsDelete(0);
            }
        }
        return row;
    }

    private static ActorPermissionRow actor(Long actorId, SystemRoleEnum systemRole) {
        ActorPermissionRow row = new ActorPermissionRow();
        row.setActorUserId(actorId);
        row.setActorSystemRole(systemRole.getValue());
        row.setActorIsDelete(0);
        return row;
    }

    private record Subject(
            String label,
            long actorId,
            SystemRoleEnum systemRole,
            boolean personal,
            TeamRoleEnum teamRole,
            boolean assignee
    ) {
        private boolean personalOwner() {
            return personal && assignee;
        }
    }

    private record TeamSubject(Subject subject, long targetId, TeamRoleEnum targetRole) {
    }

    private record ReviewSubject(
            String label,
            Subject subject,
            boolean author,
            TeamRoleEnum teamRole,
            String scope,
            Long teamId
    ) {
    }

    private enum ProjectAction {
        PROJECT_VIEW((service, actorId) -> service.requireProjectView(actorId, PROJECT_ID)),
        TASK_CREATE((service, actorId) -> service.requireProjectCreateTask(actorId, PROJECT_ID)),
        PROJECT_CREATE_TASK((service, actorId) -> service.requireProjectCreateTask(actorId, PROJECT_ID)),
        PROJECT_UPDATE((service, actorId) -> service.requireProjectManage(actorId, PROJECT_ID)),
        PROJECT_ARCHIVE((service, actorId) -> service.requireProjectManage(actorId, PROJECT_ID)),
        PROJECT_DELETE((service, actorId) -> service.requireProjectManage(actorId, PROJECT_ID)),
        PROJECT_MEMBER_LIST((service, actorId) -> service.requireProjectMemberList(actorId, PROJECT_ID));

        private final BiPermissionCall call;

        ProjectAction(BiPermissionCall call) {
            this.call = call;
        }

        private void invoke(PermissionService service, long actorId) {
            call.accept(service, actorId);
        }
    }

    private enum TaskAction {
        TASK_VIEW((service, actorId) -> service.requireTaskView(actorId, TASK_ID)),
        TASK_EDIT_CONTENT((service, actorId) -> service.requireTaskEditContent(actorId, TASK_ID)),
        TASK_CHANGE_STATUS((service, actorId) -> service.requireTaskChangeStatus(actorId, TASK_ID)),
        TASK_REORGANIZE((service, actorId) -> service.requireTaskReorganize(actorId, TASK_ID)),
        TASK_ASSIGN((service, actorId) -> service.requireTaskAssign(actorId, TASK_ID)),
        TASK_DELETE((service, actorId) -> service.requireTaskDelete(actorId, TASK_ID)),
        TASK_ASSIGNMENT_HISTORY_VIEW((service, actorId) -> service.requireTaskAssignmentHistoryView(actorId, TASK_ID));

        private final BiPermissionCall call;

        TaskAction(BiPermissionCall call) {
            this.call = call;
        }

        private void invoke(PermissionService service, long actorId) {
            call.accept(service, actorId);
        }
    }

    private enum TeamAction {
        TEAM_VIEW((service, actorId, targetId) -> service.requireTeamView(actorId, TEAM_ID)),
        TEAM_MANAGE_PROJECT((service, actorId, targetId) -> service.requireTeamManageProject(actorId, TEAM_ID)),
        TEAM_MEMBER_LIST((service, actorId, targetId) -> service.requireTeamMemberList(actorId, TEAM_ID)),
        TEAM_MEMBER_ROLE_UPDATE((service, actorId, targetId) ->
                service.requireTeamMemberRoleUpdate(actorId, TEAM_ID, targetId)),
        TEAM_MEMBER_REMOVE((service, actorId, targetId) ->
                service.requireTeamMemberRemove(actorId, TEAM_ID, targetId)),
        TEAM_LEAVE((service, actorId, targetId) -> service.requireTeamLeave(actorId, TEAM_ID));

        private final TriPermissionCall call;

        TeamAction(TriPermissionCall call) {
            this.call = call;
        }

        private void invoke(PermissionService service, long actorId, long targetId) {
            call.accept(service, actorId, targetId);
        }
    }

    private enum ReviewAction {
        REVIEW_FULL_VIEW((service, actorId) -> service.requireWeeklyReviewFullView(actorId, REVIEW_ID)),
        REVIEW_UPDATE((service, actorId) -> service.requireWeeklyReviewUpdate(actorId, REVIEW_ID)),
        REVIEW_DELETE((service, actorId) -> service.requireWeeklyReviewDelete(actorId, REVIEW_ID)),
        PRIVATE_REVIEW_DISCOVER((service, actorId) -> service.requireWeeklyReviewFullView(actorId, REVIEW_ID)),
        TEAM_SUMMARY_VIEW((service, actorId) -> service.requireWeeklyReviewSharedView(actorId, REVIEW_ID));

        private final BiPermissionCall call;

        ReviewAction(BiPermissionCall call) {
            this.call = call;
        }

        private void invoke(PermissionService service, long actorId) {
            call.accept(service, actorId);
        }
    }

    @FunctionalInterface
    private interface BiPermissionCall {
        void accept(PermissionService service, long actorId);
    }

    @FunctionalInterface
    private interface TriPermissionCall {
        void accept(PermissionService service, long actorId, long targetId);
    }
}
