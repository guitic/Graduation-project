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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthDtos.LoginResp login(AuthDtos.LoginReq req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
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
                Teacher t = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getUserId, user.getId()).last("limit 1"));
                if (t != null) {
                    resp.setBizId(t.getId());
                    resp.setBizNo(t.getTeacherNo());
                }
            }
            case "STUDENT" -> {
                Student s = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                        .eq(Student::getUserId, user.getId()).last("limit 1"));
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

    /** 演示账号列表（登录页 chip 用），不含任何真实密码以外的敏感信息 */
    public Map<String, Object> demoAccounts() {
        return Map.of("password", "123456", "accounts", java.util.List.of(
                Map.of("role", "导师", "username", "teacher01", "label", "张明远 · 学业导师"),
                Map.of("role", "导师", "username", "teacher02", "label", "李静 · 学业导师"),
                Map.of("role", "学生", "username", "student01", "label", "王小明 · 计科2201"),
                Map.of("role", "教务", "username", "admin", "label", "教务处 · 王主任")
        ));
    }
}
