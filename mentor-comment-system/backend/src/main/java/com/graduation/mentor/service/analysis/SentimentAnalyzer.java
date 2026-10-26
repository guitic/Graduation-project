package com.graduation.mentor.service.analysis;

import com.hankcs.hanlp.seg.common.Term;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 情感倾向分析器 —— 情感词典法（可解释基线方案）。
 *
 * <p>算法（论文 §情感分析设计可直接引用）：
 *
 * <pre>
 * 1. 脱敏：屏蔽姓名 / 学号 / 手机号 / 邮箱，避免身份信息进入分析链路
 * 2. 分词：HanLP 精确模式
 * 3. 遍历词条，若命中情感词，则向前回看 2 个词：
 *    - 命中否定词 → 极性翻转（乘 -0.8，弱于直接肯定）
 *    - 命中程度副词 → 权重相乘（很 1.6 / 非常 2.0 / 比较 0.8 ...）
 * 4. 累计净得分 score = Σ(极性 × 程度权重 × 否定修正)
 * 5. 阈值映射：score ≥ +1.5 正向；score ≤ -1.5 负向；其余中性
 * 6. 灰区标记：|score| &lt; 2.0 判为低置信度，供人工复核
 * </pre>
 *
 * <p>为什么用阈值 1.5 而不是按词数归一：学业点评多为 30~80 字，绝对得分更稳定； 若做归一化，一句"很好"（1.0/1 词）会被放大成强正向，反而失真。
 */
@Component
@RequiredArgsConstructor
public class SentimentAnalyzer {

    /** 正向阈值 */
    private static final double POS_THRESHOLD = 1.5;

    /** 负向阈值 */
    private static final double NEG_THRESHOLD = -1.5;

    /** 灰区上界：|score| 小于该值视为低置信度 */
    private static final double GRAY_ZONE = 2.0;

    /** 否定词修正系数（"不好"弱于"很差"） */
    private static final double NEGATION_DAMPING = -0.8;

    /** 向前回看窗口 */
    private static final int LOOK_BACK = 2;

    public static final String METHOD = "LEXICON";
    public static final String METHOD_VERSION = "lexicon-v1.0";

    private final SentimentLexicon lexicon;
    private final WordSegmenter segmenter;

    public SentimentResult analyze(String rawText) {
        return analyze(rawText, null);
    }

    public SentimentResult analyze(String rawText, java.util.Set<String> namesToMask) {
        String text = PrivacyFilter.mask(rawText, namesToMask);
        List<Term> terms = segmenter.segment(text);

        double score = 0;
        int posHits = 0;
        int negHits = 0;
        List<SentimentResult.Hit> hits = new ArrayList<>();

        for (int i = 0; i < terms.size(); i++) {
            String word = terms.get(i).word;
            int polarity;
            if (lexicon.isPositive(word)) {
                polarity = 1;
            } else if (lexicon.isNegative(word)) {
                polarity = -1;
            } else {
                continue;
            }

            double degreeWeight = 1.0;
            boolean negated = false;
            for (int j = Math.max(0, i - LOOK_BACK); j < i; j++) {
                String prev = terms.get(j).word;
                if (lexicon.isNegation(prev)) {
                    negated = true;
                }
                if (lexicon.degreeWeight(prev) != 1.0) {
                    degreeWeight *= lexicon.degreeWeight(prev);
                }
            }

            double w = polarity * degreeWeight * (negated ? NEGATION_DAMPING : 1.0);
            score += w;
            if (polarity > 0) {
                posHits++;
            } else {
                negHits++;
            }
            hits.add(new SentimentResult.Hit(word, polarity > 0 ? "POS" : "NEG", w, negated));
        }

        SentimentResult r = new SentimentResult();
        r.setScore(round4(score));
        r.setPosHits(posHits);
        r.setNegHits(negHits);
        r.setHits(hits);

        String label;
        if (score >= POS_THRESHOLD) {
            label = "POSITIVE";
        } else if (score <= NEG_THRESHOLD) {
            label = "NEGATIVE";
        } else {
            label = "NEUTRAL";
        }
        r.setLabel(label);

        // 置信度：离中性越远越自信
        double conf = Math.min(1.0, Math.abs(score) / 3.0);
        if ("NEUTRAL".equals(label)) {
            conf = 1.0 - Math.min(1.0, Math.abs(score) / POS_THRESHOLD);
        }
        r.setConfidence(round4(conf));
        r.setLowConfidence(Math.abs(score) < GRAY_ZONE);
        return r;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
