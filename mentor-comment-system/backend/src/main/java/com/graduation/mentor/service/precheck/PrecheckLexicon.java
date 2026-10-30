package com.graduation.mentor.service.precheck;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 预审词表加载器。
 *
 * <p>与情感词典同一套思路：词表以纯文本放在 {@code classpath:lexicon/} 下，改词表不用改代码， 论文里可以直接写"审核规则词表可扩展、可维护"。四类词表：
 *
 * <ul>
 *   <li>{@code precheck-vague.txt} 通用套话（"表现良好""继续努力"）——判定内容是否空泛
 *   <li>{@code precheck-derogatory.txt} 贬损性表述——命中即阻断，建议驳回
 *   <li>{@code precheck-advice.txt} 建议引导词——判定是否给出改进方向
 *   <li>{@code precheck-domain.txt} 学业领域线索词（课程、成绩、论文……）——判定是否有具体事实
 * </ul>
 *
 * <p>加载失败只告警不启动失败：某一维度词表缺失时，对应规则不生效，其余规则照常工作。 审核辅助能力不能因为一个 txt 文件缺失就把整个系统拦在启动之外。
 */
@Slf4j
@Component
public class PrecheckLexicon {

    /** 一次词表命中：词、在原文中的起止下标 */
    public record Match(String word, int start, int end) {}

    private final Set<String> vague = new LinkedHashSet<>();
    private final Set<String> derogatory = new LinkedHashSet<>();
    private final Set<String> advice = new LinkedHashSet<>();
    private final Set<String> domain = new LinkedHashSet<>();

    @PostConstruct
    public void load() {
        read("lexicon/precheck-vague.txt", vague);
        read("lexicon/precheck-derogatory.txt", derogatory);
        read("lexicon/precheck-advice.txt", advice);
        read("lexicon/precheck-domain.txt", domain);
        log.info(
                "预审词表加载完成：套话 {} 词，贬损 {} 词，建议 {} 词，领域 {} 词",
                vague.size(),
                derogatory.size(),
                advice.size(),
                domain.size());
    }

    public List<Match> matchVague(String text) {
        return match(vague, text);
    }

    public List<Match> matchDerogatory(String text) {
        return match(derogatory, text);
    }

    public List<Match> matchAdvice(String text) {
        return match(advice, text);
    }

    public List<Match> matchDomain(String text) {
        return match(domain, text);
    }

    /** 逐词查找首次出现位置，按位置升序返回。词表规模在百级，线性扫描足够。 */
    private List<Match> match(Set<String> words, String text) {
        List<Match> hits = new ArrayList<>();
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return hits;
        }
        for (String w : words) {
            int idx = text.indexOf(w);
            if (idx >= 0) {
                hits.add(new Match(w, idx, idx + w.length()));
            }
        }
        hits.sort(Comparator.comparingInt(Match::start));
        return hits;
    }

    private void read(String path, Set<String> target) {
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
            log.warn("预审词表加载失败：{}（{}），该维度规则将不生效", path, e.getMessage());
        }
    }
}
