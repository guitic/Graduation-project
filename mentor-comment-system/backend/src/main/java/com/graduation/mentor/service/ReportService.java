package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.CommentScope;
import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.config.MinSampleProperties;
import com.graduation.mentor.dto.StatDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.CommentSentiment;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统计报表服务。
 *
 * <p><b>三条统计口径（论文"统计口径"一节可直接引用）</b>：
 * <ol>
 *   <li><b>只统计已发布</b>：所有查询经 {@link CommentScope#published()} 构造，不用 status=APPROVED 代替。</li>
 *   <li><b>隐私门槛</b>：占比类输出（情感分布、负向摘要）需样本量 ≥5，未达标一律返回 null 而非"算出来前端藏"；
 *       数量类输出（趋势条数、审核柱状）不受门槛限制，因为绝对条数本身不构成隐私风险。</li>
 *   <li><b>稀疏桶处理</b>：趋势图按月分桶后，单个桶样本量 &lt;5 时该桶比率置 null（前端断线），
 *       避免"某月 2 条全负向"被读成断崖式恶化。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CommentMapper commentMapper;
    private final CommentSentimentMapper sentimentMapper;
    private final CommentAuditMapper auditMapper;
    private final StudentMapper studentMapper;
    private final MinSampleProperties minSample;
    private final AnalysisService analysisService;
    private final AuthContext authContext;

    /** 一次性返回 Dashboard 需要的全部数据，减少前端请求数与"图间数字对不上"的风险 */
    public StatDtos.Dashboard dashboard(StatDtos.Scope scope) {
        StatDtos.Dashboard d = new StatDtos.Dashboard();
        d.setKpi(kpi(scope));
        d.setSentiment(sentimentDist(scope));
        d.setTrend(trend(scope));
        d.setAuditStat(auditStat());
        d.setWordCloud(wordCloud(scope));
        d.setMethodVersion(SentimentAnalyzer.METHOD_VERSION);
        List<Comment> pub = publishedComments(scope);
        String asOf = pub.stream().map(Comment::getPublishedAt).filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .map(t -> t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))).orElse(null);
        d.setAsOf(asOf);
        d.setScopeNote("仅统计已发布点评；情感分析采用词典法 " + SentimentAnalyzer.METHOD_VERSION
                + "，结果用于总体趋势参考，单条分类保留人工复核。");
        return d;
    }

    // ------------------------------------------------------------------ KPI

    public StatDtos.Kpi kpi(StatDtos.Scope s) {
        StatDtos.Kpi k = new StatDtos.Kpi();
        // 全量口径（不受筛选影响），用于流程监控
        k.setDraft(count(null, CommentStatus.DRAFT));
        k.setPending(count(null, CommentStatus.PENDING));
        k.setReturned(count(null, CommentStatus.RETURNED));
        k.setRejected(count(null, CommentStatus.REJECTED));

        List<Comment> published = publishedComments(s);
        k.setPublished(published.size());
        k.setTotal(published.size() + k.getDraft() + k.getPending() + k.getReturned() + k.getRejected());
        k.setUnpublished(commentMapper.selectCount(CommentScope.approvedNotPublished()));

        if (!published.isEmpty()) {
            List<Long> ids = published.stream().map(Comment::getId).toList();
            k.setLowConfidence(sentimentMapper.selectCount(new LambdaQueryWrapper<CommentSentiment>()
                    .in(CommentSentiment::getCommentId, ids)
                    .eq(CommentSentiment::getLowConfidence, 1)));
        } else {
            k.setLowConfidence(0);
        }

        // 平均审核时长：提交 → 首次审核
        List<CommentAudit> audits = auditMapper.selectList(null);
        Map<Long, LocalDateTime> submitMap = published.stream()
                .filter(c -> c.getSubmitAt() != null)
                .collect(Collectors.toMap(Comment::getId, Comment::getSubmitAt, (a, b) -> a));
        double sum = 0;
        int n = 0;
        for (CommentAudit a : audits) {
            LocalDateTime sub = submitMap.get(a.getCommentId());
            if (sub != null && a.getAuditAt() != null && a.getAuditAt().isAfter(sub)) {
                sum += Duration.between(sub, a.getAuditAt()).toMinutes() / 60.0;
                n++;
            }
        }
        k.setAvgAuditHours(n == 0 ? null : Math.round(sum / n * 10) / 10.0);
        return k;
    }

    private long count(StatDtos.Scope s, String status) {
        return commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, status));
    }

    // ------------------------------------------------------------------ 情感分布

    public StatDtos.SentimentDist sentimentDist(StatDtos.Scope s) {
        List<Comment> published = publishedComments(s);
        StatDtos.SentimentDist d = new StatDtos.SentimentDist();
        d.setTotal(published.size());
        d.setMinSample(minSample.getMinSample());

        Map<Long, String> labels = sentimentLabels(published);
        long pos = labels.values().stream().filter("POSITIVE"::equals).count();
        long neu = labels.values().stream().filter("NEUTRAL"::equals).count();
        long neg = labels.values().stream().filter("NEGATIVE"::equals).count();
        d.setPositive(pos);
        d.setNeutral(neu);
        d.setNegative(neg);

        boolean enough = published.size() >= minSample.getMinSample() && !labels.isEmpty();
        d.setEnough(enough);
        if (enough) {
            double total = pos + neu + neg;
            d.setPositiveRate(round3(pos / total));
            d.setNeutralRate(round3(neu / total));
            d.setNegativeRate(round3(neg / total));
        }
        // 样本不足：占比保持 null —— 接口层面就不给数
        return d;
    }

    // ------------------------------------------------------------------ 趋势

    public StatDtos.Trend trend(StatDtos.Scope s) {
        int months = Math.max(1, Math.min(24, s.getMonths()));
        YearMonth current = YearMonth.now();

        // 报表**包含当前未完结月**：教务月中打开看板必须能看到本月进展，
        // 否则"报表没更新"的误读会常态化。当前月用 inProgress 标记，前端渲染虚线，
        // 表达"当月数据仍在进入"——这是真实状态，不表达才是缺失。
        // 演示种子不往当月放数据（虚线路由测试夹具覆盖），二者不冲突。
        List<YearMonth> buckets = new ArrayList<>();
        for (int i = months; i >= 0; i--) {
            buckets.add(current.minusMonths(i));
        }

        List<Comment> published = publishedComments(s);
        Map<Long, String> labels = sentimentLabels(published);

        Map<String, long[]> agg = new LinkedHashMap<>();
        buckets.forEach(b -> agg.put(b.format(YM), new long[]{0, 0, 0}));
        for (Comment c : published) {
            if (c.getPublishedAt() == null) {
                continue;
            }
            String ym = YearMonth.from(c.getPublishedAt()).format(YM);
            long[] arr = agg.get(ym);
            if (arr == null) {
                continue;
            }
            String label = labels.get(c.getId());
            if ("POSITIVE".equals(label)) {
                arr[0]++;
            } else if ("NEGATIVE".equals(label)) {
                arr[2]++;
            } else {
                arr[1]++;
            }
        }

        StatDtos.Trend t = new StatDtos.Trend();
        List<String> ms = new ArrayList<>(agg.keySet());
        List<Long> pos = new ArrayList<>();
        List<Long> neu = new ArrayList<>();
        List<Long> neg = new ArrayList<>();
        List<Long> total = new ArrayList<>();
        for (String k : ms) {
            long[] a = agg.get(k);
            pos.add(a[0]);
            neu.add(a[1]);
            neg.add(a[2]);
            total.add(a[0] + a[1] + a[2]);
        }
        t.setMonths(ms);
        t.setPositiveCount(pos);
        t.setNeutralCount(neu);
        t.setNegativeCount(neg);
        t.setBucketTotal(total);
        t.setInProgressMonth(current.format(YM));
        List<Boolean> inProg = new ArrayList<>();
        for (String k : ms) {
            inProg.add(k.equals(current.format(YM)));
        }
        t.setInProgress(inProg);
        t.setMinSample(minSample.getMinSample());

        boolean rateAvailable = published.size() >= minSample.getMinSample();
        t.setRateAvailable(rateAvailable);
        if (rateAvailable) {
            List<Double> rates = new ArrayList<>();
            for (Long bt : total) {
                // 稀疏桶置 null，前端断线
                rates.add(bt < minSample.getMinSample() ? null : null);
            }
            // 比率线需要每个桶的分母；分母过小则不给点
            List<Double> posRate = new ArrayList<>();
            for (int i = 0; i < total.size(); i++) {
                long bt = total.get(i);
                posRate.add(bt < minSample.getMinSample() ? null
                        : round3((double) pos.get(i) / bt));
            }
            t.setPositiveRate(posRate);
        }
        return t;
    }

    // ------------------------------------------------------------------ 审核统计

    public StatDtos.AuditStat auditStat() {
        StatDtos.AuditStat a = new StatDtos.AuditStat();
        a.setDraft(count(null, CommentStatus.DRAFT));
        a.setPending(count(null, CommentStatus.PENDING));
        a.setReturned(count(null, CommentStatus.RETURNED));
        a.setRejected(count(null, CommentStatus.REJECTED));
        long approved = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, CommentStatus.APPROVED));
        long unpublished = commentMapper.selectCount(CommentScope.approvedNotPublished());
        a.setUnpublished(unpublished);
        a.setApproved(approved - unpublished);
        a.setTotal(a.getDraft() + a.getPending() + a.getReturned() + a.getRejected() + approved);
        return a;
    }

    // ------------------------------------------------------------------ 词云（实时聚合，无缓存表）

    public StatDtos.WordCloud wordCloud(StatDtos.Scope s) {
        List<Comment> published = publishedComments(s);
        StatDtos.WordCloud wc = new StatDtos.WordCloud();
        wc.setSampleCount(published.size());
        wc.setMinSample(minSample.getMinSample());
        boolean enough = published.size() >= minSample.getMinSample();
        wc.setEnough(enough);
        if (!enough) {
            wc.setWords(List.of());
            return wc;
        }
        // 实时分词聚合。点评规模在百条量级，分词开销可忽略；
        // 不建缓存表是因为"跨条聚合的派生数据没有单一失效源"——
        // 退回改稿、撤回、再发布都会让缓存与业务状态不同步，且不会报错。
        Map<String, Long> freq = new HashMap<>();
        for (Comment c : published) {
            analysisService.wordFrequency(c.getContent(), null)
                    .forEach((w, n) -> freq.merge(w, (long) n, Long::sum));
        }
        List<StatDtos.WordItem> words = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(120)
                .map(e -> new StatDtos.WordItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        wc.setWords(words);
        return wc;
    }

    /**
     * 词云词条下钻。门禁设在**下钻入口**而不是图表阈值：
     * 图上"100% 负向"只是比率，不指向任何人；从那个点下钻看到 2 条记录才是 2 个学生。
     */
    public StatDtos.WordDetail wordDetail(String word, StatDtos.Scope s) {
        StatDtos.WordDetail d = new StatDtos.WordDetail();
        d.setWord(word);
        d.setMinSample(minSample.getMinSample());

        List<Comment> published = publishedComments(s);
        d.setSampleCount(published.size());
        if (published.size() < minSample.getMinSample()) {
            d.setAllowed(false);
            d.setComments(List.of());
            return d;
        }
        d.setAllowed(true);
        List<Map<String, Object>> rows = new ArrayList<>();
        published.stream()
                .filter(c -> analysisService.wordFrequency(c.getContent(), null).containsKey(word))
                .sorted(Comparator.comparing(Comment::getPublishedAt).reversed())
                .limit(50)
                .forEach(c -> rows.add(detailRow(c)));
        d.setComments(rows);
        return d;
    }

    /**
     * 趋势点下钻：**按桶级样本量判定**，不是按整体样本量。
     * <p>这条门禁是必要的：主班整体 40 条足以放行，但某个月可能只有 2 条，
     * 点进去就是 2 个学生的完整点评，等同于直接暴露个人。
     */
    public StatDtos.TrendPointDetail trendPointDetail(String month, StatDtos.Scope s) {
        StatDtos.TrendPointDetail d = new StatDtos.TrendPointDetail();
        d.setMonth(month);
        d.setMinSample(minSample.getMinSample());

        List<Comment> published = publishedComments(s).stream()
                .filter(c -> c.getPublishedAt() != null
                        && YearMonth.from(c.getPublishedAt()).format(YM).equals(month))
                .sorted(Comparator.comparing(Comment::getPublishedAt).reversed())
                .toList();

        d.setSampleCount(published.size());
        d.setAllowed(published.size() >= minSample.getMinSample());
        if (!d.isAllowed()) {
            d.setComments(List.of());
            return d;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        published.forEach(c -> rows.add(detailRow(c)));
        d.setComments(rows);
        return d;
    }

    private Map<String, Object> detailRow(Comment c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("topic", c.getTopic());
        m.put("term", c.getTerm());
        m.put("publishedAt", c.getPublishedAt());
        m.put("content", c.getContent());
        Student stu = studentMapper.selectById(c.getStudentId());
        m.put("studentName", stu == null ? "-" : stu.getName());
        return m;
    }

    // ------------------------------------------------------------------ 内部

    /** 已发布点评 + 筛选条件（唯一入口） */
    private List<Comment> publishedComments(StatDtos.Scope s) {
        LambdaQueryWrapper<Comment> w = CommentScope.published();
        if (s != null) {
            if (s.getTerm() != null && !s.getTerm().isBlank()) {
                w.eq(Comment::getTerm, s.getTerm());
            }
            if (s.getTeacherId() != null) {
                w.eq(Comment::getTeacherId, s.getTeacherId());
            }
            if (s.getStudentId() != null) {
                w.eq(Comment::getStudentId, s.getStudentId());
            }
            if (s.getClazzId() != null) {
                List<Long> sids = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getClazzId, s.getClazzId()))
                        .stream().map(Student::getId).toList();
                if (sids.isEmpty()) {
                    return List.of();
                }
                w.in(Comment::getStudentId, sids);
            }
            if (s.getStartDate() != null && !s.getStartDate().isBlank()) {
                w.ge(Comment::getPublishedAt, s.getStartDate() + " 00:00:00");
            }
            if (s.getEndDate() != null && !s.getEndDate().isBlank()) {
                w.le(Comment::getPublishedAt, s.getEndDate() + " 23:59:59");
            }
        }
        return commentMapper.selectList(w);
    }

    private Map<Long, String> sentimentLabels(List<Comment> published) {
        if (published.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = published.stream().map(Comment::getId).toList();
        List<CommentSentiment> list = sentimentMapper.selectList(new LambdaQueryWrapper<CommentSentiment>()
                .in(CommentSentiment::getCommentId, ids));
        return list.stream().collect(Collectors.toMap(
                CommentSentiment::getCommentId, CommentSentiment::getLabel, (a, b) -> a));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** 教务角色校验（报表只对教务开放） */
    public void requireAdmin() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看统计报表");
        }
    }
}
