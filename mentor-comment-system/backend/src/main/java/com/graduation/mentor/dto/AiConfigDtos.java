package com.graduation.mentor.dto;

import lombok.Data;

/** AI 摘要配置（教务后台）DTO */
public class AiConfigDtos {

    /**
     * 配置视图。
     *
     * <p><b>永远不返回明文 Key</b>，只返回"是否已配置"和掩码。教务自己填的 Key 也看不到原文——
     * 想看原文只能重新填一次。这不是过度谨慎：一个能读回密钥的接口，等于把"密钥保管" 降级成"任何拿到管理员会话的人都能导出密钥"。
     */
    @Data
    public static class View {

        private boolean enabled;

        private String baseUrl;

        private String model;

        /** 是否已配置密钥 */
        private boolean keyConfigured;

        /** 掩码，如 sk-****3f2a；未配置时为空串 */
        private String keyMasked;

        /** 配置来源：DB（教务后台）/ ENV（环境变量）/ NONE（都没有） */
        private String source;

        /** 来源的中文说明，前端直接展示 */
        private String sourceLabel;

        /** 当前是否真的可用（开关 + Key 都齐） */
        private boolean usable;
    }

    /**
     * 修改请求。**字段为 null 表示不修改**，空串表示清除该键并回落环境变量。
     *
     * <p>这个语义是为了让前端"只改模型名"时不必回填密钥——回填就意味着密钥要先传到浏览器。
     */
    @Data
    public static class Req {

        private Boolean enabled;

        private String baseUrl;

        private String model;

        /** null=不改；""=清除；其他=设置新密钥 */
        private String apiKey;
    }
}
