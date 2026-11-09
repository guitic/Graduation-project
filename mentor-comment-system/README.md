# 基于 Web 的学业导师智能点评分析系统

面向高校学业指导场景，规范导师点评的 **提交 → 审核 → 发布 → 查看 → 统计** 闭环，并对点评文本做**情感倾向分析 + 词频词云 + 统计报表**。

- 前端：Vue 3 + Vite + Element Plus + ECharts（含 echarts-wordcloud）
- 后端：Spring Boot 3.2.5 + MyBatis-Plus + Sa-Token + HanLP（分词）
- 情感分析：**情感词典法**（正向/负向/否定/程度词），可解释、可复现
- 学生反馈：**学生对点评的回应**（有帮助 / 帮助不大 + 补充说明），可修改；导师只读可见，**不进统计、不改状态、不构成评分**
- AI 预审：**规则引擎**（9 条规则 + 风险分 + 意见草稿），只给建议、不改状态
- AI 摘要建议：**Spring AI（ChatClient）+ 规则实现双通道**，默认关闭；关闭 / 断网 / 无 Key / 模型超时时自动回落规则实现，接口结构不变
- AI 小助手：**全局悬浮对话面板**，三角色可见（学生=学习助手 / 导师=写作助手 / 教务=口径助手）；Key 复用教务端配置；按用户限流（12 次/分、150 次/日）
- 主题：**亮 / 暗双主题**，默认跟随系统，顶栏一键切换并持久化；状态色、情感色、图表配色各有一套暗色值（不是简单反色）
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

### 5. 启用大模型摘要（可选）

默认走规则实现，**不配任何东西也能完整演示**。有两种开启方式，任选其一：

**方式 A：教务后台填写（推荐，不用重启）**

`admin` 登录 → 侧边栏「系统设置」→ AI 配置 → 选服务商（DeepSeek / 通义 / 自定义）→ 填 API Key → 保存 → 点「测试连接」。
保存即生效，**全体导师共用这一份 Key**，不需要重启、不需要改环境变量。

**方式 B：环境变量（适合 CI / 容器）**

```bash
# Windows PowerShell
$env:AI_ENABLED="true"; $env:AI_API_KEY="sk-xxx"; $env:AI_MODEL="deepseek-chat"
mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_ENABLED` | `false` | 总开关 |
| `AI_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容端点（DeepSeek / 商汤日日新 / 通义 / 自建 vLLM） |
| `AI_API_KEY` | 空 | 密钥，只从环境变量读，不写进仓库 |
| `AI_MODEL` | `deepseek-chat` | 模型名 |

**各厂商填法**（端点都填**不带 `/v1`** 的部分，见下方说明）：

| 服务商 | 端点 | 模型示例 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| 商汤日日新 | `https://token.sensenova.cn` | `sensenova-6.8-flash-lite` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode` | `qwen-plus` |
| 自建 vLLM / Ollama | `http://内网IP:端口` | 部署时的模型名 |

> **模型名不要猜**。厂商文档里的展示名常与接口 ID 不一致（商汤是 `sensenova-6.8-flash-lite`，
> 不是 `sense-nova-...`），填错只会得到 `404 model is not found`。
> 「系统设置 → AI 配置」页的模型名是**可搜索下拉**，先保存 Key，再点旁边的「拉取」，
> 会调用 `GET /v1/models` 列出该账号实际可用的模型。

> **关于 `/v1`（最容易踩的坑）**：厂商文档给的 `base_url` 通常自带 `/v1`
> （如 `https://token.sensenova.cn/v1`），而 Spring AI 会自动再拼一次 `/v1/chat/completions`。
> 照抄会变成 `/v1/v1/chat/completions` → 404，而报错里只有一串 URL，看不出原因。
> 本项目在 `AiRuntimeConfig` 构造时**统一剥掉结尾的 `/v1`**，所以照抄文档也不会错；
> 保存后页面上显示的是实际生效值（已去掉 `/v1`）。

**优先级**：教务后台（`sys_config` 表） > 环境变量 > 空。清除后台密钥后自动回落环境变量。

`GET /api/ai/status` 可查当前是否可用；不可用时返回原因，导师端按钮文案自动切换为「生成摘要建议」。

