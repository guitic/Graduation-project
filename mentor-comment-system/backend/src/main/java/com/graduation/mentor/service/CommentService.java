package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.mentor.common.AuditItemMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点评提交与查询服务。
 *
 * <p>权限模型：
 *
 * <ul>
 *   <li>导师：仅本人提交的点评（含草稿），且只能提交给指导关系内的学生
 *   <li>教务：全部点评
 *   <li>学生：仅本人「已发布」的点评
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
        result.getRecords()
                .forEach(
                        r -> {
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
            List<Long> ids =
                    studentMapper
                            .selectList(
                                    new LambdaQueryWrapper<Student>()
                                            .eq(Student::getClazzId, q.getClazzId()))
                            .stream()
                            .map(Student::getId)
                            .toList();
            if (ids.isEmpty()) {
                w.eq(Comment::getId, -1L);
            } else {
                w.in(Comment::getStudentId, ids);
            }
        }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            List<Long> sids =
                    studentMapper
                            .selectList(
                                    new LambdaQueryWrapper<Student>()
                                            .like(Student::getName, q.getKeyword())
                                            .or()
                                            .like(Student::getStudentNo, q.getKeyword()))
                            .stream()
                            .map(Student::getId)
                            .toList();
            if (sids.isEmpty()) {
                w.like(Comment::getContent, q.getKeyword());
            } else {
                w.and(
                        x ->
                                x.like(Comment::getContent, q.getKeyword())
                                        .or()
                                        .in(Comment::getStudentId, sids));
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
        return PageResult.of(toRows(p.getRecords()), p.getTotal(), q.getPage(), q.getSize());
    }

    /** 详情：按角色做数据范围校验 */
    public CommentDtos.Detail detail(Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        assertCanView(c);

        CommentDtos.Detail d = new CommentDtos.Detail();
        d.setComment(toRows(List.of(c)).get(0));

        List<CommentAudit> audits =
                auditMapper.selectList(
                        new LambdaQueryWrapper<CommentAudit>()
                                .eq(CommentAudit::getCommentId, id)
                                .orderByAsc(CommentAudit::getAuditAt));
        // 裁剪规则只在 AuditItemMapper 一处实现（审核意见永不进学生端）
        boolean forStudent = authContext.isStudent();
        d.setAudits(audits.stream().map(a -> AuditItemMapper.of(a, forStudent)).toList());

        CommentSentiment s =
                sentimentMapper.selectOne(
                        new LambdaQueryWrapper<CommentSentiment>()
                                .eq(CommentSentiment::getCommentId, id)
                                .last("limit 1"));
        d.setSentiment(s);
        return d;
    }

    /**
     * 数据范围校验：管理员全量 / 导师仅本人 / 学生仅本人且已发布。
     *
     * <p><b>为什么是 public</b>：这是"谁能看这条点评"的唯一实现。 {@link AuditService#audits(Long)} 也需要同一判定，若各自写一份，
     * 迟早出现"一个接口校验了、另一个忘了"——本项目已经因此出过一次越权。 新增任何"按 id 读点评"的入口，都必须先调用这里，禁止另写一套。
     */
    public void assertCanView(Comment c) {
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
        boolean published =
                CommentStatus.APPROVED.equals(c.getStatus()) && c.getPublishedAt() != null;
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
        operLogService.record(
                req.isSubmit() ? "SUBMIT_COMMENT" : "SAVE_DRAFT",
                "COMMENT",
                c.getId(),
                c.getTopic());
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

        LambdaUpdateWrapper<Comment> u =
                new LambdaUpdateWrapper<Comment>()
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
        operLogService.record(
                req.isSubmit() ? "RESUBMIT_COMMENT" : "UPDATE_DRAFT", "COMMENT", id, c.getTopic());
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
        int rows =
                commentMapper.update(
                        null,
                        new LambdaUpdateWrapper<Comment>()
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
        int rows =
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>()
                                .eq(Comment::getId, id)
                                .eq(Comment::getStatus, CommentStatus.DRAFT));
        if (rows == 0) {
            throw BizException.conflict("仅草稿状态可删除");
        }
        operLogService.record("DELETE_DRAFT", "COMMENT", id, c.getTopic());
    }

    /** 指导关系校验：导师只能给自己指导的学生写点评 */
    public void assertGuiding(Long teacherId, Long studentId) {
        Long cnt =
                teacherStudentMapper.selectCount(
                        new LambdaQueryWrapper<TeacherStudent>()
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
        Student s =
                studentMapper.selectOne(
                        new LambdaQueryWrapper<Student>()
                                .eq(Student::getUserId, authContext.userId())
                                .last("limit 1"));
        if (s == null) {
            throw BizException.forbidden("当前账号未绑定学生档案");
        }
        return s.getId();
    }

    // ------------------------------------------------------------------ 行转换

    /**
     * 批量行转换（列表页唯一入口）。
     *
     * <p><b>为什么不是逐行查</b>：旧实现每行要查导师、学生、班级、专业、情感、最近审核共 6 次， pageSize=20 时约 120 次 SQL。其中导师/学生只靠两个
     * HashMap 缓存，而班级与专业随学生变化、 情感与审核随点评变化，命中率几乎为零——分页越深越慢，且压测时表现为"数据库 CPU 高、接口慢"。
     *
     * <p>改为按 id 集合批量取：每个维度 1 条 SQL，空集合直接跳过。分页固定 **6 条 SQL**，与页大小无关。
     */
    public List<CommentDtos.Row> toRows(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        RowContext ctx = preload(comments);
        List<CommentDtos.Row> rows = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            rows.add(toRow(c, ctx));
        }
        return rows;
    }

    /** 一次性把一页数据依赖的关联对象取全 */
    private RowContext preload(List<Comment> comments) {
        RowContext ctx = new RowContext();
        Set<Long> teacherIds = new HashSet<>();
        Set<Long> studentIds = new HashSet<>();
        List<Long> commentIds = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            if (c.getTeacherId() != null) {
                teacherIds.add(c.getTeacherId());
            }
            if (c.getStudentId() != null) {
                studentIds.add(c.getStudentId());
            }
            commentIds.add(c.getId());
        }

        if (!teacherIds.isEmpty()) {
            for (Teacher t : teacherMapper.selectBatchIds(teacherIds)) {
                ctx.teacherNames.put(t.getId(), t.getName());
            }
        }
        if (!studentIds.isEmpty()) {
            for (Student s : studentMapper.selectBatchIds(studentIds)) {
                ctx.students.put(s.getId(), s);
            }
        }
        Set<Long> clazzIds = new HashSet<>();
        for (Student s : ctx.students.values()) {
            if (s.getClazzId() != null) {
                clazzIds.add(s.getClazzId());
            }
        }
        if (!clazzIds.isEmpty()) {
            for (Clazz cl : clazzMapper.selectBatchIds(clazzIds)) {
                ctx.clazzes.put(cl.getId(), cl);
            }
        }
        Set<Long> majorIds = new HashSet<>();
        for (Clazz cl : ctx.clazzes.values()) {
            if (cl.getMajorId() != null) {
                majorIds.add(cl.getMajorId());
            }
        }
        if (!majorIds.isEmpty()) {
            for (Major m : majorMapper.selectBatchIds(majorIds)) {
                ctx.majors.put(m.getId(), m);
            }
        }

        // 情感结果按 id 升序覆盖：同一点评若存在历史多行，取最新一条（与 limit 1 语义一致）
        for (CommentSentiment se :
                sentimentMapper.selectList(
                        new LambdaQueryWrapper<CommentSentiment>()
                                .in(CommentSentiment::getCommentId, commentIds)
                                .orderByAsc(CommentSentiment::getId))) {
            ctx.sentiments.put(se.getCommentId(), se);
        }
        // 最近一条审核事件：按时间倒序，putIfAbsent 保留首条
        for (CommentAudit a :
                auditMapper.selectList(
                        new LambdaQueryWrapper<CommentAudit>()
                                .in(CommentAudit::getCommentId, commentIds)
                                .orderByDesc(CommentAudit::getAuditAt)
                                .orderByDesc(CommentAudit::getId))) {
            ctx.lastAudits.putIfAbsent(a.getCommentId(), a);
        }
        return ctx;
    }

    public CommentDtos.Row toRow(Comment c, RowContext ctx) {
        CommentDtos.Row r = new CommentDtos.Row();
        r.setId(c.getId());
        r.setTeacherId(c.getTeacherId());
        String tName = ctx.teacherNames.get(c.getTeacherId());
        r.setTeacherName(tName == null ? "未知导师" : tName);

        Student s = ctx.students.get(c.getStudentId());
        if (s != null) {
            r.setStudentId(s.getId());
            r.setStudentName(s.getName());
            r.setStudentNo(s.getStudentNo());
            Clazz cl = ctx.clazzes.get(s.getClazzId());
            if (cl != null) {
                r.setClazzName(cl.getName());
                Major m = ctx.majors.get(cl.getMajorId());
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

        CommentSentiment se = ctx.sentiments.get(c.getId());
        if (se != null) {
            r.setSentimentLabel(se.getLabel());
            r.setLowConfidence(se.getLowConfidence() != null && se.getLowConfidence() == 1);
        }

        CommentAudit last = ctx.lastAudits.get(c.getId());
        if (last != null) {
            r.setLastOpinion(authContext.isStudent() ? null : last.getOpinion());
            r.setLastAuditAt(last.getAuditAt());
            r.setLastAuditor(last.getAuditorName());
        }
        return r;
    }

    /** 一页数据的关联对象快照：把 N+1 压成固定条数的 SQL */
    public static final class RowContext {

        private final Map<Long, String> teacherNames = new HashMap<>();
        private final Map<Long, Student> students = new HashMap<>();
        private final Map<Long, Clazz> clazzes = new HashMap<>();
        private final Map<Long, Major> majors = new HashMap<>();
        private final Map<Long, CommentSentiment> sentiments = new HashMap<>();
        private final Map<Long, CommentAudit> lastAudits = new HashMap<>();
    }
}
