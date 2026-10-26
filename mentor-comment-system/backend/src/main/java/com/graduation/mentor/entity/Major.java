package com.graduation.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 专业 */
@Data
@TableName("major")
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String college;
    private String name;
    private String code;
}
