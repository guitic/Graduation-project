package com.graduation.mentor.service.ai;

import com.graduation.mentor.common.BizException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 调用限流（按用户，内存滑动窗口）。
 *
 * <p><b>为什么必须有</b>：Key 是教务配的、全系统共用一份配额。没有限流时， 一个脚本就能把额度刷完，而受害者是所有人——包括正在答辩演示的人。
 *
 * <p><b>为什么是内存而不是 Redis</b>：本项目单机部署，引入 Redis 只为限流属于 用一台新中间件解决一个数组能解决的问题。多实例部署时这里会失效， 届时应换成 Redis
 * 或网关限流——这是已知局限，写在设计文档里。
 *
 * <p><b>两道窗口</b>：分钟窗口挡"连点"，日窗口挡"长时间刷"。只做分钟窗口的话， 每分钟 12 次 × 一天 = 一万七千次，配额照样会被吃光。
 */
@Component
public class AiRateLimiter {

    /** 单用户每分钟上限 */
    private final int perMinute;

    /** 单用户每日上限 */
    private final int perDay;

    /** 追踪用户数上限，超过后清理过期桶，防止内存无限增长 */
    private static final int MAX_TRACKED_USERS = 2000;

    private static final long MINUTE_MS = 60_000L;

    private static final long DAY_MS = 86_400_000L;

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

    public AiRateLimiter(
            @Value("${app.ai.chat.per-minute:12}") int perMinute,
            @Value("${app.ai.chat.per-day:150}") int perDay) {
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /**
     * 占用一次配额。
     *
     * @return 今日剩余次数
     * @throws BizException 429 超限
     */
    public int acquire(Long userId) {
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(userId, k -> new Bucket());
        int remaining;
        synchronized (b) {
            // 分钟窗口：滑出 60 秒前的记录
            while (!b.minute.isEmpty() && now - b.minute.peekFirst() >= MINUTE_MS) {
                b.minute.pollFirst();
            }
            if (b.minute.size() >= perMinute) {
                long waitMs = MINUTE_MS - (now - b.minute.peekFirst());
                throw new BizException(429, "问得太快了，请 " + Math.max(1, waitMs / 1000) + " 秒后再试");
            }

            // 日窗口：跨天重置
            long today = now / DAY_MS;
            if (b.day != today) {
                b.day = today;
                b.dayCount = 0;
            }
            if (b.dayCount >= perDay) {
                throw new BizException(429, "今日 AI 助手使用次数已用完（" + perDay + " 次），请明天再试");
            }

            b.minute.addLast(now);
            b.dayCount++;
            remaining = perDay - b.dayCount;
        }

        if (buckets.size() > MAX_TRACKED_USERS) {
            evictStale(now);
        }
        return remaining;
    }

    /** 清掉"分钟窗口已空且不是今天"的桶 */
    private void evictStale(long now) {
        long today = now / DAY_MS;
        buckets.entrySet()
                .removeIf(
                        e -> {
                            Bucket b = e.getValue();
                            synchronized (b) {
                                return b.minute.isEmpty() && b.day != today;
                            }
                        });
    }

    /** 单用户计数桶 */
    private static final class Bucket {

        /** 最近 60 秒的调用时间戳（毫秒） */
        final ArrayDeque<Long> minute = new ArrayDeque<>();

        /** 当前日期序号（epoch day） */
        long day = -1;

        /** 当日已用次数 */
        int dayCount;
    }
}
