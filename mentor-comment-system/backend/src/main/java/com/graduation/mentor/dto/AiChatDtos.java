package com.graduation.mentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** AI 小助手对话 DTO */
public class AiChatDtos {

    /**
     * 对话请求。
     *
     * <p><b>不带"角色/场景"字段</b>：场景由服务端按当前登录身份决定， 前端传什么都不生效。否则学生可以传 {@code scene=admin} 拿到教务提示词，
     * 进而套出内部口径——提示词也是权限边界的一部分。
     */
    @Data
    public static class ChatReq {

        @NotEmpty(message = "对话内容不能为空")
        @Size(max = 20, message = "单次对话最多 20 条消息")
        @Valid
        private List<Msg> messages;
    }

    @Data
    public static class Msg {

        /** user / assistant */
        @NotBlank(message = "role 不能为空")
        private String role;

        @NotBlank(message = "内容不能为空")
        @Size(max = 2000, message = "单条消息不超过 2000 字")
        private String content;
    }

    /** 对话响应 */
    @Data
    public static class ChatResp {

        /** 助手回复。降级时为固定说明文案，不抛异常 */
        private String reply;

        /** LLM / DISABLED */
        private String source;

        private String model;

        private long latencyMs;

        /** 今日剩余调用次数；-1 表示不限（限流关闭时） */
        private int remainingToday;
    }
}
