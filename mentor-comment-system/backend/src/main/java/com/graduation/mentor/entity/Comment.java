package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 点评主体。
 *
 * <p>status 表达"内容审核结论"，publishedAt 表达"是否已推送给学生"，两者正交。
 */
@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;
    private Long studentId;
    private String term;
    private String topic;
    private String content;

    /** DRAFT / PENDING / APPROVED / RETURNED / REJECTED */
    private String status;

    private LocalDateTime submitAt;

    /** null = 通过未发布；非 null = 已发布 */
    private LocalDateTime publishedAt;

    /**
     * 最近一次撤回发布的时间。
     *
     * <p>存在的理由：撤回会把 publishedAt 置回 null，导致"从未发布"与"曾发布后撤回" 在库里完全无法区分，而前端需要据此渲染 [待发布] 与 [曾发布·已撤回]
     * 两种标签。 用时间戳而非布尔位，是因为撤回时间本身可审计。
     */
    private LocalDateTime withdrawnAt;

    /** 每次退回后再提交 +1，用于快照轮次对齐 */
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
