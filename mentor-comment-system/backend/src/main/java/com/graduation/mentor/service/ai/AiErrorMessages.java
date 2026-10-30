package com.graduation.mentor.service.ai;

/**
 * 把模型调用失败的底层异常翻译成"能据此行动"的一句话。
 *
 * <p><b>为什么单独抽出来</b>：同一类错误会在三个地方出现——教务点「测试连接」、 教务点「拉取模型列表」、导师点「生成摘要建议」。如果各写一份，很快就会出现 "测试页说 401，导师页说
 * RestClientException"这种对不上的情况。
 *
 * <p><b>为什么不能只按异常类型判断</b>：Spring AI 会把 HTTP 错误包成 {@code NonTransientAiException}，message 里只有一串
 * {@code 404 - [{"error":{"message":"model is not found"...}}]}， 既没有 {@code
 * RestClientResponseException}，也没有 cause 链。所以必须同时看 message 文本。
 */
public final class AiErrorMessages {

    private AiErrorMessages() {}

    public static String describe(Throwable e) {
        String all = collectMessages(e);

        // 模型名错误：最常见，且报错文本里有明确标识，优先识别
        if (all.contains("model is not found")
                || all.contains("model_not_found")
                || all.contains("model does not exist")
                || all.contains("invalid model")) {
            return "模型名不存在（HTTP 404）：点「拉取模型列表」选一个该账号可用的模型";
        }

        Throwable root = rootCause(e);

        if (root instanceof java.net.HttpRetryException hre) {
            // JDK HttpURLConnection 在流模式下遇到 401 抛这个，而不是 RestClientResponseException。
            // 消息是 "cannot retry due to server authentication"，容易被误读成网络问题。
            return "认证失败（HTTP " + hre.responseCode() + "）：API Key 无效、已被禁用或余额不足";
        }
        if (root instanceof org.springframework.web.client.RestClientResponseException rce) {
            return byStatus(rce.getStatusCode().value());
        }
        if (root instanceof java.net.SocketTimeoutException) {
            return "连接超时：检查网络、代理设置或端点是否可达";
        }
        if (root instanceof java.net.ConnectException) {
            return "无法连接端点：检查 base-url 是否正确、本机能否访问该域名";
        }

        // 兜底：从消息里抠 HTTP 状态码
        int code = extractStatus(all);
        if (code > 0) {
            return byStatus(code);
        }
        String msg = root.getMessage();
        return "调用失败："
                + root.getClass().getSimpleName()
                + (msg == null ? "" : " - " + truncate(msg, 120));
    }

    private static String byStatus(int code) {
        String hint =
                switch (code) {
                    case 401 -> "API Key 无效、已被禁用或余额不足";
                    case 402 -> "账户余额不足";
                    case 403 -> "该 Key 无权访问此模型";
                    case 404 -> "端点或模型名不存在，请检查 base-url 与模型";
                    case 429 -> "请求过于频繁或额度用尽";
                    default -> "服务端返回错误";
                };
        return "调用失败（HTTP " + code + "）：" + hint;
    }

    /** 把 cause 链上的 message 拼起来，供关键字匹配 */
    private static String collectMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int guard = 0;
        while (cur != null && guard++ < 8) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(' ');
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return sb.toString();
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cur = e;
        int guard = 0;
        while (cur.getCause() != null && cur.getCause() != cur && guard++ < 8) {
            cur = cur.getCause();
        }
        return cur;
    }

    /** 从 "404 - [...]" 这类文本里抠出状态码 */
    private static int extractStatus(String text) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\b(4\\d{2}|5\\d{2})\\b").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
