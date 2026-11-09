package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学生对一条已发布点评的反馈。
 *
 * <p><b>不是评分</b>：{@code helpful} 描述的是「这条点评对我的帮助」，不是「导师好不好」。 它不参与情感统计、不影响点评状态机、不进任何导师考核报表。
 */
@Data
@TableName("comment_feedback")
public class CommentFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    /**
     * 冗余存学生ID，不靠 join comment 取。
     *
     * <p>写入时强制取自点评本身（不接受前端传值），因此不会与 comment.student_id 不一致； 冗余的目的是让「按学生查反馈」「判断是否已反馈」不必回表。
     */
    private Long studentId;

    /** 1 = 有帮助；0 = 帮助不大 */
    private Integer helpful;

    /** 可选补充说明，最多 500 字 */
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
