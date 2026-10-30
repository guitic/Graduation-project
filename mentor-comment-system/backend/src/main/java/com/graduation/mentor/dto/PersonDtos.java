package com.graduation.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 师生档案维护 DTO */
public class PersonDtos {

    /** 导师新增 / 修改入参 */
    @Data
    public static class TeacherReq {

        @NotBlank(message = "姓名不能为空")
        @Size(max = 20, message = "姓名过长")
        private String name;

        /** 工号，同时作为登录用户名 */
        @NotBlank(message = "工号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,20}$", message = "工号仅支持字母数字下划线，3~20 位")
        private String teacherNo;

        @Size(max = 20, message = "职称过长")
        private String title;

        @Size(max = 50, message = "学院名过长")
        private String college;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        @Pattern(regexp = "^$|^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
        private String email;

        /** 1 启用 / 0 停用，新增时省略默认启用 */
        private Integer status;
    }

    /** 学生新增 / 修改入参 */
    @Data
    public static class StudentReq {

        @NotBlank(message = "姓名不能为空")
        @Size(max = 20, message = "姓名过长")
        private String name;

        /** 学号，同时作为登录用户名 */
        @NotBlank(message = "学号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,20}$", message = "学号仅支持字母数字下划线，3~20 位")
        private String studentNo;

        @NotNull(message = "班级不能为空") private Long clazzId;

        /** 1 男 / 2 女 / null 未填 */
        private Integer gender;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        @Pattern(regexp = "^$|^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
        private String email;

        private Integer status;
    }

    /**
     * 新建结果。
     *
     * <p>{@code initialPassword} **只在新建时返回一次**，之后任何接口都不再给出。 管理员漏记只能走重置，不能让"查初始密码"成为一个可反复调用的接口。
     */
    @Data
    public static class Created {
        private Long id;
        private String username;
        private String initialPassword;
    }
}
