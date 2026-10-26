package com.graduation.mentor.service.analysis;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 情感词典加载器。
 *
 * <p>四类词表：正向、负向、程度副词（带权重）、否定词。词表以纯文本放在 classpath:lexicon/ 下，可热替换，方便论文里说明"领域词典可扩展"。
 */
@Slf4j
@Component
public class SentimentLexicon {

    private final Set<String> positive = new HashSet<>();
    private final Set<String> negative = new HashSet<>();
    private final Set<String> negation = new HashSet<>();
    private final Map<String, Double> degree = new HashMap<>();
    private final Set<String> stopwords = new HashSet<>();

    @PostConstruct
    public void load() {
        readLines("lexicon/positive.txt", positive, false);
        readLines("lexicon/negative.txt", negative, false);
        readLines("lexicon/negation.txt", negation, false);
        readLines("lexicon/stopwords.txt", stopwords, false);
        readDegree("lexicon/degree.txt");
        log.info(
                "情感词典加载完成：正向 {} 词，负向 {} 词，否定 {} 词，程度 {} 词，停用 {} 词",
                positive.size(),
                negative.size(),
                negation.size(),
                degree.size(),
                stopwords.size());
    }

    private void readLines(String path, Set<String> target, boolean ignore) {
        try (BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource(path).getInputStream(),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                target.add(line);
            }
        } catch (Exception e) {
            log.warn("词典文件加载失败：{}（{}），相关词表将为空", path, e.getMessage());
        }
    }

    private void readDegree(String path) {
        try (BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource(path).getInputStream(),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("[,，\\s]+");
                if (parts.length >= 2) {
                    try {
                        degree.put(parts[0], Double.parseDouble(parts[1]));
                    } catch (NumberFormatException ignored) {
                        // 跳过脏行
                    }
                }
            }
        } catch (Exception e) {
            log.warn("程度词表加载失败：{}", e.getMessage());
        }
    }

    public boolean isPositive(String w) {
        return positive.contains(w);
    }

    public boolean isNegative(String w) {
        return negative.contains(w);
    }

    public boolean isNegation(String w) {
        return negation.contains(w);
    }

    public double degreeWeight(String w) {
        return degree.getOrDefault(w, 1.0);
    }

    public boolean isStopword(String w) {
        return stopwords.contains(w);
    }

    public Set<String> allSentimentWords() {
        Set<String> all = new HashSet<>(positive);
        all.addAll(negative);
        return Collections.unmodifiableSet(all);
    }
}
