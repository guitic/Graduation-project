package com.graduation.mentor.controller;

import com.graduation.mentor.common.R;
import com.graduation.mentor.config.MinSampleProperties;
import com.graduation.mentor.service.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时规则元数据。
 *
 * <p><b>为什么需要这个接口</b>：审核意见字数门槛与隐私样本门槛原来各写两份——后端常量一份、前端 {@code auditRules}
 * 一份。两份数字一旦不同步，前端会放行后端拒绝的输入（用户看到"已提交"，实际 400）， 或前端拦下后端本可接受的输入。这类不一致不会报错，只会表现为"偶尔提交失败"。
 *
 * <p>因此把门槛收敛为后端唯一来源，前端启动后读取一次并按返回值渲染提示文案。 修改 {@link AuditService} 常量或 {@code analysis.min-sample}
 * 后，前端文案自动跟随，无需改前端。
 *
 * <p>接口要求登录（未列入 {@code /api/**} 白名单）：门槛本身不敏感，但没必要对匿名开放。
 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MinSampleProperties minSampleProperties;

    /** 前端渲染"至少 N 字"提示所需的全部阈值 */
    @GetMapping("/limits")
    public R<Map<String, Object>> limits() {
        Map<String, Object> opinion = new LinkedHashMap<>();
        opinion.put("approvePublish", AuditService.MIN_OPINION_APPROVE_PUBLISH);
        opinion.put("approveOnly", AuditService.MIN_OPINION_APPROVE_ONLY);
        opinion.put("return", AuditService.MIN_OPINION_RETURN);
        opinion.put("reject", AuditService.MIN_OPINION_REJECT);
        opinion.put("unpublish", AuditService.MIN_OPINION_UNPUBLISH);
        opinion.put("republish", AuditService.MIN_OPINION_REPUBLISH);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minSample", minSampleProperties.getMinSample());
        data.put("opinion", opinion);
        return R.ok(data);
    }
}
