package com.graduation.mentor.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 统计报表 DTO */
public class StatDtos {

    /** 统计筛选条件（与点评列表共用一套维度） */
    @Data
    public static class Scope {
        private String term;
        private Long teacherId;
        private Long clazzId;
        private Long studentId;
        private String startDate;
        private String endDate;
        /** 趋势图回溯月数，默认 6 */
        private int months = 6;
    }

    /** 概览卡片 */
    @Data
    public static class Kpi {
        private long total;
        private long published;
        private long pending;
        private long unpublished;
        private long returned;
        private long rejected;
        private long draft;
        private long lowConfidence;
        /** 平均审核时长（小时），无样本时为 null */
        private Double avgAuditHours;
    }

    /** 情感分布（受 n>=5 门槛约束） */
    @Data
    public static class SentimentDist {
        private long total;
        private long positive;
        private long neutral;
        private long negative;
        /** 占比：样本不足时一律为 null，前端拿不到数，避免"藏起来但接口给了" */
        private Double positiveRate;
        private Double neutralRate;
        private Double negativeRate;
        /** 是否达到最小样本量 */
        private boolean enough;
        private int minSample;
    }

    /** 趋势图 */
    @Data
    public static class Trend {
        /** 月份标签，如 2026-05 */
        private List<String> months;
        /** 各情感的数量序列（永远可画） */
        private List<Long> positiveCount;
        private List<Long> neutralCount;
        private List<Long> negativeCount;
        /**
         * 比率序列。稀疏桶为 null（前端断线），
         * 整图样本 <5 时整条为 null（前端不画比率线）。
         */
        private List<Double> positiveRate;
        /** 每个桶的样本量，前端做浅色处理 */
        private List<Long> bucketTotal;
        private boolean rateAvailable;
        private int minSample;
        /**
         * 当前未完结月份（最后一个桶）。前端据此把该段渲染成虚线/斜纹，
         * 表达"当月持续更新中"。生产环境每月都会触发，演示种子不放当月数据。
         */
        private String inProgressMonth;
        /** 每个桶是否属于当前未完结月 */
        private List<Boolean> inProgress;
    }

    /** 审核状态统计 */
    @Data
    public static class AuditStat {
        private long draft;
        private long pending;
        private long approved;
        private long unpublished;
        private long returned;
        private long rejected;
        private long total;
    }

    /** 词云项 */
    @Data
    public static class WordItem {
        private String name;
        private long value;

        public WordItem(String name, long value) {
            this.name = name;
            this.value = value;
        }
    }

    /** 词云结果（受 n>=3 门槛约束） */
    @Data
    public static class WordCloud {
        private long sampleCount;
        private boolean enough;
        private int minSample;
        private List<WordItem> words;
    }

    /** 词云词条下钻明细 */
    @Data
    public static class WordDetail {
        private String word;
        private long sampleCount;
        private boolean allowed;
        private int minSample;
        private List<Map<String, Object>> comments;
    }

    /** 趋势点下钻（门禁按桶级样本量） */
    @Data
    public static class TrendPointDetail {
        private String month;
        private long sampleCount;
        private boolean allowed;
        private int minSample;
        private List<Map<String, Object>> comments;
    }

    /** Dashboard 聚合返回 */
    @Data
    public static class Dashboard {
        private Kpi kpi;
        private SentimentDist sentiment;
        private Trend trend;
        private AuditStat auditStat;
        private WordCloud wordCloud;
        private String methodVersion;
        private String scopeNote;
        /** 统计截至：已发布点评中最大的 published_at。由数据自证窗口，不靠人记 */
        private String asOf;
    }
}
