package com.graduation.mentor.service.ai;

/**
 * 摘要实现契约。
 *
 * <p>两个实现必须满足同一组约束，否则前端和调用方无法互换：
 *
 * <ul>
 *   <li>摘要 ≤80 字，建议 ≤3 条；
 *   <li>不修改入参，不落库，不做状态变更；
 *   <li>不抛异常——失败由 {@link AiSuggestionService} 捕获并切换实现。
 * </ul>
 */
public interface SummarySuggester {

    /** 实现标识，与 {@link AiSuggestion#getSource()} 一致 */
    String name();

    AiSuggestion suggest(SuggestionInput in);
}
