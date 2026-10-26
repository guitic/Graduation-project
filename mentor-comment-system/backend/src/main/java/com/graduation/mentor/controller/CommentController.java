package com.graduation.mentor.controller;

import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 点评接口。
 * <p>路由按角色分组，但**真正的权限判定在 Service 层**，不依赖路由分组。
 */
@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 导师端列表 */
    @GetMapping("/page")
    public R<PageResult<CommentDtos.Row>> page(CommentDtos.Query q) {
        return R.ok(commentService.pageForTeacher(q));
    }

    /** 教务端列表（全量） */
    @GetMapping("/admin-page")
    public R<PageResult<CommentDtos.Row>> adminPage(CommentDtos.Query q) {
        return R.ok(commentService.pageForAdmin(q));
    }

    /** 学生端列表（仅本人已发布） */
    @GetMapping("/student-page")
    public R<PageResult<CommentDtos.Row>> studentPage(CommentDtos.Query q) {
        return R.ok(commentService.pageForStudent(q));
    }

    @GetMapping("/{id}")
    public R<CommentDtos.Detail> detail(@PathVariable Long id) {
        return R.ok(commentService.detail(id));
    }

    @PostMapping
    public R<Map<String, Object>> save(@Valid @RequestBody CommentDtos.SaveReq req) {
        Long id = commentService.save(req);
        return R.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CommentDtos.SaveReq req) {
        commentService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        commentService.submit(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return R.ok();
    }
}
