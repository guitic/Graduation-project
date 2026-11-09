package com.graduation.mentor.dto;

import lombok.Data;

/** 操作日志查询 DTO */
public class OperLogDtos {

    /**
     * 查询条件。
     *
     * <p>{@code page}/{@code size} 给了默认值：日志页首次打开必然带分页参数， 但如果调用方漏传，默认值能让接口返回第 1 页而不是 {@code limit 0}
     * 的空结果——后者看起来像"系统没有日志"，是最容易误判的故障形态。
     */
    @Data
    public static class Query {
        /** 动作名，模糊匹配（如「审核」「发布」） */
        private String action;

        /** 操作人登录名，模糊匹配 */
        private String username;

        /** 角色精确匹配：ADMIN / TEACHER / STUDENT */
        private String role;

        private String startDate;
        private String endDate;

        private long page = 1;
        private long size = 20;
    }
}
