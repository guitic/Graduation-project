# 基于 Web 的学业导师智能点评分析系统

面向高校学业指导场景，规范导师点评的 **提交 → 审核 → 发布 → 查看 → 统计** 闭环，并对点评文本做**情感倾向分析 + 词频词云 + 统计报表**。

- 前端：Vue 3 + Vite + Element Plus + ECharts（含 echarts-wordcloud）
- 后端：Spring Boot 3.2.5 + MyBatis-Plus + Sa-Token + HanLP（分词）
- 情感分析：**情感词典法**（正向/负向/否定/程度词），可解释、可复现
- 数据库：MySQL 8（默认） / H2 内存库（`demo` 演示模式，零依赖）

---

## 一、快速启动（演示模式，零依赖）

> 演示模式使用 H2 内存库，**无需安装 MySQL**，启动即自动建表 + 生成种子数据。

### 1. 启动后端（端口 8090）

```bash
cd mentor-comment-system/backend
mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

启动成功的日志会包含：

```
种子数据完成：账号 17 个，学生 12 名，点评 已发布56 / 待审核8 / 退回4 / 驳回2
初始分析完成：{...}
Tomcat started on port(s): 8090
```

### 2. 启动前端（端口 5173）

```bash
cd mentor-comment-system/frontend
npm install
npm run dev
```

浏览器打开 **http://127.0.0.1:5173**，前端已把 `/api` 代理到 `http://127.0.0.1:8090`。

### 3. 切换 MySQL 模式（可选）

1. 建库：`CREATE DATABASE mentor_comment DEFAULT CHARSET utf8mb4;`
2. 设置数据库账号环境变量（**仓库中不含密码**）：
   ```bash
   export DB_USERNAME=root
   export DB_PASSWORD=你的MySQL密码
   ```
   连接串在 `backend/src/main/resources/application.yml`，可用 `DB_USERNAME` / `DB_PASSWORD` 覆盖。
3. **首次建表要手动执行**（默认 profile 不会自动建表，原因见下）：
   ```bash
   mysql -u root -p mentor_comment < sql/schema.sql
   ```
4. 不带 `demo` profile 启动：`mvn -s ../maven-settings.xml spring-boot:run`

> **为什么 MySQL 模式不自动建表**：`schema.sql` 是「先 drop 再 create」的重置脚本。
> 若交给 `spring.sql.init.mode=always` 自动执行，每次重启都会清空业务数据——而且不报错，只是数据没了。
> 所以默认 profile 设为 `mode: never`，只有 demo（H2 内存库）才在启动时重建。
>
> **种子数据默认关闭**：`DemoDataInitializer` 与登录页演示账号都受 `app.demo-seed.enabled` 控制，
> 该值只在 `demo` profile 为 `true`。MySQL 模式如需演示数据，显式加 `--app.demo-seed.enabled=true`。


### 4. 常见问题

- **启动报 `Port xxxxx was already in use`（如 59616）**：说明运行环境注入了 `SERVER__PORT` 环境变量，Spring Boot 会把它当成 `server.port`。用 `SERVER__PORT=8090` 前缀覆盖即可：
  ```bash
  SERVER__PORT=8090 mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo
  ```
- **建表报 `Column "action_type"/"round" not found`**：`backend/src/main/resources/db/schema.sql` 与 `sql/schema.sql` 不同步。执行 `cp sql/schema.sql backend/src/main/resources/db/schema.sql` 后重启。
- **登录页能开但点登录无反应**：先确认后端 `/api/health` 返回 `UP`，前端代理目标是 8090。

---

## 二、演示账号（统一密码 `123456`）

| 角色 | 用户名 | 姓名 | 说明 |
| --- | --- | --- | --- |
| 教务管理员 | `admin` | 王主任 | 审核、发布/撤回、统计报表、师生管理 |
| 教务管理员 | `admin02` | 李教务 | 同上 |
| 学业导师 | `teacher01` | 张明远 | 指导计科2201（6 人），种子含待审/退回/驳回 |
| 学业导师 | `teacher02` | 李静 | 指导计科2205（2 人） |
| 学业导师 | `teacher03` | 王海涛 | 指导计科2207、2208 |
| 学生 | `student01` | 王小明 | 计科2201，可查看本人已发布点评 |

登录页提供演示账号快捷 chip，点击即自动填充。

---

## 三、种子数据（专为演示门槛而设计）

| 班级 | 已发布条数 | 分布 | 演示意图 |
| --- | --- | --- | --- |
| 计科2201 | 40 | 5 个月 8/11/7/9/5 | 主班：数据健康时的正常形态，每月均 ≥5 |
| 计科2205 | 5 | 单月 | 阈值点：恰好放行（n=5） |
| 计科2207 | 4 | 单月 | 下边界：三图全空（n<5） |
| 计科2208 | 7 | 6 + 1 | 桶级门禁探针：前月可下钻、后月 1 条禁用 |

另有 **待审核 8 / 退回修改 4 / 已驳回 2**（均为 teacher01 名下，便于导师端演示各状态）。

---

## 四、5 分钟演示脚本

