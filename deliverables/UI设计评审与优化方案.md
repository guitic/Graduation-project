# UI 设计评审与优化方案

> 对象：基于 Web 的学业导师智能点评分析系统（`mentor-comment-system/frontend`）
> 技术栈：Vue 3.4 + Element Plus 2.8 + ECharts 5.5
> 评审日期：2026-10-30　｜　评审分辨率：1440 × 900（另验证 1280）
> 可视化版：[`index.html`](./index.html)（含实测截图与重绘示意）
> 证据截图：`./assets/`（demo 种子数据实测，非模拟）

---

## 一、一页摘要

**总体评分 7.2 / 10。设计语言已经成型，短板在信息密度控制。**

做得好的部分（**建议保留，不要重构**）：

| 项 | 说明 |
|---|---|
| 设计令牌体系 | `--mc-*` 变量统一了色/间距/圆角/阴影，改一处全站生效 |
| 状态色独立成套 | 6 种点评状态各有 fg/bg 对，与主色解耦，避免「蓝色=可点」的误读 |
| 统计口径可视化 | 每张图都有 `.mc-caliber` 口径行，写明"算的是什么" |
| 隐私门禁空态 | `GateEmpty` 明确区分「没数据」与「数据不足不展示」，是本系统最有说服力的设计点 |
| 双维度状态机表达 | 「通过」与「发布」分成两个态，界面没有偷懒合并 |

必须改的部分（**P0，答辩前完成**）：

| # | 问题 | 位置 | 后果 |
|---|---|---|---|
| P0-1 | 审核队列预审列被固定列压断，5 个按钮折行 | `AuditQueue.vue` | 内容被裁切，一眼看出"没做完" |
| P0-2 | 趋势图图例 / 右轴名 / 当月标注三处叠字 | `TrendCard.vue` | 投屏时最显眼的一张图糊成一团 |
| P0-3 | 待发布页同屏三个空态 | `Unpublished.vue` | 看起来像加载失败 |
| P0-4 | 学生端 3 列网格 + 3 行截断，一屏一张卡 | `StudentComments.vue` | 大面积留白 + 读不全 |

P1 一致性问题 7 项、P2 打磨 8 项，见第三、四节。

---

## 二、评审范围与方法

**覆盖页面（9 个 / 3 角色）**

| 角色 | 页面 |
|---|---|
| 公共 | 登录 |
| 教务 | 统计报表、审核队列、待发布/已撤回、师生管理、AI 配置 |
| 导师 | 我的点评、我的画像 |
| 学生 | 我的点评 |

**方法**

1. 用 `demo` profile（H2 + 种子数据）真实启动前后端，Playwright 以 1440×900 @2x 截取全部页面与关键交互态（详情抽屉、新建弹窗）。
2. 逐页检查：布局溢出 / 文本裁切 / 层级对比 / 一致性 / 可达性。
3. 对每个 P0 给出**重绘的修复示意**，验证方案在当前宽度下确实成立，而不是纸面建议。

**未覆盖**：移动端（系统定位为 PC 端教务/教师工作台，暂不做响应式下沉）；暗色模式（令牌已具备条件，本期不做）。

---

## 三、P0 详细方案

### P0-1 审核队列：列宽预算失控

**现象**（`assets/p0-audit-table.jpg`）

- 「AI 预审」胶囊被右侧固定列覆盖，只剩「建议」二字，等级色点与结论被吃掉；
- 操作列 5 个文字按钮在 330px 内排不下，「驳回」折到第二行，行高 46 → 62px。

**根因**

8 列固定宽度合计已超过容器：`120+110+100+150+120+160+132+330 = 1222px`，而 1440 视口下表格可用宽度约 1124px。
`el-table` 的 `fixed="right"` 列会覆盖在内容之上，于是预审列被"吃掉"而不是触发滚动。

**方案**

1. **建立列宽预算**：固定列合计 ≤ 850px，只保留「主题」一个 `1fr` 列吃剩余空间。
2. **操作收敛**：主操作常驻 + 其余进 `⋯` 下拉。操作列 330 → 150px。
3. **预审列瘦身**：只留「色点 + 分数」，全文进 tooltip。132 → 64px。
4. **时间短格式**：`2026-10-07 11:15:29` → `10-07 11:15`，列宽 160 → 96px。

