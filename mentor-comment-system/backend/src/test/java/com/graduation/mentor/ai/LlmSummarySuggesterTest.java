package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.graduation.mentor.config.AiProperties;
import com.graduation.mentor.service.ai.AiChatClientHolder;
import com.graduation.mentor.service.ai.AiConfigStore;
import com.graduation.mentor.service.ai.AiRuntimeConfig;
import com.graduation.mentor.service.ai.LlmSummarySuggester;
import com.graduation.mentor.service.ai.SuggestionInput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spring AI 接入的兼容性、配置热更新与降级测试。
 *
 * <p><b>为什么这些测试必须存在</b>：本项目用 Spring Boot 3.2.5，而 Spring AI 1.0.0 是按 Spring Boot 3.4.x
 * 编译的。二者能否共存不能靠"应该可以"来判断——必须有一条断言跑通 {@code OpenAiApi → OpenAiChatModel → ChatClient}
 * 的构建链路。这条链路一旦在运行期 抛 NoClassDefFoundError，只会在导师点"生成建议"时暴露，而且是在答辩现场。
 *
 * <p>第二个必须守住的是<b>配置热更新</b>：教务在后台改 Key 之后，导师端下一次调用必须用上新的， 不能还揣着旧 client。
 */
class LlmSummarySuggesterTest {

    /** 指向一个必定拒绝连接的本地端口：验证"不联网也能完成初始化" */
    private static final String DEAD_ENDPOINT = "http://127.0.0.1:9";

    private static SuggestionInput sample() {
        return new SuggestionInput(
                "本周完成了开题报告的文献综述部分，进度符合预期。", "开题", "POSITIVE", 2.4, List.of("开题", "文献"), List.of());
    }

    private static AiProperties props() {
        AiProperties p = new AiProperties();
        p.setConnectTimeoutSeconds(1);
        p.setReadTimeoutSeconds(1);
        return p;
    }

    private static AiConfigStore store(AiRuntimeConfig cfg) {
        AiConfigStore s = mock(AiConfigStore.class);
        when(s.current()).thenReturn(cfg);
        return s;
    }

    /** 摘要实现现在依赖共享的 ChatClient 持有者，这里按同样的方式装配 */
    private static LlmSummarySuggester build(AiConfigStore store) {
        AiProperties p = props();
        return new LlmSummarySuggester(p, new AiChatClientHolder(p, store));
    }

    private static AiRuntimeConfig cfg(boolean enabled, String apiKey, long version) {
        return new AiRuntimeConfig(enabled, DEAD_ENDPOINT, apiKey, "deepseek-chat", version, "DB");
    }

    @Test
    @DisplayName("默认关闭时返回 null 并给出原因，不抛异常")
    void disabledReturnsNull() {
        LlmSummarySuggester s = build(store(cfg(false, "sk-test", 1L)));

        assertNull(s.suggest(sample()), "未启用时应返回 null，由门面回落规则实现");
        assertNotNull(s.unavailableReason(), "应说明不可用原因");
    }

    @Test
    @DisplayName("缺少 api-key 时判定不可用，而不是启动失败")
    void missingApiKeyIsNotFatal() {
        LlmSummarySuggester s = build(store(cfg(true, "", 1L)));

        assertNull(s.suggest(sample()));
        assertNotNull(s.unavailableReason());
    }

    @Test
    @DisplayName("配置完整时 Spring AI 组件可在 Boot 3.2.5 下完成构建（不联网）")
    void chatClientBuildsOnBoot32() {
        LlmSummarySuggester s = build(store(cfg(true, "sk-test-not-a-real-key", 1L)));

        assertNull(
                s.unavailableReason(),
                "OpenAiApi / OpenAiChatModel / ChatClient 构建失败，说明与当前 Spring Boot 版本不兼容："
                        + s.unavailableReason());
    }

    @Test
    @DisplayName("端点不可达时抛异常，交由门面捕获并回落")
    void unreachableEndpointThrows() {
        LlmSummarySuggester s = build(store(cfg(true, "sk-test-not-a-real-key", 1L)));

        assertThrows(RuntimeException.class, () -> s.suggest(sample()));
    }

    @Test
    @DisplayName("教务改配置后立即生效：无需重启，旧 ChatClient 被丢弃")
    void configChangeTakesEffectImmediately() {
        AiConfigStore store = mock(AiConfigStore.class);
        when(store.current()).thenReturn(cfg(true, "sk-old-key", 100L));
        LlmSummarySuggester s = build(store);

        assertNull(s.unavailableReason(), "初始配置应可用");

        // 教务清空 Key（版本号同时变化），下一次调用必须立刻反映出来
        when(store.current()).thenReturn(cfg(true, "", 200L));
        String reason = s.unavailableReason();

        assertNotNull(reason, "清空 Key 后应判定不可用");
        assertTrue(reason.contains("API Key"), "原因应指向缺少 Key：" + reason);
    }
}