1. **登录**：`admin / 123456` → 进入统计报表。
2. **看口径**：每张图下方都有「口径行」，说明算的是什么、门槛是多少。
3. **验门槛**：班级筛选切到「计科2207」→ 情感分布、词云变空态；切到「计科2205」→ 恰好放行。
4. **验桶级门禁**：切回「全部」或「计科2208」，点趋势图 4 月柱子 → 该月样本 1 条，下钻被禁用（整体充足 ≠ 单个桶充足）。
5. **审核闭环**：审核队列 → 选一条 → 分别演示「通过发布 / 仅通过 / 退回 / 驳回」（意见字数门槛 5/10/10/20）。
6. **双维度**：发布管理 → 待发布 tab 发布；已发布 tab 撤回（≥10 字理由）→ 学生端立即不可见。
7. **可解释性**：审核详情抽屉展示情感命中词、权重、是否被否定。
8. **学生端**：`student01 / 123456` → 只能看到本人已发布点评的 5 个字段（无审核意见、无情感标签）。

---

## 五、关键设计（答辩要点）

1. **「审核通过」≠「已发布」**：用 `status` + `published_at` 两个正交维度表达，撤回可逆（`withdrawn_at`）。合并成一个状态会导致「撤回」无法实现。
2. **单一统计口径**：所有报表只经 `CommentScope.published()` 一个入口，杜绝多处各算各的。
3. **隐私门槛 k=5**：单一配置 `analysis.min-sample`，同时作用于饼图 / 趋势 / 词云 / 下钻；且趋势下钻按**桶级**样本量判定。
4. **可解释**：词典法给出命中词、权重、否定，可逐条复核；结果标注方法版本。
5. **结构约束优于口头约定**：学生端 DTO 不含审核意见与情感标签，从数据层就下不来。
6. **实时聚合、无缓存表**：词频实时计算，避免缓存与业务状态不一致且不报错。

---

## 六、目录结构

```
mentor-comment-system/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/graduation/mentor/
│   │   ├── common/          # R / PageResult / CommentScope / CommentStatus / 异常
│   │   ├── config/          # DemoDataInitializer / MinSampleProperties
│   │   ├── controller/      # Auth / Comment / Audit / Report / Person / Student
│   │   ├── dto/             # AuthDtos / CommentDtos / StatDtos
│   │   ├── entity/ mapper/  # 实体与 MyBatis-Plus Mapper
│   │   └── service/         # 业务 + analysis/（词典、分词、隐私过滤）
│   └── src/main/resources/
│       ├── application.yml          # 默认 MySQL
│       ├── application-demo.yml     # demo：H2
│       ├── db/schema.sql            # 建表
│       └── lexicon/                 # 情感词典 / 停用词
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── api/             # axios 封装 + 各模块接口
│       ├── components/      # StatusTag / SentimentTag / AuditTimeline / GateEmpty / dashboard/*
│       ├── composables/     # useChart（ECharts 生命周期）
│       ├── layouts/         # AppLayout（角色菜单）
│       ├── views/           # Login / Dashboard / teacher / admin / student
│       ├── router/ store/   # 路由守卫 + Pinia 登录态
│       └── utils/           # 状态/情感/动作映射 + limits（门槛读取点）
├── sql/schema.sql           # 数据库脚本（与 resources/db 一致）
└── maven-settings.xml       # 阿里云镜像（避免直连超时）
```

---

## 七、接口概览

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login` · `GET /api/auth/me` · `GET /api/demo/accounts` | 登录/当前用户/演示账号（非 demo 返回空） |
| 元数据 | `GET /api/meta/limits` | 门槛单一来源：审核意见字数 + 隐私样本量，前端文案由此渲染 |
| 点评 | `/api/comment/page` · `/admin-page` · `/student-page` · `/{id}` · `POST/PUT/DELETE` · `/{id}/submit` | 三端列表 + 增改删 + 提交 |
| 审核 | `/api/audit/pending` · `/unpublished` · `/published` · `/{id}/approve|return|reject|publish|unpublish` · `/{id}/history` | 审核与发布/撤回 |
| 报表 | `/api/report/dashboard` · `/sentiment` · `/trend` · `/audit-stat` · `/wordcloud` · `/word-detail` · `/trend-point` · `/comment/{id}/sentiment` | 统计与下钻 |
| 人员 | `/api/person/teachers` · `/my-students` · `/students` · `/clazz` · `/terms` · `/bind` · `/bind/{id}` | 师生关系 |
| 学生 | `/api/student-comment/page` · `/{id}` | 学生仅 5 字段 |

> 所有响应统一为 `{ code, msg, data }`，`code=0` 表示成功。鉴权令牌放在请求头 `satoken`。

---

## 八、质量门禁

```bash
cd backend  && mvn -s ../maven-settings.xml test    # Checkstyle 0 违规 + 32 个测试
cd ../frontend && npm run check                     # ESLint 0 error + Prettier 一致 + 构建通过
cd .. && sh scripts/check-schema-sync.sh            # 两份 schema 同源
```

| 测试类 | 数量 | 守住什么 |
| --- | --- | --- |
| `AuditStateMachineTest` | 21 | 七个事件类型、字数门槛分档、条件化 UPDATE（影响 0 行 → 409）、发布才触发分析 |
| `AuditPermissionTest` | 10 | 学生/导师/管理员 × 本人/他人 × 已发布/未发布；history 越权回归守卫 |
| `SentimentEvalTest` | 1 | 情感分析准确率 82.7% / Kappa 0.74（固定验证集，防方法退化） |

