package com.graduation.mentor.service.precheck;

/**
 * 预审建议动作。
 *
 * <p>取值刻意与 {@link com.graduation.mentor.service.AuditService} 的动作编码同名，
 * 前端可以一一映射到已有的四个按钮；但**没有任何代码路径会用这里的值去写 comment.status**， 它们只用于渲染按钮高亮与预填意见草稿。
 */
public final class SuggestAction {

    /** 建议通过并发布 */
    public static final String APPROVE_PUBLISH = "APPROVE_PUBLISH";

    /** 建议仅通过（进入待发布） */
    public static final String APPROVE_ONLY = "APPROVE_ONLY";

    /** 建议退回修改 */
    public static final String RETURN = "RETURN";

    /** 建议驳回 */
    public static final String REJECT = "REJECT";

    private SuggestAction() {}

    public static String label(String action) {
        return switch (action == null ? "" : action) {
            case APPROVE_PUBLISH -> "通过并发布";
            case APPROVE_ONLY -> "仅通过（待发布）";
            case RETURN -> "退回修改";
            case REJECT -> "驳回";
            default -> action;
        };
    }
}
