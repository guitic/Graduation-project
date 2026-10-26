package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.graduation.mentor.common.AuditItemMapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.AuditService;
import com.graduation.mentor.service.CommentService;
import com.graduation.mentor.service.OperLogService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据范围权限回归测试。
 *
 * <p><b>为什么这个类存在</b>：{@code GET /api/audit/{id}/history} 曾经只校验登录、 不校验数据范围，任意学生可用自己 token 遍历 id
 * 读到任意点评的审核意见与修改前正文。 修复后如果没有断言，任何人下次重构都可能再次把 {@code assertCanView} 的调用删掉，
 * 而代码照样能编译、页面照样能点——这类缺陷只有测试能守住。
 *
 * <p>覆盖矩阵：{学生, 导师, 管理员} × {本人/他人} × {已发布/未发布}
 */
class AuditPermissionTest {

    private static final Long STUDENT_SELF = 7L;
    private static final Long STUDENT_OTHER = 8L;
    private static final Long TEACHER_SELF = 2L;
    private static final Long TEACHER_OTHER = 1L;

    private CommentMapper commentMapper;
    private CommentAuditMapper auditMapper;
    private CommentSentimentMapper sentimentMapper;
    private StudentMapper studentMapper;
    private TeacherMapper teacherMapper;
    private ClazzMapper clazzMapper;
    private MajorMapper majorMapper;
    private TeacherStudentMapper teacherStudentMapper;
    private AuthContext authContext;
    private OperLogService operLogService;
    private AnalysisService analysisService;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        auditMapper = mock(CommentAuditMapper.class);
        sentimentMapper = mock(CommentSentimentMapper.class);
        studentMapper = mock(StudentMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        clazzMapper = mock(ClazzMapper.class);
        majorMapper = mock(MajorMapper.class);
        teacherStudentMapper = mock(TeacherStudentMapper.class);
        authContext = mock(AuthContext.class);
        operLogService = mock(OperLogService.class);
        analysisService = mock(AnalysisService.class);

        commentService =
                new CommentService(
                        commentMapper,
                        auditMapper,
                        sentimentMapper,
                        studentMapper,
                        teacherMapper,
                        clazzMapper,
                        majorMapper,
                        teacherStudentMapper,
                        authContext,
                        operLogService,
                        analysisService);
    }

    // ------------------------------------------------------------------ 学生

