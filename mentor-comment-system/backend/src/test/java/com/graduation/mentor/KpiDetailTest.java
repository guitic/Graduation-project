package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.config.MinSampleProperties;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.dto.StatDtos;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.CommentSentimentMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.CommentService;
import com.graduation.mentor.service.ReportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KPI 卡下钻。
 *
 * <p><b>为什么这个类存在</b>：卡片上写「待审核 8」，点进去却列出 6 条，是最伤信任的一类缺陷—— 用户会先怀疑数据丢了，再怀疑系统算错了，最后不再相信报表上任何一个数字。
 * 这里把「明细条数 = 卡片数字」「每个指标都带口径说明」「未知指标必须报错而不是静默返回空」 写成断言，把这类错挡在演示之前。
 *
 * <p>另一条回归：低置信口径要先取已发布 id 再拼 {@code IN (...)}，空集合会生成 {@code IN ()} 这种 SQL 语法错误。没有已发布数据时点「低置信」必须是 0
 * 条，而不是 500。
 */
class KpiDetailTest {

    private static final Map<String, String> LABELS =
            Map.of(
                    "total", "点评总数",
                    "published", "已发布",
                    "pending", "待审核",
                    "unpublished", "待发布",
                    "returned", "退回修改",
                    "rejected", "已驳回",
                    "lowConfidence", "低置信");

    private CommentMapper commentMapper;
    private CommentService commentService;
    private ReportService service;

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        commentService = mock(CommentService.class);
        // 默认没有任何已发布点评：lowConfidence 分支会走「空集合」这条最容易出错的路径
        when(commentMapper.selectList(any())).thenReturn(List.of());
        service =
                new ReportService(
                        commentMapper,
                        mock(CommentSentimentMapper.class),
                        mock(CommentAuditMapper.class),
                        mock(StudentMapper.class),
                        new MinSampleProperties(),
                        mock(AnalysisService.class),
                        mock(AuthContext.class),
                        commentService);
    }

    /** 让分页查询固定返回 total 条（行内容由 toRows 决定，这里不关心） */
    private void stubPage(long total) {
        Page<Comment> p = new Page<>(1, 10);
        p.setTotal(total);
        p.setRecords(total == 0 ? List.of() : List.of(new Comment()));
        doReturn(p).when(commentMapper).selectPage(any(), any());
        when(commentService.toRows(any()))
                .thenReturn(total == 0 ? List.of() : List.of(new CommentDtos.Row()));
    }

    @Test
    @DisplayName("七个 KPI 都能下钻，标题与口径由后端下发")
    void everyKpiIsDrillable() {
        stubPage(8);
        LABELS.forEach(
                (key, label) -> {
                    StatDtos.KpiDetail d = service.kpiDetail(key, null, 1, 10);
                    assertEquals(label, d.getLabel(), "指标 " + key + " 的标题");
                    assertFalse(d.getCaliber().isBlank(), "指标 " + key + " 缺少口径说明");
                    assertEquals(8, d.getTotal(), "指标 " + key + " 的明细条数应与卡片一致");
                    assertEquals(1, d.getRecords().size());
                });
    }

    @Test
    @DisplayName("未知指标直接报错，不返回空列表冒充「0 条」")
    void unknownKpiIsRejected() {
        assertThrows(BizException.class, () -> service.kpiDetail("draft", null, 1, 10));
        assertThrows(BizException.class, () -> service.kpiDetail(null, null, 1, 10));
    }

    @Test
    @DisplayName("分页参数被夹到 1 页起、每页最多 100 条")
    void pageParamsAreClamped() {
        stubPage(0);
        StatDtos.KpiDetail d = service.kpiDetail("pending", null, 0, 500);
        assertEquals(1, d.getPage());
        assertEquals(100, d.getSize());
    }

    @Test
    @DisplayName("没有已发布点评时，低置信返回 0 条而不是 IN () 报错")
    void lowConfidenceWithoutPublishedIsZero() {
        stubPage(0);
        StatDtos.KpiDetail d = service.kpiDetail("lowConfidence", null, 1, 10);
        assertEquals(0, d.getTotal());
        assertEquals(List.of(), d.getRecords());
    }
}
