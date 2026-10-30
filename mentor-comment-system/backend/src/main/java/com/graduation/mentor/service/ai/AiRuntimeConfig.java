package com.graduation.mentor.service.ai;

/**
 * 运行期生效的 AI 配置快照。
 *
 * <p><b>为什么要快照 + 版本号</b>：ChatClient 建一次要几百毫秒，不可能每次请求都重建； 但教务在后台改了 Key 之后，旧 client 还揣着旧
 * Key，用户会看到"保存成功但仍报未配置"。 版本号取 {@code sys_config.updated_at} 的毫秒时间戳，配置一变版本就变， {@link
 * LlmSummarySuggester} 据此判断是否需要重建——既不用重启，也不用轮询。
 *
 * @param enabled 是否启用大模型
 * @param baseUrl OpenAI 兼容端点
 * @param apiKey 明文密钥，只在服务端内存中流转，任何接口都不回传
 * @param model 模型名
 * @param version 配置版本（毫秒时间戳）；来自环境变量时为 0
 * @param source 配置来源：DB / ENV / NONE
 */
public record AiRuntimeConfig(
        boolean enabled, String baseUrl, String apiKey, String model, long version, String source) {

    public static final String SOURCE_DB = "DB";

    public static final String SOURCE_ENV = "ENV";

    public static final String SOURCE_NONE = "NONE";

    /**
     * 构造时归一化 baseUrl。
     *
     * <p><b>为什么必须做这件事</b>：Spring AI 的 {@code OpenAiApi} 默认把请求发到 {@code {baseUrl} +
     * "/v1/chat/completions"}，而几乎所有厂商文档给的 base_url 都自带 {@code /v1}（SenseNova {@code
     * https://token.sensenova.cn/v1}、通义 {@code .../compatible-mode/v1}）。照抄文档填进来就会变成 {@code
     * /v1/v1/chat/completions}，报 404，而错误信息完全看不出是这个原因。
     *
     * <p>所以这里统一剥掉结尾的 {@code /v1}：用户怎么抄都不会错。剥完再由 Spring AI 拼回去， 对 {@code https://xxx.com/openai/v1}
     * 这类带路径前缀的端点同样成立。
     */
    public AiRuntimeConfig {
        baseUrl = normalizeBaseUrl(baseUrl);
    }

    /** 去掉结尾的斜杠与 {@code /v1}，空值原样返回 */
    public static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/v1")) {
            u = u.substring(0, u.length() - 3);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** 是否具备调用条件：开关打开且有 Key */
    public boolean usable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** 掩码：只保留前 3 位与后 4 位，中间固定 4 个星号 */
    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
