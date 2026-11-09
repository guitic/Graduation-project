package com.graduation.mentor.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.AuthDtos;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 新密码长度下限：低于 8 位的口令在离线爆破下基本等于没有 */
    private static final int PWD_MIN = 8;

    /** 上限与 {@code sys_user.password}(varchar 100) 及 BCrypt 输入习惯对齐 */
    private static final int PWD_MAX = 32;

    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperLogService operLogService;

    /** 演示能力开关：非 demo 环境不暴露演示账号（含统一密码） */
    @Value("${app.demo-seed.enabled:false}")
    private boolean demoSeedEnabled;

    public AuthDtos.LoginResp login(AuthDtos.LoginReq req) {
        User user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getUsername, req.getUsername())
                                .last("limit 1"));
        // 统一提示，不区分"用户不存在"和"密码错误"，避免账号枚举
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已停用，请联系教务管理员");
        }

        StpUtil.login(user.getId());
        StpUtil.getSession().set("role", user.getRole());
        StpUtil.getSession().set("username", user.getUsername());

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        AuthDtos.LoginResp resp = new AuthDtos.LoginResp();
        resp.setToken(StpUtil.getTokenValue());
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRole(user.getRole());
        resp.setRoleLabel(roleLabel(user.getRole()));
        fillBiz(resp, user);
        return resp;
    }

    /**
     * 修改本人密码。
     *
     * <p><b>为什么改完强制登出</b>：密码是凭证，改密码的语义是"旧凭证作废"。 若保留当前会话，攻击者用旧密码登录拿到的 token 依然有效，改密码就只改了数据库里的一个字段。
     * 因此这里 {@code logout(userId)} 踢掉该账号的全部会话，前端收到成功响应后跳登录页。
     *
     * <p><b>为什么先记日志再登出</b>：{@code OperLogService} 需要从登录态取操作人，
     * 登出后再记会取不到人，日志就变成一条"匿名改密"——恰恰是审计最需要看清的场景。
     */
    public void changePassword(Long userId, AuthDtos.ChangePasswordReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "账号不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            // 不用"账号不存在"式统一提示：此处用户已登录，账号身份已知，模糊化没有收益
            throw new BizException(400, "当前密码不正确");
        }
        String newPwd = req.getNewPassword();
        validateNewPassword(newPwd);
        if (passwordEncoder.matches(newPwd, user.getPassword())) {
            throw new BizException(400, "新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPwd));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        operLogService.record("修改密码", "USER", userId, "用户 " + user.getUsername() + " 修改本人密码");
        StpUtil.logout(userId);
    }

    /**
     * 新密码强度校验。
     *
     * <p>规则刻意保持"低门槛但有效"：长度 8-32 + 字母与数字兼具。 不强制大小写与特殊字符——本项目是校内系统，口令过于苛刻会导致 用户把密码写在便签上贴在显示器边，反而更不安全。
     */
    private void validateNewPassword(String pwd) {
        if (pwd.length() < PWD_MIN || pwd.length() > PWD_MAX) {
            throw new BizException(400, "新密码长度需为 " + PWD_MIN + "-" + PWD_MAX + " 位");
        }
        boolean hasLetter = pwd.chars().anyMatch(Character::isLetter);
        boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(400, "新密码需同时包含字母和数字");
        }
    }

    public AuthDtos.MeResp me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "账号不存在");
        }
        AuthDtos.MeResp me = new AuthDtos.MeResp();
        me.setUserId(user.getId());
        me.setUsername(user.getUsername());
        me.setRealName(user.getRealName());
        me.setRole(user.getRole());
        me.setRoleLabel(roleLabel(user.getRole()));

        AuthDtos.LoginResp tmp = new AuthDtos.LoginResp();
        fillBiz(tmp, user);
        me.setBizId(tmp.getBizId());
        me.setBizNo(tmp.getBizNo());
        me.setClazzName(tmp.getClazzName());
        if ("TEACHER".equals(user.getRole())) {
            Teacher t = teacherMapper.selectById(tmp.getBizId());
            me.setCollege(t == null ? null : t.getCollege());
        }
        return me;
    }

    private void fillBiz(AuthDtos.LoginResp resp, User user) {
        switch (user.getRole()) {
            case "TEACHER" -> {
                Teacher t =
                        teacherMapper.selectOne(
                                new LambdaQueryWrapper<Teacher>()
                                        .eq(Teacher::getUserId, user.getId())
                                        .last("limit 1"));
                if (t != null) {
                    resp.setBizId(t.getId());
                    resp.setBizNo(t.getTeacherNo());
                }
            }
            case "STUDENT" -> {
                Student s =
                        studentMapper.selectOne(
                                new LambdaQueryWrapper<Student>()
                                        .eq(Student::getUserId, user.getId())
                                        .last("limit 1"));
                if (s != null) {
                    resp.setBizId(s.getId());
                    resp.setBizNo(s.getStudentNo());
                    Clazz c = clazzMapper.selectById(s.getClazzId());
                    resp.setClazzName(c == null ? null : c.getName());
                }
            }
            default -> {
                // 教务管理员无业务档案
            }
        }
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case "ADMIN" -> "教务管理员";
            case "TEACHER" -> "学业导师";
            case "STUDENT" -> "学生";
            default -> role;
        };
    }

    /**
     * 演示账号列表（登录页 chip 用）。
     *
     * <p><b>为什么必须跟着 demo 开关走</b>：这个接口在鉴权白名单里（登录前就要取 chip）， 若在真实环境也返回"统一密码 123456"，
     * 等于把可登录的账号口令公开写在匿名接口上。生产环境返回空列表，登录页自动隐藏演示区。
     */
    public Map<String, Object> demoAccounts() {
        if (!demoSeedEnabled) {
            return Map.of("enabled", false, "accounts", java.util.List.of());
        }
        return Map.of(
                "enabled",
                true,
                "password",
                "123456",
                "accounts",
                java.util.List.of(
                        Map.of("role", "导师", "username", "teacher01", "label", "张明远 · 学业导师"),
                        Map.of("role", "导师", "username", "teacher02", "label", "李静 · 学业导师"),
                        Map.of("role", "学生", "username", "student01", "label", "王小明 · 计科2201"),
                        Map.of("role", "教务", "username", "admin", "label", "教务处 · 王主任")));
    }
}
