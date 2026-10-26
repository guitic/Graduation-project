package com.graduation.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 认证相关 DTO */
public class AuthDtos {

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResp {
        private String token;
        private Long userId;
        private String username;
        private String realName;
        private String role;
        private String roleLabel;

        /** 导师/学生业务ID，前端据此判断可操作范围 */
        private Long bizId;

        private String bizNo;
        private String clazzName;
    }

    @Data
    public static class MeResp {
        private Long userId;
        private String username;
        private String realName;
        private String role;
        private String roleLabel;
        private Long bizId;
        private String bizNo;
        private String clazzName;
        private String college;
    }
}
