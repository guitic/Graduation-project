package com.graduation.mentor.service;

import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.StatDtos;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentSentiment;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 报表导出（xlsx）。
 *
 * <p><b>为什么单独一个 Service</b>：{@link ReportService} 负责"算数"，本类负责"排版"。 把 POI 塞进 ReportService
 * 会让那个类同时承担统计与格式两种变更原因， 而且 POI 的异常处理会把统计逻辑淹掉。
 *
 * <p><b>口径约束</b>：明细必须来自 {@link ReportService#publishedList}，即 {@code
 * CommentScope.published()}。导出是"把页面上的数字落成文件"，不是第二条数据通道； 一旦这里自己写 {@code where
 * status='APPROVED'}，撤回过的点评会重新出现在 Excel 里，而页面上已经看不到了——这种不一致最难被发现。
 *
 * <p><b>门槛约束</b>：聚合占比沿用 {@code k=5}（样本不足时不计算并注明）， 但明细行不受该门槛限制——明细的访问控制是角色权限（仅教务），
 * 与"防止从小样本反推个人信息"是两件不同的事。
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    /**
     * 单次导出行数上限。
     *
     * <p>5000 条已远超本项目真实规模（种子 68 条）。设上限不是为了性能， 而是为了避免"导出全部学期"这种请求把教务的浏览器拖死，同时提示用户收窄范围。
     */
    private static final int MAX_ROWS = 5000;

    private static final DateTimeFormatter READABLE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SENTIMENT_POS = "正向";
    private static final String SENTIMENT_NEU = "中性";
    private static final String SENTIMENT_NEG = "负向";

    private final ReportService reportService;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final TeacherMapper teacherMapper;
    private final CommentSentimentMapper sentimentMapper;

    /** 生成 xlsx 字节流。调用方负责写响应头与文件名。 */
    public byte[] export(StatDtos.Scope scope) {
        reportService.requireAdmin();

        List<Comment> list = reportService.publishedList(scope);
        if (list.isEmpty()) {
            throw new BizException(400, "当前筛选条件下没有已发布的点评，无法导出");
        }
        if (list.size() > MAX_ROWS) {
            throw new BizException(
                    400, "本次筛选命中 " + list.size() + " 条，超过单次导出上限 " + MAX_ROWS + " 条，请缩小时间或班级范围后重试");
        }

        // 批量预加载：导出 N 条不能打 4N 次数据库（参见审查报告 P1-1 的 N+1 教训）
        Map<Long, Student> studentMap = loadStudents(list);
        Map<Long, Clazz> clazzMap = loadClazzes(studentMap.values());
        Map<Long, Teacher> teacherMap = loadTeachers(list);
        Map<Long, String> sentimentMap = loadSentimentLabels(list);

        StatDtos.Kpi kpi = reportService.kpi(scope);
        StatDtos.SentimentDist dist = reportService.sentimentDist(scope);

        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            buildSummarySheet(wb, styles, scope, kpi, dist, list.size());
            buildDetailSheet(wb, styles, list, studentMap, clazzMap, teacherMap, sentimentMap);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(500, "生成报表失败：" + e.getMessage());
        }
    }

    /** 建议的文件名（不含路径），带时间戳避免多次导出互相覆盖 */
    public String suggestFileName() {
        return "学业导师点评分析报表_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".xlsx";
    }

    // ------------------------------------------------------------------ 汇总表

    private void buildSummarySheet(
            XSSFWorkbook wb,
            Styles st,
            StatDtos.Scope scope,
            StatDtos.Kpi kpi,
            StatDtos.SentimentDist dist,
            int detailRows) {
        Sheet sheet = wb.createSheet("汇总");
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 40 * 256);

        int r = 0;
        Row title = sheet.createRow(r++);
        title.setHeightInPoints(24);
        Cell tc = title.createCell(0);
        tc.setCellValue("学业导师点评分析报表");
        tc.setCellStyle(st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        Row meta = sheet.createRow(r++);
        meta.createCell(0).setCellValue("导出时间");
        meta.createCell(1).setCellValue(LocalDateTime.now().format(READABLE));
        meta.getCell(0).setCellStyle(st.label);

        Row scopeRow = sheet.createRow(r++);
        scopeRow.createCell(0).setCellValue("筛选条件");
        scopeRow.createCell(1).setCellValue(describeScope(scope));
        scopeRow.getCell(0).setCellStyle(st.label);

        r++; // 空行

        r = sectionHeader(sheet, st, r, "一、流程指标");
        r = kvRow(sheet, st, r, "点评总数", String.valueOf(kpi.getTotal()));
        r = kvRow(sheet, st, r, "已发布（纳入统计）", String.valueOf(kpi.getPublished()));
        r = kvRow(sheet, st, r, "待审核", String.valueOf(kpi.getPending()));
        r = kvRow(sheet, st, r, "已通过待发布", String.valueOf(kpi.getUnpublished()));
        r = kvRow(sheet, st, r, "退回修改", String.valueOf(kpi.getReturned()));
        r = kvRow(sheet, st, r, "已驳回", String.valueOf(kpi.getRejected()));
        r = kvRow(sheet, st, r, "草稿", String.valueOf(kpi.getDraft()));
        r = kvRow(sheet, st, r, "低置信度（待人工复核）", String.valueOf(kpi.getLowConfidence()));
        r =
                kvRow(
                        sheet,
                        st,
                        r,
                        "平均审核时长（小时）",
                        kpi.getAvgAuditHours() == null
                                ? "—"
                                : String.valueOf(kpi.getAvgAuditHours()));

        r++; // 空行

        r = sectionHeader(sheet, st, r, "二、情感分布（已发布点评）");
        Row head = sheet.createRow(r++);
        head.createCell(0).setCellValue("情感类别");
        head.createCell(1).setCellValue("数量");
        head.createCell(2).setCellValue("占比");
        for (int i = 0; i < 3; i++) {
            head.getCell(i).setCellStyle(st.header);
        }

        if (!dist.isEnough()) {
            Row note = sheet.createRow(r++);
            Cell nc = note.createCell(0);
            nc.setCellValue(
                    "样本不足（本次 "
                            + dist.getTotal()
                            + " 条 < 门槛 "
                            + dist.getMinSample()
                            + " 条），不计算情感占比");
            nc.setCellStyle(st.warn);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 2));
        }

        r =
                distRow(
                        sheet,
                        st,
                        r,
                        SENTIMENT_POS,
                        dist.getPositive(),
                        dist.getPositiveRate(),
                        dist.isEnough());
        r =
                distRow(
                        sheet,
                        st,
                        r,
                        SENTIMENT_NEU,
                        dist.getNeutral(),
                        dist.getNeutralRate(),
                        dist.isEnough());
        r =
                distRow(
                        sheet,
                        st,
                        r,
                        SENTIMENT_NEG,
                        dist.getNegative(),
                        dist.getNegativeRate(),
                        dist.isEnough());
        kvRow(sheet, st, r, "合计", String.valueOf(dist.getTotal()));

        r += 2;
        r = sectionHeader(sheet, st, r, "三、统计口径说明");
        r = noteRow(sheet, st, r, "数据范围", "仅统计审核通过且已发布给学生的点评（status=APPROVED 且 published_at 非空）。");
        r = noteRow(sheet, st, r, "情感分析方法", "情感词典法（正向/负向/否定/程度词），按得分阈值映射为正向、中性、负向，结果可逐条复核。");
        r =
                noteRow(
                        sheet,
                        st,
                        r,
                        "隐私门槛",
                        "聚合占比需满足最小样本量 k=" + dist.getMinSample() + "；明细行按角色权限控制（仅教务可导出），不适用该门槛。");
        noteRow(sheet, st, r, "使用边界", "情感倾向反映总体趋势，不等同于对导师工作质量或学生表现的评价，单条结果需保留人工复核空间。");

        sheet.createFreezePane(0, 4);
    }

    private int sectionHeader(Sheet sheet, Styles st, int r, String text) {
        Row row = sheet.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(st.section);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 2));
        return r + 1;
    }

    private int kvRow(Sheet sheet, Styles st, int r, String label, String value) {
        Row row = sheet.createRow(r);
        Cell l = row.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(st.label);
        Cell v = row.createCell(1);
        v.setCellValue(value);
        v.setCellStyle(st.value);
        return r + 1;
    }

    private int noteRow(Sheet sheet, Styles st, int r, String label, String text) {
        Row row = sheet.createRow(r);
        Cell l = row.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(st.label);
        Cell t = row.createCell(1);
        t.setCellValue(text);
        t.setCellStyle(st.wrap);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 1, 2));
        return r + 1;
    }

    private int distRow(
            Sheet sheet, Styles st, int r, String label, long count, Double rate, boolean enough) {
        Row row = sheet.createRow(r);
        Cell l = row.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(st.label);
        Cell c = row.createCell(1);
        c.setCellValue(count);
        c.setCellStyle(st.value);
        Cell rt = row.createCell(2);
        // 样本不足时写「—」而不是 0.0%：0 会被读成"没有负向点评"，与"不计算"是两件事
        rt.setCellValue(enough && rate != null ? pct(rate) : "—");
        rt.setCellStyle(st.value);
        return r + 1;
    }

    // ------------------------------------------------------------------ 明细表

    private void buildDetailSheet(
            XSSFWorkbook wb,
            Styles st,
            List<Comment> list,
            Map<Long, Student> studentMap,
            Map<Long, Clazz> clazzMap,
            Map<Long, Teacher> teacherMap,
            Map<Long, String> sentimentMap) {
        Sheet sheet = wb.createSheet("点评明细");
        String[] heads = {"序号", "学号", "学生", "班级", "导师", "学期", "指导主题", "情感倾向", "发布时间", "点评内容"};
        int[] widths = {6, 14, 10, 14, 10, 14, 24, 10, 20, 70};

        Row head = sheet.createRow(0);
        head.setHeightInPoints(22);
        for (int i = 0; i < heads.length; i++) {
            Cell c = head.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(st.header);
            sheet.setColumnWidth(i, widths[i] * 256);
        }

        int r = 1;
        for (Comment cm : list) {
            Student stu = studentMap.get(cm.getStudentId());
            Clazz clazz = stu == null ? null : clazzMap.get(stu.getClazzId());
            Teacher tea = teacherMap.get(cm.getTeacherId());

            Row row = sheet.createRow(r);
            row.setHeightInPoints(34);
            int c = 0;
            setText(row, c++, st.cellCenter, String.valueOf(r));
            setText(row, c++, st.cell, stu == null ? "—" : stu.getStudentNo());
            setText(row, c++, st.cell, stu == null ? "—" : stu.getName());
            setText(row, c++, st.cell, clazz == null ? "—" : clazz.getName());
            setText(row, c++, st.cell, tea == null ? "—" : tea.getName());
            setText(row, c++, st.cell, cm.getTerm());
            setText(row, c++, st.cell, cm.getTopic());
            setText(row, c++, st.cellCenter, sentimentLabel(sentimentMap.get(cm.getId())));
            setText(
                    row,
                    c++,
                    st.cell,
                    cm.getPublishedAt() == null ? "—" : cm.getPublishedAt().format(READABLE));
            setText(row, c, st.cellWrap, cm.getContent());
            r++;
        }

        // 冻结表头 + 首列自动筛选：几百条明细靠肉眼找某位学生是不现实的
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, r - 1, 0, heads.length - 1));
    }

    private void setText(Row row, int col, CellStyle style, String value) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    // ------------------------------------------------------------------ 批量加载

    private Map<Long, Student> loadStudents(List<Comment> list) {
        List<Long> ids =
                list.stream()
                        .map(Comment::getStudentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Clazz> loadClazzes(Collection<Student> students) {
        List<Long> ids =
                students.stream()
                        .map(Student::getClazzId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clazzMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Teacher> loadTeachers(List<Comment> list) {
        List<Long> ids =
                list.stream()
                        .map(Comment::getTeacherId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return teacherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> loadSentimentLabels(List<Comment> list) {
        List<Long> ids = list.stream().map(Comment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<CommentSentiment> rows =
                sentimentMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                        CommentSentiment>()
                                .in(CommentSentiment::getCommentId, ids));
        return rows.stream()
                .collect(
                        Collectors.toMap(
                                CommentSentiment::getCommentId,
                                CommentSentiment::getLabel,
                                (a, b) -> a));
    }

    private static String sentimentLabel(String label) {
        if (label == null) {
            return "未分析";
        }
        return switch (label) {
            case "POSITIVE" -> SENTIMENT_POS;
            case "NEGATIVE" -> SENTIMENT_NEG;
            case "NEUTRAL" -> SENTIMENT_NEU;
            default -> label;
        };
    }

    private static String pct(Double rate) {
        return String.format("%.1f%%", rate * 100);
    }

    /** 把筛选条件翻译成人话，避免导出文件脱离页面后不知道这份数据是怎么筛出来的 */
    private String describeScope(StatDtos.Scope s) {
        if (s == null) {
            return "全部已发布点评";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s.getTerm() == null || s.getTerm().isBlank() ? "全部学期" : "学期 " + s.getTerm());
        if (s.getStartDate() != null && !s.getStartDate().isBlank()) {
            sb.append("，自 ").append(s.getStartDate());
        }
        if (s.getEndDate() != null && !s.getEndDate().isBlank()) {
            sb.append(" 至 ").append(s.getEndDate());
        }
        if (s.getTeacherId() != null) {
            sb.append("，导师ID ").append(s.getTeacherId());
        }
        if (s.getClazzId() != null) {
            sb.append("，班级ID ").append(s.getClazzId());
        }
        return sb.toString();
    }

    /** 样式集中创建：每个 Workbook 只建一次，避免每格 createCellStyle 触达 xlsx 样式表上限 */
    private static final class Styles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle label;
        private final CellStyle value;
        private final CellStyle wrap;
        private final CellStyle warn;
        private final CellStyle cell;
        private final CellStyle cellWrap;
        private final CellStyle cellCenter;

        private Styles(XSSFWorkbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font bold = wb.createFont();
            bold.setBold(true);

            section = wb.createCellStyle();
            section.setFont(bold);
            section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            border(header);

            label = wb.createCellStyle();
            label.setFont(bold);

            value = wb.createCellStyle();
            value.setAlignment(HorizontalAlignment.LEFT);

            wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            warn = wb.createCellStyle();
            Font warnFont = wb.createFont();
            warnFont.setColor(IndexedColors.DARK_RED.getIndex());
            warn.setFont(warnFont);

            cell = wb.createCellStyle();
            cell.setVerticalAlignment(VerticalAlignment.CENTER);
            border(cell);

            cellWrap = wb.createCellStyle();
            cellWrap.setWrapText(true);
            cellWrap.setVerticalAlignment(VerticalAlignment.TOP);
            border(cellWrap);

            cellCenter = wb.createCellStyle();
            cellCenter.setAlignment(HorizontalAlignment.CENTER);
            cellCenter.setVerticalAlignment(VerticalAlignment.CENTER);
            border(cellCenter);
        }

        private static void border(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
