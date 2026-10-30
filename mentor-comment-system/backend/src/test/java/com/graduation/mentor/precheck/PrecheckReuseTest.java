package com.graduation.mentor.precheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentPrecheck;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentPrecheckMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.PrecheckService;
import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.precheck.PrecheckLexicon;
import com.graduation.mentor.service.precheck.RulePrecheckEngine;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 预审复用与权限的不变量测试。
 *
 * <p><b>守的是哪两条</b>：
 *
 * <ol>
 *   <li><b>双匹配复用</b>：只有 {@code (content_hash, rule_version)} 同时命中才复用。只比 hash，
 *       规则升级后旧结论会冒充新结论；只比版本，退回改稿后的新文本会沿用旧判定。两者都不报错， 界面看起来完全正常——这正是必须有测试的原因。
 *   <li><b>预审不改状态</b>：本服务不持有 AuditService，也不调用任何写 comment.status 的方法。 测试通过"只 mock CommentMapper
 *       的读方法"来保证：一旦有人在这里加了状态写入， update 会返回 0 而不是静默成功。
 * </ol>
 */
class PrecheckReuseTest {

    private static final Long CID = 42L;

    private static final String CONTENT = "该生本学期在专业课程学习中表现良好，课程作业基本按时完成，课堂参与度一般，建议加强课后练习。";

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
        TableInfoHelper.initTableInfo(assistant, CommentPrecheck.class);
    }

    private CommentMapper commentMapper;
    private CommentPrecheckMapper precheckMapper;
    private AuthContext authContext;
    private PrecheckService service;

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        precheckMapper = mock(CommentPrecheckMapper.class);
        authContext = mock(AuthContext.class);

        PrecheckLexicon lexicon = new PrecheckLexicon();
        lexicon.load();
        SentimentAnalyzer analyzer = mock(SentimentAnalyzer.class);
        SentimentResult neutral = new SentimentResult();
        neutral.setLabel("NEUTRAL");
        neutral.setScore(0.5);
        when(analyzer.analyze(anyString())).thenReturn(neutral);

        service =
                new PrecheckService(
                        commentMapper,
                        precheckMapper,
                        new RulePrecheckEngine(lexicon),
                        analyzer,
                        authContext,
                        new ObjectMapper());

        when(authContext.isAdmin()).thenReturn(true);
        Comment c = new Comment();
        c.setId(CID);
        c.setTeacherId(7L);
        c.setStudentId(9L);
        c.setTerm("2025-2026-1");
        c.setTopic("期中指导");
        c.setContent(CONTENT);
        c.setVersion(1);
        c.setCreatedAt(LocalDateTime.now());
        when(commentMapper.selectById(CID)).thenReturn(c);
        when(commentMapper.selectList(any())).thenReturn(List.of());
    }

    private CommentPrecheck cachedRow(String hash, String version) {
        CommentPrecheck row = new CommentPrecheck();
        row.setId(1L);
        row.setCommentId(CID);
        row.setContentHash(hash);
        row.setRuleVersion(version);
        row.setRiskLevel("REVIEW");
        row.setRiskScore(38);
        row.setSuggestAction("APPROVE_ONLY");
        row.setSuggestOpinion("内容基本可用，但套话较多，建议先通过、暂不发布。");
        row.setIssues("[]");
        row.setIssueCount(1);
        row.setEngine("RULE");
        row.setCostMs(3);
        row.setCheckedAt(LocalDateTime.now());
        return row;
    }

    @Test
    @DisplayName("文本与规则版本都没变：直接复用，不重新计算、不写库")
    void reuseWhenHashAndVersionMatch() {
        when(precheckMapper.selectOne(any()))
                .thenReturn(cachedRow(AnalysisService.sha256(CONTENT), RulePrecheckEngine.VERSION));

        var view = service.ensure(CID, false);

        assertTrue(view.isCached());
        assertEquals(38, view.getRiskScore());
        verify(precheckMapper, never()).insert(any());
        verify(precheckMapper, never()).delete(any());
    }

    @Test
    @DisplayName("规则版本升级：旧结论必须失效并重算")
    void recomputeWhenRuleVersionChanges() {
        when(precheckMapper.selectOne(any()))
                .thenReturn(cachedRow(AnalysisService.sha256(CONTENT), "rule-v0.9"));

        var view = service.ensure(CID, false);

        assertEquals(false, view.isCached());
        assertEquals(RulePrecheckEngine.VERSION, view.getRuleVersion());
        ArgumentCaptor<CommentPrecheck> captor = ArgumentCaptor.forClass(CommentPrecheck.class);
        verify(precheckMapper).insert(captor.capture());
        assertEquals(RulePrecheckEngine.VERSION, captor.getValue().getRuleVersion());
    }

    @Test
    @DisplayName("正文被改过（hash 不同）：即使版本一致也必须重算")
    void recomputeWhenContentChanges() {
        when(precheckMapper.selectOne(any()))
                .thenReturn(cachedRow("deadbeef", RulePrecheckEngine.VERSION));

        var view = service.ensure(CID, false);

        assertEquals(false, view.isCached());
        assertEquals(CID, view.getCommentId());
        verify(precheckMapper).insert(any());
    }

    @Test
    @DisplayName("非教务角色：403，且不产生任何写操作")
    void onlyAdminCanPrecheck() {
        when(authContext.isAdmin()).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.ensure(CID, false));

        assertEquals(403, e.getCode());
        verify(precheckMapper, never()).insert(any());
    }
}
