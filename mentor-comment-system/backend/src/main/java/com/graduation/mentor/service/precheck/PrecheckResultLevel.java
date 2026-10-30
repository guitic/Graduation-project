package com.graduation.mentor.service.precheck;

/**
 * 预审风险等级。
 *
 * <p>刻意用三个词描述"建议"而不是"结论"：PASS 不等于已通过，RETURN 也不等于已退回。 教务看到的应当是一份意见，不是一份已经生效的判决。
 */
public final class PrecheckResultLevel {

    /** 未见明显问题，建议通过 */
    public static final String PASS = "PASS";

    /** 存在问题但不足以退回，建议人工复核后处理 */
    public static final String REVIEW = "REVIEW";

    /** 问题较集中，建议退回导师修改 */
    public static final String RETURN = "RETURN";

    private PrecheckResultLevel() {}

    public static String label(String level) {
        return switch (level == null ? "" : level) {
            case PASS -> "建议通过";
            case REVIEW -> "建议复核";
            case RETURN -> "建议退回";
            default -> level;
        };
    }
}
