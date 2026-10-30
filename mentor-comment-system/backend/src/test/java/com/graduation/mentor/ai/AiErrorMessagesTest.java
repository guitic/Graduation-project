package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graduation.mentor.service.ai.AiErrorMessages;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错误翻译测试。
 *
 * <p>这些断言来自真实踩过的坑：
 *
 * <ul>
 *   <li>Spring AI 把 HTTP 错误包成 {@code NonTransientAiException}，message 里只有 {@code 404 -
 *       [{"error":{"message":"model is not found"}}]}，既不是 {@code RestClientResponseException} 也没有
 *       cause 链——只按类型判断会漏；
 *   <li>JDK 的 {@code HttpURLConnection} 遇到 401 抛 {@code HttpRetryException} （"cannot retry due to
 *       server authentication"），看起来像网络问题，实际是 Key 错。
 * </ul>
 */
class AiErrorMessagesTest {

    @Test
    @DisplayName("404 model is not found → 提示去拉取模型列表")
    void modelNotFound() {
        RuntimeException e =
                new RuntimeException(
                        "404 - [{\"error\":{\"message\":\"model is not found\","
                                + "\"type\":\"not_found_error\",\"code\":\"5\"}}]");

        String msg = AiErrorMessages.describe(e);

        assertTrue(msg.contains("模型名不存在"), "应识别为模型名错误：" + msg);
        assertTrue(msg.contains("拉取模型列表"), "应给出下一步动作：" + msg);
    }

    @Test
    @DisplayName("401 HttpRetryException → 指向 Key 问题而不是网络")
    void unauthorized() {
        String msg = AiErrorMessages.describe(new HttpRetryException("auth failed", 401));

        assertTrue(msg.contains("401"), msg);
        assertTrue(msg.contains("API Key"), msg);
    }

    @Test
    @DisplayName("连接超时 → 提示检查网络/代理")
    void timeout() {
        String msg = AiErrorMessages.describe(new SocketTimeoutException("Read timed out"));

        assertTrue(msg.contains("超时"), msg);
    }

    @Test
    @DisplayName("连接被拒 → 提示检查 base-url")
    void connectRefused() {
        String msg = AiErrorMessages.describe(new ConnectException("Connection refused"));

        assertTrue(msg.contains("无法连接"), msg);
    }

    @Test
    @DisplayName("被包装的 404 也能识别（cause 链匹配）")
    void wrappedModelNotFound() {
        RuntimeException inner =
                new RuntimeException("404 - {\"error\":{\"message\":\"model is not found\"}}");
        RuntimeException outer = new RuntimeException("Error while extracting response", inner);

        assertTrue(AiErrorMessages.describe(outer).contains("模型名不存在"));
    }

    @Test
    @DisplayName("未知错误有兜底文案，不返回 null")
    void unknownFallsBack() {
        String msg = AiErrorMessages.describe(new IllegalStateException("something odd"));

        assertTrue(msg.startsWith("调用失败"), msg);
        assertTrue(msg.contains("something odd"), msg);
    }
}
