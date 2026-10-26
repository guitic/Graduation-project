package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 情感分析结果（一条点评一行，重算时覆盖并升级 method_version） */
@Data
@TableName("comment_sentiment")
public class CommentSentiment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    /** 被分析正文的 SHA-256，用于"文本未变则复用"的判据 */
    private String contentHash;

    /** 被分析正文的版本号，必须等于 comment.version（P0-05 断言） */
    private Integer textVersion;

    /** POSITIVE / NEUTRAL / NEGATIVE */
    private String label;

    private BigDecimal score;
    private BigDecimal confidence;
    private Integer posHits;
    private Integer negHits;

    /** 1 = 待人工复核（得分落在阈值灰区） */
    private Integer lowConfidence;

    private String method;
    private String methodVersion;
    private LocalDateTime analyzedAt;
}
