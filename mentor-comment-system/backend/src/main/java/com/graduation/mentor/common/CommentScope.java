package com.graduation.mentor.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.entity.Comment;

/**
 * 统计口径的**唯一入口**。
 *
 * <p>设计约束（论文可引用）：全系统所有报表、词云、明细下钻、导出， 都必须经由 {@link #published()} 构造条件，禁止各处手写 status/published_at。
 * 这样"学生可见 = 纳入统计"这条业务规则只有一个实现点，想漏都漏不掉。
 */
public final class CommentScope {

    private CommentScope() {}

    /** 已发布口径：审核通过 **且** 已推送给学生 */
    public static LambdaQueryWrapper<Comment> published() {
        return new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, CommentStatus.APPROVED)
                .isNotNull(Comment::getPublishedAt);
    }

    /** 已通过但尚未发布 */
    public static LambdaQueryWrapper<Comment> approvedNotPublished() {
        return new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, CommentStatus.APPROVED)
                .isNull(Comment::getPublishedAt);
    }

    /** 某学生可见范围 = 已发布 且 属于本人。 学生端列表与详情都必须走这里。 */
    public static LambdaQueryWrapper<Comment> visibleToStudent(Long studentId) {
        return published().eq(Comment::getStudentId, studentId);
    }

    /** 某导师的可见范围 = 本人提交的全部点评（含草稿） */
    public static LambdaQueryWrapper<Comment> ownedByTeacher(Long teacherId) {
        return new LambdaQueryWrapper<Comment>().eq(Comment::getTeacherId, teacherId);
    }
}