> **演示注意**：`demo` profile 用 H2 内存库，**重启后后台填的 Key 会丢失**（环境变量不受影响）。
> 要长期保存请用 MySQL 模式，`sys_config` 表会持久化。
>
> **为什么不用 `spring-ai-starter-model-openai`**：该 starter 的自动配置绑定 Spring Boot 3.4.x（本项目 3.2.5），
> 且无 Key 时会让**整个应用启动失败**——把可选功能做成了启动硬依赖。本项目改用 `spring-ai-openai` +
> `spring-ai-client-chat` 显式装配，`LlmSummarySuggester` 内部懒初始化，构造阶段不连网、不校验 Key。
> 兼容性由 `LlmSummarySuggesterTest` 的五条断言守住（含"改配置后立即生效"）。

### 6. AI 小助手（复用同一份 Key）

登录后右下角有悬浮按钮，点击打开对话面板。**三角色可见，但定位不同**：

| 角色 | 定位 | 能做什么 |
| --- | --- | --- |
| 学生 | 学习助手 | 解释点评里的术语与要求、把建议拆成周计划、说明系统怎么用 |
| 导师 | 写作助手 | 检查点评是否够具体、把笼统表述改成可执行动作、梳理结构 |
| 教务 | 口径助手 | 解释统计口径（已发布口径 / k=5 / 桶级门禁）、起草审核意见 |

**三条边界**（写在系统提示词里，不是靠前端拦）：

1. **场景由后端按登录角色决定**，请求里传 `scene` 也不生效——否则学生能拿到教务口径提示词。
2. **不编造**：学生不能被承诺成绩/评优；导师的点评内容不能被助手代写判断；教务的统计数据不能被编。
3. **不落库**：对话只存在前端内存，刷新即清空。

**限流**：按用户 12 次/分钟、150 次/天（`app.ai.chat.*` 可调）。Key 是全校共用一份配额，没有限流时一个脚本就能刷完。

**没配 Key 时的表现**：助手返回一句说明（`source=DISABLED`），HTTP 仍是成功——助手默认就是关的，
不该表现为接口报错。此时系统其余功能完全不受影响。

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

> **两套密码不要混淆**：上表是**种子账号**的固定密码 `123456`；
> 通过「师生管理」新建的导师/学生账号，初始密码是 `app.person.default-password`（默认 `Mentor@2026`），
> 只在保存成功与重置密码的那一次响应里展示，之后不可再查。

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
5. **审核闭环**：侧边栏「审核与发布」→ 待审核 → 选一条 → 分别演示「通过发布 / 仅通过 / 退回 / 驳回」。
   意见**不限字数**：不想打字就点输入框下方的「常用理由」下拉，选中即填入（同意 / 不同意分组），两个字也能提交。
   （一级菜单只有 4 项：统计报表 / 审核与发布 / 师生管理 / 系统设置。待审核 → 待发布 → 已发布 是同一条流程的三段，做成了页面内二级 tab。）
6. **双维度**：仍在「审核与发布」页，切到待发布 tab 发布；已发布 tab 撤回（理由同样可从常用理由里选）→ 学生端立即不可见。三个 tab 连续走完一条流程，不用来回换菜单。
7. **可解释性**：审核详情抽屉展示情感命中词、权重、是否被否定。
8. **AI 预审**：待审核列表每行有「AI 预审」徽章；打开详情看问题清单（命中片段在正文里高亮）与建议意见，点「填入审核意见」预填草稿——**状态仍要人点按钮才变**。现场造一条 30 字套话点评，预审会直接判「建议退回」。
9. **学生端**：`student01 / 123456` → 只能看到本人已发布点评的 5 个字段（无审核意见、无情感标签）。
10. **导师画像**：`teacher01 / 123456` → 侧边栏「我的画像」→ 情感分布 + 趋势 + 词云。数据只有本人 40 条，不是全院 56 条；页面上没有审核队列统计（那是教务指标）。
11. **AI 摘要建议**：「我的点评」→ 新建点评 → 写 30 字以上正文 → 点「生成摘要建议」→ 展示摘要 + 最多 3 条改进建议。默认显示「规则生成」（黄标）；配了 Key 后显示「模型生成」（蓝标）。降级时卡片底部会写明原因。
12. **AI 配置（跨角色生效）**：`admin` → 侧边栏「系统设置」→ AI 配置 → 填 Key → 保存 → 点「测试连接」看到成功；**不重启**直接切到 `teacher01`，点「生成摘要建议」标签变蓝（模型生成）。再回后台点「清除密钥」→ 导师端立刻退回黄标。
13. **师生档案维护**：「师生管理」→ 新增导师（工号即登录名，保存后弹一次初始密码）→ 用该账号登录成功；再试停用 `teacher01`，会被拦下并报出「还有 12 条未完结点评」，二次确认后才强制停用，停用后该账号登录返回 403。
14. **AI 小助手**：任意角色点右下角悬浮按钮 → 面板按角色给出不同快捷问题（学生是"帮我制定复习计划"，教务是"k=5 是什么意思"）→ 提问后看回复。同一份 Key，三种提示词，互不串味。
15. **导出报表**：`admin` → 统计报表 → 右上「导出报表」→ 下载 xlsx。打开可见两个 sheet：「汇总」（流程指标 + 情感分布 + 口径说明）与「点评明细」。
    **验口径**：明细行数必须等于页面上「已发布」的 KPI 数——两者都只经 `CommentScope.published()`，实测 56 = 56。
    切到「计科2207」再导出 → 返回 400「没有已发布的点评」，而不是给一个只有表头的空文件。
