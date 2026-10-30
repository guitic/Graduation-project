package com.graduation.mentor.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.config.AiProperties;
import com.graduation.mentor.dto.AiConfigDtos;
import com.graduation.mentor.entity.SysConfig;
import com.graduation.mentor.mapper.SysConfigMapper;
import com.graduation.mentor.service.OperLogService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * AI 配置的读写入口（教务后台填写，导师端使用）。
 *
 * <p><b>为什么要做成"运行期可改"而不是改配置文件</b>：改 yml 需要重启、需要登录服务器、 而且密钥会留在文件里。教务在页面上填一次，保存即生效——导师下一次点按钮就用上新 Key。
 *
 * <p><b>优先级</b>：数据库配置 &gt; 环境变量 &gt; 空。 这样三种部署形态都能覆盖：校内给教务自助填；CI/容器用环境变量；本地开发不配就走规则实现。
 *
 * <p><b>缓存策略</b>：整份配置读一次缓存在内存，写操作直接失效。 版本号取 {@code sys_config.updated_at} 的毫秒时间戳，{@link
 * LlmSummarySuggester} 据此判断 ChatClient 是否需要重建——比"每次请求查库"省一次 IO，比"定时刷新"少一个线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigStore {

    static final String K_ENABLED = "ai.enabled";

    static final String K_BASE_URL = "ai.base-url";

    static final String K_MODEL = "ai.model";

    static final String K_API_KEY = "ai.api-key";

    private final SysConfigMapper configMapper;
    private final AiProperties props;
    private final AuthContext authContext;
    private final OperLogService operLogService;

    private volatile AiRuntimeConfig cache;

    // ------------------------------------------------------------------ 读

    /** 当前生效配置（带缓存）。任何业务代码都从这里取，不要直接读 AiProperties */
    public AiRuntimeConfig current() {
        AiRuntimeConfig c = cache;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (cache == null) {
                cache = load();
            }
            return cache;
        }
    }

    /** 教务后台视图：不含明文 Key */
    public AiConfigDtos.View view() {
        requireAdmin();
        AiRuntimeConfig c = current();
        AiConfigDtos.View v = new AiConfigDtos.View();
        v.setEnabled(c.enabled());
        v.setBaseUrl(c.baseUrl());
        v.setModel(c.model());
        v.setKeyConfigured(c.apiKey() != null && !c.apiKey().isBlank());
        v.setKeyMasked(c.maskedKey());
        v.setSource(c.source());
        v.setSourceLabel(sourceLabel(c.source()));
        v.setUsable(c.usable());
        return v;
    }

    /**
     * 拉取该账号可用的模型列表（OpenAI 兼容的 {@code GET {baseUrl}/v1/models}）。
     *
     * <p><b>为什么需要这个接口</b>：模型名是最容易错的一项——厂商改名、账号没开通某个模型、 文档里写的是展示名而接口要的是 ID。让教务"点一下看到能选什么"，
     * 比让他对着文档猜、猜错后看一个 404 强得多。
     *
     * <p>失败时把底层错误翻成人话（见 {@link AiErrorMessages}），不抛原始异常。
     */
    public List<String> listModels() {
        requireAdmin();
        AiRuntimeConfig cfg = current();
        if (!cfg.usable()) {
            throw new BizException(400, "请先保存并启用 API Key，再拉取模型列表");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(props.getReadTimeoutSeconds() * 1000);
        String url = cfg.baseUrl() + "/v1/models";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body =
                    RestClient.builder()
                            .requestFactory(factory)
                            .build()
                            .get()
                            .uri(url)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.apiKey())
                            .retrieve()
                            .body(Map.class);
            return parseModelIds(body);
        } catch (Exception e) {
            // 捕 Exception 而不只是 RuntimeException：RestClient 的错误类型在
            // 不同 HTTP 客户端下不一致，漏一种就会变成 500「系统繁忙」，
            // 而这恰恰是教务最需要看到具体原因的地方。
            log.warn("拉取模型列表失败 {}：{}", url, e.toString());
            throw new BizException(400, "拉取模型列表失败：" + AiErrorMessages.describe(e));
        }
    }

    /** 从 OpenAI 格式的 {@code {"data":[{"id":"..."}]}} 里取模型 ID */
    private static List<String> parseModelIds(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof List<?> data)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object o : data) {
            if (o instanceof Map<?, ?> m) {
                Object id = m.get("id");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private AiRuntimeConfig load() {
        Map<String, String> db = new HashMap<>();
        long version = 0;
        try {
            List<SysConfig> rows =
                    configMapper.selectList(
                            new LambdaQueryWrapper<SysConfig>()
                                    .likeRight(SysConfig::getConfigKey, "ai."));
            for (SysConfig r : rows) {
                db.put(r.getConfigKey(), r.getConfigValue());
                if (r.getUpdatedAt() != null) {
                    long t =
                            r.getUpdatedAt()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli();
                    version = Math.max(version, t);
                }
            }
        } catch (RuntimeException e) {
            // 配置表读不到不能让 AI 能力整体崩掉：回落环境变量即可，业务无感
            log.warn("读取 sys_config 失败，回落环境变量：{}", e.toString());
        }

        String dbKey = db.get(K_API_KEY);
        boolean hasDbKey = dbKey != null && !dbKey.isBlank();
        boolean hasEnvKey = props.getApiKey() != null && !props.getApiKey().isBlank();

        String apiKey = hasDbKey ? dbKey : (hasEnvKey ? props.getApiKey() : "");
        String source =
                hasDbKey
                        ? AiRuntimeConfig.SOURCE_DB
                        : (hasEnvKey ? AiRuntimeConfig.SOURCE_ENV : AiRuntimeConfig.SOURCE_NONE);

        boolean enabled =
                db.containsKey(K_ENABLED)
                        ? Boolean.parseBoolean(db.get(K_ENABLED))
                        : props.isEnabled();
        String baseUrl = blankToDefault(db.get(K_BASE_URL), props.getBaseUrl());
        String model = blankToDefault(db.get(K_MODEL), props.getModel());

        return new AiRuntimeConfig(enabled, baseUrl, apiKey, model, version, source);
    }

    // ------------------------------------------------------------------ 写

    /**
     * 更新配置。字段语义：null 不改，空串清除（回落环境变量）。
     *
     * <p>写完之后立刻失效缓存，导师端下一次调用就会重建 ChatClient—— 这就是"教务填的 Key 通用于所有导师"的实现点。
     */
    @Transactional
    public void update(AiConfigDtos.Req req) {
        requireAdmin();
        Long uid = authContext.userId();
        LocalDateTime now = LocalDateTime.now();

        if (req.getEnabled() != null) {
            upsert(K_ENABLED, String.valueOf(req.getEnabled()), uid, now);
        }
        if (req.getBaseUrl() != null) {
            upsert(K_BASE_URL, emptyToNull(req.getBaseUrl()), uid, now);
        }
        if (req.getModel() != null) {
            upsert(K_MODEL, emptyToNull(req.getModel()), uid, now);
        }
        if (req.getApiKey() != null) {
            upsert(K_API_KEY, emptyToNull(req.getApiKey()), uid, now);
        }

        cache = null;
        // 日志只记"改了哪几项"，绝不记 Key 内容
        StringBuilder detail = new StringBuilder("更新 AI 配置：");
        if (req.getEnabled() != null) {
            detail.append("enabled=").append(req.getEnabled()).append(' ');
        }
        if (req.getApiKey() != null) {
            detail.append(req.getApiKey().isEmpty() ? "apiKey=清除 " : "apiKey=重设 ");
        }
        if (req.getModel() != null) {
            detail.append("model=").append(req.getModel()).append(' ');
        }
        operLogService.record("UPDATE_AI_CONFIG", "SYS_CONFIG", null, detail.toString().trim());
    }

    private void upsert(String key, String value, Long uid, LocalDateTime now) {
        SysConfig exist =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>()
                                .eq(SysConfig::getConfigKey, key)
                                .last("limit 1"));
        if (value == null) {
            if (exist != null) {
                configMapper.deleteById(exist.getId());
            }
            return;
        }
        if (exist == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            c.setUpdatedBy(uid);
            c.setUpdatedAt(now);
            configMapper.insert(c);
        } else {
            exist.setConfigValue(value);
            exist.setUpdatedBy(uid);
            exist.setUpdatedAt(now);
            configMapper.updateById(exist);
        }
    }

    // ------------------------------------------------------------------ 工具

    private void requireAdmin() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看或修改 AI 配置");
        }
    }

    private static String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String sourceLabel(String source) {
        return switch (source) {
            case AiRuntimeConfig.SOURCE_DB -> "教务后台";
            case AiRuntimeConfig.SOURCE_ENV -> "环境变量";
            default -> "未配置";
        };
    }
}