```vue
<!-- AuditQueue.vue -->
<el-table-column label="学生" min-width="120">
  <template #default="{ row }">
    <div class="stu">
      <span class="stu-name">{{ row.studentName }}</span>
      <span class="stu-no">{{ row.studentNo }}</span>
    </div>
  </template>
</el-table-column>
<el-table-column prop="clazzName" label="班级" width="100" />
<el-table-column prop="teacherName" label="导师" width="90" />
<el-table-column label="主题" min-width="180">
  <template #default="{ row }">
    <span class="topic-link" @click="openDetail(row)">{{ row.topic }}</span>
  </template>
</el-table-column>
<el-table-column prop="term" label="学期" width="110" />
<el-table-column label="提交时间" width="96">
  <template #default="{ row }">
    <span class="mc-mono time">{{ fmtShort(row.submitAt || row.createdAt) }}</span>
  </template>
</el-table-column>

<!-- 预审：色点 + 分数，全文进 tooltip -->
<el-table-column label="预审" width="64" align="center">
  <template #default="{ row }">
    <el-tooltip
      :content="preMap[row.id] ? `${preMap[row.id].riskLevelLabel} · 建议${preMap[row.id].suggestActionLabel}` : '未预审'"
      placement="top"
    >
      <PrecheckTag :view="preMap[row.id]" show-score />
    </el-tooltip>
  </template>
</el-table-column>

<!-- 操作：1 主 + 更多 -->
<el-table-column label="操作" width="150" fixed="right" align="right">
  <template #default="{ row }">
    <el-button type="primary" size="small" @click="openAction(row, 'approvePublish')">
      通过发布
    </el-button>
    <el-dropdown trigger="click" @command="(c) => openAction(row, c)">
      <el-button size="small" :icon="MoreFilled" style="margin-left:6px" />
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="approveOnly">仅通过（待发布）</el-dropdown-item>
          <el-dropdown-item command="return">退回修改</el-dropdown-item>
          <el-dropdown-item command="reject" divided>驳回</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>
</el-table-column>
```

**同步调整**：`People.vue` 学生表操作列同样折行（`编辑 / 解除 / 停用` 两行），一并收敛为「编辑 + ⋯」。

**验收**：1440 下无横向滚动、无换行；1280 下表格仍完整可读。

---

### P0-2 趋势图：三处标签叠字

**现象**（`assets/p0-trend-legend.jpg`）

- 图例 `top:0, right:8` 与右轴名「正向率」重叠，出现两个"正向率"；
- `markArea.label` 的「当月·更新中」被卡片右边缘裁掉一半；
- 当月柱子只有 0.5 条的高度 + 一条贯穿全高的竖带，容易被读成"断崖下跌"。

**根因**

ECharts 的 `legend`、`yAxis[1].name`、`markArea.label` 三者都默认占据右上角，卡片高度只有 288px，没有让位空间。

**方案**

1. **图例下沉到页脚**：`legend.show = false`，用 `ChartCard` 已有的 `#foot` 插槽 + `.legend-row` 手写图例（样式已存在于 `index.css`，只是没用上）。
2. **去掉第二根 Y 轴**：双轴在 288px 高度里必然挤压，比率线改为「单轴 + 数据标签」或直接取消轴名、只保留 `%` 刻度。
3. **当月不画柱**：未完结月用虚线占位框 + 9px「更新中」标注，比"半截柱子"诚实。
4. **刻度取整**：`splitNumber: 4`，避免 21/18/15 这种非整数刻度。

```js
// TrendCard.vue buildOption()
legend: { show: false },   // 交给 ChartCard 页脚

// markArea 只留底纹，不写字
markArea: inProgIdx >= 0 ? {
  silent: true,
  itemStyle: { color: 'rgba(43,92,230,0.05)' },
  label: { show: false },                    // ← 关键：去掉 label
  data: [[{ xAxis: t.months[inProgIdx] }, { xAxis: t.months[inProgIdx] }]]
} : undefined,

// x 轴直接标注"更新中"，不额外占空间
xAxis: {
  axisLabel: {
    color: '#6b7280',
    fontSize: 11,
    formatter: (v, i) => (i === inProgIdx ? `${v}\n更新中` : v),
    lineHeight: 16
  }
},

yAxis: [
  { type: 'value', splitNumber: 4, /* ... */ }
  // 删除第二根轴；比率线改用 label.formatter 显示百分比
]
```

