package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 预审结果（规则引擎输出）。
 *
 * <p><b>本表只存"建议"，不参与任何状态判定。</b> comment.status 的唯一写入者仍是 {@link
 * com.graduation.mentor.service.AuditService} 中的人工动作。这条边界是整套"智能审批"能被
 * 追溯与解释的前提：预审表被清空、规则版本回滚、模型不可用，点评流程都照常运行。
 *
 * <p>复用判据与 comment_sentiment 一致：{@code (content_hash, rule_version)} 双匹配。 只比 hash
 * 挡不住"规则变了"，只比版本挡不住"文本被退回改稿"，两者都会让教务看到过期结论 而界面不报错。
 */
@Data
@TableName("comment_precheck")
public class CommentPrecheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    /** 被预审正文的 SHA-256，与 comment.version 无关 */
    private String contentHash;

    /** 规则版本，规则或词表调整后必须升版本，否则旧结论会被复用 */
    private String ruleVersion;

    /** PASS / REVIEW / RETURN */
    private String riskLevel;

    /** 风险分 0~100，各条规则权重累加后截断 */
    private Integer riskScore;

    /** APPROVE_PUBLISH / APPROVE_ONLY / RETURN / REJECT */
    private String suggestAction;

    /** 建议审核意见草稿。仅供教务修改后使用，不落 comment_audit */
    private String suggestOpinion;

    /** 问题清单 JSON 数组，元素结构见 PrecheckIssue */
    private String issues;

    private Integer issueCount;

    /** 产出该结论的引擎：RULE（后续可扩展 RULE+LLM） */
    private String engine;

    /** 预审耗时（毫秒），用于论文里说明"规则法开销可忽略" */
    private Integer costMs;

    private LocalDateTime checkedAt;
}
