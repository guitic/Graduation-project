package com.graduation.mentor.service.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 规则版摘要（降级实现，也是 {@code app.ai.enabled=false} 时的唯一实现）。
 *
 * <p><b>为什么降级实现必须有独立价值，而不是"模型挂了就返回空"</b>： 一个只在模型可用时才有输出的功能，本质上是把系统的可用性外包给了第三方。 这里用已有的情感分析结论 +
 * 预检问题清单拼装摘要，断网、无 Key、限流时表现完全一致， 只是措辞不如模型自然。
 *
 * <p>输出受同一套长度与条数约束（摘要 ≤80 字、建议 ≤3 条），与模型版可直接互换。
 */
@Component
public class RuleSummarySuggester implements SummarySuggester {

    /** 摘要里最多列举的主题词个数 */
    private static final int TOP_WORD_LIMIT = 3;

    @Override
    public String name() {
        return AiSuggestion.SOURCE_RULE;
    }

    @Override
    public AiSuggestion suggest(SuggestionInput in) {
        AiSuggestion out = AiSuggestion.rule();
        out.setSummary(summarize(in));
        out.setSuggestions(advise(in));
        out.setSentimentLabel(in.sentimentLabel());
        out.setSentimentScore(in.sentimentScore());
        return out;
    }

    /** 摘要：情绪结论 + 主题词 + 篇幅，三要素固定，便于横向对比 */
    private String summarize(SuggestionInput in) {
        String mood =
                switch (in.sentimentLabel() == null ? "NEUTRAL" : in.sentimentLabel()) {
                    case "POSITIVE" -> "整体正向，以肯定学生进展为主";
                    case "NEGATIVE" -> "偏负向，指出了需要改进的问题";
                    default -> "情绪中性，以事实陈述为主";
                };
        String words = joinTopWords(in.topWords());
        String topic =
                in.topic() == null || in.topic().isBlank()
                        ? ""
                        : "，主题为「" + trim(in.topic(), 12) + "」";
        String base = "点评" + mood + topic;
        if (words.isEmpty()) {
            return base + "。";
        }
        return base + "，围绕「" + words + "」展开。";
    }

    /** 建议：按预检问题编码逐条映射，无问题时给一条通用收尾建议 */
    private List<String> advise(SuggestionInput in) {
        List<String> tips = new ArrayList<>();
        List<String> issues = in.issues();
        if (issues.contains("NO_ADVICE")) {
            tips.add("补一条可执行的下一步：做什么、什么时候检查、达到什么标准");
        }
        if (issues.contains("NO_SPECIFIC")) {
            tips.add("补入具体事实（课程名、任务、分数、时间节点），避免只有评价没有依据");
        }
        if (issues.contains("VAGUE")) {
            tips.add("把套话替换为本次指导的具体场景，例如" + sceneHint(in));
        }
        if (issues.contains("LENGTH_TOO_SHORT")) {
            tips.add("篇幅偏短，建议补充指导过程与学生反馈，便于后续追踪");
        }
        if (issues.contains("NEG_EXTREME")) {
            tips.add("负面表述较集中，建议同步给出改进路径，避免只指出问题");
        }
        if (tips.isEmpty()) {
            tips.add("可在结尾明确下次指导时间与检查项，形成闭环");
        }
        return tips.size() > 3 ? tips.subList(0, 3) : tips;
    }

    /** 场景提示取首个主题词，取不到时给一个中性例子 */
    private String sceneHint(SuggestionInput in) {
        String w = in.topWords().isEmpty() ? "" : in.topWords().get(0);
        return w.isEmpty() ? "「本次讨论了什么、学生卡在哪一步」" : "「" + trim(w, 8) + "」的具体进展";
    }

    private String joinTopWords(List<String> words) {
        if (words.isEmpty()) {
            return "";
        }
        List<String> top = words.subList(0, Math.min(TOP_WORD_LIMIT, words.size()));
        return String.join("、", top);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
