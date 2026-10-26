package com.graduation.mentor.controller;

import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学生端接口。
 *
 * <p><b>设计约束（重要）</b>：学生端详情只返回
 * <b>正文 / 导师姓名 / 发布时间</b> 三个字段。
 * <ul>
 *   <li>不返回审核意见 —— 审核意见是教务与导师之间的内部沟通，给学生看会引发误解与冲突；</li>
 *   <li>不返回情感倾向标签 —— 情感分析的对象是"导师写的文本"，不是"对学生的评价"。
 *       若把标签展示给学生，会被误读为导师对自己有负面评价，属语义错位。</li>
 * </ul>
 * 这两个字段在 DTO 层就不存在，而不是"查出来前端不显示"。
 */
@RestController
@RequestMapping("/api/student-comment")
@RequiredArgsConstructor
public class StudentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;

    @GetMapping("/page")
    public R<PageResult<CommentDtos.Row>> page(CommentDtos.Query q) {
        return R.ok(commentService.pageForStudent(q));
    }

    /** 学生详情：只有正文、导师姓名、发布时间 */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        CommentDtos.Detail d = commentService.detail(id);
        CommentDtos.Row c = d.getComment();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("content", c.getContent());
        out.put("teacherName", c.getTeacherName());
        out.put("publishedAt", c.getPublishedAt());
        out.put("term", c.getTerm());
        out.put("topic", c.getTopic());
        return R.ok(out);
    }
}
