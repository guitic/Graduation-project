package com.graduation.mentor.analysis;

import com.graduation.mentor.service.analysis.SentimentAnalyzer;
import com.graduation.mentor.service.analysis.SentimentLexicon;
import com.graduation.mentor.service.analysis.SentimentResult;
import com.graduation.mentor.service.analysis.WordSegmenter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 情感分析评估：在人工标注验证集上运行真实分析器，输出 准确率 / 各类 Precision·Recall·F1 / 混淆矩阵 / Cohen's Kappa / 错误分析。
 *
 * <p>不依赖 Spring 上下文，直接构造分析组件，保证评估对象就是线上同一个算法实现。 结果写入 target/sentiment-eval/report.md 与
 * predictions.tsv。
 */
public class SentimentEvalTest {

    private static final String[] LABELS = {"POSITIVE", "NEUTRAL", "NEGATIVE"};

    @Test
    void evaluate() throws Exception {
        SentimentLexicon lexicon = new SentimentLexicon();
        lexicon.load();
        WordSegmenter segmenter = new WordSegmenter();
        segmenter.warmUp();
        SentimentAnalyzer analyzer = new SentimentAnalyzer(lexicon, segmenter);

        List<String[]> data = load();
        int n = data.size();
        int[][] cm = new int[3][3]; // 行=真实, 列=预测
        List<String> errors = new ArrayList<>();
        StringBuilder pred = new StringBuilder("label\tpred\tscore\tconfidence\tlowConf\ttext\n");

        for (String[] row : data) {
            String truth = row[0];
            String text = row[1];
            SentimentResult r = analyzer.analyze(text);
            String p = r.getLabel();
            cm[idx(truth)][idx(p)]++;
            pred.append(truth)
                    .append('\t')
                    .append(p)
                    .append('\t')
                    .append(r.getScore())
                    .append('\t')
                    .append(r.getConfidence())
                    .append('\t')
                    .append(r.isLowConfidence())
                    .append('\t')
                    .append(text)
                    .append('\n');
            if (!truth.equals(p)) {
                errors.add(String.format("[%s→%s] score=%.2f | %s", truth, p, r.getScore(), text));
            }
        }

        int correct = 0;
        for (int i = 0; i < 3; i++) correct += cm[i][i];
        double acc = (double) correct / n;

        double[] precision = new double[3];
        double[] recall = new double[3];
        double[] f1 = new double[3];
        int[] support = new int[3];
        int[] predCount = new int[3];
        for (int i = 0; i < 3; i++) {
            int tp = cm[i][i];
            int fn = 0, fp = 0;
            for (int j = 0; j < 3; j++) {
                if (j != i) {
                    fn += cm[i][j];
                    fp += cm[j][i];
                }
            }
            support[i] = tp + fn;
            predCount[i] = tp + fp;
            precision[i] = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
            recall[i] = (tp + fn) == 0 ? 0 : (double) tp / (tp + fn);
            f1[i] =
                    (precision[i] + recall[i]) == 0
                            ? 0
                            : 2 * precision[i] * recall[i] / (precision[i] + recall[i]);
        }
        double macroP = 0, macroR = 0, macroF = 0;
        double wF = 0;
        for (int i = 0; i < 3; i++) {
            macroP += precision[i];
            macroR += recall[i];
            macroF += f1[i];
            wF += f1[i] * support[i];
        }
        macroP /= 3;
        macroR /= 3;
        macroF /= 3;
        wF /= n;

        // Cohen's Kappa
        double pe = 0;
        for (int i = 0; i < 3; i++) {
            pe += ((double) support[i] / n) * ((double) predCount[i] / n);
        }
        double kappa = (pe >= 1.0) ? 1.0 : (acc - pe) / (1 - pe);

        StringBuilder md = new StringBuilder();
        md.append("# 情感分析评估报告\n\n");
        md.append("**方法**：")
                .append(SentimentAnalyzer.METHOD)
                .append(' ')
                .append(SentimentAnalyzer.METHOD_VERSION)
                .append("\n\n");
        md.append("**验证集**：人工标注 ")
                .append(n)
                .append(" 条（正向 ")
                .append(support[0])
                .append(" / 中性 ")
                .append(support[1])
                .append(" / 负向 ")
                .append(support[2])
                .append("）\n\n");
        md.append("## 总体指标\n\n");
        md.append("| 指标 | 数值 |\n|---|---|\n");
        md.append(String.format("| 准确率 Accuracy | %.4f (%.1f%%) |%n", acc, acc * 100));
        md.append(String.format("| 宏平均 Precision | %.4f |%n", macroP));
        md.append(String.format("| 宏平均 Recall | %.4f |%n", macroR));
        md.append(String.format("| 宏平均 F1 | %.4f |%n", macroF));
        md.append(String.format("| 加权 F1 | %.4f |%n", wF));
        md.append(String.format("| Cohen's Kappa | %.4f |%n", kappa));
        md.append("\n## 分类别指标\n\n");
        md.append("| 类别 | Precision | Recall | F1 | Support |\n|---|---|---|---|---|\n");
        for (int i = 0; i < 3; i++) {
            md.append(
                    String.format(
                            "| %s | %.4f | %.4f | %.4f | %d |%n",
                            LABELS[i], precision[i], recall[i], f1[i], support[i]));
        }
        md.append("\n## 混淆矩阵（行=真实，列=预测）\n\n");
        md.append("| 真实\\预测 | POSITIVE | NEUTRAL | NEGATIVE |\n|---|---|---|---|\n");
        for (int i = 0; i < 3; i++) {
            md.append(
                    String.format(
                            "| %s | %d | %d | %d |%n", LABELS[i], cm[i][0], cm[i][1], cm[i][2]));
        }
        md.append("\n## 错误分析（").append(errors.size()).append(" 条）\n\n");
        if (errors.isEmpty()) {
            md.append("无\n");
        } else {
            for (String e : errors) md.append("- ").append(e).append('\n');
        }

        Path outDir = Paths.get("target", "sentiment-eval");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                outDir.resolve("predictions.tsv"), pred.toString(), StandardCharsets.UTF_8);

        System.out.println(
                "EVAL_DONE accuracy=" + acc + " kappa=" + kappa + " errors=" + errors.size());
    }

    private static int idx(String label) {
        for (int i = 0; i < 3; i++) {
            if (LABELS[i].equals(label)) return i;
        }
        throw new IllegalArgumentException("unknown label: " + label);
    }

    private static List<String[]> load() throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = SentimentEvalTest.class.getResourceAsStream("/sentiment_eval.tsv")) {
            if (in == null)
                throw new IllegalStateException("sentiment_eval.tsv not found on classpath");
            BufferedReader br =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int t = line.indexOf('\t');
                if (t < 0) continue;
                out.add(new String[] {line.substring(0, t).trim(), line.substring(t + 1).trim()});
            }
        }
        return out;
    }
}