```vue
<!-- 页脚图例（复用已有样式） -->
<template #foot>
  <div class="legend-row">
    <span class="lg"><i style="background:#12805c" />正向</span>
    <span class="lg"><i style="background:#8f959e" />中性</span>
    <span class="lg"><i style="background:#d92d20" />负向</span>
    <span class="lg"><i style="background:#2b5ce6;border-radius:50%" />正向率</span>
  </div>
  <span class="hint"><el-icon><Pointer /></el-icon> 点击月份柱查看明细</span>
</template>
```

**验收**：任意数据（含空月份、单月、当月）下无标签重叠；图例在卡片内不换行。

---

### P0-3 待发布页：三个空态叠加

**现象**（`assets/p0-empty-state.jpg`）

表格自带「暂无数据」→ 下方 `el-empty` 插画 → 右下「共 0 条」分页。三处说同一件事。

**方案**

```vue
<!-- 1. 用 #empty 接管表格空态，删掉外部的 el-empty -->
<el-table :data="rows" row-key="id">
  <template #empty>
    <div class="mc-gate">
      <div class="gate-icon"><el-icon><Promotion /></el-icon></div>
      <div class="gate-title">当前没有待发布的点评</div>
      <div class="gate-desc">
        「通过」与「发布」是两个维度：已通过的点评先进入这里，确认后再推送给学生。
      </div>
      <el-button type="primary" plain size="small" @click="goAudit">
        去审核队列处理 {{ pendingCount }} 条
      </el-button>
    </div>
  </template>
  <!-- ...列定义 -->
</el-table>

<!-- 2. 0 条时不渲染分页 -->
<div v-if="total > 0" class="pager"> ... </div>
```

**原则（写进组件规范）**：一个列表页，**空态只出现一次，且必须给下一步动作**。

---

### P0-4 学生端：网格留白 + 阅读截断

**现象**（`assets/p1-student-grid.jpg`）

学生通常只有 1–5 条点评，3 列固定网格让 2/3 屏幕空着；卡片又把正文截断到 3 行，读一条 500 字点评必须点开弹窗。
**该给内容的地方给了留白，该留白的地方塞了字。**

**方案：卡片网格 → 单列阅读流**

```vue
<div class="read-list">
  <article v-for="c in rows" :key="c.id" class="read-card" @click="openDetail(c)">
    <div class="date-col">
      <b>{{ day(c.publishedAt) }}</b>{{ monthYear(c.publishedAt) }}
    </div>
    <div class="rc-body">
      <h3 class="rc-title">{{ c.topic }}</h3>
      <div class="rc-meta">{{ c.teacherName }} · {{ c.term }}</div>
      <p class="rc-text">{{ c.content }}</p>
      <span class="rc-more">查看完整点评 →</span>
    </div>
  </article>
</div>
```

```css
.read-list { display: flex; flex-direction: column; gap: 12px; max-width: 880px; }
.read-card { display: flex; gap: 16px; padding: 18px 20px; cursor: pointer; }
.date-col { flex: 0 0 54px; text-align: center; font-size: 11px; color: var(--mc-text-3); }
.date-col b { display: block; font-size: 18px; color: var(--mc-text-2); font-weight: 600; }
.rc-text {
  font-size: 13.5px; line-height: 1.85; color: var(--mc-text-2);
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden;
}
```

若产品坚持网格：`repeat(auto-fill, minmax(300px, 1fr))`，且条数 < 3 时降为 1 列。

---

## 四、P1 / P2 清单

### P1 一致性问题（7 项）

