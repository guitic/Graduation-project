package com.graduation.mentor.precheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.precheck.PrecheckIssue;
import com.graduation.mentor.service.precheck.PrecheckLexicon;
import com.graduation.mentor.service.precheck.PrecheckResult;
import com.graduation.mentor.service.precheck.PrecheckResultLevel;
import com.graduation.mentor.service.precheck.RulePrecheckEngine;
import com.graduation.mentor.service.precheck.SuggestAction;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则引擎回归测试。
 *
 * <p><b>为什么值得写</b>：预审结论会直接影响教务的第一反应。规则阈值、权重、词表任何一处被改动， 都会让"同样的文本给出不同的建议"，而界面不会报错——只有测试能守住。
 *
 * <p>断言分三类：
 *
 * <ol>
 *   <li>等级与建议动作的映射（PASS/REVIEW/RETURN → 四个动作）
 *   <li>每类问题都能被检出，且定位信息可用（span 落在原文内）
 *   <li>意见草稿长度必须高于对应动作的字数门槛——否则教务点"填入"后立刻被前端拦下
 * </ol>
 */
class RulePrecheckEngineTest {

    /** 各动作对应的审核意见字数门槛，与 AuditService 常量保持一致 */
    private static final int MIN_APPROVE_PUBLISH = 5;

    private static final int MIN_APPROVE_ONLY = 10;

    private static final int MIN_RETURN = 10;

    private static final int MIN_REJECT = 20;

    private static final String GOOD =
            "本学期该生《数据结构》课程出勤情况良好，三次阶段作业均按时提交，期末卷面成绩86分，"
                    + "较上学期有明显提升。课堂讨论中能主动提出算法优化思路，但独立编码速度偏慢。"
                    + "建议下学期参加ACM竞赛训练，注意加强动态规划类题目的限时练习，并在每次实验后补充复盘记录。";

    private RulePrecheckEngine engine;

    @BeforeEach
    void setUp() {
        PrecheckLexicon lexicon = new PrecheckLexicon();
        lexicon.load();
        engine = new RulePrecheckEngine(lexicon);
    }

    private PrecheckResult check(String content, String... others) {
        return engine.check(new RulePrecheckEngine.Input(content, "期中指导", List.of(others), null));
    }

    private static boolean has(PrecheckResult r, String code) {
        return r.getIssues().stream().anyMatch(i -> code.equals(i.getCode()));
    }

    // ------------------------------------------------------------ 等级映射

    @Test
    @DisplayName("内容完整、有事实有建议：PASS，建议通过并发布，风险分 0")
    void goodCommentPasses() {
        PrecheckResult r = check(GOOD);

        assertEquals(PrecheckResultLevel.PASS, r.getRiskLevel());
        assertEquals(SuggestAction.APPROVE_PUBLISH, r.getSuggestAction());
        assertEquals(0, r.getRiskScore());
        assertTrue(r.getIssues().isEmpty(), "完整点评不应产生任何问题项");
    }

    @Test
    @DisplayName("空泛短句：RETURN，建议退回，且命中篇幅/套话/无事实/无建议四项")
    void vagueShortCommentReturns() {
        PrecheckResult r = check("该生表现良好，继续努力。");

        assertEquals(PrecheckResultLevel.RETURN, r.getRiskLevel());
        assertEquals(SuggestAction.RETURN, r.getSuggestAction());
        assertEquals(100, r.getRiskScore(), "权重累加超过 100 应截断");
        assertTrue(has(r, "LENGTH_TOO_SHORT"));
        assertTrue(has(r, "VAGUE"));
        assertTrue(has(r, "NO_SPECIFIC"));
        assertTrue(has(r, "NO_ADVICE"));
    }

    @Test
    @DisplayName("套话但篇幅够、有事实：REVIEW，建议仅通过（不发布）")
    void vagueButCompleteNeedsReview() {
        PrecheckResult r = check("该生本学期在专业课程学习中表现良好，课程作业基本按时完成，课堂参与度一般，" + "建议加强课后练习与复习安排。");

        assertEquals(PrecheckResultLevel.REVIEW, r.getRiskLevel());
        assertEquals(SuggestAction.APPROVE_ONLY, r.getSuggestAction());
        assertTrue(has(r, "VAGUE"));
    }

