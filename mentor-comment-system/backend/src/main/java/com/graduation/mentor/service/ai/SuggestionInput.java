package com.graduation.mentor.service.ai;

import java.util.List;

/**
 * 摘要输入。**已脱敏**：姓名、学号、手机号、邮箱在进入本对象前已被替换。
 *
 * <p>把脱敏放在服务入口而不是各个实现里：实现方（尤其是外部模型）不该有机会看到原文。
 */
public record SuggestionInput(
        String content,
        String topic,
        String sentimentLabel,
        double sentimentScore,
        List<String> topWords,
        List<String> issues) {

    public SuggestionInput {
        topWords = topWords == null ? List.of() : topWords;
        issues = issues == null ? List.of() : issues;
    }
}
