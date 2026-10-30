package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graduation.mentor.service.ai.AiSuggestion;
import com.graduation.mentor.service.ai.RuleSummarySuggester;
import com.graduation.mentor.service.ai.SuggestionInput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则版摘要的单元测试。
 *
 * <p>这是**降级路径**的测试，不是"次要功能"的测试：{@code app.ai.enabled=false} 是默认状态，
 * 也就是说这个实现是绝大多数情况下用户真正看到的那个。它挂了，功能就整体挂了。
 */
class RuleSummarySuggesterTest {

    private final RuleSummarySuggester suggester = new RuleSummarySuggester();

    @Test
    @DisplayName("正向点评：摘要含情绪结论与主题词")
    void positiveSummaryCarriesMoodAndTopicWords() {
        AiSuggestion s =
                suggester.suggest(
                        new SuggestionInput(
                                "本阶段学习态度很好，作业完成质量高，继续保持。",
                                "阶段总结",
                                "POSITIVE",
                                3.2,
                                List.of("学习", "态度", "作业"),
                                List.of()));

        assertEquals(AiSuggestion.SOURCE_RULE, s.getSource());
        assertTrue(s.getSummary().contains("正向"), "摘要应体现正向倾向：" + s.getSummary());
        assertTrue(s.getSummary().contains("学习"), "摘要应带出主题词：" + s.getSummary());
        assertNotNull(s.getSuggestions());
        assertFalse(s.getSuggestions().isEmpty());
    }

    @Test
    @DisplayName("命中 NO_ADVICE：提示补充可执行建议")
    void noAdviceIssueProducesActionableTip() {
        AiSuggestion s =
                suggester.suggest(
                        new SuggestionInput(
                                "最近状态一般。",
                                "",
                                "NEUTRAL",
                                0.0,
                                List.of("状态"),
                                List.of("NO_ADVICE")));

        assertTrue(
                s.getSuggestions().stream().anyMatch(t -> t.contains("可执行")),
                "应提示补充可执行建议：" + s.getSuggestions());
    }

    @Test
    @DisplayName("建议条数上限为 3")
    void suggestionsCappedAtThree() {
        AiSuggestion s =
                suggester.suggest(
                        new SuggestionInput(
                                "内容偏短且缺具体事实。",
                                "",
                                "NEGATIVE",
                                -2.0,
                                List.of(),
                                List.of("NO_ADVICE", "NO_SPECIFIC", "VAGUE", "LENGTH_TOO_SHORT")));

        assertTrue(s.getSuggestions().size() <= 3, "建议最多 3 条：" + s.getSuggestions());
    }

    @Test
    @DisplayName("无问题时给一条收尾建议，而不是空列表")
    void cleanTextStillGetsOneClosingTip() {
        AiSuggestion s =
                suggester.suggest(
                        new SuggestionInput(
                                "本周完成了数据库设计，进展正常。",
                                "周报",
                                "POSITIVE",
                                1.8,
                                List.of("数据库"),
                                List.of()));

        assertEquals(1, s.getSuggestions().size());
    }
}
