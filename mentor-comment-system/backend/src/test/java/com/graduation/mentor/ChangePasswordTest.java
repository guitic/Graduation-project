package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.AuthDtos;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.UserMapper;
import com.graduation.mentor.service.AuthService;
import com.graduation.mentor.service.OperLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 修改密码。
 *
 * <p><b>为什么这个类存在</b>：改密码此前完全不存在——「师生管理」的弹窗却写着「请提醒本人首次登录后修改」。 一个承诺了但不存在的能力，比没有承诺更糟：用户会以为密码已经改过。
 *
 * <p>这里守住四件事：旧密码必须校验、强度规则必须生效、新旧不能相同、成功路径必须踢掉会话。 前三条防止弱口令与误操作，第四条防止"改了密码但旧 token 还能用"。
 */
class ChangePasswordTest {

    private static final Long USER_ID = 1L;
    private static final String OLD_RAW = "old123456";
    private static final String OLD_HASH = "$2a$10$oldhash";
    private static final String NEW_RAW = "new123456";
    private static final String NEW_HASH = "$2a$10$newhash";

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private OperLogService operLogService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        operLogService = mock(OperLogService.class);
        service =
                new AuthService(
                        userMapper,
                        mock(TeacherMapper.class),
                        mock(StudentMapper.class),
                        mock(ClazzMapper.class),
                        passwordEncoder,
                        operLogService);
    }

    private User existingUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername("teacher01");
        u.setPassword(OLD_HASH);
        u.setStatus(1);
        return u;
    }

    private AuthDtos.ChangePasswordReq req(String oldPwd, String newPwd) {
        AuthDtos.ChangePasswordReq r = new AuthDtos.ChangePasswordReq();
        r.setOldPassword(oldPwd);
        r.setNewPassword(newPwd);
        return r;
    }

    @Test
    @DisplayName("旧密码错误 → 400，且不写库、不踢会话")
    void wrongOldPassword() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.changePassword(USER_ID, req("wrong-pwd", NEW_RAW)));

        assertEquals(400, e.getCode());
        assertEquals("当前密码不正确", e.getMessage());
        verify(userMapper, never()).updateById(any());
        verify(operLogService, never()).record(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("新密码不足 8 位 → 400")
    void tooShort() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.matches(OLD_RAW, OLD_HASH)).thenReturn(true);

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.changePassword(USER_ID, req(OLD_RAW, "ab12")));

        assertEquals(400, e.getCode());
        assertEquals("新密码长度需为 8-32 位", e.getMessage());
    }

    @Test
    @DisplayName("新密码缺数字 / 缺字母 → 400")
    void missingLetterOrDigit() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.matches(OLD_RAW, OLD_HASH)).thenReturn(true);

        BizException onlyLetter =
                assertThrows(
                        BizException.class,
                        () -> service.changePassword(USER_ID, req(OLD_RAW, "abcdefghij")));
        assertEquals("新密码需同时包含字母和数字", onlyLetter.getMessage());

        BizException onlyDigit =
                assertThrows(
                        BizException.class,
                        () -> service.changePassword(USER_ID, req(OLD_RAW, "1234567890")));
        assertEquals("新密码需同时包含字母和数字", onlyDigit.getMessage());
    }

    @Test
    @DisplayName("新密码与当前密码相同 → 400")
    void sameAsOld() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        // 旧密码校验通过；新密码也命中同一个哈希
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        BizException e =
                assertThrows(
                        BizException.class,
                        () -> service.changePassword(USER_ID, req(OLD_RAW, OLD_RAW)));

        assertEquals(400, e.getCode());
        assertEquals("新密码不能与当前密码相同", e.getMessage());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("成功：写入新哈希 → 记审计日志 → 踢掉全部会话")
    void success() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser());
        when(passwordEncoder.matches(OLD_RAW, OLD_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_RAW, OLD_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.changePassword(USER_ID, req(OLD_RAW, NEW_RAW));

            // 会话必须被踢：只改库不踢会话，等于旧 token 仍然是有效凭证
            stp.verify(() -> StpUtil.logout(USER_ID));
        }

        verify(userMapper).updateById(any(User.class));
        // 日志必须在登出前写入，否则记录不到操作人
        verify(operLogService).record(anyString(), anyString(), anyLong(), anyString());
    }
}
