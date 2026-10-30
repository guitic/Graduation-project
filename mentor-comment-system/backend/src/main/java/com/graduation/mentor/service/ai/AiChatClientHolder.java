package com.graduation.mentor.service.ai;

import com.graduation.mentor.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ChatClient 的唯一持有者。
 *
 * <p><b>为什么抽出来</b>：摘要（{@link LlmSummarySuggester}）和对话（{@link AiChatService}）
 * 需要的是同一个东西——一个按当前配置建好、配置变了会自动重建的 {@link ChatClient}。 各建一份的话，会出现"教务改了 Key，摘要生效了、对话还揣着旧
 * Key"这种极难排查的问题。
 *
 * <p><b>三个刻意的设计决定</b>：
 *
 * <ol>
 *   <li><b>懒初始化</b>：构造阶段不连网、不校验 Key。未启用或缺 Key 时返回 null， 容器启动完全不受影响。若改用 Spring AI starter 自动配置，无 Key
 *       会直接让整个应用起不来。
 *   <li><b>按配置版本号重建</b>：教务在后台改 Key 后，旧 client 还揣着旧 Key， 用户会看到"保存成功但仍报未配置"。版本号取 {@code
 *       sys_config.updated_at}， 配置一变就重建——不重启、不轮询，每次调用只多一次 volatile 读。
 *   <li><b>超时是功能的一部分</b>：连接 3 秒、读取 10 秒。模型抖动时不能把用户卡住。
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatClientHolder {

    private final AiProperties props;
    private final AiConfigStore configStore;

    private volatile ChatClient client;

    /** 当前 client 对应的配置版本；与 store 的版本不一致时重建 */
    private volatile long clientVersion = Long.MIN_VALUE;

    private volatile String initError;

    /** 当前生效配置（端点、模型名、来源），供调用方回传展示 */
    public AiRuntimeConfig config() {
        return configStore.current();
    }

    /**
     * 取 ChatClient。
     *
     * @return null 表示当前不可用，原因见 {@link #unavailableReason()}
     */
    public ChatClient get() {
        AiRuntimeConfig cfg = configStore.current();
        if (!cfg.enabled()) {
            initError = "AI 未启用（可在教务后台「AI 配置」开启）";
            return null;
        }
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            initError = "未配置 API Key（可在教务后台「AI 配置」填写，或设置环境变量 AI_API_KEY）";
            return null;
        }

        ChatClient local = client;
        if (local != null && clientVersion == cfg.version()) {
            return local;
        }

        synchronized (this) {
            if (client != null && clientVersion == cfg.version()) {
                return client;
            }
            try {
                client = build(cfg);
                clientVersion = cfg.version();
                initError = null;
                log.info("Spring AI 已就绪：{} / {}（来源 {}）", cfg.baseUrl(), cfg.model(), cfg.source());
            } catch (RuntimeException e) {
                client = null;
                clientVersion = Long.MIN_VALUE;
                initError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Spring AI 初始化失败，相关能力回落：{}", initError);
            }
            return client;
        }
    }

    /** 不可用原因；可用时返回 null */
    public String unavailableReason() {
        get();
        return initError;
    }

    private ChatClient build(AiRuntimeConfig cfg) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(props.getReadTimeoutSeconds() * 1000);
        OpenAiApi api =
                OpenAiApi.builder()
                        .baseUrl(cfg.baseUrl())
                        .apiKey(cfg.apiKey())
                        .restClientBuilder(RestClient.builder().requestFactory(factory))
                        .build();
        OpenAiChatModel model =
                OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(
                                OpenAiChatOptions.builder()
                                        .model(cfg.model())
                                        .temperature(props.getTemperature())
                                        .build())
                        .build();
        return ChatClient.builder(model).build();
    }
}
