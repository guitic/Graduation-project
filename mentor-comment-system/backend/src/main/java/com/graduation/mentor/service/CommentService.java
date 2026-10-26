package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.CommentScope;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.CommentSentiment;
import com.graduation.mentor.entity.Major;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.TeacherStudent;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点评提交与查询服务。
 * <p>权限模型：
 * <ul>
 *   <li>导师：仅本人提交的点评（含草稿），且只能提交给指导关系内的学生</li>
 *   <li>教务：全部点评</li>
 *   <li>学生：仅本人「已发布」的点评</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final CommentAuditMapper auditMapper;
    private final CommentSentimentMapper sentimentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final TeacherStudentMapper teacherStudentMapper;
    private final AuthContext authContext;
    private final OperLogService operLogService;
    private final AnalysisService analysisService;

    // ------------------------------------------------------------------ 查询

    /** 导师端分页（本人全部） */
    public PageResult<CommentDtos.Row> pageForTeacher(CommentDtos.Query q) {
        Long teacherId = authContext.currentTeacherId();
        LambdaQueryWrapper<Comment> w = CommentScope.ownedByTeacher(teacherId);
        applyFilters(w, q);
        return page(w, q);
    }

    /** 教务端分页（全部） */
    public PageResult<CommentDtos.Row> pageForAdmin(CommentDtos.Query q) {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看全量点评");
        }
        LambdaQueryWrapper<Comment> w = new LambdaQueryWrapper<>();
        applyFilters(w, q);
        return page(w, q);
    }

    /** 学生端分页（仅本人已发布） */
    public PageResult<CommentDtos.Row> pageForStudent(CommentDtos.Query q) {
        Long studentId = currentStudentId();
        LambdaQueryWrapper<Comment> w = CommentScope.visibleToStudent(studentId);
        if (q.getTerm() != null && !q.getTerm().isBlank()) {
            w.eq(Comment::getTerm, q.getTerm());
        }
        w.orderByDesc(Comment::getPublishedAt);
        PageResult<CommentDtos.Row> result = page(w, q);
        // 学生端列表同样不携带「审核意见 / 情感标签」：
        // 审核意见是教务与导师的内部沟通；情感标签的对象是导师文本，展示给学生会被误读为对自己的评价。
        result.getRecords().forEach(r -> {
            r.setLastOpinion(null);
            r.setLastAuditor(null);
            r.setSentimentLabel(null);
            r.setLowConfidence(false);
        });
        return result;
    }

    private void applyFilters(LambdaQueryWrapper<Comment> w, CommentDtos.Query q) {
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            if ("PUBLISHED".equals(q.getStatus())) {
                // 前端"已发布"页签：双维度合成一个筛选项
                w.eq(Comment::getStatus, CommentStatus.APPROVED).isNotNull(Comment::getPublishedAt);
            } else if ("APPROVED_UNPUBLISHED".equals(q.getStatus())) {
                w.eq(Comment::getStatus, CommentStatus.APPROVED).isNull(Comment::getPublishedAt);
            } else {
                w.eq(Comment::getStatus, q.getStatus());
            }
        }
        if (q.getTerm() != null && !q.getTerm().isBlank()) {
            w.eq(Comment::getTerm, q.getTerm());
        }
        if (q.getTeacherId() != null) {
            w.eq(Comment::getTeacherId, q.getTeacherId());
        }
        if (q.getStudentId() != null) {
            w.eq(Comment::getStudentId, q.getStudentId());
        }
        if (q.getClazzId() != null) {
            List<Long> ids = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                            .eq(Student::getClazzId, q.getClazzId()))
                    .stream().map(Student::getId).toList();
            if (ids.isEmpty()) {
                w.eq(Comment::getId, -1L);
            } else {
                w.in(Comment::getStudentId, ids);
            }
        }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            List<Long> sids = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                            .like(Student::getName, q.getKeyword())
                            .or().like(Student::getStudentNo, q.getKeyword()))
                    .stream().map(Student::getId).toList();
            if (sids.isEmpty()) {
                w.like(Comment::getContent, q.getKeyword());
            } else {
                w.and(x -> x.like(Comment::getContent, q.getKeyword())
                        .or().in(Comment::getStudentId, sids));
            }
        }
        if (q.getStartDate() != null && !q.getStartDate().isBlank()) {
            w.ge(Comment::getCreatedAt, q.getStartDate() + " 00:00:00");
        }
        if (q.getEndDate() != null && !q.getEndDate().isBlank()) {
            w.le(Comment::getCreatedAt, q.getEndDate() + " 23:59:59");
        }
        w.orderByDesc(Comment::getUpdatedAt).orderByDesc(Comment::getId);
    }

    private PageResult<CommentDtos.Row> page(LambdaQueryWrapper<Comment> w, CommentDtos.Query q) {
        Page<Comment> p = commentMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        List<CommentDtos.Row> rows = new ArrayList<>();
        Map<Long, String> tName = new HashMap<>();
        Map<Long, Student> sCache = new HashMap<>();
        for (Comment c : p.getRecords()) {
            rows.add(toRow(c, tName, sCache));
        }
        return PageResult.of(rows, p.getTotal(), q.getPage(), q.getSize());
    }

    /** 详情：按角色做数据范围校验 */
    public CommentDtos.Detail detail(Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        assertCanView(c);

        CommentDtos.Detail d = new CommentDtos.Detail();
        d.setComment(toRow(c, new HashMap<>(), new HashMap<>()));

        List<CommentAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CommentAudit>()
                .eq(CommentAudit::getCommentId, id).orderByAsc(CommentAudit::getAuditAt));
        List<CommentDtos.AuditItem> items = new ArrayList<>();
        for (CommentAudit a : audits) {
            CommentDtos.AuditItem it = new CommentDtos.AuditItem();
            it.setId(a.getId());
            it.setResult(a.getResult());
            it.setResultLabel(actionLabel(a.getActionType(), a.getResult()));
            // 设计规则：审核意见永不进学生端
            it.setOpinion(authContext.isStudent() ? null : a.getOpinion());
            it.setAuditorName(a.getAuditorName());
            it.setRound(a.getRound());
            it.setAuditAt(a.getAuditAt());
            it.setBeforeContent(authContext.isStudent() ? null : a.getBeforeContent());
            items.add(it);
        }
        d.setAudits(items);

        CommentSentiment s = sentimentMapper.selectOne(new LambdaQueryWrapper<CommentSentiment>()
                .eq(CommentSentiment::getCommentId, id).last("limit 1"));
        d.setSentiment(s);
        return d;
    }

    /** 学生可见性校验：必须是本人 + 已发布 */
    private void assertCanView(Comment c) {
        if (authContext.isAdmin()) {
            return;
        }
        if (authContext.isTeacher()) {
            if (!authContext.currentTeacherId().equals(c.getTeacherId())) {
                operLogService.record("VIEW_COMMENT", "COMMENT", c.getId(), "越权访问他人点评", false);
                throw BizException.forbidden("无权查看该点评");
            }
            return;
        }
        // 学生
        Long sid = currentStudentId();
        boolean published = CommentStatus.APPROVED.equals(c.getStatus()) && c.getPublishedAt() != null;
        if (!sid.equals(c.getStudentId()) || !published) {
            operLogService.record("VIEW_COMMENT", "COMMENT", c.getId(), "越权访问他人或未发布点评", false);
            throw BizException.forbidden("无权查看该点评");
        }
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public Long save(CommentDtos.SaveReq req) {
        Long teacherId = authContext.currentTeacherId();
        assertGuiding(teacherId, req.getStudentId());

        Comment c = new Comment();
        c.setTeacherId(teacherId);
        c.setStudentId(req.getStudentId());
        c.setTerm(req.getTerm());
        c.setTopic(req.getTopic());
        c.setContent(req.getContent());
        c.setVersion(1);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        if (req.isSubmit()) {
            c.setStatus(CommentStatus.PENDING);
            c.setSubmitAt(LocalDateTime.now());
        } else {
            c.setStatus(CommentStatus.DRAFT);
        }
        commentMapper.insert(c);
        operLogService.record(req.isSubmit() ? "SUBMIT_COMMENT" : "SAVE_DRAFT",
                "COMMENT", c.getId(), c.getTopic());
        return c.getId();
    }

    /** 修改草稿 / 退回后修改。仅 DRAFT 与 RETURNED 可改。 */
    @Transactional
    public void update(Long id, CommentDtos.SaveReq req) {
        Long teacherId = authContext.currentTeacherId();
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        if (!teacherId.equals(c.getTeacherId())) {
            operLogService.record("UPDATE_COMMENT", "COMMENT", id, "越权修改他人点评", false);
            throw BizException.forbidden("无权修改该点评");
        }
        assertGuiding(teacherId, req.getStudentId());

        LambdaUpdateWrapper<Comment> u = new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, id)
                .in(Comment::getStatus, CommentStatus.DRAFT, CommentStatus.RETURNED)
                .set(Comment::getStudentId, req.getStudentId())
                .set(Comment::getTerm, req.getTerm())
                .set(Comment::getTopic, req.getTopic())
                .set(Comment::getContent, req.getContent())
                .set(Comment::getUpdatedAt, LocalDateTime.now());
        if (req.isSubmit()) {
            u.set(Comment::getStatus, CommentStatus.PENDING)
                    .set(Comment::getSubmitAt, LocalDateTime.now())
                    .setSql("version = version + 1");
        }
        int rows = commentMapper.update(null, u);
        if (rows == 0) {
            throw BizException.conflict("当前状态不允许修改（仅草稿或退回修改状态可编辑）");
        }
        operLogService.record(req.isSubmit() ? "RESUBMIT_COMMENT" : "UPDATE_DRAFT", "COMMENT", id, c.getTopic());
    }

    /** 草稿直接提交 */
    @Transactional
    public void submit(Long id) {
        Long teacherId = authContext.currentTeacherId();
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        if (!teacherId.equals(c.getTeacherId())) {
            throw BizException.forbidden("无权提交该点评");
        }
        int rows = commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, id)
                .in(Comment::getStatus, CommentStatus.DRAFT, CommentStatus.RETURNED)
                .set(Comment::getStatus, CommentStatus.PENDING)
                .set(Comment::getSubmitAt, LocalDateTime.now())
                .set(Comment::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BizException.conflict("当前状态不允许提交（仅草稿或退回修改状态可提交）");
        }
        operLogService.record("SUBMIT_COMMENT", "COMMENT", id, c.getTopic());
    }

    /** 删除：仅草稿可删 */
    @Transactional
    public void delete(Long id) {
        Long teacherId = authContext.currentTeacherId();
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        if (!teacherId.equals(c.getTeacherId())) {
            throw BizException.forbidden("无权删除该点评");
        }
        int rows = commentMapper.delete(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getId, id)
                .eq(Comment::getStatus, CommentStatus.DRAFT));
        if (rows == 0) {
            throw BizException.conflict("仅草稿状态可删除");
        }
        operLogService.record("DELETE_DRAFT", "COMMENT", id, c.getTopic());
    }

    /** 指导关系校验：导师只能给自己指导的学生写点评 */
    public void assertGuiding(Long teacherId, Long studentId) {
        Long cnt = teacherStudentMapper.selectCount(new LambdaQueryWrapper<TeacherStudent>()
                .eq(TeacherStudent::getTeacherId, teacherId)
                .eq(TeacherStudent::getStudentId, studentId)
                .eq(TeacherStudent::getStatus, 1));
        if (cnt == null || cnt == 0) {
            operLogService.record("SUBMIT_COMMENT", "STUDENT", studentId, "非指导关系学生，已拒绝", false);
            throw BizException.forbidden("该学生不在您的指导范围内，无法提交点评");
        }
    }

    public Long currentStudentId() {
        if (!authContext.isStudent()) {
            throw BizException.forbidden("仅学生可访问");
        }
        Student s = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, authContext.userId()).last("limit 1"));
        if (s == null) {
            throw BizException.forbidden("当前账号未绑定学生档案");
        }
        return s.getId();
    }

    // ------------------------------------------------------------------ 行转换

    public CommentDtos.Row toRow(Comment c, Map<Long, String> tNameCache, Map<Long, Student> sCache) {
        CommentDtos.Row r = new CommentDtos.Row();
        r.setId(c.getId());
        r.setTeacherId(c.getTeacherId());
        r.setTeacherName(tNameCache.computeIfAbsent(c.getTeacherId(), tid -> {
            Teacher t = teacherMapper.selectById(tid);
            return t == null ? "未知导师" : t.getName();
        }));
        Student s = sCache.computeIfAbsent(c.getStudentId(), sid -> studentMapper.selectById(sid));
        if (s != null) {
            r.setStudentId(s.getId());
            r.setStudentName(s.getName());
            r.setStudentNo(s.getStudentNo());
            Clazz cl = clazzMapper.selectById(s.getClazzId());
            if (cl != null) {
                r.setClazzName(cl.getName());
                Major m = majorMapper.selectById(cl.getMajorId());
                r.setMajorName(m == null ? null : m.getName());
            }
        }
        r.setTerm(c.getTerm());
        r.setTopic(c.getTopic());
        r.setContent(c.getContent());
        r.setStatus(c.getStatus());
        r.setStatusLabel(CommentStatus.label(c.getStatus()));
        r.setPublished(c.getPublishedAt() != null);
        r.setPublishedAt(c.getPublishedAt());
        r.setWithdrawnAt(c.getWithdrawnAt());
        r.setEverWithdrawn(c.getWithdrawnAt() != null);
        r.setSubmitAt(c.getSubmitAt());
        r.setCreatedAt(c.getCreatedAt());
        r.setVersion(c.getVersion());

        CommentSentiment se = sentimentMapper.selectOne(new LambdaQueryWrapper<CommentSentiment>()
                .eq(CommentSentiment::getCommentId, c.getId()).last("limit 1"));
        if (se != null) {
            r.setSentimentLabel(se.getLabel());
            r.setLowConfidence(se.getLowConfidence() != null && se.getLowConfidence() == 1);
        }

        CommentAudit last = auditMapper.selectOne(new LambdaQueryWrapper<CommentAudit>()
                .eq(CommentAudit::getCommentId, c.getId())
                .orderByDesc(CommentAudit::getAuditAt).last("limit 1"));
        if (last != null) {
            r.setLastOpinion(authContext.isStudent() ? null : last.getOpinion());
            r.setLastAuditAt(last.getAuditAt());
            r.setLastAuditor(last.getAuditorName());
        }
        return r;
    }

    /**
     * 事件标题：按 actionType 映射，而不是按 result。
     * <p>否则"仅通过"与"通过并发布"都渲染成"通过"，撤回渲染成"审核"，
     * 时间线上看起来像教务审了两次——字段标题必须随事件类型变。
     */
    public static String actionLabel(String actionType, String result) {
        if (actionType == null) {
            return result;
        }
        return switch (actionType) {
            case "SUBMIT" -> "提交审核";
            case "APPROVE_PUBLISH" -> "审核通过并发布";
            case "APPROVE_ONLY" -> "审核通过（待发布）";
            case "RETURN" -> "退回修改";
            case "REJECT" -> "驳回";
            case "PUBLISH" -> "发布";
            case "WITHDRAW" -> "撤回发布";
            default -> result;
        };
    }
}