| # | 问题 | 位置 | 改法 |
|---|---|---|---|
| 1 | 标题重复两遍（顶栏 + 页面内） | `AppLayout.vue` | 顶栏改面包屑「工作台 / 统计报表」，或只留产品名 + 全局操作 |
| 2 | 词云 10 色彩虹，红绿与情感语义冲突 | `WordCloudCard.vue` | 单色相 5 档明度，按词频加深（见下） |
| 3 | KPI 7 列过窄，1280 掉 4 列留空 | `KpiGrid.vue` | `repeat(auto-fit, minmax(148px,1fr))`；数值 26→28px；左侧色条改顶部细线 |
| 4 | 辅助文字对比度不足（3.0:1 / 2.0:1） | `styles/index.css` | 灰度 4 级 → 3 级，最小级 `#6b7280`（5.0:1） |
| 5 | 「更新时间」取的是 `createdAt` | `CommentList.vue` | 后端补 `updatedAt`，或列名改「创建时间」 |
| 6 | AI 配置页两列高度失衡 | `AiConfig.vue` | `1fr 1fr`；提示文案压到 2 行 + 「查看说明」展开 |
| 7 | 操作按钮四色并排 | `AuditQueue` / `People` | 主操作实心、次操作进「更多」，危险操作用图标 + 二次确认 |

**词云单色相改法**

```js
// 用主色的明度梯度代替彩虹；不出现红/绿，避免暗示情感
const shade = (i, n) => {
  const t = n <= 1 ? 0 : i / (n - 1)          // 0 = 最高频
  const light = 34 + t * 34                    // 34% → 68%
  return `hsl(224, 68%, ${light}%)`
}
data: (w.words || []).map((x, i) => ({
  name: x.name, value: x.value,
  textStyle: { color: shade(i, w.words.length) }
}))
```

### P2 打磨（8 项）

1. 登录页演示账号区占比过大，可折叠为「使用演示账号」按钮。
2. 登录页左右栏视觉重量不均（左满高渐变 vs 右大量留白），右侧可加一句页脚版权。
3. 表格行 hover 整行可点进详情，减少"找查看按钮"的动作。
4. 侧栏折叠后图标无 tooltip，`el-menu` 需 `collapse` 模式下补 `el-tooltip`。
5. 键盘焦点态缺失：`teacher-item` 有 `tabindex` 但无 `:focus-visible` 样式。
6. 空态插画统一：`el-empty` 默认图与 `GateEmpty` 自绘图标风格不一致。
7. 表单错误提示统一位置（当前部分 `blur`、部分 `change`）。
8. 数字统一 `tabular-nums`（部分 KPI 已用 `.mc-mono`，部分未用）。

---

## 五、设计规范升级

### 5.1 令牌调整

```css
:root {
  /* ---- 灰度：4 级 → 3 级，全部达 WCAG AA ---- */
  --mc-text-1: #1f2329;   /* 主文本   15.8:1 */
  --mc-text-2: #646a73;   /* 次文本    5.5:1 */
  --mc-text-3: #6b7280;   /* 辅助文本  5.0:1  ← 原 #8f959e(3.0) / #b4b8bf(2.0) 合并 */
  /* --mc-text-4 废弃 */

  /* ---- 圆角：5 种 → 3 档 ---- */
  --mc-radius-sm: 6px;    /* 控件、标签 */
  --mc-radius: 10px;      /* 卡片、面板 */
  --mc-radius-pill: 999px;

  /* ---- 间距：4 的倍数 ---- */
  --mc-gap-1: 4px;  --mc-gap-2: 8px;   --mc-gap-3: 12px;
  --mc-gap-4: 16px; --mc-gap-5: 20px;  --mc-gap-6: 24px;

  /* ---- 新增：键盘焦点态 ---- */
  --mc-focus: 0 0 0 3px rgba(43, 92, 230, 0.18);
}
```

### 5.2 字号阶梯（10 档 → 6 档）

| 用途 | 字号 / 字重 | 替代原值 |
|---|---|---|
| KPI 数值 | 28 / 700 | 26 |
| 页面标题 | 20 / 600 | 20 |
| 区块标题 | 16 / 600 | 15 / 17 |
| 正文 | 14 / 400 | 14 |
| 次要正文 | 13 / 400 | 12.5 / 13 / 13.5 |
| 辅助 / 标签 | 12 / 500 | 11.5 / 12 |

### 5.3 表格密度规范（新增）

