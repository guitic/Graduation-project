package com.graduation.mentor.controller;

import com.graduation.mentor.common.R;
import com.graduation.mentor.service.PersonService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/person")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @GetMapping("/teachers")
    public R<List<Map<String, Object>>> teachers() {
        return R.ok(personService.teacherOptions());
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

    /** 教务：全部学生 */
    @GetMapping("/students")
    public R<List<Map<String, Object>>> students(@RequestParam(required = false) Long clazzId) {
        return R.ok(personService.allStudents(clazzId));
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
}
