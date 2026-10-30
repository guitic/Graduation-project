package com.graduation.mentor.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 摘要建议相关 DTO */
public class AiDtos {

    /**
     * 摘要请求：二选一。
     *
     * <ul>
     *   <li>{@code commentId}：对已保存的点评生成（服务端取正文，避免前端传错版本）；
     *   <li>{@code content}：对编辑中的草稿生成（不落库，纯辅助）。
     * </ul>
     */
    @Data
    public static class SummaryReq {

        private Long commentId;

        /** 草稿正文。上限与点评正文校验保持一致，防止超长文本打爆模型上下文 */
        @Size(max = 5000, message = "点评正文超长")
        private String content;

        private String topic;
    }
}