    @Test
    @DisplayName("学生访问他人点评 → 403")
    void student_cannot_view_others_comment() {
        givenStudent(STUDENT_SELF);
        Comment c = comment(STUDENT_OTHER, CommentStatus.APPROVED, LocalDateTime.now());

        BizException e = assertThrows(BizException.class, () -> commentService.assertCanView(c));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("学生访问本人未发布点评 → 403")
    void student_cannot_view_own_unpublished_comment() {
        givenStudent(STUDENT_SELF);
        Comment c = comment(STUDENT_SELF, CommentStatus.APPROVED, null);

        BizException e = assertThrows(BizException.class, () -> commentService.assertCanView(c));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("学生访问本人已发布点评 → 放行")
    void student_can_view_own_published_comment() {
        givenStudent(STUDENT_SELF);
        Comment c = comment(STUDENT_SELF, CommentStatus.APPROVED, LocalDateTime.now());

        assertDoesNotThrow(() -> commentService.assertCanView(c));
    }

    // ------------------------------------------------------------------ 导师

    @Test
    @DisplayName("导师访问他人点评 → 403")
    void teacher_cannot_view_others_comment() {
        when(authContext.isAdmin()).thenReturn(false);
        when(authContext.isTeacher()).thenReturn(true);
        when(authContext.currentTeacherId()).thenReturn(TEACHER_SELF);
        Comment c = comment(STUDENT_SELF, CommentStatus.APPROVED, LocalDateTime.now());
        c.setTeacherId(TEACHER_OTHER);

        BizException e = assertThrows(BizException.class, () -> commentService.assertCanView(c));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("导师访问本人点评（含草稿）→ 放行")
    void teacher_can_view_own_draft() {
        when(authContext.isAdmin()).thenReturn(false);
        when(authContext.isTeacher()).thenReturn(true);
        when(authContext.currentTeacherId()).thenReturn(TEACHER_SELF);
        Comment c = comment(STUDENT_SELF, CommentStatus.DRAFT, null);
        c.setTeacherId(TEACHER_SELF);

        assertDoesNotThrow(() -> commentService.assertCanView(c));
    }

    // ------------------------------------------------------------------ 管理员

    @Test
    @DisplayName("管理员访问任意点评 → 放行")
    void admin_can_view_any_comment() {
        when(authContext.isAdmin()).thenReturn(true);
        Comment c = comment(STUDENT_OTHER, CommentStatus.PENDING, null);

        assertDoesNotThrow(() -> commentService.assertCanView(c));
    }

    // ------------------------------------------------ 回归守卫：history 接口

    @Test
    @DisplayName("回归守卫：/api/audit/{id}/history 必须经过数据范围校验")
    void history_endpoint_must_check_scope() {
        Comment c = comment(STUDENT_OTHER, CommentStatus.APPROVED, LocalDateTime.now());
        when(commentMapper.selectById(9L)).thenReturn(c);
        givenStudent(STUDENT_SELF);

        AuditService auditService =
                new AuditService(
                        commentMapper,
                        auditMapper,
                        commentService,
                        analysisService,
                        authContext,
                        operLogService);

        BizException e = assertThrows(BizException.class, () -> auditService.audits(9L));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("回归守卫：audits() 必须调用 assertCanView，不得另写一套判定")
    void audits_must_delegate_to_assertCanView() {
        CommentService spyService = mock(CommentService.class);
        Comment c = comment(STUDENT_SELF, CommentStatus.APPROVED, LocalDateTime.now());
        when(commentMapper.selectById(9L)).thenReturn(c);
        when(auditMapper.selectList(any())).thenReturn(List.of());
        when(authContext.isStudent()).thenReturn(true);

        AuditService auditService =
                new AuditService(
                        commentMapper,
                        auditMapper,
                        spyService,
                        analysisService,
                        authContext,
                        operLogService);
        auditService.audits(9L);

        verify(spyService).assertCanView(c);
    }

    // ------------------------------------------------------------------ 字段裁剪

    @Test
    @DisplayName("时间线映射：学生看不到审核意见与修改前正文")
    void mapper_strips_internal_fields_for_student() {
        CommentDtos.AuditItem it = AuditItemMapper.of(audit(), true);

        assertNull(it.getOpinion());
        assertNull(it.getBeforeContent());
        // actionType 必须下发，否则前端时间线无法区分"通过并发布/仅通过/撤回"
        assertEquals("RETURN", it.getActionType());
        assertEquals("退回修改", it.getResultLabel());
    }

    @Test
    @DisplayName("时间线映射：教务与导师可见完整审核意见")
    void mapper_keeps_internal_fields_for_staff() {
        CommentDtos.AuditItem it = AuditItemMapper.of(audit(), false);

        assertEquals("内容与学期不匹配，请补充具体建议", it.getOpinion());
        assertEquals("修改前原文", it.getBeforeContent());
    }

    // ------------------------------------------------------------------ 夹具

    private void givenStudent(Long selfStudentId) {
        when(authContext.isAdmin()).thenReturn(false);
        when(authContext.isTeacher()).thenReturn(false);
        when(authContext.isStudent()).thenReturn(true);
        when(studentMapper.selectOne(any())).thenReturn(student(selfStudentId));
    }

    private Comment comment(Long studentId, String status, LocalDateTime publishedAt) {
        Comment c = new Comment();
        c.setId(9L);
        c.setTeacherId(TEACHER_SELF);
        c.setStudentId(studentId);
        c.setStatus(status);
        c.setPublishedAt(publishedAt);
        return c;
    }

    private Student student(Long id) {
        Student s = new Student();
        s.setId(id);
        s.setName("测试学生");
        return s;
    }

    private CommentAudit audit() {
        CommentAudit a = new CommentAudit();
        a.setId(1L);
        a.setActionType("RETURN");
        a.setResult("RETURNED");
        a.setOpinion("内容与学期不匹配，请补充具体建议");
        a.setBeforeContent("修改前原文");
        a.setAuditorName("王主任");
        a.setRound(1);
        return a;
    }
}
