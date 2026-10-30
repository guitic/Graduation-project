package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.dto.AiChatDtos;
import com.graduation.mentor.service.AiChatService;
import com.graduation.mentor.service.ai.AiChatClientHolder;
import com.graduation.mentor.service.ai.AiRateLimiter;
import com.graduation.mentor.service.ai.AiRuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * 小助手对话的降级与提示词测试。
 *
 * <p>三条必须守住的：
 *
 * <ol>
 *   <li>无 Key 时返回说明文案而不是抛异常——助手默认就是关的，不该表现成接口报错；
 *   <li>场景按登录角色决定，学生拿不到教务提示词；
 *   <li>模型调用失败时也返回人话，前端还能继续用系统。
 * </ol>
 */
class AiChatServiceTest {

    private static AiChatDtos.ChatReq req(String text) {
        AiChatDtos.Msg m = new AiChatDtos.Msg();
        m.setRole("user");
        m.setContent(text);
        AiChatDtos.ChatReq r = new AiChatDtos.ChatReq();
        r.setMessages(List.of(m));
        return r;
    }

    private static AuthContext auth(String role) {
        AuthContext c = mock(AuthContext.class);
        when(c.userId()).thenReturn(1L);
        when(c.role()).thenReturn(role);
        return c;
    }

    @Test
    @DisplayName("无 Key：返回说明文案，source=DISABLED，不抛异常")
    void disabledReturnsFriendlyText() {
        AiChatClientHolder holder = mock(AiChatClientHolder.class);
        when(holder.get()).thenReturn(null);
        when(holder.unavailableReason()).thenReturn("AI 未启用（可在教务后台「AI 配置」开启）");

        AiChatService svc = new AiChatService(holder, auth("STUDENT"), new AiRateLimiter(10, 10));
        AiChatDtos.ChatResp resp = svc.chat(req("我该怎么改进？"));

        assertEquals("DISABLED", resp.getSource());
        assertTrue(resp.getReply().contains("AI 未启用"), resp.getReply());
        assertTrue(resp.getReply().contains("教务"), "应指出去哪开启：" + resp.getReply());
    }

    @Test
    @DisplayName("模型调用抛异常：返回人话，不把原始异常抛给前端")
    void modelFailureDegrades() {
        AiChatClientHolder holder = mock(AiChatClientHolder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        when(holder.get()).thenReturn(client);
        when(holder.config()).thenReturn(cfg());
        when(client.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenThrow(new RuntimeException("boom"));

        AiChatService svc = new AiChatService(holder, auth("STUDENT"), new AiRateLimiter(10, 10));
        AiChatDtos.ChatResp resp = svc.chat(req("帮我制定计划"));

        assertEquals("DISABLED", resp.getSource());
        assertTrue(resp.getReply().contains("暂时不可用"), resp.getReply());
    }

    @Test
    @DisplayName("学生拿到的系统提示词是学习助手，不是教务口径")
    void studentGetsStudentPrompt() {
        List<Message> captured = new ArrayList<>();
        AiChatClientHolder holder = mock(AiChatClientHolder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(holder.get()).thenReturn(client);
        when(holder.config()).thenReturn(cfg());
        when(client.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("可以这样做：1. 每天复盘 30 分钟。");

        AiChatService svc = new AiChatService(holder, auth("STUDENT"), new AiRateLimiter(10, 10));
        svc.chat(req("我该怎么改进？"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> cap = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(spec).messages(cap.capture());
        captured.addAll(cap.getValue());

        String sys = captured.get(0).getText();
        assertTrue(sys.contains("学习助手"), "学生应为学习助手提示词：" + sys.substring(0, 40));
        assertTrue(sys.contains("不能") || sys.contains("你不能做"), "应包含能力边界");
    }

    @Test
    @DisplayName("限流命中时抛 429，不消耗模型调用")
    void rateLimited() {
        AiChatClientHolder holder = mock(AiChatClientHolder.class);
        when(holder.get()).thenReturn(null);
        when(holder.unavailableReason()).thenReturn("未启用");

        AiRateLimiter limiter = new AiRateLimiter(1, 100);
        AiChatService svc = new AiChatService(holder, auth("STUDENT"), limiter);
        svc.chat(req("第一次"));

        BizException e = assertThrows(BizException.class, () -> svc.chat(req("第二次")));
        assertEquals(429, e.getCode());
    }

    private static AiRuntimeConfig cfg() {
        return new AiRuntimeConfig(
                true, "http://127.0.0.1:9", "sk-test-1234", "test-model", 1L, "DB");
    }
}
