package com.graduation.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.dto.OperLogDtos;
import com.graduation.mentor.entity.OperLog;
import com.graduation.mentor.mapper.OperLogMapper;
import com.graduation.mentor.service.OperLogService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 操作日志查询权限。
 *
 * <p><b>为什么这个类存在</b>：日志写入一直存在，但从未有过查询入口——审计能力做了 70%， 答辩时"操作可追溯"只能翻代码演示。补上查询的同时必须把权限一起守住： 日志里有操作人、IP
 * 和被退回点评的上下文，对导师/学生开放等于给了一条查看他人操作轨迹的旁路。
 */
class OperLogQueryTest {

    private OperLogMapper operLogMapper;
    private AuthContext authContext;
    private OperLogService service;

    @BeforeEach
    void setUp() {
        operLogMapper = mock(OperLogMapper.class);
        authContext = mock(AuthContext.class);
        service = new OperLogService(operLogMapper, authContext);
    }

    @Test
    @DisplayName("导师 / 学生查日志 → 403，且不触达数据库")
    void nonAdminForbidden() {
        when(authContext.isAdmin()).thenReturn(false);

        BizException e =
                assertThrows(BizException.class, () -> service.page(new OperLogDtos.Query()));

        assertEquals(403, e.getCode());
        verify(operLogMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("动作清单同样只对教务开放")
    void actionsForbiddenForNonAdmin() {
        when(authContext.isAdmin()).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.actions());

        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("教务可查：返回分页结构且带上 page/size")
    void adminCanQuery() {
        when(authContext.isAdmin()).thenReturn(true);
        Page<OperLog> page = new Page<>(2, 50);
        page.setRecords(List.of(new OperLog()));
        page.setTotal(51);
        doReturn(page).when(operLogMapper).selectPage(any(), any());

        OperLogDtos.Query q = new OperLogDtos.Query();
        q.setPage(2);
        q.setSize(50);
        PageResult<OperLog> result = service.page(q);

        assertEquals(51, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(50, result.getSize());
        assertEquals(1, result.getRecords().size());
    }
}
