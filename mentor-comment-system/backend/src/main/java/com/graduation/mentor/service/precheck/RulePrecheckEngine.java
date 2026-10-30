package com.graduation.mentor.service.precheck;

import com.graduation.mentor.service.analysis.SentimentResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 点评预审规则引擎（L1，纯本地，不依赖任何外部服务）。
 *
 * <p><b>定位</b>：给教务一份"该看哪里"的清单，不是替教务做决定。输出只有风险分、问题清单和 意见草稿；状态迁移仍由 {@link
 * com.graduation.mentor.service.AuditService} 的人工动作完成。
 *
 * <p><b>为什么规则优先于模型</b>：审核场景要求结论可解释、可复现、可答辩。规则法的每一条命中 都能定位到原文片段和权重，模型不能；规则法在断网、无
 * Key、模型超时的情况下照常工作，模型不能。
 *
 * <p>规则与权重（论文"智能审核"一节可直接引用，改阈值或词表必须同时升 {@link #VERSION}）：
 *
 * <pre>
 * 编码              维度            权重  级别     触发条件
 * DEROGATORY        用语规范         60   BLOCKER  命中贬损词表
 * LENGTH_TOO_SHORT  内容完整性       45   BLOCKER  去标点后不足 25 字（不足 60 字记 12 分）
 * TEMPLATE_DUP      原创性           45   WARN     与同导师近 30 天点评字符二元组 Jaccard ≥ 0.85
 * VAGUE             内容具体性       28   WARN     命中 ≥2 处套话，或 1 处且篇幅 < 120 字
 * NEG_EXTREME       情绪倾向         22   WARN     情感净得分 ≤ -3.0
 * NO_ADVICE         指导性           16   INFO     未命中任何建议引导词
 * NO_SPECIFIC       内容具体性       15   INFO     既无数字，也无学业领域线索词
 * PRIVACY           隐私             12   INFO     命中手机号 / 身份证 / 邮箱 / 长数字串
 * LOW_CONFIDENCE    情绪倾向          6   INFO     情感分析落在阈值灰区
 * </pre>
 *
 * <p>风险分 = 权重求和后截断到 100；出现 BLOCKER 或 ≥55 判 RETURN，≥30 判 REVIEW，其余 PASS。 阈值定在 30 而不是更低，是为了让"篇幅偏短 +
 * 缺具体事实"（12+15=27）仍判 PASS： 学业点评常见篇幅就是 40~60 字，把这类正常文本一律标成"建议复核"，AI 第一次上线就会 被教务判定为噪声源——提示归提示，结论归结论。
 */
@Component
@RequiredArgsConstructor
public class RulePrecheckEngine {

    public static final String ENGINE = "RULE";

    /** 规则版本：改权重、阈值、词表都必须升版本，否则旧结论会被 content_hash 复用 */
    public static final String VERSION = "rule-v1.0";

    /** 风险分阈值 */
    private static final int SCORE_RETURN = 55;

    private static final int SCORE_REVIEW = 30;

    /** 篇幅门槛（去标点后字数） */
    private static final int LEN_BLOCKER = 25;

    private static final int LEN_WARN = 60;

    /** 相似度门槛：字符二元组 Jaccard */
    private static final double DUP_HIGH = 0.85;

    private static final double DUP_LOW = 0.70;

    /** 负面表达集中阈值（与 SentimentAnalyzer 的 ±1.5 分类阈值区分：这里判的是"值得看一眼"） */
    private static final double NEG_EXTREME_SCORE = -3.0;

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");

    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{8,12}");

    /** 标点与空白：算篇幅、算相似度前统一剔除 */
    private static final Pattern NOISE =
            Pattern.compile("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFF65]+");

    /** 问题编码 → 短语，用于拼装意见草稿 */
    private static final Map<String, String> SHORT_LABEL =
            Map.of(
                    "DEROGATORY", "存在贬损性表述",
                    "LENGTH_TOO_SHORT", "篇幅偏短",
                    "TEMPLATE_DUP", "与近期点评高度重复",
                    "VAGUE", "套话较多",
                    "NEG_EXTREME", "负面表达集中",
                    "NO_ADVICE", "未给出改进建议",
                    "NO_SPECIFIC", "缺少具体事实",
                    "PRIVACY", "含可识别个人信息",
                    "LOW_CONFIDENCE", "情感倾向不明");

    private final PrecheckLexicon lexicon;

    /** 预审输入：正文、主题、同导师近期正文（用于查重）、情感分析结果（可为 null） */
    public record Input(
            String content,
            String topic,
            List<String> recentSameTeacher,
            SentimentResult sentiment) {

        public Input {
            recentSameTeacher = recentSameTeacher == null ? List.of() : recentSameTeacher;
        }
    }

    /** 规则目录：供前端"预审规则说明"弹层与论文表格共用一份口径 */
    public record RuleInfo(String code, String label, int weight, String level) {}

    public static List<RuleInfo> rules() {
        return List.of(
                new RuleInfo("DEROGATORY", "贬损性表述", 60, PrecheckIssue.BLOCKER),
                new RuleInfo("LENGTH_TOO_SHORT", "篇幅过短", 45, PrecheckIssue.BLOCKER),
                new RuleInfo("TEMPLATE_DUP", "与近期点评高度重复", 45, PrecheckIssue.WARN),
                new RuleInfo("VAGUE", "通用套话", 28, PrecheckIssue.WARN),
                new RuleInfo("NEG_EXTREME", "负面表达集中", 22, PrecheckIssue.WARN),
                new RuleInfo("NO_ADVICE", "未给出改进建议", 16, PrecheckIssue.INFO),
                new RuleInfo("NO_SPECIFIC", "缺少具体事实", 15, PrecheckIssue.INFO),
                new RuleInfo("PRIVACY", "含可识别个人信息", 12, PrecheckIssue.INFO),
                new RuleInfo("LOW_CONFIDENCE", "情感倾向不明", 6, PrecheckIssue.INFO));
    }

    public PrecheckResult check(Input in) {
        String text = in.content() == null ? "" : in.content();
        int len = NOISE.matcher(text).replaceAll("").length();
        List<PrecheckIssue> issues = new ArrayList<>();

        checkLength(len, issues);
        checkVague(text, len, issues);
        checkSpecific(text, issues);
        checkPrivacy(text, issues);
        checkDerogatory(text, issues);
        checkDuplicate(text, in.recentSameTeacher(), issues);
        checkSentiment(in.sentiment(), issues);
        checkAdvice(text, issues);

        // 权重降序：前端直接按顺序展示，最重要的排最前
        issues.sort(Comparator.comparingInt(PrecheckIssue::getWeight).reversed());

        int score = Math.min(100, issues.stream().mapToInt(PrecheckIssue::getWeight).sum());
        boolean blocker = issues.stream().anyMatch(i -> PrecheckIssue.BLOCKER.equals(i.getLevel()));
        // 阻断级不等于驳回：篇幅过短应当退回让导师补充，只有用语违规才建议直接驳回。
        // 两者混为一谈会把"写得太短"的点评直接打成终态，导师连改的机会都没有。
        boolean rejectable = issues.stream().anyMatch(i -> "DEROGATORY".equals(i.getCode()));

        PrecheckResult r = new PrecheckResult();
        r.setRiskLevel(resolveLevel(score, blocker));
        r.setRiskScore(score);
        r.setSuggestAction(resolveAction(r.getRiskLevel(), rejectable));
        r.setSuggestOpinion(draftOpinion(r.getSuggestAction(), in.topic(), issues));
        r.setIssues(issues);
        return r;
    }

    // ------------------------------------------------------------------ 各维度规则

    private void checkLength(int len, List<PrecheckIssue> issues) {
        if (len < LEN_BLOCKER) {
            issues.add(
                    issue(
                            "LENGTH_TOO_SHORT",
                            PrecheckIssue.BLOCKER,
                            45,
                            "正文去掉标点仅 " + len + " 字，过于简略，无法支撑学业指导结论"));
        } else if (len < LEN_WARN) {
            issues.add(
                    issue(
                            "LENGTH_TOO_SHORT",
                            PrecheckIssue.WARN,
                            12,
                            "正文 " + len + " 字，信息量偏少，建议补充该生的具体表现"));
        }
    }

    private void checkVague(String text, int len, List<PrecheckIssue> issues) {
        List<PrecheckLexicon.Match> hits = lexicon.matchVague(text);
        if (hits.isEmpty()) {
            return;
        }
        // 单处套话在短点评里才构成问题；长点评夹一句"继续努力"属正常表达，不告警
        boolean thin = hits.size() >= 2 || len < 120;
        if (!thin) {
            return;
        }
        PrecheckLexicon.Match first = hits.get(0);
        issues.add(
                issueAt(
                        "VAGUE",
                        PrecheckIssue.WARN,
                        hits.size() >= 2 ? 28 : 20,
                        "命中 " + hits.size() + " 处通用套话（如「" + first.word() + "」），" + "缺少针对该生的具体描述",
                        first));
    }

    private void checkSpecific(String text, List<PrecheckIssue> issues) {
        boolean hasNumber = text.chars().anyMatch(Character::isDigit);
        boolean hasDomain = !lexicon.matchDomain(text).isEmpty();
        if (!hasNumber && !hasDomain) {
            issues.add(issue("NO_SPECIFIC", PrecheckIssue.INFO, 15, "未出现课程、成绩、作业等可核对的线索，内容偏笼统"));
        }
    }

    /**
     * 隐私检查。
     *
     * <p>命中片段**不写入**预审结果：预审表是长期留存的分析产物，把手机号、学号抄进去等于 在"隐私过滤"之外新开一条泄漏路径。这里只报告类型。
     */
    private void checkPrivacy(String text, List<PrecheckIssue> issues) {
        String label = firstPrivacyLabel(text);
        if (label == null) {
            return;
        }
        issues.add(issue("PRIVACY", PrecheckIssue.INFO, 12, "正文含疑似" + label + "，建议脱敏后再向学生展示"));
    }

    private String firstPrivacyLabel(String text) {
        if (ID_CARD.matcher(text).find()) {
            return "身份证号";
        }
        if (PHONE.matcher(text).find()) {
            return "手机号";
        }
        if (EMAIL.matcher(text).find()) {
            return "邮箱地址";
        }
        if (DIGIT_RUN.matcher(text).find()) {
            return "学号或工号";
        }
        return null;
    }

    private void checkDerogatory(String text, List<PrecheckIssue> issues) {
        List<PrecheckLexicon.Match> hits = lexicon.matchDerogatory(text);
        if (hits.isEmpty()) {
            return;
        }
        PrecheckLexicon.Match first = hits.get(0);
        issues.add(
                issueAt(
                        "DEROGATORY",
                        PrecheckIssue.BLOCKER,
                        60,
                        "存在贬损性表述「" + first.word() + "」，不符合学业指导用语规范",
                        first));
    }

    private void checkDuplicate(String text, List<String> others, List<PrecheckIssue> issues) {
        double max = 0;
        for (String other : others) {
            max = Math.max(max, similarity(text, other));
        }
        if (max >= DUP_HIGH) {
            issues.add(
                    issue(
                            "TEMPLATE_DUP",
                            PrecheckIssue.WARN,
                            45,
                            "与同导师近期另一条点评相似度 " + percent(max) + "%，疑似套用同一模板"));
        } else if (max >= DUP_LOW) {
            issues.add(
                    issue(
                            "TEMPLATE_DUP",
                            PrecheckIssue.INFO,
                            18,
                            "与同导师近期点评相似度 " + percent(max) + "%，可留意是否针对个人"));
        }
    }

    private void checkSentiment(SentimentResult sentiment, List<PrecheckIssue> issues) {
        if (sentiment == null) {
            return;
        }
        if (sentiment.getScore() <= NEG_EXTREME_SCORE) {
            issues.add(
                    issue(
                            "NEG_EXTREME",
                            PrecheckIssue.WARN,
                            22,
                            "整体负面表达集中（净得分 "
                                    + String.format("%.1f", sentiment.getScore())
                                    + "），建议确认是否存在不当评价"));
        }
        if (sentiment.isLowConfidence()) {
            issues.add(issue("LOW_CONFIDENCE", PrecheckIssue.INFO, 6, "情感倾向落在阈值灰区，仅作参考，不构成判断依据"));
        }
    }

    private void checkAdvice(String text, List<PrecheckIssue> issues) {
        if (lexicon.matchAdvice(text).isEmpty()) {
            issues.add(issue("NO_ADVICE", PrecheckIssue.INFO, 16, "未给出改进建议或后续安排，反馈的指导性不足"));
        }
    }

    // ------------------------------------------------------------------ 结论映射

    private String resolveLevel(int score, boolean blocker) {
        if (blocker || score >= SCORE_RETURN) {
            return PrecheckResultLevel.RETURN;
        }
        return score >= SCORE_REVIEW ? PrecheckResultLevel.REVIEW : PrecheckResultLevel.PASS;
    }

    private String resolveAction(String level, boolean rejectable) {
        if (rejectable) {
            return SuggestAction.REJECT;
        }
        return switch (level) {
            case PrecheckResultLevel.RETURN -> SuggestAction.RETURN;
            case PrecheckResultLevel.REVIEW -> SuggestAction.APPROVE_ONLY;
            default -> SuggestAction.APPROVE_PUBLISH;
        };
    }

    /**
     * 意见草稿。
     *
     * <p>刻意写成"可读但需要改写"的句子，而不是一段完美文案：草稿越像成品，越容易被原样提交， comment_audit
     * 里的意见就会退化成同一个模板——那正是"审核意见必填"想避免的结果。 每档草稿长度都高于对应动作的字数门槛，避免教务点开就被前端拦下。
     */
    private String draftOpinion(String action, String topic, List<PrecheckIssue> issues) {
        String subject = topic == null || topic.isBlank() ? "本次指导" : "「" + topic + "」";
        String problems = joinProblems(issues);
        return switch (action) {
            case SuggestAction.APPROVE_PUBLISH ->
                    "内容完整，围绕" + subject + "给出了具体反馈与改进方向，表述规范，同意通过并发布。";
            case SuggestAction.APPROVE_ONLY -> "内容基本可用，但" + problems + "，建议先通过、暂不发布，待导师补充说明后再推送学生。";
            case SuggestAction.RETURN -> "点评" + problems + "，请补充针对该生的具体事实与改进建议后重新提交。";
            case SuggestAction.REJECT -> "文本存在不当或贬损性表述，不符合学业指导点评的规范要求，经复核不予通过，请修改后重新提交。";
            default -> "请结合预审问题清单人工复核后再决定处理方式。";
        };
    }

    /** 取权重最高的 3 条问题拼成短语，LOW_CONFIDENCE 属噪声不参与 */
    private String joinProblems(List<PrecheckIssue> issues) {
        List<String> parts = new ArrayList<>();
        for (PrecheckIssue i : issues) {
            if ("LOW_CONFIDENCE".equals(i.getCode())) {
                continue;
            }
            String label = SHORT_LABEL.get(i.getCode());
            if (label != null) {
                parts.add(label);
            }
            if (parts.size() == 3) {
                break;
            }
        }
        return parts.isEmpty() ? "内容仍需核实" : String.join("、", parts);
    }

    // ------------------------------------------------------------------ 文本相似度

    /** 字符二元组 Jaccard 相似度。中文无需分词即可稳定识别"套模板"，且对增删一两句不敏感。 */
    public static double similarity(String a, String b) {
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        return (double) inter.size() / union.size();
    }

    private static Set<String> bigrams(String s) {
        String t = NOISE.matcher(s == null ? "" : s).replaceAll("");
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            set.add(t.substring(i, i + 2));
        }
        return set;
    }

    private static String percent(double v) {
        return String.format("%.0f", v * 100);
    }

    private static PrecheckIssue issue(String code, String level, int weight, String message) {
        return new PrecheckIssue(code, level, weight, message, -1, -1, null);
    }

    private static PrecheckIssue issueAt(
            String code, String level, int weight, String message, PrecheckLexicon.Match m) {
        return new PrecheckIssue(code, level, weight, message, m.start(), m.end(), m.word());
    }
}
