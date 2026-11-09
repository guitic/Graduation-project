package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.graduation.mentor.service.ReportExportService;
import com.graduation.mentor.service.ReportService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 报表导出。
 *
 * <p><b>为什么这个类存在</b>：导出最容易出的错不是格式难看，而是**口径不一致**—— 页面上说 56 条，Excel 里却有 60 条，多出来的是待审核和已撤回的点评。 这种错误在
 * Excel 里完全看不出来，只有把"明细行数 == 已发布列表条数"写成断言才守得住。
 *
 * <p>同时守住边界：空结果给出可读提示而不是生成一个只有表头的空文件； 超量导出被拒绝而不是把浏览器拖死。
 */
class ReportExportTest {

    private ReportService reportService;
    private StudentMapper studentMapper;
    private ClazzMapper clazzMapper;
    private TeacherMapper teacherMapper;
    private CommentSentimentMapper sentimentMapper;
    private ReportExportService service;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        studentMapper = mock(StudentMapper.class);
        clazzMapper = mock(ClazzMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        sentimentMapper = mock(CommentSentimentMapper.class);
        service =
                new ReportExportService(
                        reportService, studentMapper, clazzMapper, teacherMapper, sentimentMapper);
    }

    private Comment comment(long id, long studentId) {
        Comment c = new Comment();
        c.setId(id);
        c.setTeacherId(10L);
        c.setStudentId(studentId);
        c.setTerm("2025-2026-1");
        c.setTopic("学业规划");
        c.setContent("本学期学习态度端正，建议加强算法练习。");
        c.setStatus("APPROVED");
        c.setPublishedAt(LocalDateTime.of(2026, 4, 12, 9, 30));
        return c;
    }

    private void stubLookups() {
        Student s1 = new Student();
        s1.setId(100L);
        s1.setStudentNo("20250373006");
        s1.setName("王小明");
        s1.setClazzId(5L);
        Clazz clazz = new Clazz();
        clazz.setId(5L);
        clazz.setName("计科2201");
        Teacher teacher = new Teacher();
        teacher.setId(10L);
        teacher.setName("张明远");
        CommentSentiment senti = new CommentSentiment();
        senti.setCommentId(1L);
        senti.setLabel("POSITIVE");

        doReturn(List.of(s1)).when(studentMapper).selectBatchIds(any());
        doReturn(List.of(clazz)).when(clazzMapper).selectBatchIds(any());
        doReturn(List.of(teacher)).when(teacherMapper).selectBatchIds(any());
        doReturn(List.of(senti)).when(sentimentMapper).selectList(any());

        StatDtos.Kpi kpi = new StatDtos.Kpi();
        kpi.setTotal(1);
        kpi.setPublished(1);
        when(reportService.kpi(any())).thenReturn(kpi);

        StatDtos.SentimentDist dist = new StatDtos.SentimentDist();
        dist.setTotal(1);
        dist.setPositive(1);
        dist.setPositiveRate(1.0);
        dist.setEnough(true);
        dist.setMinSample(5);
        when(reportService.sentimentDist(any())).thenReturn(dist);
    }

    @Test
    @DisplayName("明细行数严格等于已发布列表条数（口径一致，不多不少）")
    void detailRowsMatchPublishedScope() throws Exception {
        stubLookups();
        when(reportService.publishedList(any()))
                .thenReturn(List.of(comment(1L, 100L), comment(2L, 100L)));

        byte[] bytes = service.export(new StatDtos.Scope());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("汇总", wb.getSheetName(0));
            assertEquals("点评明细", wb.getSheetName(1));

            Sheet detail = wb.getSheetAt(1);
            // 表头 1 行 + 数据 2 行；若导出绕过了 published()，这里会变成 3 行以上
            assertEquals(3, detail.getLastRowNum() + 1);

            Row first = detail.getRow(1);
            assertEquals("20250373006", first.getCell(1).getStringCellValue());
            assertEquals("王小明", first.getCell(2).getStringCellValue());
            assertEquals("计科2201", first.getCell(3).getStringCellValue());
            assertEquals("张明远", first.getCell(4).getStringCellValue());
            // 情感标签必须是中文，不能把 POSITIVE 原样丢进 Excel
            assertEquals("正向", first.getCell(7).getStringCellValue());
        }
    }

    @Test
    @DisplayName("无数据 → 400 可读提示，不生成只有表头的空文件")
    void emptyScopeRejected() {
        when(reportService.publishedList(any())).thenReturn(List.of());

        BizException e =
                assertThrows(BizException.class, () -> service.export(new StatDtos.Scope()));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("没有已发布的点评"));
    }

    @Test
    @DisplayName("超过单次上限 → 400，提示收窄范围")
    void tooManyRowsRejected() {
        Comment one = comment(1L, 100L);
        when(reportService.publishedList(any())).thenReturn(Collections.nCopies(5001, one));

        BizException e =
                assertThrows(BizException.class, () -> service.export(new StatDtos.Scope()));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("超过单次导出上限"));
    }

    @Test
    @DisplayName("样本不足时占比写「—」，不写 0.0%（两者含义不同）")
    void insufficientSampleWritesDash() throws Exception {
        stubLookups();
        when(reportService.publishedList(any())).thenReturn(List.of(comment(1L, 100L)));

        StatDtos.SentimentDist dist = new StatDtos.SentimentDist();
        dist.setTotal(3);
        dist.setPositive(3);
        dist.setPositiveRate(null);
        dist.setEnough(false);
        dist.setMinSample(5);
        when(reportService.sentimentDist(any())).thenReturn(dist);

        byte[] bytes = service.export(new StatDtos.Scope());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summary = wb.getSheetAt(0);
            boolean found = false;
            for (int r = 0; r <= summary.getLastRowNum(); r++) {
                Row row = summary.getRow(r);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }
                if ("正向".equals(row.getCell(0).getStringCellValue())) {
                    assertEquals("—", row.getCell(2).getStringCellValue());
                    found = true;
                }
            }
            assertTrue(found, "汇总表里应能找到「正向」行");
        }
    }
}