| 规则 | 值 |
|---|---|
| 行高 | 单行 46px / 双行 56px，同表统一 |
| 固定列 | 最多 1 个（操作列），且宽 ≤ 150px |
| 操作按钮 | 同排 ≤ 2 个；≥ 3 个必须收进「更多」 |
| 列宽预算 | 固定列合计 ≤ 容器宽度 × 0.75 |
| 时间格式 | 列表内用短格式 `MM-DD HH:mm`，详情页用全格式 |
| 空态 | 全表只出现一处，且带下一步动作 |

### 5.4 色彩语义三条铁律（沿用并强化）

1. 主色 `#2b5ce6` **只用于可点击动作**，不用于装饰、不用于数据。
2. 状态色与情感色**互不共用**；红/绿在系统内只有「负向/正向」一种含义，词云、图表配色都要避开。
3. 同一屏内，**主操作不超过 1 个**（实心按钮）。

---

## 六、落地计划

| 阶段 | 内容 | 改动面 | 预估 |
|---|---|---|---|
| **P0** | 审核队列列重排、趋势图图例下沉、空态合并、学生端阅读流 | 4 个 `.vue`，无接口变更 | 1 天 |
| **P1** | 令牌收敛、顶栏面包屑、词云单色、KPI 自适应、`updatedAt` | `index.css` + 5 个组件 | 1.5 天 |
| **P2** | 登录页、hover 行为、焦点态、空态插画 | 零散增量 | 1 天 |

**风险提示**

- 改灰度会同时影响状态色胶囊的文字对比（`--st-*` 是独立变量，不受影响，但 `--mc-text-3` 用在浅底胶囊上时需复查）。
- 表格列宽调整后，1280 以下需验证；建议给 `el-table` 加 `:fit="false"` + 横向滚动兜底。
- 学生端改单列后，若后续支持"学期分组"，需要在列表上增加 sticky 分组头，避免长列表失去定位感。

---

## 七、验收清单

- [x] 1440×900 下审核队列表格无横向溢出、无按钮换行
- [x] 1280 宽度下所有表格仍可完整阅读
- [x] 趋势图任意数据下无标签重叠（含当月标注）
- [x] 任一列表 0 条数据时，整页只出现一处空态
- [x] 词云不再出现红/绿色词
- [x] 所有 12px 文字对比度 ≥ 4.5:1
- [x] 页面内不再出现与顶栏重复的标题
- [x] 主操作在任何页面都不超过 1 个
- [ ] 键盘 Tab 可到达所有可点击项，且焦点可见（已加全局 `:focus-visible`，未逐页实测）
- [x] 学生端 1 条数据时不出现大面积空白

---

## 八、实施记录（2026-10-30）

P0 四项与 P1 七项已全部落地，前端 `lint / format:check / build` 通过，后端 **77 个测试全过**。

### 8.1 改动清单

| 文件 | 改动 |
|---|---|
| `styles/index.css` | 灰度 4 级 → 3 级（`#6b7280` 5.0:1）；新增 `--mc-radius-pill`、`--mc-focus`；全局 `:focus-visible`；新增 `.mc-link`、`.mc-gate .gate-actions` |
| `utils/status.js` | 新增 `fmtShort()`：列表内 `MM-DD HH:mm` 短格式 |
| `layouts/AppLayout.vue` | 顶栏标题 → 面包屑「工作台 / 当前页」，视觉权重 16px → 13px |
| `views/admin/AuditQueue.vue` | 列宽重排（固定列 850 → 748px）；预审列改「色点 + 分数 + tooltip」；操作列改「通过发布 + ⋯」；主题改 `.mc-link`；空态接管 + 筛选态空态区分 |
| `components/PrecheckTag.vue` | 新增 `compact` 模式（只显分数） |
| `components/dashboard/TrendCard.vue` | 图例下沉页脚；`markArea.label` 关闭、改 x 轴「更新中」；Y 轴刻度取整；`grid.top` 32px 防轴名被裁 |
| `views/admin/Unpublished.vue` | 两个 tab 均用 `#empty` 接管空态；0 条不渲染分页；空态带「去审核队列」 |
| `views/student/StudentComments.vue` | 3 列网格 → 单列阅读流（880px，日期锚点，4 行截断） |
| `components/dashboard/WordCloudCard.vue` | 10 色彩虹 → 单色相 `hsl(224,66%,30~54%)`，按词频加深 |
| `components/dashboard/KpiGrid.vue` | `min-width:0`；断点 1280 → 1360 / 900；`warn`/`danger` 数值上色 |
| `views/teacher/CommentList.vue` | 「更新」改用 `updatedAt` + 短格式；操作列 196 → 176px，删除收进「⋯」；文字按钮内边距收紧 |
| `views/admin/People.vue` | 学生表操作列 → 「编辑 + ⋯（启用/停用/解除）」 |
| `views/admin/AiConfig.vue` | 端点/模型名两列等宽（`1fr 1fr`），提示文案压缩 |
| `dto/CommentDtos.java` · `CommentService.java` | `Row` 暴露 `updatedAt`（原来只有 `createdAt`，列名与数据不符） |

