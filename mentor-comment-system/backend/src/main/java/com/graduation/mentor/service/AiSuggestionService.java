package com.graduation.mentor.service;

import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.service.ai.AiErrorMessages;
import com.graduation.mentor.service.ai.AiSuggestion;
import com.graduation.mentor.service.ai.LlmSummarySuggester;
import com.graduation.mentor.service.ai.RuleSummarySuggester;
import com.graduation.mentor.service.ai.SuggestionInput;
import com.graduation.mentor.service.analysis.PrivacyFilter;
import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.precheck.PrecheckIssue;
import com.graduation.mentor.service.precheck.PrecheckResult;
import com.graduation.mentor.service.precheck.RulePrecheckEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 摘要建议门面：决定"这次用模型还是用规则"，并保证两者输出可直接互换。
 *
 * <p><b>调用链</b>：脱敏 → 词典法情感 → 规则预检 → 拼装输入 → 优先模型 → 失败回落规则。
 *
 * <p><b>降级不抛异常</b>：模型超时、限流、返回不合规、Key 失效，全部转为"规则结果 + fallbackReason"。 对导师来说，按钮永远有结果；对教务来说，结果来源永远可查。
 *
 * <p><b>为什么不落库</b>：摘要建议是编辑期的辅助，不是审核结论。落库会带来两个问题——草稿阶段 反复点击会堆积垃圾数据；模型版本变化后旧摘要与当前正文不对应。真正需要留痕的是审核意见，
 * 那条走 {@code comment_audit}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    /** 送入规则实现的主题词个数上限 */
    private static final int TOP_WORD_LIMIT = 5;

    private final CommentMapper commentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SentimentAnalyzer analyzer;
    private final RulePrecheckEngine precheckEngine;
    private final AnalysisService analysisService;
    private final LlmSummarySuggester llmSuggester;
    private final RuleSummarySuggester ruleSuggester;
    private final AuthContext authContext;

    /** 针对已保存的点评生成摘要（导师本人或教务） */
    public AiSuggestion suggestForComment(Long commentId) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw new BizException(404, "点评不存在");
        }
        assertCanUse(c);
        return doSuggest(c.getContent(), c.getTopic());
    }

    /** 针对编辑中的草稿正文生成摘要（不落库） */
    public AiSuggestion suggestForText(String content, String topic) {
        if (!authContext.isTeacher() && !authContext.isAdmin()) {
            throw BizException.forbidden("仅导师或教务可使用摘要建议");
        }
        if (content == null || content.trim().length() < 10) {
            throw new BizException(400, "点评正文过短，无法生成摘要");
        }
        return doSuggest(content, topic);
    }

    /** 当前 AI 能力状态，供前端决定是否显示"AI 生成"入口 */
    public Map<String, Object> status() {
        String reason = llmSuggester.unavailableReason();
        return Map.of(
                "llmAvailable",
                reason == null,
                "reason",
                reason == null ? "" : reason,
                "fallback",
                AiSuggestion.SOURCE_RULE);
    }

    /**
     * 连通性自检（仅教务，在「AI 配置」页点"测试连接"）。
     *
     * <p>用一句固定的短文本发一次真实请求：这是唯一能区分"ChatClient 建起来了"与 "网络真能通、Key 真有效"的办法。只建 client 不发请求，会把 401 / 超时
     * 留到导师点按钮时才暴露。
     *
     * <p>返回结构里不含密钥；失败原因直接给原文，方便教务判断是 Key 错、余额不足还是网络不通。
     */
    public Map<String, Object> testConnectivity() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可测试 AI 连接");
        }
        long start = System.currentTimeMillis();
        String probe = "该生本学期按时完成课程作业，课堂参与度较高，建议继续巩固薄弱章节。";
        try {
            AiSuggestion s =
                    llmSuggester.suggest(
                            new SuggestionInput(
                                    probe, "连通性自检", "POSITIVE", 1.8, List.of(), List.of()));
            long cost = System.currentTimeMillis() - start;
            if (s == null) {
                String why = llmSuggester.unavailableReason();
                return Map.of(
                        "ok", false, "latencyMs", cost, "message", why == null ? "模型未返回可用结果" : why);
            }
            return Map.of(
                    "ok",
                    true,
                    "latencyMs",
                    cost,
                    "model",
                    s.getModel() == null ? "" : s.getModel(),
                    "message",
                    "连接正常，已返回摘要：" + s.getSummary());
        } catch (RuntimeException e) {
            log.warn("AI 连通性自检失败：{}", e.toString());
            return Map.of(
                    "ok",
                    false,
                    "latencyMs",
                    System.currentTimeMillis() - start,
                    "message",
                    AiErrorMessages.describe(e));
        }
    }

    // ------------------------------------------------------------- 内部

    private AiSuggestion doSuggest(String rawContent, String topic) {
        long start = System.currentTimeMillis();
        Set<String> names = nameSet();
        String masked = PrivacyFilter.mask(rawContent, names);

        SentimentResult sentiment = analyzer.analyze(masked);
        PrecheckResult precheck =
                precheckEngine.check(
                        new RulePrecheckEngine.Input(masked, topic, List.of(), sentiment));

        SuggestionInput input =
                new SuggestionInput(
                        masked,
                        topic,
                        sentiment.getLabel(),
                        sentiment.getScore(),
                        topWords(rawContent, names),
                        issueCodes(precheck));

        AiSuggestion out = null;
        String fallbackReason = null;
        try {
            out = llmSuggester.suggest(input);
            if (out == null) {
                String why = llmSuggester.unavailableReason();
                fallbackReason = why == null ? "模型输出不合规，已回落规则实现" : why;
            }
        } catch (RuntimeException e) {
            log.warn("模型摘要失败，回落规则实现：{}", e.toString());
            // 给导师看的原因要能指向"配置问题"而不是"你操作错了"，同时不暴露堆栈细节
            String why = AiErrorMessages.describe(e);
            fallbackReason = why.length() > 60 ? why.substring(0, 60) + "…" : why;
        }
        if (out == null) {
            out = ruleSuggester.suggest(input);
            out.setFallbackReason(fallbackReason);
        }
        out.setLatencyMs(System.currentTimeMillis() - start);
        return out;
    }

    private List<String> topWords(String content, Set<String> names) {
        Map<String, Integer> freq = analysisService.wordFrequency(content, names);
        List<String> words = new ArrayList<>();
        for (String w : freq.keySet()) {
            words.add(w);
            if (words.size() >= TOP_WORD_LIMIT) {
                break;
            }
        }
        return words;
    }

    private List<String> issueCodes(PrecheckResult r) {
        List<String> codes = new ArrayList<>();
        if (r == null || r.getIssues() == null) {
            return codes;
        }
        for (PrecheckIssue i : r.getIssues()) {
            codes.add(i.getCode());
        }
        return codes;
    }

    private Set<String> nameSet() {
        Set<String> names = new HashSet<>();
        studentMapper.selectList(null).forEach(s -> addIfPresent(names, s.getName()));
        teacherMapper.selectList(null).forEach(t -> addIfPresent(names, t.getName()));
        return names;
    }

    private static void addIfPresent(Set<String> set, String v) {
        if (v != null && !v.isBlank()) {
            set.add(v);
        }
    }

    /** 导师只能对自己提交的点评用；教务不限 */
    private void assertCanUse(Comment c) {
        if (authContext.isAdmin()) {
            return;
        }
        if (authContext.isTeacher()) {
            Long tid = authContext.currentTeacherId();
            if (tid.equals(c.getTeacherId())) {
                return;
            }
        }
        throw BizException.forbidden("无权对该点评生成摘要");
    }
}
