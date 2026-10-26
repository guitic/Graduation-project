package com.graduation.mentor.controller;

import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.CommentDtos;
import com.graduation.mentor.service.AuditService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核与发布接口。
 *
 * <p>注意：**不提供批量发布接口**。批量更新极易写成 {@code WHERE id IN (...)} 的裸 SQL， 绕过逐条状态校验，是状态机被破坏的主要入口。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/pending")
    public R<PageResult<CommentDtos.Row>> pending(CommentDtos.Query q) {
        return R.ok(auditService.pendingPage(q));
    }

    @GetMapping("/unpublished")
    public R<PageResult<CommentDtos.Row>> unpublished(CommentDtos.Query q) {
        return R.ok(auditService.unpublishedPage(q));
    }

    /** 已发布列表（供「撤回发布」操作） */
    @GetMapping("/published")
    public R<PageResult<CommentDtos.Row>> published(CommentDtos.Query q) {
        return R.ok(auditService.publishedPage(q));
    }

    @GetMapping("/pending-count")
    public R<Map<String, Object>> pendingCount() {
        return R.ok(Map.of("pending", auditService.pendingCount()));
    }

    /** 通过。publish=true → 通过并发布；false → 仅通过（进入待发布） */
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody CommentDtos.AuditReq req) {
        auditService.approve(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/return")
    public R<Void> returnBack(@PathVariable Long id, @Valid @RequestBody CommentDtos.AuditReq req) {
        auditService.returnBack(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody CommentDtos.AuditReq req) {
        auditService.reject(id, req);
        return R.ok();
    }

    /**
     * 发布（单条）。
     *
     * <p>首次发布无需理由；曾撤回过的点评属于"再发布"，必须填理由（≥5 字）， 因为"为什么压下来"有据可查、"为什么放回去"也必须可查。
     */
    @PostMapping("/{id}/publish")
    public R<Void> publish(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        auditService.publish(id, reason);
        return R.ok();
    }

    /** 撤回发布：published_at 置回 null，学生立即不可见，重新进入待发布。须填理由 ≥10 字 */
    @PostMapping("/{id}/unpublish")
    public R<Void> unpublish(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        auditService.unpublish(id, reason);
        return R.ok();
    }

    @GetMapping("/{id}/history")
    public R<List<CommentDtos.AuditItem>> history(@PathVariable Long id) {
        return R.ok(auditService.audits(id));
    }
}
