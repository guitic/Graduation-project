package com.graduation.mentor.service.analysis;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 中文分词封装（HanLP portable，离线自带词典）。
 *
 * <p>降级策略：若 HanLP 数据缺失导致初始化/分词失败，自动退化为"按非汉字切分 + 双字组合"， 保证系统主流程不因分词组件异常而不可用（对应文档"核心流程不依赖外部服务"的要求）。
 */
@Slf4j
@Component
public class WordSegmenter {

    private boolean hanlpAvailable = true;

    @PostConstruct
    public void warmUp() {
        try {
            List<Term> terms = HanLP.segment("学习态度端正，成绩稳步提升。");
            if (terms == null || terms.isEmpty()) {
                throw new IllegalStateException("分词结果为空");
            }
            log.info("HanLP 分词器就绪，预热结果：{}", terms);
        } catch (Throwable t) {
            hanlpAvailable = false;
            log.warn("HanLP 不可用（{}），自动降级为简易切分。", t.getMessage());
        }
    }

    /** 返回带词性的词条列表 */
    public List<Term> segment(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        if (hanlpAvailable) {
            try {
                return HanLP.segment(text);
            } catch (Throwable t) {
                hanlpAvailable = false;
                log.warn("分词异常，降级处理：{}", t.getMessage());
            }
        }
        return fallback(text);
    }

    /** 降级切分：按非汉字边界切开，每段取 2~3 字滑窗。 精度不如 HanLP，但保证功能可用。 */
    private List<Term> fallback(String text) {
        List<Term> out = new ArrayList<>();
        for (String chunk : text.split("[^\\u4e00-\\u9fa5]+")) {
            if (chunk.length() < 2) {
                continue;
            }
            for (int i = 0; i + 2 <= chunk.length(); i++) {
                out.add(new Term(chunk.substring(i, i + 2), com.hankcs.hanlp.corpus.tag.Nature.n));
            }
        }
        return out;
    }

    public boolean isHanlpAvailable() {
        return hanlpAvailable;
    }
}
