package com.graduation.mentor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 基于 Web 的学业导师智能点评分析系统 —— 后端启动类 */
@SpringBootApplication
@MapperScan("com.graduation.mentor.mapper")
public class MentorCommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentorCommentApplication.class, args);
    }
}
