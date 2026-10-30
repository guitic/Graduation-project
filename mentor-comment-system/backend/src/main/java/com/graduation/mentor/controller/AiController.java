package com.graduation.mentor.controller;

import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.R;
import com.graduation.mentor.dto.AiChatDtos;
import com.graduation.mentor.dto.AiConfigDtos;
import com.graduation.mentor.dto.AiDtos;
import com.graduation.mentor.service.AiChatService;
import com.graduation.mentor.service.AiSuggestionService;
import com.graduation.mentor.service.ai.AiConfigStore;
import com.graduation.mentor.service.ai.AiErrorMessages;
import com.graduation.mentor.service.ai.AiSuggestion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 相关接口。
 *
 * <p>三组能力，权限与生命周期完全不同，不要混在一起讲：
 *
 * <ul>
 *   <li>{@code /precheck}（另一个 Controller）：规则引擎，教务用，结论进审核流程；
 *   <li>{@code /summary}：模型/规则双通道，导师写点评时用，是编辑辅助，不进任何状态机；
 *   <li>{@code /config}：教务填 API Key 的地方。**填一次，全体导师生效**——因为 Key 存在 服务端配置表里，不是每个账号一份。
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiSuggestionService aiSuggestionService;
    private final AiConfigStore aiConfigStore;
    private final AiChatService aiChatService;

    /** 当前 AI 能力状态：前端据此决定按钮文案（"AI 生成"还是"规则生成"） */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(aiSuggestionService.status());
    }

    @PostMapping("/summary")
    public R<AiSuggestion> summary(@RequestBody AiDtos.SummaryReq req) {
        if (req.getCommentId() != null) {
            return R.ok(aiSuggestionService.suggestForComment(req.getCommentId()));
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(400, "commentId 与 content 至少提供一个");
        }
        return R.ok(aiSuggestionService.suggestForText(req.getContent(), req.getTopic()));
    }

    /**
     * AI 小助手对话（多轮）。
     *
     * <p>所有登录角色可用，但**场景由服务端按角色决定**——前端不传、也传不进来。 学生拿到的是学习助手提示词，不会拿到教务口径。
     *
     * <p>无 Key 时返回 {@code source=DISABLED} + 说明文案，HTTP 仍是成功： 助手不可用是常态（默认就是关的），不该表现为接口报错。
     */
    @PostMapping("/chat")
    public R<AiChatDtos.ChatResp> chat(@Valid @RequestBody AiChatDtos.ChatReq req) {
        return R.ok(aiChatService.chat(req));
    }

    // ------------------------------------------------------------------ 配置（仅教务）

    /** 读取当前配置。密钥只返回掩码，明文永不出服务端 */
    @GetMapping("/config")
    public R<AiConfigDtos.View> config() {
        return R.ok(aiConfigStore.view());
    }

    /**
     * 保存配置。字段为 null 表示不修改，空串表示清除并回落环境变量。
     *
     * <p>保存后立即失效配置缓存，导师端下一次调用就重建 ChatClient——不需要重启。
     */
    @PutMapping("/config")
    public R<Void> updateConfig(@RequestBody AiConfigDtos.Req req) {
        aiConfigStore.update(req);
        return R.ok();
    }

    /** 连通性自检：真发一次请求，区分"配置齐了"与"真能通" */
    @PostMapping("/config/test")
    public R<Map<String, Object>> testConfig() {
        return R.ok(aiSuggestionService.testConnectivity());
    }

    /**
     * 拉取该账号可用的模型列表。
     *
     * <p>解决的是最烦的一类问题：模型名猜错只会得到 404 "model is not found"， 而厂商文档里的名字往往和接口 ID 不一致。这里直接问服务端要清单。
     */
    @GetMapping("/models")
    public R<List<String>> models() {
        try {
            return R.ok(aiConfigStore.listModels());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 这个接口是教务唯一的自助排错入口。返回「系统繁忙，请稍后重试」等于把人挡在门外，
            // 宁可给一句具体的失败原因。
            log.warn("拉取模型列表异常：{}", e.toString());
            return R.fail(400, "拉取模型列表失败：" + AiErrorMessages.describe(e));
        }
    }
}
