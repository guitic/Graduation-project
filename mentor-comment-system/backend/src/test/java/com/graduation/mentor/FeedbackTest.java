package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentFeedback;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentFeedbackMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.CommentService;
import com.graduation.mentor.service.OperLogService;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 学生反馈的权限与写入规则。
 *
 * <p><b>为什么这个类存在</b>：反馈的可见范围必须与点评完全一致。若这里另写一套判断， 点评被撤回后就会出现「学生看不到点评、却能给它写反馈」的裂缝——而这条数据仍然会出现在导师端。
 *
 * <p>同时守住两条容易写错的默认值：
 *
 * <ul>
 *   <li>{@code helpful} 为 null 不能被静默当成 false（那会把"没表态"记成"帮助不大"）；
 *   <li>反馈归属必须取自点评本身，不能取请求参数，否则能给别人的点评写反馈。
 * </ul>
 */
class FeedbackTest {

    private static final Long COMMENT_ID = 1L;
    private static final Long STUDENT_ID = 100L;
    private static final Long OTHER_STUDENT_ID = 101L;
    private static final Long USER_ID = 6L;

    private CommentMapper commentMapper;
    private CommentFeedbackMapper feedbackMapper;
    private StudentMapper studentMapper;
    private AuthContext authContext;
    private CommentService service;

    /**
     * MyBatis-Plus 的 Lambda 条件需要实体元数据缓存，这层缓存在 Spring 启动时才建立。 纯单测没有上下文，必须手动初始化，否则 {@code
     * LambdaUpdateWrapper.set(...)} 会抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CommentFeedback.class);
    }

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        feedbackMapper = mock(CommentFeedbackMapper.class);
        studentMapper = mock(StudentMapper.class);
        authContext = mock(AuthContext.class);
        service =
                new CommentService(
                        commentMapper,
                        mock(CommentAuditMapper.class),
                        mock(CommentSentimentMapper.class),
                        feedbackMapper,
                        studentMapper,
                        mock(TeacherMapper.class),
                        mock(ClazzMapper.class),
                        mock(MajorMapper.class),
                        mock(TeacherStudentMapper.class),
                        authContext,
                        mock(OperLogService.class),
                        mock(AnalysisService.class));
    }

    private CommentDtos.FeedbackReq req(Boolean helpful, String content) {
        CommentDtos.FeedbackReq r = new CommentDtos.FeedbackReq();
        r.setHelpful(helpful);
        r.setContent(content);
        return r;
    }

    private Comment comment(Long studentId, String status, LocalDateTime publishedAt) {
        Comment c = new Comment();
        c.setId(COMMENT_ID);
        c.setTeacherId(10L);
        c.setStudentId(studentId);
        c.setStatus(status);
        c.setPublishedAt(publishedAt);
        return c;
    }

    private void asStudent() {
        when(authContext.isStudent()).thenReturn(true);
        when(authContext.userId()).thenReturn(USER_ID);
        Student s = new Student();
        s.setId(STUDENT_ID);
        s.setUserId(USER_ID);
        when(studentMapper.selectOne(any())).thenReturn(s);
    }

    @Test
    @DisplayName("导师提交反馈 → 403（反馈的主体只能是学生）")
    void teacherCannotSubmit() {
        when(authContext.isStudent()).thenReturn(false);

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.saveFeedback(COMMENT_ID, req(true, "自评一下")));

        assertEquals(403, e.getCode());
        assertEquals("只有学生可以提交点评反馈", e.getMessage());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("helpful 为 null → 400，不能静默当成「帮助不大」")
    void nullHelpfulRejected() {
        asStudent();

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.saveFeedback(COMMENT_ID, req(null, "只写了字")));

        assertEquals(400, e.getCode());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("对他人点评反馈 → 403（复用点评的数据范围校验）")
    void cannotFeedbackOthersComment() {
        asStudent();
        when(commentMapper.selectById(COMMENT_ID))
                .thenReturn(comment(OTHER_STUDENT_ID, "APPROVED", LocalDateTime.now()));

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.saveFeedback(COMMENT_ID, req(true, null)));

        assertEquals(403, e.getCode());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("对未发布点评反馈 → 403（撤回后学生看不到点评，也就不该能反馈）")
    void cannotFeedbackUnpublished() {
        asStudent();
        when(commentMapper.selectById(COMMENT_ID))
                .thenReturn(comment(STUDENT_ID, "APPROVED", null));

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.saveFeedback(COMMENT_ID, req(true, null)));

        assertEquals(403, e.getCode());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("首次提交：归属取自点评、内容去空格、helpful 落 1/0")
    void firstSubmitInserts() {
        asStudent();
        when(commentMapper.selectById(COMMENT_ID))
                .thenReturn(comment(STUDENT_ID, "APPROVED", LocalDateTime.now()));
        when(feedbackMapper.selectOne(any())).thenReturn(null);

        service.saveFeedback(COMMENT_ID, req(true, "  想再问问课程设计的要求  "));

        ArgumentCaptor<CommentFeedback> cap = ArgumentCaptor.forClass(CommentFeedback.class);
        verify(feedbackMapper).insert(cap.capture());
        CommentFeedback saved = cap.getValue();
        assertEquals(STUDENT_ID, saved.getStudentId());
        assertEquals(1, saved.getHelpful());
        assertEquals("想再问问课程设计的要求", saved.getContent());
    }

    @Test
    @DisplayName("重复提交走更新；纯空白内容归一成 null 且必须真正写入")
    void secondSubmitUpdates() {
        asStudent();
        when(commentMapper.selectById(COMMENT_ID))
                .thenReturn(comment(STUDENT_ID, "APPROVED", LocalDateTime.now()));
        CommentFeedback exist = new CommentFeedback();
        exist.setId(9L);
        exist.setCommentId(COMMENT_ID);
        exist.setStudentId(STUDENT_ID);
        exist.setHelpful(1);
        exist.setContent("原来写过的话");
        when(feedbackMapper.selectOne(any())).thenReturn(exist);

        service.saveFeedback(COMMENT_ID, req(false, "    "));

        verify(feedbackMapper, never()).insert(any());
        // 必须是显式 set 更新：updateById 会跳过 null，导致"清空补充说明"静默失效
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<CommentFeedback>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(feedbackMapper).update(isNull(), cap.capture());
        String sqlSet = cap.getValue().getSqlSet();
        assertTrue(sqlSet.contains("helpful"), "helpful 必须进 SET 子句");
        assertTrue(sqlSet.contains("content"), "content 必须进 SET 子句，即使值为 null");
    }
}
