package com.graduation.mentor.service.precheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条预审问题。
 *
 * <p>{@code start}/{@code end} 是命中片段在**原始正文**中的下标，前端据此把问题定位到原文
 * 高亮。刻意不存脱敏后文本的下标：展示给教务的是原文，两套下标一旦混用就会错位。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrecheckIssue {

    /** 提示级：供参考，不影响结论 */
    public static final String INFO = "INFO";

    /** 警告级：累计后进入"建议复核" */
    public static final String WARN = "WARN";

    /** 阻断级：出现即建议退回或驳回 */
    public static final String BLOCKER = "BLOCKER";

    /** 规则编码，见 {@link RulePrecheckEngine#rules()} */
    private String code;

    /** INFO / WARN / BLOCKER */
    private String level;

    /** 风险分权重，0~60 */
    private int weight;

    /** 面向教务的说明文案 */
    private String message;

    /** 命中片段起点，-1 表示该问题无法定位到具体片段 */
    private int start = -1;

    private int end = -1;

    /** 命中片段原文（隐私类问题一律不填，避免预审表二次留存个人信息） */
    private String evidence;
}
