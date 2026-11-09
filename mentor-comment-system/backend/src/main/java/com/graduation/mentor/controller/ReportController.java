package com.graduation.mentor.controller;

import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.StatDtos;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.ReportExportService;
import com.graduation.mentor.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统计分析与报表接口（仅教务）。 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AnalysisService analysisService;
    private final ReportExportService exportService;
    private final com.graduation.mentor.common.AuthContext authContext;

    /** Dashboard 聚合：一次拿全，避免多请求之间数字对不上 */
    @GetMapping("/dashboard")
    public R<StatDtos.Dashboard> dashboard(StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.dashboard(scope));
    }

    /**
     * 导师端个人画像（词云 + 情感分布 + 趋势）。
     *
     * <p>teacherId 由登录态决定，请求里传了也不生效——避免"改个数字看别人"。 非导师身份（学生 / 未绑定档案的账号）由 {@code currentTeacherId()}
     * 直接拒绝。
     */
    @GetMapping("/my-dashboard")
    public R<StatDtos.MyDashboard> myDashboard(StatDtos.Scope scope) {
        return R.ok(reportService.myDashboard(authContext.currentTeacherId(), scope));
    }

    @GetMapping("/sentiment")
    public R<StatDtos.SentimentDist> sentiment(StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.sentimentDist(scope));
    }

    @GetMapping("/trend")
    public R<StatDtos.Trend> trend(StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.trend(scope));
    }

    @GetMapping("/audit-stat")
    public R<StatDtos.AuditStat> auditStat() {
        reportService.requireAdmin();
        return R.ok(reportService.auditStat());
    }

    /**
     * KPI 卡下钻：点「待审核 8」列出这 8 条。
     *
     * <p>口径与 dashboard 的 kpi 同源（同一套条件构造），所以明细条数必然等于卡片上的数字。
     */
    @GetMapping("/kpi-detail")
    public R<StatDtos.KpiDetail> kpiDetail(
            @RequestParam String kpi,
            StatDtos.Scope scope,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        reportService.requireAdmin();
        return R.ok(reportService.kpiDetail(kpi, scope, page, size));
    }

    @GetMapping("/wordcloud")
    public R<StatDtos.WordCloud> wordCloud(StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.wordCloud(scope));
    }

    /** 词云词条下钻明细（受 n>=5 门槛） */
    @GetMapping("/word-detail")
    public R<StatDtos.WordDetail> wordDetail(@RequestParam String word, StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.wordDetail(word, scope));
    }

    /** 趋势点下钻：门禁按**桶级**样本量判定，不是整体样本量 */
    @GetMapping("/trend-point")
    public R<StatDtos.TrendPointDetail> trendPoint(
            @RequestParam String month, StatDtos.Scope scope) {
        reportService.requireAdmin();
        return R.ok(reportService.trendPointDetail(month, scope));
    }

    /** 单条点评的分析依据（可解释性展示：命中词 + 权重 + 是否被否定） */
    @GetMapping("/comment/{id}/sentiment")
    public R<Map<String, Object>> commentSentiment(@PathVariable Long id) {
        reportService.requireAdmin();
        return R.ok(analysisService.detail(id));
    }

    /** 全量重算（分析版本迁移 / 演示用） */
    @PostMapping("/rebuild")
    public R<Map<String, Object>> rebuild() {
        reportService.requireAdmin();
        return R.ok(analysisService.rebuildAll());
    }

    /**
     * 报表导出（xlsx，汇总 + 明细两个 sheet）。
     *
     * <p>直接写二进制而不是包一层 {@code R<byte[]>}：前端拿到的是文件， 走 JSON 再 base64 解码只会让下载链路多一次失败点。 错误仍由全局异常处理返回统一
     * JSON，前端按 content-type 区分成功与失败。
     */
    @GetMapping("/export")
    public void export(StatDtos.Scope scope, HttpServletResponse resp) throws IOException {
        byte[] bytes = exportService.export(scope);
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(exportService.suggestFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        resp.setContentLength(bytes.length);
        resp.getOutputStream().write(bytes);
        resp.getOutputStream().flush();
    }
}
