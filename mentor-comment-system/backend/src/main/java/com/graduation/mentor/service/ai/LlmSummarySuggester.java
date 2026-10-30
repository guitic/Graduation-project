package com.graduation.mentor.service.ai;

import com.graduation.mentor.config.AiProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 大模型摘要实现。
 *
 * <p>客户端生命周期（懒初始化、按配置版本号重建、超时）统一由 {@link AiChatClientHolder} 负责， 这里只做三件事：拼提示词、发请求、把输出过闸。
 *
 * <p><b>输出必须过闸</b>：长度、条数、格式（{@code 摘要：} / {@code 建议：} 两行）任一不合规 都判定失败并回落规则实现。模型输出直接进审核意见是事故，不是效率。
 *
 * <p><b>不做"尽力修复"</b>：把半截 JSON 或带解释的输出塞进审核意见，比没有建议更糟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSummarySuggester implements SummarySuggester {

    /** 系统提示词：限定角色、禁止编造、禁止输出个人信息 */
    private static final String SYSTEM_PROMPT =
            """
            你是高校学业导师点评的辅助分析工具。你的任务是给已经写好的点评生成一句话摘要和改进行动建议。
            硬性要求：
            1. 只依据用户给出的点评正文，不得编造学生姓名、分数、课程等原文没有的信息；
            2. 不评价学生本人，只评价这段点评文本的写法与信息完整度；
            3. 摘要不超过 60 字，建议每条不超过 40 字，最多 3 条；
            4. 输出格式严格为两行：
               摘要：<一句话>
               建议：<建议1>|<建议2>|<建议3>
            5. 不要输出任何解释、前缀、Markdown 标记。
            """;

    /** 仅用于长度上限等启动期固定的参数（超时/温度在 holder 里用） */
    private final AiProperties props;

    /** 共享的 ChatClient 持有者 */
    private final AiChatClientHolder holder;

    @Override
    public String name() {
        return AiSuggestion.SOURCE_LLM;
    }

    /** 供 /api/ai/status 与降级原因使用 */
    public String unavailableReason() {
        return holder.unavailableReason();
    }

    @Override
    public AiSuggestion suggest(SuggestionInput in) {
        AiRuntimeConfig cfg = holder.config();
        ChatClient c = holder.get();
        if (c == null) {
            return null;
        }
        String userPrompt = buildUserPrompt(in);
        String raw = c.prompt().system(SYSTEM_PROMPT).user(userPrompt).call().content();
        AiSuggestion out = parse(raw);
        if (out == null) {
            return null;
        }
        out.setSource(AiSuggestion.SOURCE_LLM);
        out.setModel(cfg.model());
        out.setSentimentLabel(in.sentimentLabel());
        out.setSentimentScore(in.sentimentScore());
        return out;
    }

    // ------------------------------------------------------------- 提示词与解析

    private String buildUserPrompt(SuggestionInput in) {
        String content = in.content() == null ? "" : in.content();
        if (content.length() > props.getMaxInputChars()) {
            content = content.substring(0, props.getMaxInputChars());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("点评正文：").append(content).append('\n');
        if (in.topic() != null && !in.topic().isBlank()) {
            sb.append("指导主题：").append(in.topic()).append('\n');
        }
        sb.append("情感分析（词典法，仅供参考）：").append(in.sentimentLabel()).append('\n');
        if (!in.issues().isEmpty()) {
            sb.append("规则预检发现的问题编码：").append(String.join(",", in.issues())).append('\n');
        }
        return sb.toString();
    }

    /** 解析模型输出。任何一项不合规都返回 null，由门面回落规则实现。 */
    private AiSuggestion parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > props.getMaxOutputChars()) {
            log.debug("模型输出为空或超长，判定不合规");
            return null;
        }
        String summary = null;
        List<String> suggestions = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("摘要：") || l.startsWith("摘要:")) {
                summary = l.substring(3).trim();
            } else if (l.startsWith("建议：") || l.startsWith("建议:")) {
                String body = l.substring(3).trim();
                Arrays.stream(body.split("[|｜]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(3)
                        .forEach(suggestions::add);
            }
        }
        if (summary == null || summary.isEmpty() || suggestions.isEmpty()) {
            log.debug("模型输出缺少摘要或建议，判定不合规：{}", raw);
            return null;
        }
        if (summary.length() > 120) {
            summary = summary.substring(0, 120) + "…";
        }
        AiSuggestion out = new AiSuggestion();
        out.setSummary(summary);
        out.setSuggestions(suggestions);
        return out;
    }
}
