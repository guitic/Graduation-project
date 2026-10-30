package com.graduation.mentor.controller;

import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.PersonDtos;
import com.graduation.mentor.service.PersonService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员与指导关系接口。
 *
 * <p>读写分两类权限：查询类接口给导师端下拉框用（本人范围内），维护类接口一律 仅教务管理员，权限在 Service 层再次校验——Controller 不承担鉴权职责， 因为同一个
 * Service 方法可能被别的入口复用。
 */
@RestController
@RequestMapping("/api/person")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    // ------------------------------------------------------------------ 查询

    @GetMapping("/teachers")
    public R<List<Map<String, Object>>> teachers() {
        return R.ok(personService.teacherOptions());
    }

    /** 管理页导师列表：含停用档案、联系方式、指导学生数 */
    @GetMapping("/teachers/all")
    public R<List<Map<String, Object>>> allTeachers() {
        return R.ok(personService.teacherRows());
    }

    /** 当前导师指导的学生（导师端下拉框） */
    @GetMapping("/my-students")
    public R<List<Map<String, Object>>> myStudents() {
        return R.ok(personService.myStudents());
    }

    /** 教务：按导师查学生 */
    @GetMapping("/students-of-teacher")
    public R<List<Map<String, Object>>> studentsOfTeacher(@RequestParam Long teacherId) {
        return R.ok(personService.studentsOfTeacher(teacherId));
    }

    /** 教务：学生列表。includeDisabled=true 时含停用档案（管理页用） */
    @GetMapping("/students")
    public R<List<Map<String, Object>>> students(
            @RequestParam(required = false) Long clazzId,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return R.ok(personService.allStudents(clazzId, includeDisabled));
    }

    @GetMapping("/clazz")
    public R<List<Map<String, Object>>> clazz() {
        return R.ok(personService.clazzOptions());
    }

    @GetMapping("/majors")
    public R<List<?>> majors() {
        return R.ok(personService.majorOptions());
    }

    @GetMapping("/terms")
    public R<List<String>> terms() {
        return R.ok(personService.termOptions());
    }

    // ------------------------------------------------------------------ 指导关系

    @PostMapping("/bind")
    public R<Void> bind(@RequestBody Map<String, Object> body) {
        Long teacherId = Long.valueOf(String.valueOf(body.get("teacherId")));
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.get("studentIds");
        List<Long> ids = raw.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        personService.bind(teacherId, ids);
        return R.ok();
    }

    @DeleteMapping("/bind/{id}")
    public R<Void> unbind(@PathVariable Long id) {
        personService.unbind(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 导师档案

    @PostMapping("/teachers")
    public R<PersonDtos.Created> createTeacher(@Valid @RequestBody PersonDtos.TeacherReq req) {
        return R.ok(personService.createTeacher(req));
    }

    @PutMapping("/teachers/{id}")
    public R<Void> updateTeacher(
            @PathVariable Long id, @Valid @RequestBody PersonDtos.TeacherReq req) {
        personService.updateTeacher(id, req);
        return R.ok();
    }

    /** 停用导师。force=false 时若有未完结点评会返回 409 与条数，由前端二次确认后带 force=true 重试 */
    @PostMapping("/teachers/{id}/disable")
    public R<Void> disableTeacher(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        personService.disableTeacher(id, force);
        return R.ok();
    }

    @PostMapping("/teachers/{id}/enable")
    public R<Void> enableTeacher(@PathVariable Long id) {
        personService.enableTeacher(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 学生档案

    @PostMapping("/students")
    public R<PersonDtos.Created> createStudent(@Valid @RequestBody PersonDtos.StudentReq req) {
        return R.ok(personService.createStudent(req));
    }

    @PutMapping("/students/{id}")
    public R<Void> updateStudent(
            @PathVariable Long id, @Valid @RequestBody PersonDtos.StudentReq req) {
        personService.updateStudent(id, req);
        return R.ok();
    }

    @PostMapping("/students/{id}/disable")
    public R<Void> disableStudent(@PathVariable Long id) {
        personService.disableStudent(id);
        return R.ok();
    }

    @PostMapping("/students/{id}/enable")
    public R<Void> enableStudent(@PathVariable Long id) {
        personService.enableStudent(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 账号

    /** 重置密码：返回新密码，仅此一次 */
    @PostMapping("/users/{id}/reset-password")
    public R<Map<String, String>> resetPassword(@PathVariable Long id) {
        return R.ok(Map.of("password", personService.resetPassword(id)));
    }
}
