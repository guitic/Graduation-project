package com.graduation.mentor.common;

/**
 * 点评状态常量。
 *
 * <p>设计要点：审核状态与发布状态是两个正交维度 —— status 表达"内容是否通过审核"，publishedAt 表达"是否已推送给学生"。 只有 status=APPROVED 且
 * publishedAt != null 的点评才对学生可见、并纳入统计。
 */
public final class CommentStatus {

    /** 草稿：导师尚未提交，任何其他角色不可见 */
    public static final String DRAFT = "DRAFT";

    /** 待审核：导师已提交，等待教务处理 */
    public static final String PENDING = "PENDING";

    /** 已通过：审核通过。是否发布看 publishedAt */
    public static final String APPROVED = "APPROVED";

    /** 退回修改：需导师按审核意见修改后重新提交 */
    public static final String RETURNED = "RETURNED";

    /** 已驳回：终态，不发布、不纳入统计 */
    public static final String REJECTED = "REJECTED";

    private CommentStatus() {}

    public static String label(String status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PENDING -> "待审核";
            case APPROVED -> "已通过";
            case RETURNED -> "退回修改";
            case REJECTED -> "已驳回";
            default -> status;
        };
    }
}
