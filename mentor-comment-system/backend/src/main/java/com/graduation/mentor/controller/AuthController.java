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
