package com.graduation.mentor.service.precheck;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 一次预审的完整输出。**只描述建议，不包含任何状态变更意图。** */
@Data
public class PrecheckResult {

    /** PASS / REVIEW / RETURN */
    private String riskLevel;

    /** 0~100，各规则权重累加后截断 */
    private int riskScore;

    /** APPROVE_PUBLISH / APPROVE_ONLY / RETURN / REJECT */
    private String suggestAction;

    /** 建议审核意见草稿（人工必须改写后提交，长度门槛照旧生效） */
    private String suggestOpinion;

    /** 按权重降序，前端按顺序展示 */
    private List<PrecheckIssue> issues = new ArrayList<>();
}
