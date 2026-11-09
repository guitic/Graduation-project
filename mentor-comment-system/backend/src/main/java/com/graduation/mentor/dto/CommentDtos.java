package com.graduation.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 点评相关 DTO */
public class CommentDtos {

    /** 导师保存草稿 / 提交 */
    @Data
    public static class SaveReq {
        @NotNull(message = "请选择学生") private Long studentId;

        @NotBlank(message = "请填写学期")
        private String term;

        @NotBlank(message = "请填写指导主题")
        @Size(max = 50, message = "主题不超过50字")
        private String topic;

        @NotBlank(message = "点评内容不能为空")
        @Size(min = 20, max = 2000, message = "点评内容需 20~2000 字")
        private String content;

        /** true = 直接提交审核；false = 存草稿 */
        private boolean submit;
    }

    /** 审核动作 */
    @Data
    public static class AuditReq {
        /** 审核意见：必填，但**不限字数**（上限 500）。前端「常用理由」下拉可直接填入 */
        @NotBlank(message = "请填写审核意见，或从常用理由中选择一条")
        @Size(max = 500, message = "审核意见不超过 500 字")
        private String opinion;

        /** 仅 approve 使用：true=通过并发布，false=仅通过（待发布） */
        private boolean publish;
    }

    /** 列表查询条件 */
    @Data
    public static class Query {
        private long page = 1;
        private long size = 10;

        /** 状态筛选，空表示全部 */
        private String status;

        private String term;
        private Long teacherId;
        private Long clazzId;
        private Long studentId;
        private String keyword;
        private String startDate;
        private String endDate;
    }

    /** 列表行 */
    @Data
    public static class Row {
        private Long id;
        private Long teacherId;
        private String teacherName;
        private Long studentId;
        private String studentName;
        private String studentNo;
        private String clazzName;
        private String majorName;
        private String term;
        private String topic;
        private String content;
        private String status;
        private String statusLabel;

        /** 双维度：是否已发布 */
        private boolean published;

        private LocalDateTime publishedAt;

        /** 最近一次撤回时间：非空表示"曾发布后撤回"，前端据此渲染 [曾发布·已撤回] */
        private LocalDateTime withdrawnAt;

        private boolean everWithdrawn;
        private LocalDateTime submitAt;
        private LocalDateTime createdAt;

        /** 最后修改时间：保存、提交、审核、发布/撤回都会刷新；列表按它倒序，列名「更新」必须取此字段 */
        private LocalDateTime updatedAt;

        private Integer version;

        /** 情感标签（未分析时为 null） */
        private String sentimentLabel;

        private boolean lowConfidence;

        /** 最近一次审核意见 */
        private String lastOpinion;

        private LocalDateTime lastAuditAt;
        private String lastAuditor;

        /**
         * 该点评对应的学生反馈：null = 未反馈，1 = 有帮助，0 = 帮助不大。
         *
         * <p>三端语义一致（一条点评只对应一个学生），因此同一个字段可直接复用： 学生端是「我反馈过吗」，导师/教务端是「这条点评收到反馈了吗」。
         */
        private Integer feedbackHelpful;
    }

    /** 详情（含审核时间线） */
    @Data
    public static class Detail {
        private Row comment;
        private List<AuditItem> audits;
        private Object sentiment;

        /** 学生反馈；未反馈时为 null */
        private FeedbackView feedback;
    }

    /** 学生反馈（内容视图） */
    @Data
    public static class FeedbackView {
        private Integer helpful;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 提交 / 更新反馈。
     *
     * <p>{@code helpful} 必填：没有表态的"纯文字反馈"无法区分是提问还是抱怨， 而这条记录的唯一用途就是判断学生对点评是否有回应，缺了它记录就没有意义。
     */
    @Data
    public static class FeedbackReq {
        @NotNull(message = "请选择这条点评是否对你有帮助") private Boolean helpful;

        @Size(max = 500, message = "补充说明不超过 500 字")
        private String content;
    }

    @Data
    public static class AuditItem {
        private Long id;
        private String result;

        /** SUBMIT / APPROVE_ONLY / APPROVE_PUBLISH / RETURN / REJECT / PUBLISH / WITHDRAW */
        private String actionType;

        private String resultLabel;
        private String opinion;
        private String auditorName;
        private Integer round;
        private LocalDateTime auditAt;
        private String beforeContent;
    }
}
