package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统配置键值对。
 *
 * <p>只存运行期需要改、且改动后不应重启的配置。当前仅 AI 摘要相关四项 （enabled / base-url / model / api-key）。静态配置仍然写在
 * application.yml， 不往这张表里塞——一张"什么都装"的配置表会变成第二个 application.yml， 而且没人知道哪个值才是生效的那个。
 */
@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 ai.api-key */
    private String configKey;

    /** 配置值。api-key 为明文，但任何接口都不回传明文 */
    private String configValue;

    /** 最后修改人（sys_user.id） */
    private Long updatedBy;

    private LocalDateTime updatedAt;
}
