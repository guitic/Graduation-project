package com.graduation.mentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型摘要能力配置（{@code app.ai.*}）。
 *
 * <p><b>默认关闭是设计决定，不是偷懒。</b> 答辩现场断网、Key 过期、模型限流都会发生；
 * 一旦默认开启，系统启动或首次调用就会把异常暴露给评委。默认关闭时走规则实现，功能可用、结果可解释， 打开后才有大模型参与，两者输出结构完全一致，前端无需分支。
 *
 * <p>兼容任意 OpenAI 协议端点（DeepSeek / 通义 / 智谱 / vLLM 自建），改 {@code base-url} 即可， 不绑定具体厂商。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 总开关：false 时完全不加载模型，全部走规则实现 */
    private boolean enabled = false;

    /** OpenAI 兼容端点，默认 DeepSeek（国内可直连，无需代理） */
    private String baseUrl = "https://api.deepseek.com";

    /** 密钥，通过环境变量注入，绝不写进仓库 */
    private String apiKey = "";

    /** 模型名 */
    private String model = "deepseek-chat";

    /** 连接超时（秒）：连不上要快速失败，不能把导师的提交按钮卡住 */
    private int connectTimeoutSeconds = 3;

    /** 读超时（秒）：超过即放弃，回落规则实现 */
    private int readTimeoutSeconds = 10;

    /** 采样温度：摘要场景要稳定，不要创造性 */
    private double temperature = 0.2;

    /** 送入模型的正文上限（字符），超出截断。点评通常 30~300 字，1200 已足够 */
    private int maxInputChars = 1200;

    /** 模型输出上限（字符），超出判定为不合规，回落规则实现 */
    private int maxOutputChars = 600;
}
