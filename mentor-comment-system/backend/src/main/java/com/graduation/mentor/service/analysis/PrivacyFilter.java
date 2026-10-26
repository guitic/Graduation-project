package com.graduation.mentor.service.analysis;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 隐私过滤：进入分词与统计之前先脱敏。
 * <p>论文口径：姓名、学号、手机号等直接身份信息不进入分析流程，
 * 也不传入任何外部模型；词云与报表只展示脱敏后的聚合结果。
 */
public final class PrivacyFilter {

    /** 11 位手机号 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 学号/工号：连续 8~12 位数字 */
    private static final Pattern NO = Pattern.compile("\\d{8,12}");
    /** 身份证 */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}");

    private PrivacyFilter() {
    }

    /**
     * 文本归一化：全角转半角、连续空白折叠、去首尾空白。
     * <p>必须在脱敏与分词之前执行，且**全系统共用这一个实现**——
     * 否则"多一个空格"会被 content_hash 判成文本变更，触发无意义重算；
     * 更糟的是分词结果与 hash 基准不一致，导致同一文本两次分析结果不同。
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 ASCII → 半角
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * @param text  原文
     * @param names 需要屏蔽的人名集合（来自学生/导师档案），可为空
     */
    public static String mask(String text, Set<String> names) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = normalize(text);
        if (names != null) {
            for (String n : names) {
                if (n != null && n.length() >= 2) {
                    s = s.replace(n, "*");
                }
            }
        }
        s = EMAIL.matcher(s).replaceAll("*");
        s = ID_CARD.matcher(s).replaceAll("*");
        s = PHONE.matcher(s).replaceAll("*");
        s = NO.matcher(s).replaceAll("*");
        return s;
    }
}