16. **操作日志**：侧边栏「系统设置」→ 操作日志 → 种子数据已留下 74 条痕迹（提交审核 / 审核通过并发布 / 退回修改 / 驳回）。
    按动作、角色、操作人、日期范围筛选；现场做一次审核，日志里立刻多一条。
    **验权限**：用 `student01` 的 token 直接请求 `/api/oper-log/page` → 403，日志不是"前端隐藏菜单"。
17. **修改密码**：任意角色 → 右上角头像 → 修改密码 → 当前密码 + 新密码（8-32 位，含字母和数字），三条规则实时勾选。
    提交成功后**当前会话立即失效**并跳回登录页；旧 token 再请求返回 401，旧密码登录失败，新密码登录成功。
    日志页会多一条「修改密码」记录，操作人为本人。
18. **学生反馈**：`student01` → 我的点评 → 打开详情 → 底部「这条点评对你有帮助吗？」→ 选「有帮助」并写一句补充说明 → 提交。
    提交后立刻变成只读态（标签 + 时间 + 「修改」），列表卡片出现「已反馈」标记；再点「修改」可改成「帮助不大」并清空文本。
    切到 `teacher01` → 我的点评 → 打开同一条 → 详情里出现「学生反馈」区块，**只有只读展示，没有回复框**。
    种子数据给其他学生预置了反馈（约 1/4 是「帮助不大」），唯独 `student01` 那条留空，就是为了现场演示这个流程。

---

## 五、关键设计（答辩要点）

1. **「审核通过」≠「已发布」**：用 `status` + `published_at` 两个正交维度表达，撤回可逆（`withdrawn_at`）。合并成一个状态会导致「撤回」无法实现。
2. **单一统计口径**：所有报表只经 `CommentScope.published()` 一个入口，杜绝多处各算各的。
3. **隐私门槛 k=5**：单一配置 `analysis.min-sample`，同时作用于饼图 / 趋势 / 词云 / 下钻；且趋势下钻按**桶级**样本量判定。
4. **可解释**：词典法给出命中词、权重、否定，可逐条复核；结果标注方法版本。
5. **结构约束优于口头约定**：学生端 DTO 不含审核意见与情感标签，从数据层就下不来。
6. **实时聚合、无缓存表**：词频实时计算，避免缓存与业务状态不一致且不报错。
7. **AI 预审不改状态**：预审只写 `comment_precheck`，`comment.status` 的唯一写入者仍是人工审核动作。规则版本或正文变化才重算（`content_hash + rule_version` 双匹配），与情感分析同一套复用策略。预审表清空、规则回滚、服务不可用，点评流程照常运行。
8. **AI 是可选增强，不是启动依赖**：摘要建议默认关闭；模型超时 10 秒即放弃并回落规则实现，用户拿到的是稍逊措辞的结果而不是错误弹窗。模型输出必须过闸（长度、条数、格式），不合规一律回落——半截 JSON 塞进审核意见比没有建议更糟。
9. **降级实现必须独立可用**：规则版摘要用已有的情感结论 + 预检问题清单拼装，断网 / 无 Key / 限流时表现一致。只在模型可用时才有输出的功能，本质是把系统可用性外包给第三方。
10. **越权在协议层不成立**：导师画像接口的 `teacherId` 由后端从登录态取，请求里传了也不生效；学生端的情感标签与审核意见在 Service 层就置空，不是"前端不显示"。
11. **停用而非删除**：师生档案被点评、审核记录、操作日志引用，物理删除会让历史报表出现"无主点评"。停用导师时若存在未完结点评（草稿/待审核/退回）默认拒绝并报出条数——否则这些点评会永久卡在流程里，而流程规范正是本系统的研究目的。
12. **导出不是第二条数据通道**：导出的明细必须来自 `CommentScope.published()`，与页面同源。若导出自己写 `where status='APPROVED'`，撤回过的点评会重新出现在 Excel 里，而页面上已经看不到——这种不一致在文件里完全看不出来，只有"明细行数 == 已发布数"的断言能守住。
13. **改密码的语义是旧凭证作废**：只改数据库不踢会话，攻击者用旧密码登录拿到的 token 依然有效。因此改密成功后 `logout(userId)` 踢掉该账号全部会话，且**先记审计日志再登出**——反过来会得到一条"匿名改密"，恰恰是审计最需要看清的场景。
14. **反馈不是评分**：`helpful` 描述的是「这条点评对我的帮助」，不是「导师好不好」。三条边界写在建表注释里：不参与情感统计口径、不影响点评状态机、不进任何导师考核报表。导师**可读不可回**（不给回复框），否则反馈会变成辩论。
15. **反馈的可见范围必须与点评一致**：提交走的是同一个 `assertCanView`，所以点评被撤回后学生连反馈入口都没有。若另写一套判断，就会出现「看不到点评却能给它写反馈」的裂缝，而这条数据仍会出现在导师端。
16. **入口按「流程」合并，不按「表」拆分**：待审核 → 待发布 → 已发布 是同一条流程的三段，拆成三个一级菜单等于让教务每走一步换一次侧边栏。合并后一级菜单 6 → 4，二级 tab 状态写进 URL query——刷新、复制链接、前进后退都停在原处。旧路由（`/audit`、`/unpublished`、`/ai-config`、`/oper-log`）保留重定向，书签与答辩 PPT 里的旧链接不会变成"页面不存在"。

