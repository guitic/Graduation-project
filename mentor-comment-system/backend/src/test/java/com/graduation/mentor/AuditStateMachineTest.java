package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.AuditService;
import com.graduation.mentor.service.CommentService;
import com.graduation.mentor.service.OperLogService;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 状态机回归测试。
 *
 * <p><b>为什么这个类必须存在</b>：本系统的核心设计承诺是「通过 ≠ 发布」「状态迁移必须条件化」 「发布是分析的唯一触发器」。这三条都只体现在 SQL 的 WHERE
 * 条件与影响行数判断里—— 一旦有人把 {@code UPDATE ... WHERE status=?} 改回「先查后改」，或者漏掉 {@code published_at IS NULL}，
 * 代码照样编译、页面照样能点，只有在两个教务同时审核、或误发布后撤回时才暴露。
 *
 * <p>测试直接断言「写进数据库的条件」而不只是返回值：只断言返回值的话，把条件化更新换成 先查后改也能全绿——那正是要被守住的退化。
 */
class AuditStateMachineTest {

    private static final Long CID = 42L;

    /**
     * LambdaUpdateWrapper 在纯单元测试里没有 Spring 容器，MyBatis-Plus 的字段缓存是空的， {@code Comment::getStatus}
     * 无法解析成列名会抛 "can not find lambda cache"。 手动初始化一次 TableInfo 即可——这比为一个纯逻辑测试拉起整个 Spring 上下文便宜得多。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
        TableInfoHelper.initTableInfo(assistant, CommentAudit.class);
    }

    private CommentMapper commentMapper;
    private CommentAuditMapper auditMapper;
    private CommentService commentService;
    private AnalysisService analysisService;
    private AuthContext authContext;
    private OperLogService operLogService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        auditMapper = mock(CommentAuditMapper.class);
        commentService = mock(CommentService.class);
        analysisService = mock(AnalysisService.class);
        authContext = mock(AuthContext.class);
        operLogService = mock(OperLogService.class);

        auditService =
                new AuditService(
                        commentMapper,
                        auditMapper,
                        commentService,
                        analysisService,
                        authContext,
                        operLogService);

        when(authContext.isAdmin()).thenReturn(true);
        when(authContext.userId()).thenReturn(1L);
        User u = new User();
        u.setRealName("王主任");
        when(authContext.currentUser()).thenReturn(u);
        when(commentMapper.update(any(), any())).thenReturn(1);
        // 默认存在一条待审点评；需要其它状态的用例自行覆盖
        when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));
    }

    // ------------------------------------------------------------ 意见字数门槛

    @Nested
    @DisplayName("审核意见字数门槛：越接近不可逆，理由要求越高")
    class OpinionThreshold {

        @Test
        @DisplayName("通过并发布：4 字被拒，5 字放行")
        void approvePublishNeeds5() {
            BizException e =
                    assertThrows(
                            BizException.class, () -> auditService.approve(CID, req("同意发布", true)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("5 字"), e.getMessage());

            assertDoesNotThrow(() -> auditService.approve(CID, req("内容完整同意", true)));
        }

        @Test
        @DisplayName("仅通过：不足 10 字被拒，10 字放行")
        void approveOnlyNeeds10() {
            BizException e =
                    assertThrows(
                            BizException.class,
                            () -> auditService.approve(CID, req("先通过暂不发布啊", false)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("10 字"), e.getMessage());

            assertDoesNotThrow(() -> auditService.approve(CID, req("内容需进一步核实再推送学生", false)));
        }

        @Test
        @DisplayName("退回：不足 10 字被拒，10 字放行")
        void returnNeeds10() {
            BizException e =
                    assertThrows(
                            BizException.class,
                            () -> auditService.returnBack(CID, req("请补充具体课程", false)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("10 字"), e.getMessage());
        }

        @Test
        @DisplayName("驳回：19 字被拒，20 字放行")
        void rejectNeeds20() {
            // 用 repeat 精确控制字数：手写中文句子数字数极易数错，而门槛测试最怕"看起来对"
            String nineteen = "字".repeat(19);
            BizException e =
                    assertThrows(
                            BizException.class,
                            () -> auditService.reject(CID, req(nineteen, false)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("20 字"), e.getMessage());

            assertDoesNotThrow(() -> auditService.reject(CID, req("字".repeat(20), false)));
        }

        @Test
        @DisplayName("撤回：不足 10 字被拒，10 字放行")
        void unpublishNeeds10() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, true));
            BizException e =
                    assertThrows(BizException.class, () -> auditService.unpublish(CID, "内容有错需要核实"));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("10 字"), e.getMessage());
        }

        @Test
        @DisplayName("再发布（曾撤回）：无理由被拒，5 字放行")
        void republishNeeds5() {
            Comment c = comment(CommentStatus.APPROVED, false);
            c.setWithdrawnAt(LocalDateTime.now().minusDays(1));
            when(commentMapper.selectById(CID)).thenReturn(c);

            BizException e =
                    assertThrows(BizException.class, () -> auditService.publish(CID, null));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("5 字"), e.getMessage());

            assertDoesNotThrow(() -> auditService.publish(CID, "内容已复核无误"));
        }

        @Test
        @DisplayName("首次发布：不需要理由（未被撤回）")
        void firstPublishNeedsNoReason() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, false));

            assertDoesNotThrow(() -> auditService.publish(CID, null));
        }
    }

    // ------------------------------------------------------------ 条件化更新

    @Nested
    @DisplayName("状态迁移必须条件化（并发下后到者收到 409）")
    class ConditionalUpdate {

        @Test
        @DisplayName("通过：WHERE 必须带 status，影响 0 行 → 409")
        void approveConflicts() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));
            when(commentMapper.update(any(), any())).thenReturn(0);

            BizException e =
                    assertThrows(
                            BizException.class,
                            () -> auditService.approve(CID, req("内容完整同意", true)));
            assertEquals(409, e.getCode());

            String where = whereOfLastUpdate();
            assertTrue(where.contains("status"), "缺少 status 条件：" + where);
        }

        @Test
        @DisplayName("退回 / 驳回：影响 0 行 → 409")
        void returnAndRejectConflict() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));
            when(commentMapper.update(any(), any())).thenReturn(0);

            assertEquals(
                    409,
                    assertThrows(
                                    BizException.class,
                                    () -> auditService.returnBack(CID, req("请补充具体课程与建议", false)))
                            .getCode());
            assertEquals(
                    409,
                    assertThrows(
                                    BizException.class,
                                    () ->
                                            auditService.reject(
                                                    CID, req("内容与学业指导无关或存在不当表述，经复核不予通过。", false)))
                            .getCode());
        }

        @Test
        @DisplayName("发布：WHERE 必须同时限制 status 与 published_at IS NULL")
        void publishRequiresUnpublished() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, false));

            auditService.publish(CID, null);

            String where = whereOfLastUpdate();
            assertTrue(where.contains("status"), "缺少 status 条件：" + where);
            assertTrue(where.contains("published_at"), "缺少 published_at 条件：" + where);
            assertTrue(
                    where.toUpperCase().contains("IS NULL"),
                    "published_at 必须是 IS NULL 条件：" + where);
        }

        @Test
        @DisplayName("发布：非「已通过」状态直接 409，不执行 UPDATE")
        void publishRejectedWhenNotApproved() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            assertEquals(
                    409,
                    assertThrows(BizException.class, () -> auditService.publish(CID, null))
                            .getCode());
            verify(commentMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("撤回：未发布直接 409；已发布则清空 published_at 并记录 withdrawn_at")
        void unpublishRules() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, false));
            // 理由先满足字数门槛，才能走到"未发布无需撤回"这一步
            assertEquals(
                    409,
                    assertThrows(
                                    BizException.class,
                                    () -> auditService.unpublish(CID, "内容存在事实性错误需核实重发"))
                            .getCode());

            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, true));
            auditService.unpublish(CID, "内容存在事实性错误需核实重发");

            ArgumentCaptor<Wrapper<Comment>> captor = wrapperCaptor();
            verify(commentMapper, org.mockito.Mockito.atLeastOnce())
                    .update(any(), captor.capture());
            String set = String.valueOf(captor.getValue().getSqlSet());
            assertTrue(set.contains("published_at"), "撤回必须清空 published_at：" + set);
            assertTrue(set.contains("withdrawn_at"), "撤回必须记录 withdrawn_at：" + set);
        }
    }

    // ------------------------------------------------------------ 分析触发

    @Nested
    @DisplayName("发布是分析的唯一触发器")
    class AnalysisTrigger {

        @Test
        @DisplayName("通过并发布 → 立即分析")
        void approvePublishAnalyzes() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.approve(CID, req("内容完整同意", true));

            verify(analysisService).analyzeAndSave(any());
        }

        @Test
        @DisplayName("仅通过（待发布）→ 不分析（学生不可见就不进统计）")
        void approveOnlyDoesNotAnalyze() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.approve(CID, req("内容需进一步核实再推送学生", false));

            verify(analysisService, never()).analyzeAndSave(any());
        }

        @Test
        @DisplayName("退回 / 驳回 → 不分析")
        void returnAndRejectDoNotAnalyze() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.returnBack(CID, req("请补充具体课程与建议", false));
            auditService.reject(CID, req("内容与学业指导无关或存在不当表述，经复核不予通过。", false));

            verify(analysisService, never()).analyzeAndSave(any());
        }
    }

    // ------------------------------------------------------------ 事件流

    @Nested
    @DisplayName("七个事件类型逐一留痕")
    class EventTrail {

        @Test
        @DisplayName("approve(publish=true) → APPROVE_PUBLISH")
        void approvePublishEvent() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.approve(CID, req("内容完整同意", true));

            assertEquals("APPROVE_PUBLISH", lastEvent().getActionType());
        }

        @Test
        @DisplayName("approve(publish=false) → APPROVE_ONLY")
        void approveOnlyEvent() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.approve(CID, req("内容需进一步核实再推送学生", false));

            assertEquals("APPROVE_ONLY", lastEvent().getActionType());
        }

        @Test
        @DisplayName("return → RETURN，且留存修改前正文")
        void returnEvent() {
            Comment c = comment(CommentStatus.PENDING, false);
            c.setContent("修改前原文");
            when(commentMapper.selectById(CID)).thenReturn(c);

            auditService.returnBack(CID, req("请补充具体课程与建议", false));

            CommentAudit a = lastEvent();
            assertEquals("RETURN", a.getActionType());
            assertEquals("修改前原文", a.getBeforeContent());
        }

        @Test
        @DisplayName("reject → REJECT")
        void rejectEvent() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.PENDING, false));

            auditService.reject(CID, req("内容与学业指导无关或存在不当表述，经复核不予通过。", false));

            assertEquals("REJECT", lastEvent().getActionType());
        }

        @Test
        @DisplayName("publish → PUBLISH；unpublish → WITHDRAW")
        void publishAndWithdrawEvents() {
            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, false));
            auditService.publish(CID, null);
            assertEquals("PUBLISH", lastEvent().getActionType());

            when(commentMapper.selectById(CID)).thenReturn(comment(CommentStatus.APPROVED, true));
            auditService.unpublish(CID, "内容有错需要核实并重发");
            assertEquals("WITHDRAW", lastEvent().getActionType());
        }
    }

    // ------------------------------------------------------------ 角色

    @Test
    @DisplayName("非教务执行审核 → 403，且不写任何事件")
    void teacherCannotAudit() {
        when(authContext.isAdmin()).thenReturn(false);

        BizException e =
                assertThrows(
                        BizException.class, () -> auditService.approve(CID, req("内容完整同意", true)));

        assertEquals(403, e.getCode());
        verify(auditMapper, never()).insert(any());
        verify(operLogService).record(eq("AUDIT"), eq("COMMENT"), any(), anyString(), eq(false));
    }

    // ------------------------------------------------------------ 夹具

    private CommentDtos.AuditReq req(String opinion, boolean publish) {
        CommentDtos.AuditReq r = new CommentDtos.AuditReq();
        r.setOpinion(opinion);
        r.setPublish(publish);
        return r;
    }

    private Comment comment(String status, boolean published) {
        Comment c = new Comment();
        c.setId(CID);
        c.setTeacherId(2L);
        c.setStudentId(7L);
        c.setStatus(status);
        c.setVersion(1);
        c.setContent("该生本学期学习态度认真，建议加强算法练习。");
        c.setPublishedAt(published ? LocalDateTime.now() : null);
        return c;
    }

    private CommentAudit lastEvent() {
        ArgumentCaptor<CommentAudit> captor = ArgumentCaptor.forClass(CommentAudit.class);
        verify(auditMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        CommentAudit a = captor.getValue();
        assertNotNull(a);
        return a;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Wrapper<Comment>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private String whereOfLastUpdate() {
        ArgumentCaptor<Wrapper<Comment>> captor = wrapperCaptor();
        verify(commentMapper, org.mockito.Mockito.atLeastOnce()).update(any(), captor.capture());
        return String.valueOf(captor.getValue().getSqlSegment());
    }
}
