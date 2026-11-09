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

    /**
     * 修改本人密码。
     *
     * <p>只收旧密码与新密码两个字段：确认密码是纯前端交互，后端不重复校验—— 后端一旦也校验，两处规则就必须同步，否则会出现"前端放行、后端拒绝"。
     */
    @Data
    public static class ChangePasswordReq {
        @NotBlank(message = "请输入当前密码")
        private String oldPassword;

        @NotBlank(message = "请输入新密码")
        private String newPassword;
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
