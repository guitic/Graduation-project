package com.graduation.mentor.common;

import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.CommentAudit;

/**
 * {@code CommentAudit → AuditItem} 的**唯一**映射点。
 *
 * <p><b>为什么必须是单点（这条注释是给未来的你留的）</b>： {@code opinion} 与 {@code beforeContent} 属于教务与导师之间的内部信息，
 * 学生端不可见。这条裁剪规则原本写在两个方法里—— {@code CommentService.detail()} 裁了，{@code AuditService.audits()} 没裁， 于是
 * {@code GET /api/audit/{id}/history} 把内部审核意见下发给了学生， 与"审核意见永不进学生端"的设计承诺直接冲突。
 *
 * <p>规则一旦有两份实现，就必然有一份是错的。所以这里只保留一份， 由调用方显式传入 {@code forStudent}，让"裁不裁"成为调用点必须回答的问题， 而不是依赖某个方法恰好记得裁。
 *
 * @see CommentScope 同样的思路：口径只有一个入口
 */
public final class AuditItemMapper {

    private AuditItemMapper() {}

    /**
     * @param forStudent 为 true 时裁掉 {@code opinion} 与 {@code beforeContent}
     */
    public static CommentDtos.AuditItem of(CommentAudit a, boolean forStudent) {
        CommentDtos.AuditItem it = new CommentDtos.AuditItem();
        it.setId(a.getId());
        it.setResult(a.getResult());
        // actionType 必须下发：前端时间线按它决定事件标题与图标。
        // 只给 resultLabel 会让"通过并发布""仅通过""撤回"看起来像同一个事件。
        it.setActionType(a.getActionType());
        it.setResultLabel(actionLabel(a.getActionType(), a.getResult()));
        it.setOpinion(forStudent ? null : a.getOpinion());
        it.setAuditorName(a.getAuditorName());
        it.setRound(a.getRound());
        it.setAuditAt(a.getAuditAt());
        it.setBeforeContent(forStudent ? null : a.getBeforeContent());
        return it;
    }

    /**
     * 事件标题：按 actionType 映射，而不是按 result。
     *
     * <p>否则"仅通过"与"通过并发布"都渲染成"通过"，撤回渲染成"审核"， 时间线上看起来像教务审了两次——字段标题必须随事件类型变。
     */
    public static String actionLabel(String actionType, String result) {
        if (actionType == null) {
            return result;
        }
        return switch (actionType) {
            case "SUBMIT" -> "提交审核";
            case "APPROVE_PUBLISH" -> "审核通过并发布";
            case "APPROVE_ONLY" -> "审核通过（待发布）";
            case "RETURN" -> "退回修改";
            case "REJECT" -> "驳回";
            case "PUBLISH" -> "发布";
            case "WITHDRAW" -> "撤回发布";
            default -> result;
        };
    }
}
