-- ============================================================================
-- 基于 Web 的学业导师智能点评分析系统 —— 数据库结构
-- DBMS : MySQL 8.0（同时兼容 H2 MODE=MySQL，便于 demo profile 零依赖启动）
-- 编码 : utf8mb4 / utf8mb4_general_ci
-- 说明 : 为避免 SQL 保留字冲突，`user` 落地为 `sys_user`，`class` 落地为 `clazz`，
--        其余表名与设计文档保持一致。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 账号与角色
-- ---------------------------------------------------------------------------
drop table if exists sys_user;
create table sys_user (
    id            bigint       not null auto_increment,
    username      varchar(50)  not null,
    password      varchar(100) not null,
    real_name     varchar(50)  not null,
    role          varchar(20)  not null,
    status        tinyint      not null default 1,
    phone         varchar(20),
    email         varchar(100),
    last_login_at datetime,
    created_at    datetime     not null,
    updated_at    datetime,
    primary key (id)
);
create unique index uk_user_username on sys_user (username);

-- ---------------------------------------------------------------------------
-- 2. 组织：专业 / 班级
-- ---------------------------------------------------------------------------
drop table if exists major;
create table major (
    id      bigint      not null auto_increment,
    college varchar(64) not null,
    name    varchar(64) not null,
    code    varchar(32),
    primary key (id)
);

drop table if exists clazz;
create table clazz (
    id       bigint      not null auto_increment,
    major_id bigint      not null,
    name     varchar(64) not null,
    grade    int,
    primary key (id)
);
create index idx_clazz_major on clazz (major_id);

-- ---------------------------------------------------------------------------
-- 3. 人员档案：导师 / 学生
-- ---------------------------------------------------------------------------
drop table if exists teacher;
create table teacher (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    teacher_no varchar(32) not null,
    name       varchar(50) not null,
    title      varchar(32),
    college    varchar(64),
    phone      varchar(20),
    email      varchar(100),
    status     tinyint     not null default 1,
    primary key (id)
);
create unique index uk_teacher_no on teacher (teacher_no);
create index idx_teacher_user on teacher (user_id);

drop table if exists student;
create table student (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    student_no varchar(32) not null,
    name       varchar(50) not null,
    clazz_id   bigint      not null,
    gender     tinyint,
    phone      varchar(20),
    email      varchar(100),
    status     tinyint     not null default 1,
    primary key (id)
);
create unique index uk_student_no on student (student_no);
create index idx_student_user on student (user_id);
create index idx_student_clazz on student (clazz_id);

-- ---------------------------------------------------------------------------
-- 4. 指导关系（导师 <-> 学生，多对多）
--    这是全部数据权限的判定依据：导师只能操作该表中存在的学生
-- ---------------------------------------------------------------------------
drop table if exists teacher_student;
create table teacher_student (
    id            bigint      not null auto_increment,
    teacher_id    bigint      not null,
    student_id    bigint      not null,
    relation_type varchar(20) not null default '学业导师',
    start_term    varchar(20),
    status        tinyint     not null default 1,
    created_at    datetime    not null,
    primary key (id)
);
create unique index uk_ts_pair on teacher_student (teacher_id, student_id);
create index idx_ts_student on teacher_student (student_id);

-- ---------------------------------------------------------------------------
-- 5. 点评主体
--    status     : DRAFT / PENDING / APPROVED / RETURNED / REJECTED
--    published_at: null = 通过但未发布；非 null = 已发布（学生可见 + 纳入统计）
-- ---------------------------------------------------------------------------
drop table if exists comment;
create table comment (
    id          bigint      not null auto_increment,
    teacher_id  bigint      not null,
    student_id  bigint      not null,
    term        varchar(20) not null,
    topic       varchar(50) not null,
    content     text        not null,
    status      varchar(20) not null,
    submit_at   datetime,
    published_at datetime,
    withdrawn_at datetime,
    version     int         not null default 1,
    created_at  datetime    not null,
    updated_at  datetime,
    primary key (id)
);
create index idx_comment_teacher on comment (teacher_id);
create index idx_comment_student on comment (student_id);
create index idx_comment_status on comment (status);
create index idx_comment_published on comment (published_at);
create index idx_comment_term on comment (term);

-- ---------------------------------------------------------------------------
-- 6. 审核记录（一次审核一条，保留历史，支持追溯与多次退回）
-- ---------------------------------------------------------------------------
drop table if exists comment_audit;
create table comment_audit (
    id             bigint       not null auto_increment,
    comment_id     bigint       not null,
    auditor_id     bigint       not null,
    auditor_name   varchar(50)  not null,
    result         varchar(20)  not null,
    action_type    varchar(32),
    opinion        varchar(500),
    before_content text,
    round          int          not null default 1,
    audit_at       datetime     not null,
    primary key (id)
);
create index idx_audit_comment on comment_audit (comment_id);

-- ---------------------------------------------------------------------------
-- 7. 情感分析结果（一条点评一条，重算时覆盖并升版本）
--    content_hash: 被分析正文的 SHA-256。与 comment.version 无关，
--    用于"文本未变则复用、文本已变则重算"的通用判据。
--    注意：本表可能存在"点评未发布但已有分析结果"的行（撤回发布所致），
--    这是合法状态而非脏数据，由报表层 CommentScope.published() 统一隔离。
-- ---------------------------------------------------------------------------
drop table if exists comment_sentiment;
create table comment_sentiment (
    id             bigint       not null auto_increment,
    comment_id     bigint       not null,
    content_hash   varchar(64)  not null,
    text_version   int          not null default 1,
    label          varchar(16)  not null,
    score          decimal(10,4) not null,
    confidence     decimal(5,4) not null,
    pos_hits       int          not null default 0,
    neg_hits       int          not null default 0,
    low_confidence tinyint      not null default 0,
    method         varchar(32)  not null,
    method_version varchar(16)  not null,
    analyzed_at    datetime     not null,
    primary key (id)
);
create unique index uk_sentiment_comment on comment_sentiment (comment_id);
create index idx_sentiment_label on comment_sentiment (label);

-- ---------------------------------------------------------------------------
-- 8. 词频统计
--    设计决策：**不建词频缓存表**。
--    理由：派生表没有失效机制，一旦点评被退回改稿或撤回，缓存就与业务状态不同步，
--    而且这类错误不报错、图看起来完全正常。种子规模 68 条、真实规模数百条，
--    实时分词 + 聚合是毫秒级，缓存买不到任何东西。
--    源文档 §5 原文本就写的是"可选的词频统计缓存"，不建表不违背设计。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 9. 操作日志（提交 / 审核 / 发布 / 撤回 等关键动作留痕）
-- ---------------------------------------------------------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
    id          bigint       not null auto_increment,
    user_id     bigint,
    username    varchar(50),
    role        varchar(20),
    action      varchar(50)  not null,
    target_type varchar(32),
    target_id   bigint,
    detail      varchar(500),
    ip          varchar(64),
    created_at  datetime     not null,
    primary key (id)
);
create index idx_log_target on sys_oper_log (target_type, target_id);
