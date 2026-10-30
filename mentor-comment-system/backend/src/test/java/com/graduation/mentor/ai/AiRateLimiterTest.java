package com.graduation.mentor.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graduation.mentor.common.BizException;
import com.graduation.mentor.service.ai.AiRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 限流测试。
 *
 * <p>Key 是教务配的、全系统共用一份配额。没有限流时一个脚本就能把额度刷完， 而受害者是所有人——包括正在演示的人。
 */
class AiRateLimiterTest {

    @Test
    @DisplayName("分钟窗口：超过上限后拒绝，且提示等待秒数")
    void minuteWindowBlocks() {
        AiRateLimiter limiter = new AiRateLimiter(3, 100);
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        BizException e = assertThrows(BizException.class, () -> limiter.acquire(1L));

        assertEquals(429, e.getCode());
        assertTrue(e.getMessage().contains("快"), e.getMessage());
    }

    @Test
    @DisplayName("日窗口：用完当日额度后拒绝")
    void dayWindowBlocks() {
        AiRateLimiter limiter = new AiRateLimiter(100, 2);
        limiter.acquire(1L);
        limiter.acquire(1L);

        BizException e = assertThrows(BizException.class, () -> limiter.acquire(1L));

        assertEquals(429, e.getCode());
        assertTrue(e.getMessage().contains("今日"), e.getMessage());
    }

    @Test
    @DisplayName("剩余次数随调用递减")
    void remainingDecreases() {
        AiRateLimiter limiter = new AiRateLimiter(10, 5);

        assertEquals(4, limiter.acquire(1L));
        assertEquals(3, limiter.acquire(1L));
    }

    @Test
    @DisplayName("不同用户互不影响")
    void usersAreIsolated() {
        AiRateLimiter limiter = new AiRateLimiter(1, 100);
        limiter.acquire(1L);

        // 用户 1 已用满，用户 2 仍可用
        assertThrows(BizException.class, () -> limiter.acquire(1L));
        assertDoesNotThrow(() -> limiter.acquire(2L));
    }
}
