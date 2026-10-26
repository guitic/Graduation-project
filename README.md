# 基于 Web 的学业导师智能点评分析系统

> 2027 届本科毕业设计 · 软件工程

面向高校学业指导场景，规范导师点评的 **提交 → 审核 → 发布 → 查看 → 统计** 闭环，并对点评文本做**情感倾向分析 + 词频词云 + 统计报表**。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 + Vite + Element Plus + ECharts（echarts-wordcloud） |
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus + Sa-Token + HanLP |
| 分析 | 情感词典法（正向 / 负向 / 否定 / 程度词），可解释、可复现 |
| 数据库 | MySQL 8（默认）/ H2 内存库（`demo` 演示模式，零依赖） |

## 目录结构

```
.
├── mentor-comment-system/          可运行工程
│   ├── backend/                    Spring Boot 后端（端口 8090）
│   ├── frontend/                   Vue 3 前端（端口 5173）
│   ├── sql/schema.sql              建表脚本
│   ├── scripts/                    校验脚本
│   └── README.md                   启动 / 演示账号 / 排错说明
├── deliverables/                   交付文档
│   ├── 设计文档.md
│   ├── 测试报告.md
│   ├── 情感分析评估报告.md + predictions.tsv
│   ├── 答辩要点.md
│   └── 代码审查/                    审查标准、流程、基线报告、修复记录
├── 技术框架概览.md                  架构与技术选型论证
├── 基于Web的学业导师智能点评分析系统-设计思路.md
└── 2027届毕业设计题目及说明-软工、软工（专升本）.xlsx
```

## 快速开始

```bash
# 后端（演示模式，H2 内存库，无需 MySQL）
cd mentor-comment-system/backend
mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo

# 前端
cd mentor-comment-system/frontend
npm install && npm run dev
```

打开 http://127.0.0.1:5173 ，演示账号 `admin / 123456`（教务）、`teacher01`（导师）、`student01`（学生）。

> 切换 MySQL 模式需先设置环境变量 `DB_USERNAME` / `DB_PASSWORD`，配置见 `backend/src/main/resources/application.yml`。
> 详细说明见 [mentor-comment-system/README.md](mentor-comment-system/README.md)。
