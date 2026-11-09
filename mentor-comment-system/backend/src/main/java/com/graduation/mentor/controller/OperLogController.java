package com.graduation.mentor.controller;

import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.OperLogDtos;
import com.graduation.mentor.entity.OperLog;
import com.graduation.mentor.service.OperLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询（仅教务）。
 *
 * <p>只读接口：日志是审计证据，任何角色都不提供修改与删除入口。 需要清理时由 DBA 按保留策略归档，不走应用层——应用层能删日志，留痕就失去意义。
 */
@RestController
@RequestMapping("/api/oper-log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @GetMapping("/page")
    public R<PageResult<OperLog>> page(OperLogDtos.Query q) {
        return R.ok(operLogService.page(q));
    }

    /** 动作名清单，供前端筛选下拉 */
    @GetMapping("/actions")
    public R<List<String>> actions() {
        return R.ok(operLogService.actions());
    }
}
