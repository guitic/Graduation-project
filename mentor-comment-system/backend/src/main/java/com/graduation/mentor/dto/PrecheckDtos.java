package com.graduation.mentor.dto;

import com.graduation.mentor.service.precheck.PrecheckIssue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** AI 预审相关 DTO */
public class PrecheckDtos {

    /** 预审结果视图（前端建议卡直接渲染） */
    @Data
    public static class View {

        private Long commentId;

        /** PASS / REVIEW / RETURN */
        private String riskLevel;

        /** 建议通过 / 建议复核 / 建议退回 */
        private String riskLevelLabel;

        /** 0~100 */
        private int riskScore;

        /** APPROVE_PUBLISH / APPROVE_ONLY / RETURN / REJECT */
        private String suggestAction;

        private String suggestActionLabel;

        /** 意见草稿：前端只能"填入"，不能自动提交 */
        private String suggestOpinion;

        /** 按权重降序 */
        private List<PrecheckIssue> issues;

        private int issueCount;
        private String engine;
        private String ruleVersion;
        private int costMs;
        private LocalDateTime checkedAt;

        /** true = 命中 content_hash + rule_version 复用，本次未重新计算 */
        private boolean cached;
    }

    /** 批量预审请求（列表页一次预审当前页） */
    @Data
    public static class BatchReq {

        @NotEmpty(message = "请选择需要预审的点评")
        @Size(max = 50, message = "单次最多预审 50 条")
        private List<Long> ids;
    }
}
