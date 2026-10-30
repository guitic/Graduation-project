package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.PrecheckDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentPrecheck;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentPrecheckMapper;
import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.precheck.PrecheckIssue;
import com.graduation.mentor.service.precheck.PrecheckResult;
import com.graduation.mentor.service.precheck.PrecheckResultLevel;
import com.graduation.mentor.service.precheck.RulePrecheckEngine;
import com.graduation.mentor.service.precheck.SuggestAction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 预审服务（L1 规则引擎）。
 *
 * <p><b>权限</b>：仅教务管理员。预审结果包含"疑似套话""存在贬损性表述"这类内部判断， 泄漏给导师或学生等于把审核意见提前摊牌——而审核意见在设计上从不进学生端。
 *
 * <p><b>与审核流程的关系</b>：只写 comment_precheck，绝不触碰 comment.status。 预审失败、规则报错、表被清空，都不影响提交与审核主流程。
 *
 * <p><b>幂等</b>：{@code (content_hash, rule_version)} 双匹配才复用。只比 hash 会让规则升级后的
 * 旧结论冒充新结论；只比版本会让退回改稿后的文本沿用旧判定。两者都不报错，只会让教务看到 一份过期的建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrecheckService {

    /** 单次批量上限：规则引擎单条毫秒级，50 条已远超一页数据量 */
    public static final int MAX_BATCH = 50;

    /** 查重窗口：同导师近 30 天 */
    private static final int RECENT_DAYS = 30;

    private static final int RECENT_LIMIT = 20;

    private final CommentMapper commentMapper;
    private final CommentPrecheckMapper precheckMapper;
    private final RulePrecheckEngine engine;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final AuthContext authContext;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 对外能力

    /** 计算并落库（命中复用则直接返回旧结论）。force=true 强制重算。 */
    @Transactional
    public PrecheckDtos.View ensure(Long commentId, boolean force) {
        requireAdmin();
        return compute(commentId, force);
    }

    /** 只读缓存，不触发计算。未预审过返回 null。 */
    public PrecheckDtos.View cached(Long commentId) {
        requireAdmin();
        CommentPrecheck row = findByComment(commentId);
        return row == null ? null : toView(row, true);
    }

    /**
     * 批量预审（列表页一次预审当前页）。
     *
     * <p>单条失败只记日志并跳过：预审是旁路能力，不能因为一条脏数据让整页列表拿不到结果。
     */
    @Transactional
    public List<PrecheckDtos.View> batch(List<Long> ids) {
        requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> targets =
                ids.stream().filter(Objects::nonNull).distinct().limit(MAX_BATCH).toList();
        List<PrecheckDtos.View> out = new ArrayList<>(targets.size());
        for (Long id : targets) {
            try {
                out.add(compute(id, false));
            } catch (Exception e) {
                log.warn("点评 {} 预审失败（不影响其余条目）：{}", id, e.getMessage());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 内部实现

    private PrecheckDtos.View compute(Long commentId, boolean force) {
        Comment c = mustGet(commentId);
        String hash = AnalysisService.sha256(c.getContent());
        CommentPrecheck old = findByComment(commentId);
        if (!force && reusable(old, hash)) {
            return toView(old, true);
        }

        long started = System.currentTimeMillis();
        SentimentResult sentiment = sentimentAnalyzer.analyze(c.getContent());
        PrecheckResult result =
                engine.check(
                        new RulePrecheckEngine.Input(
                                c.getContent(), c.getTopic(), recentContents(c), sentiment));
        int cost = (int) (System.currentTimeMillis() - started);

        CommentPrecheck row = new CommentPrecheck();
        row.setCommentId(commentId);
        row.setContentHash(hash);
        row.setRuleVersion(RulePrecheckEngine.VERSION);
        row.setRiskLevel(result.getRiskLevel());
        row.setRiskScore(result.getRiskScore());
        row.setSuggestAction(result.getSuggestAction());
        row.setSuggestOpinion(result.getSuggestOpinion());
        row.setIssues(writeIssues(result.getIssues()));
        row.setIssueCount(result.getIssues().size());
        row.setEngine(RulePrecheckEngine.ENGINE);
        row.setCostMs(cost);
        row.setCheckedAt(LocalDateTime.now());

        // 与 comment_sentiment 一致：先删后插，一条点评只保留当前结论
        precheckMapper.delete(
                new LambdaQueryWrapper<CommentPrecheck>()
                        .eq(CommentPrecheck::getCommentId, commentId));
        precheckMapper.insert(row);
        return toView(row, false);
    }

    private boolean reusable(CommentPrecheck old, String hash) {
        return old != null
                && hash.equals(old.getContentHash())
                && RulePrecheckEngine.VERSION.equals(old.getRuleVersion());
    }

    /** 同导师近 30 天其他点评正文，用于模板化查重 */
    private List<String> recentContents(Comment c) {
        List<Comment> list =
                commentMapper.selectList(
                        new LambdaQueryWrapper<Comment>()
                                .eq(Comment::getTeacherId, c.getTeacherId())
                                .ne(Comment::getId, c.getId())
                                .ge(
                                        Comment::getCreatedAt,
                                        LocalDateTime.now().minusDays(RECENT_DAYS))
                                .orderByDesc(Comment::getId)
                                .last("limit " + RECENT_LIMIT));
        return list.stream().map(Comment::getContent).toList();
    }

    private CommentPrecheck findByComment(Long commentId) {
        return precheckMapper.selectOne(
                new LambdaQueryWrapper<CommentPrecheck>()
                        .eq(CommentPrecheck::getCommentId, commentId)
                        .last("limit 1"));
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
            throw BizException.forbidden("仅教务管理员可执行 AI 预审");
        }
    }

    // ------------------------------------------------------------------ 行转换

    private PrecheckDtos.View toView(CommentPrecheck row, boolean cached) {
        PrecheckDtos.View v = new PrecheckDtos.View();
        v.setCommentId(row.getCommentId());
        v.setRiskLevel(row.getRiskLevel());
        v.setRiskLevelLabel(PrecheckResultLevel.label(row.getRiskLevel()));
        v.setRiskScore(row.getRiskScore() == null ? 0 : row.getRiskScore());
        v.setSuggestAction(row.getSuggestAction());
        v.setSuggestActionLabel(SuggestAction.label(row.getSuggestAction()));
        v.setSuggestOpinion(row.getSuggestOpinion());
        v.setIssues(readIssues(row.getIssues()));
        v.setIssueCount(row.getIssueCount() == null ? 0 : row.getIssueCount());
        v.setEngine(row.getEngine());
        v.setRuleVersion(row.getRuleVersion());
        v.setCostMs(row.getCostMs() == null ? 0 : row.getCostMs());
        v.setCheckedAt(row.getCheckedAt());
        v.setCached(cached);
        return v;
    }

    private String writeIssues(List<PrecheckIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (Exception e) {
            // 序列化失败不应阻断预审：结论本身（风险分/建议动作）仍然可用
            log.warn("预审问题清单序列化失败：{}", e.getMessage());
            return "[]";
        }
    }

    private List<PrecheckIssue> readIssues(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PrecheckIssue>>() {});
        } catch (Exception e) {
            log.warn("预审问题清单解析失败：{}", e.getMessage());
            return List.of();
        }
    }
}
