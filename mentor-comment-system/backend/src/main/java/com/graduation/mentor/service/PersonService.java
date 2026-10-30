package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.dto.PersonDtos;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Major;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.TeacherStudent;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import com.graduation.mentor.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人员与指导关系服务。
 *
 * <p>导师选择学生的下拉框只能看到自己指导的学生 —— 这是数据权限的第一道闸门， 但**不能只靠下拉框**：提交接口会用 {@link
 * CommentService#assertGuiding} 再校验一次。
 */
@Service
@RequiredArgsConstructor
public class PersonService {

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final TeacherStudentMapper teacherStudentMapper;
    private final com.graduation.mentor.mapper.CommentMapper commentMapper;
    private final AuthContext authContext;
    private final OperLogService operLogService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 新建档案时的初始密码。**不落库明文**，只在创建 / 重置的那一次响应里返回。
     *
     * <p>可配置是为了让学校能改成统一的迎新密码；默认值写死在代码里， 避免"忘了配环境变量导致初始密码为空"。
     */
    @Value("${app.person.default-password:Mentor@2026}")
    private String defaultPassword;

    public List<Map<String, Object>> teacherOptions() {
        List<Teacher> list =
                teacherMapper.selectList(
                        new LambdaQueryWrapper<Teacher>()
                                .eq(Teacher::getStatus, 1)
                                .orderByAsc(Teacher::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Teacher t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("teacherNo", t.getTeacherNo());
            m.put("title", t.getTitle());
            out.add(m);
        }
        return out;
    }

    /** 当前导师指导的学生 */
    public List<Map<String, Object>> myStudents() {
        Long teacherId = authContext.currentTeacherId();
        List<TeacherStudent> rels =
                teacherStudentMapper.selectList(
                        new LambdaQueryWrapper<TeacherStudent>()
                                .eq(TeacherStudent::getTeacherId, teacherId)
                                .eq(TeacherStudent::getStatus, 1));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeacherStudent r : rels) {
            Student s = studentMapper.selectById(r.getStudentId());
            if (s == null) {
                continue;
            }
            out.add(studentMap(s));
        }
        return out;
    }

    /** 教务：按导师查学生 */
    public List<Map<String, Object>> studentsOfTeacher(Long teacherId) {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可按导师查询学生名单");
        }
        List<TeacherStudent> rels =
                teacherStudentMapper.selectList(
                        new LambdaQueryWrapper<TeacherStudent>()
                                .eq(TeacherStudent::getTeacherId, teacherId)
                                .eq(TeacherStudent::getStatus, 1));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeacherStudent r : rels) {
            Student s = studentMapper.selectById(r.getStudentId());
            if (s != null) {
                Map<String, Object> m = studentMap(s);
                m.put("relationId", r.getId());
                out.add(m);
            }
        }
        return out;
    }

    /** 教务：全部学生（含班级、导师信息）。includeDisabled=true 时含停用档案（管理页用） */
    public List<Map<String, Object>> allStudents(Long clazzId, boolean includeDisabled) {
        requireAdmin();
        LambdaQueryWrapper<Student> w = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            w.eq(Student::getStatus, 1);
        }
        if (clazzId != null) {
            w.eq(Student::getClazzId, clazzId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Student s : studentMapper.selectList(w.orderByAsc(Student::getStudentNo))) {
            Map<String, Object> m = studentMap(s);
            m.put("status", s.getStatus());
            m.put("gender", s.getGender());
            m.put("phone", s.getPhone());
            m.put("email", s.getEmail());
            m.put("userId", s.getUserId());
            List<TeacherStudent> rels =
                    teacherStudentMapper.selectList(
                            new LambdaQueryWrapper<TeacherStudent>()
                                    .eq(TeacherStudent::getStudentId, s.getId())
                                    .eq(TeacherStudent::getStatus, 1));
            List<String> tNames = new ArrayList<>();
            for (TeacherStudent r : rels) {
                Teacher t = teacherMapper.selectById(r.getTeacherId());
                if (t != null) {
                    tNames.add(t.getName());
                }
            }
            m.put("teachers", String.join("、", tNames));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> clazzOptions() {
        List<Clazz> list =
                clazzMapper.selectList(new LambdaQueryWrapper<Clazz>().orderByAsc(Clazz::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Clazz c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("grade", c.getGrade());
            Major ma = majorMapper.selectById(c.getMajorId());
            m.put("majorName", ma == null ? null : ma.getName());
            m.put("college", ma == null ? null : ma.getCollege());
            out.add(m);
        }
        return out;
    }

    public List<Major> majorOptions() {
        return majorMapper.selectList(null);
    }

    /** 学期下拉（从点评表里取已有学期，避免硬编码） */
    public List<String> termOptions() {
        return commentMapper
                .selectList(
                        new LambdaQueryWrapper<com.graduation.mentor.entity.Comment>()
                                .select(com.graduation.mentor.entity.Comment::getTerm))
                .stream()
                .map(com.graduation.mentor.entity.Comment::getTerm)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .toList();
    }

    @Transactional
    public void bind(Long teacherId, List<Long> studentIds) {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可维护指导关系");
        }
        for (Long sid : studentIds) {
            Long exists =
                    teacherStudentMapper.selectCount(
                            new LambdaQueryWrapper<TeacherStudent>()
                                    .eq(TeacherStudent::getTeacherId, teacherId)
                                    .eq(TeacherStudent::getStudentId, sid));
            if (exists != null && exists > 0) {
                continue;
            }
            TeacherStudent rel = new TeacherStudent();
            rel.setTeacherId(teacherId);
            rel.setStudentId(sid);
            rel.setRelationType("学业导师");
            rel.setStatus(1);
            rel.setCreatedAt(LocalDateTime.now());
            teacherStudentMapper.insert(rel);
        }
        operLogService.record(
                "BIND_TEACHER_STUDENT", "TEACHER", teacherId, "绑定学生数：" + studentIds.size());
    }

    @Transactional
    public void unbind(Long id) {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可维护指导关系");
        }
        teacherStudentMapper.deleteById(id);
        operLogService.record("UNBIND_TEACHER_STUDENT", "TEACHER_STUDENT", id, "解除指导关系");
    }

    // ==================================================================
    // 档案维护（教务）
    //
    // 三条原则：
    // 1) 停用而非删除。点评、审核记录、操作日志都通过外键指向档案，物理删除会让历史报表
    //    出现"无主点评"——数据还在，人没了。status=0 保留可追溯性。
    // 2) 档案与账号同事务。分开写会出现"有档案不能登录"或"能登录没有档案"，
    //    后者更糟：登录成功但 currentTeacherId() 抛 403，用户看到的是"系统坏了"。
    // 3) 工号/学号即登录名，二者不允许分叉。改工号必须同步改用户名，且要做占用检查。
    // ==================================================================

    /** 管理页导师列表：含停用、联系方式与指导学生数 */
    public List<Map<String, Object>> teacherRows() {
        requireAdmin();
        List<Teacher> list =
                teacherMapper.selectList(
                        new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getTeacherNo));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Teacher t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("teacherNo", t.getTeacherNo());
            m.put("title", t.getTitle());
            m.put("college", t.getCollege());
            m.put("phone", t.getPhone());
            m.put("email", t.getEmail());
            m.put("status", t.getStatus());
            m.put("userId", t.getUserId());
            m.put(
                    "studentCount",
                    teacherStudentMapper.selectCount(
                            new LambdaQueryWrapper<TeacherStudent>()
                                    .eq(TeacherStudent::getTeacherId, t.getId())
                                    .eq(TeacherStudent::getStatus, 1)));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public PersonDtos.Created createTeacher(PersonDtos.TeacherReq req) {
        requireAdmin();
        assertTeacherNoFree(req.getTeacherNo(), null);
        User u =
                createUser(
                        req.getTeacherNo(),
                        req.getName(),
                        "TEACHER",
                        req.getPhone(),
                        req.getEmail());

        Teacher t = new Teacher();
        t.setUserId(u.getId());
        t.setTeacherNo(req.getTeacherNo());
        t.setName(req.getName());
        t.setTitle(req.getTitle());
        t.setCollege(req.getCollege());
        t.setPhone(req.getPhone());
        t.setEmail(req.getEmail());
        t.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        teacherMapper.insert(t);

        operLogService.record(
                "CREATE_TEACHER",
                "TEACHER",
                t.getId(),
                "新增导师 " + t.getName() + "(" + t.getTeacherNo() + ")");
        return created(t.getId(), u.getUsername());
    }

    @Transactional
    public void updateTeacher(Long id, PersonDtos.TeacherReq req) {
        requireAdmin();
        Teacher t = teacherMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "导师不存在");
        }
        if (!req.getTeacherNo().equals(t.getTeacherNo())) {
            assertTeacherNoFree(req.getTeacherNo(), id);
        }
        t.setTeacherNo(req.getTeacherNo());
        t.setName(req.getName());
        t.setTitle(req.getTitle());
        t.setCollege(req.getCollege());
        t.setPhone(req.getPhone());
        t.setEmail(req.getEmail());
        if (req.getStatus() != null) {
            t.setStatus(req.getStatus());
        }
        teacherMapper.updateById(t);

        syncUser(
                t.getUserId(),
                req.getTeacherNo(),
                req.getName(),
                req.getPhone(),
                req.getEmail(),
                t.getStatus());
        operLogService.record("UPDATE_TEACHER", "TEACHER", id, "修改导师档案 " + t.getName());
    }

    /**
     * 停用导师。
     *
     * <p>{@code force=false} 时若存在未完结点评（草稿 / 待审核 / 退回），拒绝停用并报出条数。
     * 理由：停用后该导师无法登录，这些点评会永久卡在流程里，而流程规范正是本系统的研究目的。 教务确认后可用 {@code force=true} 强制停用（例如导师离职、点评需转交他人）。
     */
    @Transactional
    public void disableTeacher(Long id, boolean force) {
        requireAdmin();
        Teacher t = teacherMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "导师不存在");
        }
        long open =
                commentMapper.selectCount(
                        new LambdaQueryWrapper<com.graduation.mentor.entity.Comment>()
                                .eq(com.graduation.mentor.entity.Comment::getTeacherId, id)
                                .in(
                                        com.graduation.mentor.entity.Comment::getStatus,
                                        List.of(
                                                CommentStatus.DRAFT,
                                                CommentStatus.PENDING,
                                                CommentStatus.RETURNED)));
        if (open > 0 && !force) {
            throw new BizException(409, "该导师还有 " + open + " 条未完结点评（草稿/待审核/退回），请先处理完毕，或确认强制停用");
        }
        t.setStatus(0);
        teacherMapper.updateById(t);
        setUserStatus(t.getUserId(), 0);
        operLogService.record("DISABLE_TEACHER", "TEACHER", id, "停用导师，未完结点评 " + open + " 条");
    }

    @Transactional
    public void enableTeacher(Long id) {
        requireAdmin();
        Teacher t = teacherMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "导师不存在");
        }
        t.setStatus(1);
        teacherMapper.updateById(t);
        setUserStatus(t.getUserId(), 1);
        operLogService.record("ENABLE_TEACHER", "TEACHER", id, "启用导师 " + t.getName());
    }

    // ------------------------------------------------------------------ 学生档案

    @Transactional
    public PersonDtos.Created createStudent(PersonDtos.StudentReq req) {
        requireAdmin();
        assertStudentNoFree(req.getStudentNo(), null);
        if (clazzMapper.selectById(req.getClazzId()) == null) {
            throw new BizException(400, "班级不存在");
        }
        User u =
                createUser(
                        req.getStudentNo(),
                        req.getName(),
                        "STUDENT",
                        req.getPhone(),
                        req.getEmail());

        Student s = new Student();
        s.setUserId(u.getId());
        s.setStudentNo(req.getStudentNo());
        s.setName(req.getName());
        s.setClazzId(req.getClazzId());
        s.setGender(req.getGender());
        s.setPhone(req.getPhone());
        s.setEmail(req.getEmail());
        s.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        studentMapper.insert(s);

        operLogService.record(
                "CREATE_STUDENT",
                "STUDENT",
                s.getId(),
                "新增学生 " + s.getName() + "(" + s.getStudentNo() + ")");
        return created(s.getId(), u.getUsername());
    }

    @Transactional
    public void updateStudent(Long id, PersonDtos.StudentReq req) {
        requireAdmin();
        Student s = studentMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "学生不存在");
        }
        if (!req.getStudentNo().equals(s.getStudentNo())) {
            assertStudentNoFree(req.getStudentNo(), id);
        }
        if (clazzMapper.selectById(req.getClazzId()) == null) {
            throw new BizException(400, "班级不存在");
        }
        s.setStudentNo(req.getStudentNo());
        s.setName(req.getName());
        s.setClazzId(req.getClazzId());
        s.setGender(req.getGender());
        s.setPhone(req.getPhone());
        s.setEmail(req.getEmail());
        if (req.getStatus() != null) {
            s.setStatus(req.getStatus());
        }
        studentMapper.updateById(s);

        syncUser(
                s.getUserId(),
                req.getStudentNo(),
                req.getName(),
                req.getPhone(),
                req.getEmail(),
                s.getStatus());
        operLogService.record("UPDATE_STUDENT", "STUDENT", id, "修改学生档案 " + s.getName());
    }

    @Transactional
    public void disableStudent(Long id) {
        requireAdmin();
        Student s = studentMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "学生不存在");
        }
        s.setStatus(0);
        studentMapper.updateById(s);
        setUserStatus(s.getUserId(), 0);
        operLogService.record("DISABLE_STUDENT", "STUDENT", id, "停用学生 " + s.getName());
    }

    @Transactional
    public void enableStudent(Long id) {
        requireAdmin();
        Student s = studentMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "学生不存在");
        }
        s.setStatus(1);
        studentMapper.updateById(s);
        setUserStatus(s.getUserId(), 1);
        operLogService.record("ENABLE_STUDENT", "STUDENT", id, "启用学生 " + s.getName());
    }

    /** 重置密码：返回新密码（一次性展示），用于管理员帮用户恢复 */
    @Transactional
    public String resetPassword(Long userId) {
        requireAdmin();
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(404, "账号不存在");
        }
        String pwd = defaultPassword;
        u.setPassword(passwordEncoder.encode(pwd));
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        operLogService.record("RESET_PASSWORD", "USER", userId, "重置账号 " + u.getUsername());
        return pwd;
    }

    // ------------------------------------------------------------------ 档案维护内部工具

    private void requireAdmin() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可维护师生档案");
        }
    }

    private User createUser(
            String username, String realName, String role, String phone, String email) {
        Long dup =
                userMapper.selectCount(
                        new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (dup != null && dup > 0) {
            throw new BizException(409, "登录名 " + username + " 已被占用");
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(defaultPassword));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        u.setPhone(phone);
        u.setEmail(email);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    /**
     * 档案修改后同步账号。
     *
     * <p>用户名冲突时不静默跳过：档案改了工号、账号还叫旧工号，用户会用自己的新工号登录失败， 而管理员看到的是"保存成功"。这里直接报错，让冲突在保存时暴露。
     */
    private void syncUser(
            Long userId,
            String username,
            String realName,
            String phone,
            String email,
            Integer status) {
        if (userId == null) {
            return;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return;
        }
        if (!username.equals(u.getUsername())) {
            Long dup =
                    userMapper.selectCount(
                            new LambdaQueryWrapper<User>()
                                    .eq(User::getUsername, username)
                                    .ne(User::getId, userId));
            if (dup != null && dup > 0) {
                throw new BizException(409, "登录名 " + username + " 已被其它账号占用");
            }
            u.setUsername(username);
        }
        u.setRealName(realName);
        u.setPhone(phone);
        u.setEmail(email);
        if (status != null) {
            u.setStatus(status);
        }
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
    }

    private void setUserStatus(Long userId, int status) {
        if (userId == null) {
            return;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return;
        }
        u.setStatus(status);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
    }

    private void assertTeacherNoFree(String teacherNo, Long excludeId) {
        LambdaQueryWrapper<Teacher> w =
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getTeacherNo, teacherNo);
        if (excludeId != null) {
            w.ne(Teacher::getId, excludeId);
        }
        Long n = teacherMapper.selectCount(w);
        if (n != null && n > 0) {
            throw new BizException(409, "工号 " + teacherNo + " 已存在");
        }
    }

    private void assertStudentNoFree(String studentNo, Long excludeId) {
        LambdaQueryWrapper<Student> w =
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo);
        if (excludeId != null) {
            w.ne(Student::getId, excludeId);
        }
        Long n = studentMapper.selectCount(w);
        if (n != null && n > 0) {
            throw new BizException(409, "学号 " + studentNo + " 已存在");
        }
    }

    private PersonDtos.Created created(Long id, String username) {
        PersonDtos.Created c = new PersonDtos.Created();
        c.setId(id);
        c.setUsername(username);
        c.setInitialPassword(defaultPassword);
        return c;
    }

    private Map<String, Object> studentMap(Student s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("studentNo", s.getStudentNo());
        Clazz c = clazzMapper.selectById(s.getClazzId());
        m.put("clazzName", c == null ? null : c.getName());
        m.put("clazzId", s.getClazzId());
        return m;
    }

    /** 供其它服务复用：学生ID → 姓名 */
    public Map<Long, String> studentNameMap() {
        Map<Long, String> m = new HashMap<>();
        studentMapper.selectList(null).forEach(s -> m.put(s.getId(), s.getName()));
        return m;
    }
}
