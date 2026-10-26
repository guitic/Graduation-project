package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 导师—学生指导关系。
 *
 * <p>这是全部数据权限的判定依据：导师只能对本表内存在的学生提交/查询点评。
 */
@Data
@TableName("teacher_student")
public class TeacherStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;
    private Long studentId;
    private String relationType;
    private String startTerm;
    private Integer status;
    private LocalDateTime createdAt;
}
