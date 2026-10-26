package com.graduation.mentor.service.analysis;

import java.util.List;
import lombok.Data;

/** 单条点评的情感分析结果 */
@Data
public class SentimentResult {

    /** POSITIVE / NEUTRAL / NEGATIVE */
    private String label;

    /** 词典法累计净得分 */
    private double score;

    /** 置信度 0~1 */
    private double confidence;

    private int posHits;
    private int negHits;

    /** 1 = 落在阈值灰区，建议人工复核 */
    private boolean lowConfidence;

    /** 命中明细，用于前端"分析依据"抽屉展示，体现可解释性 */
    private List<Hit> hits;

    @Data
    public static class Hit {
        private String word;
        private String polarity;
        private double weight;
        private boolean negated;

        public Hit(String word, String polarity, double weight, boolean negated) {
            this.word = word;
            this.polarity = polarity;
            this.weight = weight;
            this.negated = negated;
        }
    }
}
