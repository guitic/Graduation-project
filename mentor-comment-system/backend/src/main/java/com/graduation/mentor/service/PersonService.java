package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Major;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.TeacherStudent;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

    /** 教务：全部学生（含班级、导师信息） */
    public List<Map<String, Object>> allStudents(Long clazzId) {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看全部学生");
        }
        LambdaQueryWrapper<Student> w = new LambdaQueryWrapper<Student>().eq(Student::getStatus, 1);
        if (clazzId != null) {
            w.eq(Student::getClazzId, clazzId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Student s : studentMapper.selectList(w.orderByAsc(Student::getStudentNo))) {
            Map<String, Object> m = studentMap(s);
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