### 8.2 与方案的三处偏差

1. **趋势图保留了双 Y 轴**。方案写的是「去掉第二根 Y 轴」，但数量（0–20）与比率（0–1）量级差太大，合成单轴后比率线会压成一条贴底直线。实际改法是：图例下沉 + 轴名 `nameGap` 内收 + `grid.top` 加到 32px，三处叠字消失，双轴信息量保住。
2. **KPI 没有改成 `auto-fit`**。实测 1440 下每卡约 154px（不是评审时估计的 118px），`auto-fit` 会在 1280 下把 7 个指标排成 6+1 的孤儿行，比现在的 4+3 更难看。最终只做了 `min-width:0`、断点前移和数值上色。
3. **额外修了一个后端 bug**：审核队列的学期/导师/关键词三个筛选条件在后端**完全没被读取**（`pendingPage` 只按状态查），搜索框点了没反应。已补 `applyQueueFilters()`，关键词匹配主题与学生姓名/学号，不匹配正文。

### 8.3 验证

- 前端：`eslint` 0 error、`prettier --check` 全通过、`vite build` 成功。
- 后端：`mvn test` **77 tests / 0 failures / 0 errors**；Spotless 通过。
- 接口实测：`keyword=zzzz` → total 0；`keyword=罗一鸣` → total 1；`term=2025-2026-1` → total 8。
- 视觉回归：1440 与 1280 两档重截 8 个页面 + 「更多」下拉 + 筛选空态，见 `.workbuddy-ai/uishot/after/`。

### 8.4 P2 追加（同日完成 5 项）

| 项 | 改法 |
|---|---|
| 登录页演示区折叠 | 4 张账号卡收成一行「使用演示账号 · 统一密码 123456」，点开展开；正式环境接口返回空列表时整块不渲染 |
| 登录页配重 | 右侧面板补页脚「教学演示版本 v1.0 · 页面数据均为模拟数据」，压住底部重心 |
| 表格整行可点 | 导师点评表与审核队列表加 `@row-click` + `row-class-name="row-clickable"`；操作列用 `@click.stop` 包裹，按钮不会误触发详情 |
| 空态统一 | 新增 `EmptyHint.vue`（图标 + 标题 + 说明 + 动作，支持 `compact`），替换全部 7 处 `el-empty`，Element 默认插画不再出现 |
| 数字等宽 | `.el-table` 与 `.mc-pill` 统一 `font-variant-numeric: tabular-nums` |

**验证**：`lint / format:check / build` 通过；点击行空白处抽屉打开（teacher / audit 均 true），点击「通过发布」只弹对话框、不弹抽屉（dialog 1 / drawer 0）。

### 8.5 仍未做

表单校验时机统一（部分 `blur` 部分 `change`）、暗色模式。前者收益低，后者令牌已具备条件但本期不做。

---

## 附录：证据文件

| 文件 | 内容 |
|---|---|
| `assets/p0-audit-table.jpg` | 审核队列：预审列被压断 + 按钮折行 |
| `assets/p0-trend-legend.jpg` | 趋势图：图例 / 轴名 / 当月标注三处叠字 |
| `assets/p0-empty-state.jpg` | 待发布页：三重空态 |
| `assets/p1-student-grid.jpg` | 学生端：3 列网格大面积留白 |
| `assets/p1-wordcloud.jpg` | 词云：10 色彩虹 |
| `assets/p2-teacher-table.jpg` | 导师点评表：操作列留白 |
