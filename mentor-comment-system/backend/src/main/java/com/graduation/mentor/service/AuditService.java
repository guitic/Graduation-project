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
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点评审核与发布服务。
 *
 * <p><b>本系统的核心不变量（论文"关键设计"一节可直接引用）</b>：
 *
 * <ol>
 *   <li><b>状态迁移必须条件化</b>：一律 {@code UPDATE ... WHERE id=? AND status=?} 并检查影响行数， 0 行返
 *       409。先查后改（check-then-act）在并发下必然双写，两个教务同时点审核会各写一条记录。
 *   <li><b>通过 ≠ 发布</b>：approve 可选立即发布或仅通过；发布是独立动作，只能从 {@code APPROVED + published_at IS NULL}
 *       迁移；撤回是发布的逆动作。
 *   <li><b>发布是分析流程的唯一触发器</b>：只有发布时才执行情感分析， 保证"学生可见 = 纳入统计"完全自洽。
 *   <li><b>可追溯是单向的</b>：既然撤回要求理由，再发布也必须要求理由—— 否则"为什么压下来"有据可查、"为什么放回去"无从追溯。
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /**
     * 审核意见的长度上限。
     *
     * <p><b>只设上限，不设下限</b>：下限（原来 5/10/20 字分档）把"意见写清楚"变成了字数凑数，
     * 实际写出来的多是"内容完整表述规范同意发布"这类填充句，信息量并不比"同意"多。 真正的质量保障交给前端「常用理由」下拉——一键选中的理由本身就写清楚了同意/不同意的依据，
     * 比逼老师凑字数有效。下限取消后，审核仍要求**至少填一条**（可从常用理由中选）， 保证 {@code comment_audit} 的留痕不出现空意见。
     */
    public static final int MAX_OPINION_LENGTH = 500;

    /** 事件类型 */
    public static final String ACT_SUBMIT = "SUBMIT";

    public static final String ACT_APPROVE_ONLY = "APPROVE_ONLY";
    public static final String ACT_APPROVE_PUBLISH = "APPROVE_PUBLISH";
    public static final String ACT_RETURN = "RETURN";
    public static final String ACT_REJECT = "REJECT";
    public static final String ACT_PUBLISH = "PUBLISH";
    public static final String ACT_WITHDRAW = "WITHDRAW";

    private final CommentMapper commentMapper;
    private final CommentAuditMapper auditMapper;
    private final StudentMapper studentMapper;
    private final CommentService commentService;
    private final AnalysisService analysisService;
    private final AuthContext authContext;
    private final OperLogService operLogService;

    // ------------------------------------------------------------------ 列表

    public PageResult<CommentDtos.Row> pendingPage(CommentDtos.Query q) {
        requireAdmin();
        LambdaQueryWrapper<Comment> w =
                new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, CommentStatus.PENDING);
        applyQueueFilters(w, q);
        // FIFO：先提交的先审，避免老单子被新单子淹没
        w.orderByAsc(Comment::getSubmitAt).orderByAsc(Comment::getId);
        return pageOf(w, q);
    }

    /**
     * 审核队列的三个筛选条件。
     *
     * <p>之前这里完全没读 q，学期/导师/搜索框都是摆设——点了「查询」列表纹丝不动， 看起来像前端没传参，实际是后端没接。关键词只匹配主题与学生姓名/学号，
     * <b>不匹配正文</b>：教务在队列里是按"给谁、写的什么主题"找人，不是全文检索。
     */
    private void applyQueueFilters(LambdaQueryWrapper<Comment> w, CommentDtos.Query q) {
        if (q.getTerm() != null && !q.getTerm().isBlank()) {
            w.eq(Comment::getTerm, q.getTerm());
        }
        if (q.getTeacherId() != null) {
            w.eq(Comment::getTeacherId, q.getTeacherId());
        }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            String kw = q.getKeyword().trim();
            List<Long> studentIds =
                    studentMapper
                            .selectList(
                                    new LambdaQueryWrapper<Student>()
                                            .like(Student::getName, kw)
                                            .or()
                                            .like(Student::getStudentNo, kw))
                            .stream()
                            .map(Student::getId)
                            .toList();
            w.and(
                    x -> {
                        x.like(Comment::getTopic, kw);
                        if (!studentIds.isEmpty()) {
                            x.or().in(Comment::getStudentId, studentIds);
                        }
                    });
        }
    }

    /**
     * 待发布列表（已通过但未发布）。
     *
     * <p>排序：曾撤回过的置顶（这是"需要人处理"的强信号），其余按通过时间升序（FIFO 防饿死）。
     */
    public PageResult<CommentDtos.Row> unpublishedPage(CommentDtos.Query q) {
        requireAdmin();
        LambdaQueryWrapper<Comment> w =
                CommentScope.approvedNotPublished()
                        .orderByDesc(Comment::getWithdrawnAt)
                        .orderByAsc(Comment::getUpdatedAt)
                        .orderByAsc(Comment::getId);
        return pageOf(w, q);
    }

    /**
     * 已发布列表（供教务执行「撤回」）。
     *
     * <p>按发布时间倒序，最近发布的排在最前，便于定位误发布的内容。
     */
    public PageResult<CommentDtos.Row> publishedPage(CommentDtos.Query q) {
        requireAdmin();
        LambdaQueryWrapper<Comment> w =
                CommentScope.published()
                        .orderByDesc(Comment::getPublishedAt)
                        .orderByDesc(Comment::getId);
        return pageOf(w, q);
    }

    private PageResult<CommentDtos.Row> pageOf(LambdaQueryWrapper<Comment> w, CommentDtos.Query q) {
        Page<Comment> p = commentMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        // 行转换统一走批量预加载，避免逐行查导师/学生/班级/情感（N+1）
        return PageResult.of(
                commentService.toRows(p.getRecords()), p.getTotal(), q.getPage(), q.getSize());
    }

    public long pendingCount() {
        return commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, CommentStatus.PENDING));
    }

    // ------------------------------------------------------------------ 审核动作

    /** 通过。publish=true → 通过并发布；false → 仅通过（进入待发布） */
    @Transactional
    public void approve(Long id, CommentDtos.AuditReq req) {
        requireAdmin();
        String opinion = requireOpinion(req.getOpinion(), req.isPublish() ? "通过并发布" : "仅通过");

        Comment c = mustGet(id);
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<Comment> u =
                new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getId, id)
                        .eq(Comment::getStatus, CommentStatus.PENDING)
                        .set(Comment::getStatus, CommentStatus.APPROVED)
                        .set(Comment::getUpdatedAt, now);
        if (req.isPublish()) {
            u.set(Comment::getPublishedAt, now);
        }
        int rows = commentMapper.update(null, u);
        if (rows == 0) {
            throw BizException.conflict("该点评状态已变化（可能已被其他教务处理），请刷新后重试");
        }

        writeEvent(
                id,
                CommentStatus.APPROVED,
                req.isPublish() ? ACT_APPROVE_PUBLISH : ACT_APPROVE_ONLY,
                opinion,
                null,
                c.getVersion(),
                now);
        operLogService.record(
                req.isPublish() ? ACT_APPROVE_PUBLISH : ACT_APPROVE_ONLY, "COMMENT", id, opinion);

        if (req.isPublish()) {
            analysisService.analyzeAndSave(commentMapper.selectById(id));
        }
    }

    @Transactional
    public void returnBack(Long id, CommentDtos.AuditReq req) {
        requireAdmin();
        String opinion = requireOpinion(req.getOpinion(), "退回修改");

        Comment c = mustGet(id);
        LocalDateTime now = LocalDateTime.now();
        int rows =
                commentMapper.update(
                        null,
                        new LambdaUpdateWrapper<Comment>()
                                .eq(Comment::getId, id)
                                .eq(Comment::getStatus, CommentStatus.PENDING)
                                .set(Comment::getStatus, CommentStatus.RETURNED)
                                .set(Comment::getUpdatedAt, now));
        if (rows == 0) {
            throw BizException.conflict("该点评状态已变化，请刷新后重试");
        }
        writeEvent(
                id,
                CommentStatus.RETURNED,
                ACT_RETURN,
                opinion,
                c.getContent(),
                c.getVersion(),
                now);
        operLogService.record(ACT_RETURN, "COMMENT", id, opinion);
    }

    @Transactional
    public void reject(Long id, CommentDtos.AuditReq req) {
        requireAdmin();
        String opinion = requireOpinion(req.getOpinion(), "驳回");

        Comment c = mustGet(id);
        LocalDateTime now = LocalDateTime.now();
        int rows =
                commentMapper.update(
                        null,
                        new LambdaUpdateWrapper<Comment>()
                                .eq(Comment::getId, id)
                                .eq(Comment::getStatus, CommentStatus.PENDING)
                                .set(Comment::getStatus, CommentStatus.REJECTED)
                                .set(Comment::getUpdatedAt, now));
        if (rows == 0) {
            throw BizException.conflict("该点评状态已变化，请刷新后重试");
        }
        writeEvent(
                id,
                CommentStatus.REJECTED,
                ACT_REJECT,
                opinion,
                c.getContent(),
                c.getVersion(),
                now);
        operLogService.record(ACT_REJECT, "COMMENT", id, opinion);
    }

    /**
     * 发布（单条）。只能从「已通过且未发布」迁移。
     *
     * <p><b>不提供批量发布</b>：批量更新极易写成 {@code WHERE id IN (...)} 的裸 SQL， 绕过逐条状态校验，是状态机被破坏的主要入口，且答辩零增量。
     *
     * <p>若该点评曾被撤回（{@code withdrawnAt != null}），则属于"再发布"，必须填理由（字数不限）。
     */
    @Transactional
    public void publish(Long id, String reason) {
        requireAdmin();
        Comment c = mustGet(id);
        if (!CommentStatus.APPROVED.equals(c.getStatus())) {
            throw BizException.conflict("仅审核通过的点评可发布，当前状态：" + CommentStatus.label(c.getStatus()));
        }
        boolean republish = c.getWithdrawnAt() != null;
        String note = normalizeOpinion(reason);
        if (republish && note == null) {
            throw new BizException(400, "再发布需要填写理由，或从常用理由中选择一条");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows =
                commentMapper.update(
                        null,
                        new LambdaUpdateWrapper<Comment>()
                                .eq(Comment::getId, id)
                                .eq(Comment::getStatus, CommentStatus.APPROVED)
                                .isNull(Comment::getPublishedAt)
                                .set(Comment::getPublishedAt, now)
                                .set(Comment::getUpdatedAt, now));
        if (rows == 0) {
            throw BizException.conflict("该点评已发布，请勿重复操作");
        }
        writeEvent(id, "PUBLISHED", ACT_PUBLISH, note, null, c.getVersion(), now);
        operLogService.record(ACT_PUBLISH, "COMMENT", id, note == null ? "首次发布" : note);

        // 文本未变则复用分析结果；已变则重算（由 ensureAnalyzed 统一判定）
        analysisService.analyzeAndSave(commentMapper.selectById(id));
    }

    /**
     * 撤回发布：publishedAt 置回 null，同时记录 withdrawnAt。
     *
     * <p>因为"学生可见 / 纳入统计"全部只认 publishedAt，撤回后学生端、报表、词云、 明细下钻、导出会自动剔除，无需任何额外改动——这是把发布做成独立字段的红利。
     *
     * <p>边界：撤回仅控制后续可见性，不追溯已发生的查看；系统不记录学生阅读行为。
     *
     * <p>撤回次数不设上限（可通过事件流统计），每次撤回都留痕。
     */
    @Transactional
    public void unpublish(Long id, String reason) {
        requireAdmin();
        String note = requireOpinion(reason, "撤回发布");

        Comment c = mustGet(id);
        if (c.getPublishedAt() == null) {
            throw BizException.conflict("该点评尚未发布，无需撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        int rows =
                commentMapper.update(
                        null,
                        new LambdaUpdateWrapper<Comment>()
                                .eq(Comment::getId, id)
                                .eq(Comment::getStatus, CommentStatus.APPROVED)
                                .isNotNull(Comment::getPublishedAt)
                                .set(Comment::getPublishedAt, null)
                                .set(Comment::getWithdrawnAt, now)
                                .set(Comment::getUpdatedAt, now));
        if (rows == 0) {
            throw BizException.conflict("该点评状态已变化，无法撤回");
        }
        writeEvent(id, "UNPUBLISHED", ACT_WITHDRAW, reason, null, c.getVersion(), now);
        operLogService.record(ACT_WITHDRAW, "COMMENT", id, reason);
    }

    /**
     * 事件时间线（升序 + id 兜底，保证同一秒内多条事件顺序稳定）。
     *
     * <p><b>权限与 {@link CommentService#detail(Long)} 完全一致</b>： 管理员全量 / 导师仅本人 / 学生仅本人且已发布。
     *
     * <p>这里曾漏掉数据范围校验，导致任意登录用户（含学生）可用自己 token 遍历 id， 通过 {@code GET /api/audit/{id}/history}
     * 读到任意点评的审核意见与修改前正文， 与"审核意见永不进学生端"的设计承诺直接冲突。 修复方式是复用 {@link
     * CommentService#assertCanView}——不再另写一套判定， 否则下次新增入口仍会漏。
     */
    public List<CommentDtos.AuditItem> audits(Long commentId) {
        Comment c = mustGet(commentId);
        commentService.assertCanView(c);

        boolean forStudent = authContext.isStudent();
        return auditMapper
                .selectList(
                        new LambdaQueryWrapper<CommentAudit>()
                                .eq(CommentAudit::getCommentId, commentId)
                                .orderByAsc(CommentAudit::getAuditAt)
                                .orderByAsc(CommentAudit::getId))
                .stream()
                .map(a -> AuditItemMapper.of(a, forStudent))
                .toList();
    }

    // ------------------------------------------------------------------ 内部

    public void writeEvent(
            Long commentId,
            String result,
            String actionType,
            String opinion,
            String beforeContent,
            Integer round,
            LocalDateTime at) {
        CommentAudit a = new CommentAudit();
        a.setCommentId(commentId);
        a.setAuditorId(authContext.userId());
        a.setAuditorName(authContext.currentUser().getRealName());
        a.setResult(result);
        a.setActionType(actionType);
        a.setOpinion(opinion);
        a.setBeforeContent(beforeContent);
        a.setRound(round);
        a.setAuditAt(at);
        auditMapper.insert(a);
    }

    /**
     * 规范化审核意见：去首尾空白、空串归一为 null、超长直接拒绝。
     *
     * <p>只做"能不能存"的校验，不做"够不够长"的校验——字数下限已取消。
     */
    private String normalizeOpinion(String opinion) {
        String v = opinion == null ? "" : opinion.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > MAX_OPINION_LENGTH) {
            throw new BizException(400, "意见不超过 " + MAX_OPINION_LENGTH + " 字，请精简后提交");
        }
        return v;
    }

    /** 必须给出意见的动作（通过 / 退回 / 驳回 / 撤回 / 再发布）：不限字数，但至少选一条 */
    private String requireOpinion(String opinion, String action) {
        String v = normalizeOpinion(opinion);
        if (v == null) {
            throw new BizException(400, action + "需要填写意见，或从常用理由中选择一条");
        }
        return v;
    }

    private Comment mustGet(Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("点评不存在");
        }
        return c;
    }

    private void requireAdmin() {
        if (!authContext.isAdmin()) {
            operLogService.record("AUDIT", "COMMENT", null, "非教务角色尝试审核，已拒绝", false);
            throw BizException.forbidden("仅教务管理员可执行审核与发布");
        }
    }
}