    @Test
    @DisplayName("贬损性表述：BLOCKER 直接判 RETURN 并建议驳回")
    void derogatoryIsBlocker() {
        PrecheckResult r = check("该生学习态度敷衍，作业多次拖延，建议重修该课程。");

        assertEquals(PrecheckResultLevel.RETURN, r.getRiskLevel());
        assertEquals(SuggestAction.REJECT, r.getSuggestAction());
        assertTrue(has(r, "DEROGATORY"));
    }

    // ------------------------------------------------------------ 单项规则

    @Test
    @DisplayName("隐私：只报类型，不把手机号抄进预审结果")
    void privacyReportsTypeOnly() {
        PrecheckResult r = check("请联系13812345678讨论毕设进度，建议下周面谈并确定选题方向。");

        PrecheckIssue issue =
                r.getIssues().stream()
                        .filter(i -> "PRIVACY".equals(i.getCode()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(issue.getMessage().contains("手机号"));
        assertNull(issue.getEvidence(), "预审结果不得二次留存个人信息");
    }

    @Test
    @DisplayName("模板化：与同导师近期点评完全相同 → TEMPLATE_DUP")
    void duplicateDetected() {
        PrecheckResult r = check(GOOD, GOOD);

        assertTrue(has(r, "TEMPLATE_DUP"));
        assertTrue(r.getRiskScore() >= 45);
    }

    @Test
    @DisplayName("相似度：同文为 1，完全不同的文本接近 0")
    void similaritySanity() {
        assertEquals(1.0, RulePrecheckEngine.similarity(GOOD, GOOD), 1e-9);
        assertTrue(RulePrecheckEngine.similarity(GOOD, "该生实验报告质量稳定，建议补充数据分析部分。") < 0.3);
    }

    @Test
    @DisplayName("情感极端：净得分 ≤ -3.0 记一条 WARN")
    void negativeSentimentWarns() {
        SentimentResult s = new SentimentResult();
        s.setLabel("NEGATIVE");
        s.setScore(-3.6);
        s.setLowConfidence(false);

        PrecheckResult r = engine.check(new RulePrecheckEngine.Input(GOOD, "期中指导", List.of(), s));

        assertTrue(has(r, "NEG_EXTREME"));
        assertEquals(PrecheckResultLevel.PASS, r.getRiskLevel(), "单条 22 分不足以判复核");
    }

    @Test
    @DisplayName("定位信息：命中片段的下标必须落在原文范围内")
    void issueSpanIsUsable() {
        PrecheckResult r = check("该生学习态度敷衍，作业多次拖延，建议重修该课程。");

        PrecheckIssue issue =
                r.getIssues().stream().filter(i -> i.getStart() >= 0).findFirst().orElseThrow();
        assertTrue(issue.getStart() < issue.getEnd());
        assertEquals("敷衍", issue.getEvidence());
    }

    // ------------------------------------------------------------ 意见草稿

    @Test
    @DisplayName("意见草稿长度必须高于对应动作的字数门槛，否则填入即被前端拦下")
    void draftOpinionMeetsThreshold() {
        assertTrue(check(GOOD).getSuggestOpinion().length() >= MIN_APPROVE_PUBLISH);
        assertTrue(
                check("该生本学期在专业课程学习中表现良好，课程作业基本按时完成，课堂参与度一般，" + "建议加强课后练习与复习安排。")
                                .getSuggestOpinion()
                                .length()
                        >= MIN_APPROVE_ONLY);
        assertTrue(check("该生表现良好，继续努力。").getSuggestOpinion().length() >= MIN_RETURN);
        assertTrue(check("该生学习态度敷衍，作业多次拖延，建议重修该课程。").getSuggestOpinion().length() >= MIN_REJECT);
    }

    @Test
    @DisplayName("规则目录：编码唯一、版本非空，前端与论文共用同一口径")
    void ruleCatalogIsComplete() {
        assertNotNull(RulePrecheckEngine.VERSION);
        assertEquals(9, RulePrecheckEngine.rules().size());
        assertEquals(
                9,
                RulePrecheckEngine.rules().stream()
                        .map(RulePrecheckEngine.RuleInfo::code)
                        .distinct()
                        .count());
    }
}
