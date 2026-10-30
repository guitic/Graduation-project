package com.graduation.mentor.controller;

import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.PrecheckDtos;
import com.graduation.mentor.service.PrecheckService;
import com.graduation.mentor.service.precheck.RulePrecheckEngine;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 预审接口。
 *
 * <p><b>这里没有任何写 comment.status 的入口，这是刻意的。</b> 预审只产出建议，审核动作仍然 只能走 {@code
 * /api/audit/{id}/approve|return|reject}，那三处的条件化更新与行数校验不可绕过。
 *
 * <p>权限：全部接口仅教务管理员。预审结果含"疑似套话""贬损性表述"等内部判断， 不向导师、学生开放。
 */
@RestController
@RequestMapping("/api/ai/precheck")
@RequiredArgsConstructor
public class PrecheckController {

    private final PrecheckService precheckService;

    /** 只读缓存：未预审过返回 data=null，前端据此显示"暂无预审结果" */
    @GetMapping("/{id}")
    public R<PrecheckDtos.View> cached(@PathVariable Long id) {
        return R.ok(precheckService.cached(id));
    }

    /** 单条预审。默认命中缓存则复用；force=true 强制重算（规则调参后核对结论时用）。 */
    @PostMapping("/{id}")
    public R<PrecheckDtos.View> ensure(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        return R.ok(precheckService.ensure(id, force));
    }

    /** 批量预审：列表页一次预审当前页，最多 50 条 */
    @PostMapping("/batch")
    public R<List<PrecheckDtos.View>> batch(@Valid @RequestBody PrecheckDtos.BatchReq req) {
        return R.ok(precheckService.batch(req.getIds()));
    }

    /** 规则目录：前端"预审规则说明"与论文表格共用同一份口径 */
    @GetMapping("/rules")
    public R<Map<String, Object>> rules() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", RulePrecheckEngine.ENGINE);
        data.put("version", RulePrecheckEngine.VERSION);
        data.put("rules", RulePrecheckEngine.rules());
        return R.ok(data);
    }
}
