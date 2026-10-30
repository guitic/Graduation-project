package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.graduation.mentor.service.ai.AiRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * baseUrl 归一化测试。
 *
 * <p>这看着是个小函数，但它挡住的是一个**必然会发生**的错误：厂商文档给的 base_url 都自带 {@code /v1}，而 Spring AI 会在后面再拼一次 {@code
 * /v1/chat/completions}。 照抄文档 → 请求发到 {@code /v1/v1/chat/completions} → 404，而报错信息里只有一串 URL，
 * 使用者根本想不到要去掉 {@code /v1}。
 */
class AiRuntimeConfigTest {

    private static String normalize(String url) {
        return AiRuntimeConfig.normalizeBaseUrl(url);
    }

    @Test
    @DisplayName("剥掉结尾的 /v1：商汤日日新")
    void stripsV1ForSenseNova() {
        assertEquals("https://token.sensenova.cn", normalize("https://token.sensenova.cn/v1"));
    }

    @Test
    @DisplayName("剥掉结尾的 /v1：通义兼容模式（带路径前缀）")
    void stripsV1KeepingPathPrefix() {
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode",
                normalize("https://dashscope.aliyuncs.com/compatible-mode/v1"));
    }

    @Test
    @DisplayName("已不带 /v1 的地址保持不变")
    void keepsPlainHost() {
        assertEquals("https://api.deepseek.com", normalize("https://api.deepseek.com"));
    }

    @Test
    @DisplayName("结尾斜杠与多余空白一并处理")
    void trimsSlashAndSpace() {
        assertEquals("https://api.deepseek.com", normalize("  https://api.deepseek.com/v1/  "));
        assertEquals("https://api.deepseek.com", normalize("https://api.deepseek.com/"));
    }

    @Test
    @DisplayName("只剥最后一层 /v1，不误伤路径中间的 v1")
    void onlyStripsTrailingV1() {
        assertEquals("https://x.com/v1beta", normalize("https://x.com/v1beta"));
        assertEquals("https://x.com/v1/proxy", normalize("https://x.com/v1/proxy"));
    }

    @Test
    @DisplayName("空值原样返回，不抛异常")
    void nullSafe() {
        assertNull(normalize(null));
        assertEquals("", normalize(""));
        assertEquals("   ", normalize("   "));
    }

    @Test
    @DisplayName("构造 record 时即完成归一化")
    void recordNormalizesOnConstruction() {
        AiRuntimeConfig cfg =
                new AiRuntimeConfig(
                        true,
                        "https://token.sensenova.cn/v1",
                        "sk-test-1234567890",
                        "sense-nova-6.8-flash-lite",
                        1L,
                        "DB");

        assertEquals("https://token.sensenova.cn", cfg.baseUrl());
        assertEquals("sk-****7890", cfg.maskedKey());
    }
}
