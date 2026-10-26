package com.graduation.mentor.common;

import cn.dev33.satoken.stp.StpUtil;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 当前登录上下文。
 *
 * <p>把 Sa-Token 的登录态翻译成业务身份（用户 / 角色 / 导师ID）， 避免每个 Service 各写一遍。
 */
@Component
public class AuthContext {

    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;

    public AuthContext(UserMapper userMapper, TeacherMapper teacherMapper) {
        this.userMapper = userMapper;
        this.teacherMapper = teacherMapper;
    }

    public Long userId() {
        return StpUtil.getLoginIdAsLong();
    }

    public User currentUser() {
        User u = userMapper.selectById(userId());
        if (u == null) {
            throw new BizException(401, "账号不存在");
        }
        return u;
    }

    public String role() {
        return currentUser().getRole();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role());
    }

    public boolean isTeacher() {
        return "TEACHER".equals(role());
    }

    public boolean isStudent() {
        return "STUDENT".equals(role());
    }

    /** 当前登录人对应的导师ID；非导师抛 403 */
    public Long currentTeacherId() {
        if (!isTeacher()) {
            throw BizException.forbidden("仅导师可执行该操作");
        }
        Teacher t =
                teacherMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                        Teacher>()
                                .eq(Teacher::getUserId, userId())
                                .last("limit 1"));
        if (t == null) {
            throw BizException.forbidden("当前账号未绑定导师档案");
        }
        return t.getId();
    }
}