---

## 六、目录结构

```
mentor-comment-system/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/graduation/mentor/
│   │   ├── common/          # R / PageResult / CommentScope / CommentStatus / 异常
│   │   ├── config/          # DemoDataInitializer / MinSampleProperties / AiProperties
│   │   ├── controller/      # Auth / Comment / Audit / Report / Person / Student / Precheck / Ai
│   │   ├── dto/             # AuthDtos / CommentDtos / StatDtos / PrecheckDtos / PersonDtos / AiDtos
│   │   ├── entity/ mapper/  # 实体与 MyBatis-Plus Mapper（含 sys_config）
│   │   └── service/         # 业务 + analysis/（词典、分词、隐私过滤）+ precheck/（预审规则引擎）
│   │                        #      + ai/（AiConfigStore 配置读写 / Spring AI 实现 / 规则降级实现）
│   └── src/main/resources/
│       ├── application.yml          # 默认 MySQL
│       ├── application-demo.yml     # demo：H2
│       ├── db/schema.sql            # 建表
│       └── lexicon/                 # 情感词典 / 停用词 / 预审词表（套话·贬损·建议·领域）
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── api/             # axios 封装 + 各模块接口
│       ├── components/      # StatusTag / SentimentTag / AuditTimeline / GateEmpty / ChangePasswordDialog / admin/SectionTabs / dashboard/*
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
| 认证 | `POST /api/auth/login` · `GET /api/auth/me` · `POST /api/auth/password` · `GET /api/demo/accounts` | 登录/当前用户/修改本人密码（成功即踢掉全部会话）/演示账号（非 demo 返回空） |
| 元数据 | `GET /api/meta/limits` | 门槛单一来源：审核意见字数 + 隐私样本量，前端文案由此渲染 |
| 点评 | `/api/comment/page` · `/admin-page` · `/student-page` · `/{id}` · `POST/PUT/DELETE` · `/{id}/submit` | 三端列表 + 增改删 + 提交 |
| 审核 | `/api/audit/pending` · `/unpublished` · `/published` · `/{id}/approve|return|reject|publish|unpublish` · `/{id}/history` | 审核与发布/撤回 |
| AI 预审 | `/api/ai/precheck/{id}`（GET 读缓存 / POST 计算，`?force=true` 重算） · `/batch` · `/rules` | 规则引擎预审，仅教务；只写 `comment_precheck` |
| AI 摘要 | `GET /api/ai/status` · `POST /api/ai/summary` | 导师写点评时的编辑辅助；模型优先、失败回落规则；不落库、不改状态 |
| AI 小助手 | `POST /api/ai/chat` | 多轮对话，三角色可用；场景由后端按角色决定；按用户限流；无 Key 时返回说明文案（`source=DISABLED`）不报错 |
| AI 配置 | `GET /api/ai/config` · `PUT /api/ai/config` · `POST /api/ai/config/test` · `GET /api/ai/models` | 仅教务。Key 存 `sys_config` 表，**填一次全体导师生效**；读取只返回掩码；保存即生效；`/models` 拉取账号可用模型 |
| 报表 | `/api/report/dashboard` · `/sentiment` · `/trend` · `/audit-stat` · `/wordcloud` · `/word-detail` · `/trend-point` · `/comment/{id}/sentiment` | 统计与下钻（教务） |
| 导出 | `GET /api/report/export` | xlsx（汇总 + 明细两个 sheet），口径复用 `CommentScope.published()`；上限 5000 条 |
| 操作日志 | `GET /api/oper-log/page` · `/actions` | 关键动作留痕查询（仅教务，只读） |
| 导师画像 | `GET /api/report/my-dashboard` | 本人已发布点评的情感/趋势/词云；`teacherId` 由登录态强制注入，不接受请求参数 |
| 人员 | `/api/person/teachers` · `/teachers/all` · `/my-students` · `/students` · `/clazz` · `/terms` · `/bind` · `/bind/{id}` | 师生关系与档案查询 |
| 档案维护 | `POST/PUT /api/person/teachers[/{id}]` · `/{id}/disable?force=` · `/{id}/enable` · 学生同构 · `POST /api/person/users/{id}/reset-password` | 仅教务；新建时同步建账号（用户名 = 工号/学号） |
| 学生 | `/api/student-comment/page` · `/{id}` · `POST /{id}/feedback` | 学生仅 5 字段 + 本人反馈；反馈对象是「这条点评对我的帮助」，不进统计、不改状态 |

> 所有响应统一为 `{ code, msg, data }`，`code=0` 表示成功。鉴权令牌放在请求头 `satoken`。

---

## 八、质量门禁

```bash
cd backend  && mvn -s ../maven-settings.xml test    # Checkstyle 0 违规 + 95 个测试
cd ../frontend && npm run check                     # ESLint 0 error + Prettier 一致 + 构建通过
cd .. && sh scripts/check-schema-sync.sh            # 两份 schema 同源
```

| 测试类 | 数量 | 守住什么 |
| --- | --- | --- |
| `AuditStateMachineTest` | 21 | 七个事件类型、意见不限字数（空/超 500 字被拒）、条件化 UPDATE（影响 0 行 → 409）、发布才触发分析 |
| `AuditPermissionTest` | 10 | 学生/导师/管理员 × 本人/他人 × 已发布/未发布；history 越权回归守卫 |
| `RulePrecheckEngineTest` | 11 | 等级映射（PASS/REVIEW/RETURN → 四个动作）、九条规则检出、意见草稿为完整可读句 |
| `PrecheckReuseTest` | 4 | `content_hash + rule_version` 双匹配复用、规则升级必须重算、非教务 403 |
| `AiRuntimeConfigTest` | 7 | baseUrl 归一化：剥 `/v1`、保留路径前缀、不误伤 `/v1beta`、空值不抛异常 |
| `AiErrorMessagesTest` | 6 | 错误翻译：`404 model is not found`、401、超时、连接拒绝、被包装的 404、未知错误兜底 |
| `AiChatServiceTest` | 4 | 无 Key 返回说明不抛异常、模型失败降级、学生拿到的是学习助手提示词、限流命中抛 429 |
| `AiRateLimiterTest` | 4 | 分钟窗口、日窗口、剩余次数递减、用户之间互不影响 |
| `LlmSummarySuggesterTest` | 5 | Spring AI 在 Boot 3.2.5 下可构建（`OpenAiApi → OpenAiChatModel → ChatClient`）、无 Key/未启用不抛异常、端点不可达必抛、**教务改配置后立即生效** |
| `RuleSummarySuggesterTest` | 4 | 降级实现是默认路径：情绪结论、主题词、建议条数上限、空问题也给收尾建议 |
| `SentimentEvalTest` | 1 | 情感分析准确率 82.7% / Kappa 0.74（固定验证集，防方法退化） |
| `ChangePasswordTest` | 5 | 旧密码校验、长度/字母数字强度、新旧不能相同、成功即踢掉全部会话 |
| `OperLogQueryTest` | 3 | 日志查询与动作清单仅教务可用（403 且不触达数据库） |
| `ReportExportTest` | 4 | **明细行数严格等于已发布条数**、空结果 400、超量拒绝、样本不足占比写「—」 |
| `FeedbackTest` | 6 | 导师不能提交、helpful 为 null 不被静默当 false、对他人/未发布点评 403、归属取自点评、清空内容必须真正写入 |

