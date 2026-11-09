package com.graduation.mentor.controller;

import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.AuthDtos;
import com.graduation.mentor.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthContext authContext;

    @PostMapping("/auth/login")
    public R<AuthDtos.LoginResp> login(@Valid @RequestBody AuthDtos.LoginReq req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/auth/logout")
    public R<Void> logout() {
        cn.dev33.satoken.stp.StpUtil.logout();
        return R.ok();
    }

    @GetMapping("/auth/me")
    public R<AuthDtos.MeResp> me() {
        return R.ok(authService.me(authContext.userId()));
    }

    /**
     * 修改本人密码（三角色通用）。
     *
     * <p>成功即代表该账号的全部会话已失效，前端应清 token 并跳登录页。 接口不区分角色——改自己的密码不需要权限，只需要知道旧密码。
     */
    @PostMapping("/auth/password")
    public R<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordReq req) {
        authService.changePassword(authContext.userId(), req);
        return R.ok();
    }

    /** 演示账号提示（登录页 chip），无需登录即可获取 */
    @GetMapping("/demo/accounts")
    public R<Map<String, Object>> demoAccounts() {
        return R.ok(authService.demoAccounts());
    }

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of("status", "UP", "app", "mentor-comment-system"));
    }
}
