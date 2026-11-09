package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统计报表服务。
 *
 * <p><b>三条统计口径（论文"统计口径"一节可直接引用）</b>：
 *
 * <ol>
 *   <li><b>只统计已发布</b>：所有查询经 {@link CommentScope#published()} 构造，不用 status=APPROVED 代替。
 *   <li><b>隐私门槛</b>：占比类输出（情感分布、负向摘要）需样本量 ≥5，未达标一律返回 null 而非"算出来前端藏"；
 *       数量类输出（趋势条数、审核柱状）不受门槛限制，因为绝对条数本身不构成隐私风险。
 *   <li><b>稀疏桶处理</b>：趋势图按月分桶后，单个桶样本量 &lt;5 时该桶比率置 null（前端断线）， 避免"某月 2 条全负向"被读成断崖式恶化。
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

    /** 只借用它的行转换（toRows）：下钻明细的字段与列表页必须一致，不能各拼一套 */
    private final CommentService commentService;

    /** 一次性返回 Dashboard 需要的全部数据，减少前端请求数与"图间数字对不上"的风险 */
    public StatDtos.Dashboard dashboard(StatDtos.Scope scope) {
        StatDtos.Dashboard d = new StatDtos.Dashboard();
        d.setKpi(kpi(scope));
        d.setSentiment(sentimentDist(scope));
        d.setTrend(trend(scope));
        d.setAuditStat(auditStat());
        d.setWordCloud(wordCloud(scope));
        d.setMethodVersion(SentimentAnalyzer.METHOD_VERSION);
        d.setAsOf(latestPublishedAt(publishedComments(scope)));
        d.setScopeNote(
                "仅统计已发布点评；情感分析采用词典法 "
                        + SentimentAnalyzer.METHOD_VERSION
                        + "，结果用于总体趋势参考，单条分类保留人工复核。");
        return d;
    }

    /**
     * 导师端个人画像。
     *
     * <p><b>teacherId 由 Controller 从登录态取，不接受请求参数</b>：一旦允许前端传， 越权只需要改一个数字。筛选条件只放行学期 / 班级 /
     * 时间窗三个维度，其余一律忽略。
     *
     * <p>返回体不含审核队列统计（{@link StatDtos.AuditStat}），见 {@link StatDtos.MyDashboard} 的注释。
     */
    public StatDtos.MyDashboard myDashboard(Long teacherId, StatDtos.Scope raw) {
        StatDtos.Scope s = new StatDtos.Scope();
        s.setTeacherId(teacherId);
        if (raw != null) {
            s.setTerm(raw.getTerm());
            s.setClazzId(raw.getClazzId());
            s.setStartDate(raw.getStartDate());
            s.setEndDate(raw.getEndDate());
            s.setMonths(raw.getMonths());
        }

        StatDtos.MyDashboard d = new StatDtos.MyDashboard();
        d.setKpi(myKpi(teacherId, s));
        d.setSentiment(sentimentDist(s));
        d.setTrend(trend(s));
        d.setWordCloud(wordCloud(s));
        d.setMethodVersion(SentimentAnalyzer.METHOD_VERSION);
        d.setAsOf(latestPublishedAt(publishedComments(s)));
        d.setScopeNote(
                "仅统计本人已发布点评；样本不足 " + minSample.getMinSample() + " 条时不展示占比与词云，避免从少量文本反推学生情况。");
        return d;
    }

    /**
     * 导师端 KPI：**必须单独算**。
     *
     * <p>{@link #kpi(StatDtos.Scope)} 里的草稿 / 待审核 / 退回数走的是全量口径 （{@code count(null, status)} 忽略
     * scope），直接复用会让导师看到全院的草稿数。
     */
    private StatDtos.Kpi myKpi(Long teacherId, StatDtos.Scope s) {
        StatDtos.Kpi k = new StatDtos.Kpi();
        k.setDraft(countByTeacher(teacherId, CommentStatus.DRAFT));
        k.setPending(countByTeacher(teacherId, CommentStatus.PENDING));
        k.setReturned(countByTeacher(teacherId, CommentStatus.RETURNED));
        k.setRejected(countByTeacher(teacherId, CommentStatus.REJECTED));
        List<Comment> published = publishedComments(s);
        k.setPublished(published.size());
        k.setTotal(
                published.size()
                        + k.getDraft()
                        + k.getPending()
                        + k.getReturned()
                        + k.getRejected());
        k.setLowConfidence(lowConfidenceCount(published));
        // 待发布是教务的待办，导师端不展示
        k.setUnpublished(0);
        return k;
    }

    private long countByTeacher(Long teacherId, String status) {
        return commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTeacherId, teacherId)
                        .eq(Comment::getStatus, status));
    }

    /** 低置信度条数：灰区样本，提示人工复核 */
    private long lowConfidenceCount(List<Comment> published) {
        if (published.isEmpty()) {
            return 0;
        }
        List<Long> ids = published.stream().map(Comment::getId).toList();
        return sentimentMapper.selectCount(
                new LambdaQueryWrapper<CommentSentiment>()
                        .in(CommentSentiment::getCommentId, ids)
                        .eq(CommentSentiment::getLowConfidence, 1));
    }

    /** 统计截至：已发布点评中最大的 published_at，由数据自证窗口 */
    private String latestPublishedAt(List<Comment> published) {
        return published.stream()
                .map(Comment::getPublishedAt)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .map(t -> t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .orElse(null);
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
        k.setTotal(
                published.size()
                        + k.getDraft()
                        + k.getPending()
                        + k.getReturned()
                        + k.getRejected());
        k.setUnpublished(commentMapper.selectCount(CommentScope.approvedNotPublished()));

        if (!published.isEmpty()) {
            List<Long> ids = published.stream().map(Comment::getId).toList();
            k.setLowConfidence(
                    sentimentMapper.selectCount(
                            new LambdaQueryWrapper<CommentSentiment>()
                                    .in(CommentSentiment::getCommentId, ids)
                                    .eq(CommentSentiment::getLowConfidence, 1)));
        } else {
            k.setLowConfidence(0);
        }

        // 平均审核时长：提交 → 首次审核
        List<CommentAudit> audits = auditMapper.selectList(null);
        Map<Long, LocalDateTime> submitMap =
                published.stream()
                        .filter(c -> c.getSubmitAt() != null)
                        .collect(
                                Collectors.toMap(
                                        Comment::getId, Comment::getSubmitAt, (a, b) -> a));
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
        return commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, status));
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
        buckets.forEach(b -> agg.put(b.format(YM), new long[] {0, 0, 0}));
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
                posRate.add(
                        bt < minSample.getMinSample() ? null : round3((double) pos.get(i) / bt));
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
        long approved =
                commentMapper.selectCount(
                        new LambdaQueryWrapper<Comment>()
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
            analysisService
                    .wordFrequency(c.getContent(), null)
                    .forEach((w, n) -> freq.merge(w, (long) n, Long::sum));
        }
        List<StatDtos.WordItem> words =
                freq.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(120)
                        .map(e -> new StatDtos.WordItem(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
        wc.setWords(words);
        return wc;
    }

    /** 词云词条下钻。门禁设在**下钻入口**而不是图表阈值： 图上"100% 负向"只是比率，不指向任何人；从那个点下钻看到 2 条记录才是 2 个学生。 */
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
     *
     * <p>这条门禁是必要的：主班整体 40 条足以放行，但某个月可能只有 2 条， 点进去就是 2 个学生的完整点评，等同于直接暴露个人。
     */
    public StatDtos.TrendPointDetail trendPointDetail(String month, StatDtos.Scope s) {
        StatDtos.TrendPointDetail d = new StatDtos.TrendPointDetail();
        d.setMonth(month);
        d.setMinSample(minSample.getMinSample());

        List<Comment> published =
                publishedComments(s).stream()
                        .filter(
                                c ->
                                        c.getPublishedAt() != null
                                                && YearMonth.from(c.getPublishedAt())
                                                        .format(YM)
                                                        .equals(month))
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

    /**
     * 已发布点评列表（导出模块使用）。
     *
     * <p>刻意做成 {@code public} 而不是让导出模块自己拼条件： 一旦导出绕过 {@link CommentScope#published()}，就会出现"页面上 56
     * 条、导出的 Excel 里 60 条"——多出来的正是待审核与已撤回的点评，而且这种错误在 Excel 里看不出来。
     */
    public List<Comment> publishedList(StatDtos.Scope s) {
        return publishedComments(s);
    }

    /** 已发布点评 + 筛选条件（唯一入口） */
    private List<Comment> publishedComments(StatDtos.Scope s) {
        return commentMapper.selectList(publishedQuery(s));
    }

    /**
     * 已发布 + 筛选条件的条件对象。
     *
     * <p>单独抽出来是因为 KPI 下钻必须**在 SQL 层分页**：先查出全量再截断， 深页会把整张表读进内存。条件构造与 {@link #publishedComments}
     * 完全同一份， 不存在"统计一套条件、明细另一套"的可能。
     */
    private LambdaQueryWrapper<Comment> publishedQuery(StatDtos.Scope s) {
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
                List<Long> sids =
                        studentMapper
                                .selectList(
                                        new LambdaQueryWrapper<Student>()
                                                .eq(Student::getClazzId, s.getClazzId()))
                                .stream()
                                .map(Student::getId)
                                .toList();
                if (sids.isEmpty()) {
                    // 空集合不能直接 in：MyBatis-Plus 会生成 IN () 语法错误
                    return w.eq(Comment::getId, -1L);
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
        return w;
    }

    private Map<Long, String> sentimentLabels(List<Comment> published) {
        if (published.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = published.stream().map(Comment::getId).toList();
        List<CommentSentiment> list =
                sentimentMapper.selectList(
                        new LambdaQueryWrapper<CommentSentiment>()
                                .in(CommentSentiment::getCommentId, ids));
        return list.stream()
                .collect(
                        Collectors.toMap(
                                CommentSentiment::getCommentId,
                                CommentSentiment::getLabel,
                                (a, b) -> a));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ------------------------------------------------------------------ KPI 卡下钻

    /**
     * KPI 卡下钻：点「待审核 8」就要能看到这 8 条。
     *
     * <p><b>为什么不复用 {@code /comment/admin-page}</b>：那张表用一个 status 参数表达状态， 而 KPI 的口径不是单一 status ——
     * 「已发布 / 低置信」是 {@link CommentScope#published()} 再叠加筛选，「待审 / 待发 / 退回 / 驳回」是刻意忽略筛选的全量口径（见 {@link
     * #kpi}）， 「总数」还是两部分的并集。这些条件塞不进一个 status 参数，强行复用就会出现 「卡片 56、明细 40」这种自相矛盾的演示事故。
     *
     * <p>因此这里直接复用 {@link #kpi} 的条件构造，保证「明细条数 = 卡片数字」恒等。
     */
    public StatDtos.KpiDetail kpiDetail(String kpi, StatDtos.Scope s, long page, long size) {
        String key = kpi == null ? "" : kpi.trim();
        LambdaQueryWrapper<Comment> w =
                switch (key) {
                    case "published" -> publishedQuery(s);
                    case "lowConfidence" -> lowConfidenceQuery(s);
                    case "pending" -> statusQuery(CommentStatus.PENDING);
                    case "unpublished" -> CommentScope.approvedNotPublished();
                    case "returned" -> statusQuery(CommentStatus.RETURNED);
                    case "rejected" -> statusQuery(CommentStatus.REJECTED);
                    case "total" -> totalQuery(s);
                    default -> throw BizException.conflict("不支持的统计指标：" + key);
                };

        long p = Math.max(1, page);
        long sz = Math.min(100, Math.max(1, size));
        w.orderByDesc(Comment::getUpdatedAt).orderByDesc(Comment::getId);
        Page<Comment> pg = commentMapper.selectPage(new Page<>(p, sz), w);

        StatDtos.KpiDetail d = new StatDtos.KpiDetail();
        d.setKpi(key);
        d.setLabel(KPI_LABEL.getOrDefault(key, key));
        d.setCaliber(KPI_CALIBER.getOrDefault(key, ""));
        d.setTotal(pg.getTotal());
        d.setPage(p);
        d.setSize(sz);
        d.setRecords(commentService.toRows(pg.getRecords()));
        return d;
    }

    /** 指标 key → 卡片标题。与前端 KpiGrid 的 k.key 一一对应 */
    private static final Map<String, String> KPI_LABEL =
            Map.of(
                    "total", "点评总数",
                    "published", "已发布",
                    "pending", "待审核",
                    "unpublished", "待发布",
                    "returned", "退回修改",
                    "rejected", "已驳回",
                    "lowConfidence", "低置信");

    /**
     * 指标 key → 口径说明。
     *
     * <p>必须写清"受不受学期/导师/班级筛选影响"：KPI 里状态类指标是全量口径， 用户筛了学期再点「待审核」，看到的是全院的待审——不解释就会被当成漏数据。
     */
    private static final Map<String, String> KPI_CALIBER =
            Map.of(
                    "total", "已发布（随筛选）+ 草稿/待审/退回/驳回（全量）；不含待发布",
                    "published", "审核通过且已推送学生，随学期/导师/班级筛选",
                    "pending", "全量口径，不受学期/导师/班级筛选影响",
                    "unpublished", "已通过但未推送学生，全量口径",
                    "returned", "全量口径，不受学期/导师/班级筛选影响",
                    "rejected", "终态·不发布，全量口径",
                    "lowConfidence", "已发布中置信度落在灰区的点评，建议人工复核；随筛选");

    private LambdaQueryWrapper<Comment> statusQuery(String status) {
        return new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, status);
    }

    /**
     * 总数 = 已发布（受筛选）∪ 草稿/待审/退回/驳回（全量）。
     *
     * <p>与 {@link #kpi} 里那段加法逐项对应：published + draft + pending + returned + rejected。 SQL 侧是 {@code
     * A AND B OR C}，AND 优先级高于 OR，等价于 {@code (A AND B) OR C}； C 只是单个 IN 谓词，不会被 OR 拆错。
     */
    private LambdaQueryWrapper<Comment> totalQuery(StatDtos.Scope s) {
        return publishedQuery(s)
                .or()
                .in(
                        Comment::getStatus,
                        List.of(
                                CommentStatus.DRAFT,
                                CommentStatus.PENDING,
                                CommentStatus.RETURNED,
                                CommentStatus.REJECTED));
    }

    /** 低置信口径：已发布 ∩ 情感结果落在灰区。空集合补 -1 兜底，避免 IN () 语法错误 */
    private LambdaQueryWrapper<Comment> lowConfidenceQuery(StatDtos.Scope s) {
        LambdaQueryWrapper<Comment> w = publishedQuery(s);
        List<Long> ids = lowConfidenceIds(s);
        if (ids.isEmpty()) {
            return w.eq(Comment::getId, -1L);
        }
        return w.in(Comment::getId, ids);
    }

    private List<Long> lowConfidenceIds(StatDtos.Scope s) {
        List<Long> publishedIds = publishedComments(s).stream().map(Comment::getId).toList();
        if (publishedIds.isEmpty()) {
            return List.of();
        }
        return sentimentMapper
                .selectList(
                        new LambdaQueryWrapper<CommentSentiment>()
                                .in(CommentSentiment::getCommentId, publishedIds)
                                .eq(CommentSentiment::getLowConfidence, 1))
                .stream()
                .map(CommentSentiment::getCommentId)
                .distinct()
                .toList();
    }

    /** 教务角色校验（报表只对教务开放） */
    public void requireAdmin() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看统计报表");
        }
    }
}
