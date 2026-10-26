package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.CommentScope;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentSentiment;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.analysis.WordSegmenter;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文本分析服务：情感分类 + 词频统计。
 * <p>触发时机：点评"发布"时同步执行；也支持全量重算（论文里叫"分析版本迁移"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    /** 词性白名单：只统计实词，过滤虚词与标点 */
    private static final Set<String> KEEP_NATURE_PREFIX = Set.of("n", "v", "a", "i", "j", "l");

    private final SentimentAnalyzer analyzer;
    private final WordSegmenter segmenter;
    private final CommentMapper commentMapper;
    private final CommentSentimentMapper sentimentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;

    /**
     * 分析入口（幂等）。
     *
     * <p><b>关键不变量：复用判据必须是 {@code (content_hash, method_version)} 双匹配。</b>
     * <ul>
     *   <li>只比 content_hash → 挡住"文本变了"，但挡不住"方法变了"。
     *       一旦调整词典或阈值（method_version 升级），旧点评 hash 未变，
     *       就会全部复用旧结果，报表里躺的是上一版方法算出来的数——
     *       而且**不报错**，会让人误以为新阈值没生效，去查一个不存在的 bug。
     *       论文写"改进词典后准确率提升"时，实验数据若是旧方法算的，这一章就废了。</li>
     *   <li>双匹配之后，"方法迭代"才敢做：每次换方法系统自动重算，不会拿旧结果冒充新方法。</li>
     * </ul>
     *
     * <p>hash 前先做文本归一化（全角转半角、空白折叠），与分词前的预处理共用同一实现，
     * 避免"多一个空格"被判成文本变更。
     */
    @Transactional
    public void analyzeAndSave(Comment comment) {
        ensureAnalyzed(comment, false);
    }

    @Transactional
    public void ensureAnalyzed(Comment comment, boolean force) {
        String hash = sha256(comment.getContent());
        CommentSentiment existing = sentimentMapper.selectOne(new LambdaQueryWrapper<CommentSentiment>()
                .eq(CommentSentiment::getCommentId, comment.getId()).last("limit 1"));
        boolean textUnchanged = existing != null && hash.equals(existing.getContentHash());
        boolean methodUnchanged = existing != null
                && SentimentAnalyzer.METHOD_VERSION.equals(existing.getMethodVersion());
        if (!force && textUnchanged && methodUnchanged) {
            // 文本未变 且 方法未变 → 复用
            return;
        }

        Set<String> names = collectNames();
        SentimentResult result = analyzer.analyze(comment.getContent(), names);

        sentimentMapper.delete(new LambdaQueryWrapper<CommentSentiment>()
                .eq(CommentSentiment::getCommentId, comment.getId()));
        CommentSentiment entity = new CommentSentiment();
        entity.setCommentId(comment.getId());
        entity.setContentHash(hash);
        entity.setTextVersion(comment.getVersion() == null ? 1 : comment.getVersion());
        entity.setLabel(result.getLabel());
        entity.setScore(BigDecimal.valueOf(result.getScore()));
        entity.setConfidence(BigDecimal.valueOf(result.getConfidence()));
        entity.setPosHits(result.getPosHits());
        entity.setNegHits(result.getNegHits());
        entity.setLowConfidence(result.isLowConfidence() ? 1 : 0);
        entity.setMethod(SentimentAnalyzer.METHOD);
        entity.setMethodVersion(SentimentAnalyzer.METHOD_VERSION);
        entity.setAnalyzedAt(LocalDateTime.now());
        sentimentMapper.insert(entity);
        // 词频不落库：词云在查询时实时聚合（见 ReportService.wordCloud）。
        // 派生表没有失效机制，退回改稿/撤回后必然不同步，且不报错。
    }

    /**
     * SHA-256 正文指纹。
     * <p>先归一化再哈希：全角转半角 + 空白折叠，与分词前预处理共用
     * {@link com.graduation.mentor.service.analysis.PrivacyFilter#normalize(String)}，
     * 保证"同一段文本"只有一个指纹。
     */
    public static String sha256(String text) {
        try {
            String normalized = com.graduation.mentor.service.analysis.PrivacyFilter.normalize(text);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(String.valueOf(text).hashCode());
        }
    }

    /** 词频统计：分词 → 去停用词 → 去单字/数字/标点 → 按词性白名单过滤 */
    public Map<String, Integer> wordFrequency(String rawText, Set<String> names) {
        String text = com.graduation.mentor.service.analysis.PrivacyFilter.mask(rawText, names);
        List<Term> terms = segmenter.segment(text);

        Map<String, Integer> freq = new LinkedHashMap<>();
        for (Term t : terms) {
            String w = t.word.trim();
            if (w.length() < 2) {
                continue;
            }
            if (!w.matches("[\\u4e00-\\u9fa5]+")) {
                continue;
            }
            if (t.nature == null) {
                continue;
            }
            String nature = t.nature.toString();
            if (KEEP_NATURE_PREFIX.stream().noneMatch(nature::startsWith)) {
                continue;
            }
            freq.merge(w, 1, Integer::sum);
        }
        // 按频次降序
        List<Map.Entry<String, Integer>> list = new ArrayList<>(freq.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        list.forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /**
     * 全量重算：把所有"已发布"点评重新分析一遍。
     * 用于分析版本升级、种子数据初始化、答辩演示"重算"。
     */
    @Transactional
    public Map<String, Object> rebuildAll() {
        List<Comment> list = commentMapper.selectList(CommentScope.published());
        int ok = 0;
        for (Comment c : list) {
            try {
                ensureAnalyzed(c, true);
                ok++;
            } catch (Exception e) {
                log.warn("点评 {} 分析失败：{}", c.getId(), e.getMessage());
            }
        }
        Map<String, Object> ret = new HashMap<>();
        ret.put("total", list.size());
        ret.put("analyzed", ok);
        ret.put("methodVersion", SentimentAnalyzer.METHOD_VERSION);
        ret.put("hanlpAvailable", segmenter.isHanlpAvailable());
        return ret;
    }

    /** 待复核条数（低置信度） */
    public long countLowConfidence() {
        List<Comment> published = commentMapper.selectList(CommentScope.published());
        if (published.isEmpty()) {
            return 0;
        }
        List<Long> ids = published.stream().map(Comment::getId).toList();
        return sentimentMapper.selectCount(new LambdaQueryWrapper<CommentSentiment>()
                .in(CommentSentiment::getCommentId, ids)
                .eq(CommentSentiment::getLowConfidence, 1));
    }

    /** 姓名集合：用于脱敏 */
    private Set<String> collectNames() {
        Set<String> names = new HashSet<>();
        studentMapper.selectList(null).forEach(s -> names.add(s.getName()));
        teacherMapper.selectList(null).forEach(t -> names.add(t.getName()));
        return names;
    }

    /** 单条点评的分析明细（前端"分析依据"抽屉） */
    public Map<String, Object> detail(Long commentId) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            return null;
        }
        CommentSentiment s = sentimentMapper.selectOne(new LambdaQueryWrapper<CommentSentiment>()
                .eq(CommentSentiment::getCommentId, commentId).last("limit 1"));
        Set<String> names = collectNames();
        SentimentResult live = analyzer.analyze(c.getContent(), names);

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("commentId", commentId);
        ret.put("stored", s);
        ret.put("live", live);
        ret.put("method", SentimentAnalyzer.METHOD);
        ret.put("methodVersion", SentimentAnalyzer.METHOD_VERSION);
        ret.put("status", c.getStatus());
        ret.put("statusLabel", CommentStatus.label(c.getStatus()));
        ret.put("published", c.getPublishedAt() != null);
        return ret;
    }
}
