package com.graduation.mentor.service.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 摘要建议的统一输出。
 *
 * <p><b>两种实现共用同一个结构</b>，前端不需要按来源分支渲染。{@code source} 只是给 审核侧与答辩现场一个可核对的口径：这条结论是模型给的，还是规则给的。
 */
@Data
public class AiSuggestion {

    /** 结果来源：LLM / RULE */
    public static final String SOURCE_LLM = "LLM";

    public static final String SOURCE_RULE = "RULE";

    private String source;

    /** 实际使用的模型名；RULE 来源时为 null */
    private String model;

    /** 一句话摘要（≤80 字） */
    private String summary;

    /** 可执行的改进建议，最多 3 条 */
    private List<String> suggestions = new ArrayList<>();

    /** 摘要所依据的情感倾向：POSITIVE / NEUTRAL / NEGATIVE */
    private String sentimentLabel;

    /** 词典法净得分，供交叉核对 */
    private double sentimentScore;

    /** 耗时（毫秒） */
    private long latencyMs;

    /**
     * 降级原因。非空即表示"本该由模型回答，实际由规则兜底"。
     *
     * <p>刻意回传而不是只写日志：评审看到"为什么这里没有大模型"时，答案在响应里， 不用去翻服务器日志。
     */
    private String fallbackReason;

    public static AiSuggestion rule() {
        AiSuggestion s = new AiSuggestion();
        s.setSource(SOURCE_RULE);
        return s;
    }
}
