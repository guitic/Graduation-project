package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 点评事件流（原设计为"审核记录"，因引入提交与发布事件，语义已扩展为事件流）。
 *
 * <p>一次动作一行，永不覆盖，用于完整追溯。事件类型见 {@code actionType}： SUBMIT / APPROVE_ONLY / APPROVE_PUBLISH / RETURN
 * / REJECT / PUBLISH / WITHDRAW。
 *
 * <p>beforeContent 保存"处理人当时看到的那一版正文"完整快照（仅在退回/驳回时写入）， 配合 round 轮次号，可完整重建任一时点的内容。
 *
 * <p><b>边界</b>：本表只记录点评自身的生命周期事件。 登录、人员变更等其它审计请走 sys_oper_log，不要塞进这里。
 */
@Data
@TableName("comment_audit")
public class CommentAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;
    private Long auditorId;
    private String auditorName;

    /** APPROVED / RETURNED / REJECTED / PUBLISHED / UNPUBLISHED */
    private String result;

    /**
     * 细分动作：APPROVE_PUBLISH / APPROVE_ONLY / RETURN / REJECT / PUBLISH / UNPUBLISH。
     *
     * <p>为什么 result 不够：审核通过与"通过并发布"、审核通过与"仅通过"的 result 相同， 只有 actionType 能区分。否则时间线会把"仅通过"渲染成一次普通通过，
     * 追溯链在答辩追问时说不清楚。
     */
    private String actionType;

    private String opinion;
    private String beforeContent;

    /** 第几轮审核（对应 comment.version） */
    private Integer round;

    private LocalDateTime auditAt;
}
