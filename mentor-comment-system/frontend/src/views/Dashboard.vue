<template>
  <div class="mc-page" v-loading="loading">
    <!-- 页头 -->
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">统计报表</div>
        <div class="mc-page-sub">
          仅统计已发布点评；样本不足时聚合结果不展示，避免从少量数据反推个人信息
        </div>
      </div>
      <div class="head-actions">
        <span v-if="dash?.asOf" class="asof">
          统计截至 <b class="mc-mono">{{ fmtTime(dash.asOf) }}</b>
        </span>
        <el-tooltip content="重算全部已发布点评的情感与词频（演示/版本迁移用）" placement="bottom">
          <el-button :icon="Refresh" :loading="rebuilding" @click="onRebuild">重建分析</el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="mc-card filter-bar">
      <el-select
        v-model="filters.term"
        placeholder="全部学期"
        clearable
        class="f-item"
        @change="reload"
      >
        <el-option v-for="t in options.terms" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="filters.teacherId"
        placeholder="全部导师"
        clearable
        filterable
        class="f-item"
        @change="reload"
      >
        <el-option v-for="t in options.teachers" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-select
        v-model="filters.clazzId"
        placeholder="全部班级"
        clearable
        filterable
        class="f-item"
        @change="reload"
      >
        <el-option v-for="c in options.clazz" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.months" class="f-item f-months" @change="reload">
        <el-option :value="3" label="近 3 个月" />
        <el-option :value="6" label="近 6 个月" />
        <el-option :value="12" label="近 12 个月" />
      </el-select>
      <el-button text :icon="RefreshLeft" @click="resetFilters">重置</el-button>
      <div class="f-spacer" />
      <span class="method-tag">
        方法 <b>{{ dash?.methodVersion || 'lexicon-v1' }}</b>
      </span>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.key" class="mc-card kpi" :class="k.tone">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value mc-mono">{{ k.value }}</div>
        <div class="kpi-foot">{{ k.foot }}</div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <!-- 情感分布 -->
      <div class="mc-card chart-card">
        <div class="chart-head">
          <div class="chart-title">情感倾向分布</div>
          <div class="mc-caliber">
            口径：仅已发布点评 · 样本 &lt; {{ minSample }} 条不展示占比
          </div>
        </div>
        <div class="chart-body">
          <GateEmpty
            v-if="!sentimentOk"
            :sample-count="dash?.sentiment?.total ?? 0"
            :min-sample="minSample"
            title="情感样本不足"
            desc="已发布点评数量低于隐私门槛，暂不展示情感占比。补充发布点评后自动恢复。"
          />
          <div v-else ref="sentimentRef" class="chart-canvas" />
        </div>
        <div v-if="sentimentOk" class="chart-foot">
          <div class="legend-row">
            <span class="lg"><i style="background: #12805c" />正向 {{ dash.sentiment.positive }}</span>
            <span class="lg"><i style="background: #8f959e" />中性 {{ dash.sentiment.neutral }}</span>
            <span class="lg"><i style="background: #d92d20" />负向 {{ dash.sentiment.negative }}</span>
          </div>
          <div class="rate-row">
            正向率
            <b class="mc-mono">{{ pct(dash.sentiment.positiveRate) }}</b> · 负向率
            <b class="mc-mono">{{ pct(dash.sentiment.negativeRate) }}</b>
          </div>
        </div>
      </div>

      <!-- 数量趋势 -->
      <div class="mc-card chart-card">
        <div class="chart-head">
          <div class="chart-title">点评数量与情感趋势</div>
          <div class="mc-caliber">
            口径：按发布月份归集 · 当月为未完结月（斜纹）· 比率线需整图样本 ≥ {{ minSample }}
          </div>
        </div>
        <div class="chart-body">
          <div ref="trendRef" class="chart-canvas" />
        </div>
        <div class="chart-foot">
          <span class="hint">
            <el-icon><Pointer /></el-icon> 点击任一月份柱，查看该月点评明细（按桶级样本量校验）
          </span>
        </div>
      </div>

      <!-- 审核状态 -->
      <div class="mc-card chart-card">
        <div class="chart-head">
          <div class="chart-title">审核状态分布</div>
          <div class="mc-caliber">口径：全量点评 · 「已发布 / 待发布」为两个独立态</div>
        </div>
        <div class="chart-body">
          <div ref="auditRef" class="chart-canvas" />
        </div>
      </div>

      <!-- 词云 -->
      <div class="mc-card chart-card">
        <div class="chart-head">
          <div class="chart-title">点评高频词词云</div>
          <div class="mc-caliber">
            口径：已发布点评分词词频 · 已过滤停用词与姓名/学号 · 样本 &lt; {{ minSample }} 不展示
          </div>
        </div>
        <div class="chart-body">
          <GateEmpty
            v-if="!wordOk"
            :sample-count="dash?.wordCloud?.sampleCount ?? 0"
            :min-sample="minSample"
            title="词云样本不足"
            desc="已发布点评数量低于隐私门槛，暂不展示词云。2~4 条点评的高频词几乎等于原文关键词。"
          />
          <div v-else ref="wordRef" class="chart-canvas" />
        </div>
        <div v-if="wordOk" class="chart-foot">
          <span class="hint">
            <el-icon><Pointer /></el-icon> 点击词语，查看包含该词的点评明细（受样本门槛约束）
          </span>
        </div>
      </div>
    </div>

    <!-- 下钻抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drill.title"
      size="560px"
      destroy-on-close
    >
      <div v-loading="drawerLoading" class="drawer-body">
        <div class="drawer-summary">
          <span>
            样本量 <b class="mc-mono">{{ drill.sampleCount }}</b> 条 · 门槛
            <b class="mc-mono">{{ drill.minSample }}</b> 条
          </span>
          <el-tag v-if="drill.allowed" type="success" size="small" effect="plain" round>
            已通过门禁
          </el-tag>
          <el-tag v-else type="warning" size="small" effect="plain" round>样本不足</el-tag>
        </div>

        <GateEmpty
          v-if="!drawerLoading && !drill.allowed"
          :sample-count="drill.sampleCount"
          :min-sample="drill.minSample"
          title="该分组样本不足"
          desc="即使整体数据充足，单个时间桶/词语下的点评过少仍会暴露个人信息，因此该层下钻被禁用。"
        />

        <div v-else class="drill-list">
          <div v-for="c in drill.comments" :key="c.id" class="drill-item">
            <div class="di-head">
              <span class="di-topic">{{ c.topic }}</span>
              <span class="di-meta">
                {{ c.studentName }} · {{ c.term }} · {{ fmtDate(c.publishedAt) }}
              </span>
            </div>
            <div class="di-content">{{ c.content }}</div>
          </div>
          <el-empty
            v-if="!drawerLoading && !drill.comments.length"
            description="暂无明细"
            :image-size="72"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Pointer } from '@element-plus/icons-vue'
import GateEmpty from '../components/GateEmpty.vue'
import { dashboard, trendPoint, wordDetail, rebuild } from '../api/report'
import { terms, teachers, clazzOptions } from '../api/person'
import { fmtTime, fmtDate } from '../utils/status'

const loading = ref(false)
const rebuilding = ref(false)
const dash = ref(null)

const filters = reactive({ term: '', teacherId: null, clazzId: null, months: 6 })
const options = reactive({ terms: [], teachers: [], clazz: [] })

const sentimentRef = ref(null)
const trendRef = ref(null)
const auditRef = ref(null)
const wordRef = ref(null)
const charts = {}
let trendClickBound = false
let wordClickBound = false

const minSample = computed(() => dash.value?.sentiment?.minSample ?? 5)
const sentimentOk = computed(() => !!dash.value?.sentiment?.enough)
const wordOk = computed(() => !!dash.value?.wordCloud?.enough)

const kpis = computed(() => {
  const k = dash.value?.kpi
  if (!k) return []
  return [
    { key: 'total', label: '点评总数', value: k.total, foot: '含草稿/待审/退回/驳回', tone: '' },
    { key: 'published', label: '已发布', value: k.published, foot: '纳入统计与分析', tone: 'ok' },
    { key: 'pending', label: '待审核', value: k.pending, foot: '等待教务处理', tone: 'warn' },
    { key: 'unpublished', label: '待发布', value: k.unpublished, foot: '已通过·未推送', tone: 'info' },
    { key: 'returned', label: '退回修改', value: k.returned, foot: '待导师修改重提', tone: 'warn' },
    { key: 'rejected', label: '已驳回', value: k.rejected, foot: '终态·不发布', tone: 'danger' },
    { key: 'lowConfidence', label: '低置信', value: k.lowConfidence, foot: '建议人工复核', tone: 'info' }
  ]
})

function pct(v) {
  if (v == null) return '—'
  return (v * 100).toFixed(1) + '%'
}

function scopeParams() {
  const p = { months: filters.months }
  if (filters.term) p.term = filters.term
  if (filters.teacherId) p.teacherId = filters.teacherId
  if (filters.clazzId) p.clazzId = filters.clazzId
  return p
}

async function loadOptions() {
  try {
    const [t, te, c] = await Promise.all([terms(), teachers(), clazzOptions()])
    options.terms = t || []
    options.teachers = te || []
    options.clazz = c || []
  } catch (e) {
    /* 选项加载失败不阻塞主报表 */
  }
}

async function reload() {
  loading.value = true
  try {
    dash.value = await dashboard(scopeParams())
    await nextTick()
    renderAll()
  } catch (e) {
    /* 错误提示已统一处理 */
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.term = ''
  filters.teacherId = null
  filters.clazzId = null
  filters.months = 6
  reload()
}

function getChart(key, el) {
  if (!el) return null
  if (!charts[key] || charts[key].isDisposed()) {
    charts[key] = echarts.init(el)
  }
  return charts[key]
}

function clearChart(key) {
  if (charts[key] && !charts[key].isDisposed()) {
    charts[key].clear()
  }
}

function renderAll() {
  renderSentiment()
  renderTrend()
  renderAudit()
  renderWord()
}

// ---------------- 情感分布（环图） ----------------
function renderSentiment() {
  if (!sentimentOk.value) {
    clearChart('sentiment')
    return
  }
  const s = dash.value.sentiment
  const chart = getChart('sentiment', sentimentRef.value)
  if (!chart) return
  chart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (p) => `${p.name}<br/>${p.value} 条 · ${p.percent}%`
      },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { color: '#646a73', fontSize: 12 }
      },
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: '36%',
          style: {
            text: String(s.total),
            fontSize: 28,
            fontWeight: 700,
            fill: '#1f2329',
            textAlign: 'center'
          }
        },
        {
          type: 'text',
          left: 'center',
          top: '47%',
          style: { text: '已发布', fontSize: 12, fill: '#8f959e', textAlign: 'center' }
        }
      ],
      series: [
        {
          type: 'pie',
          radius: ['56%', '76%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          labelLine: { show: false },
          emphasis: { scale: true, scaleSize: 4 },
          data: [
            { value: s.positive, name: '正向', itemStyle: { color: '#12805c' } },
            { value: s.neutral, name: '中性', itemStyle: { color: '#8f959e' } },
            { value: s.negative, name: '负向', itemStyle: { color: '#d92d20' } }
          ]
        }
      ]
    },
    true
  )
}

// ---------------- 数量与情感趋势 ----------------
function renderTrend() {
  const t = dash.value?.trend
  if (!t || !t.months?.length) {
    clearChart('trend')
    return
  }
  const chart = getChart('trend', trendRef.value)
  if (!chart) return

  const inProgIdx = (t.inProgress || []).findIndex(Boolean)
  const POS = '#12805c'
  const NEU = '#8f959e'
  const NEG = '#d92d20'

  const barData = (arr, color) =>
    (arr || []).map((v, i) =>
      i === inProgIdx
        ? {
            value: v,
            itemStyle: {
              color,
              opacity: 0.45,
              borderColor: color,
              borderWidth: 1.5,
              borderType: 'dashed'
            }
          }
        : v
    )

  const series = [
    {
      name: '正向',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.positiveCount, POS),
      itemStyle: { color: POS },
      markArea:
        inProgIdx >= 0
          ? {
              silent: true,
              itemStyle: { color: 'rgba(43,92,230,0.06)' },
              label: {
                show: true,
                position: 'top',
                formatter: '当月·更新中',
                color: '#2b5ce6',
                fontSize: 11
              },
              data: [[{ xAxis: t.months[inProgIdx] }, { xAxis: t.months[inProgIdx] }]]
            }
          : undefined
    },
    {
      name: '中性',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.neutralCount, NEU),
      itemStyle: { color: NEU }
    },
    {
      name: '负向',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.negativeCount, NEG),
      itemStyle: { color: NEG, borderRadius: [3, 3, 0, 0] }
    }
  ]

  const yAxis = [
    {
      type: 'value',
      name: '条数',
      nameTextStyle: { color: '#b4b8bf', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f1f4' } },
      axisLabel: { color: '#8f959e', fontSize: 11 }
    }
  ]

  if (t.rateAvailable) {
    series.push({
      name: '正向率',
      type: 'line',
      yAxisIndex: 1,
      data: t.positiveRate,
      connectNulls: false,
      symbol: 'circle',
      symbolSize: 6,
      smooth: false,
      lineStyle: { width: 2, color: '#2b5ce6' },
      itemStyle: { color: '#2b5ce6' }
    })
    yAxis.push({
      type: 'value',
      name: '正向率',
      min: 0,
      max: 1,
      nameTextStyle: { color: '#b4b8bf', fontSize: 11 },
      splitLine: { show: false },
      axisLabel: {
        color: '#8f959e',
        fontSize: 11,
        formatter: (v) => Math.round(v * 100) + '%'
      }
    })
  }

  chart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: {
        top: 0,
        right: 8,
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: '#646a73', fontSize: 11 }
      },
      grid: { left: 46, right: t.rateAvailable ? 50 : 18, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: t.months,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#e5e6eb' } },
        axisLabel: { color: '#8f959e', fontSize: 11 }
      },
      yAxis,
      series
    },
    true
  )

  if (!trendClickBound) {
    chart.on('click', (p) => {
      if (p.componentType !== 'series') return
      openTrendDrill(p.name)
    })
    trendClickBound = true
  }
}

// ---------------- 审核状态 ----------------
function renderAudit() {
  const a = dash.value?.auditStat
  if (!a) {
    clearChart('audit')
    return
  }
  const chart = getChart('audit', auditRef.value)
  if (!chart) return

  const raw = [
    { name: '草稿', value: a.draft, color: '#8f959e' },
    { name: '待审核', value: a.pending, color: '#d97706' },
    { name: '已发布', value: a.approved, color: '#12805c' },
    { name: '待发布', value: a.unpublished, color: '#0e7490' },
    { name: '退回修改', value: a.returned, color: '#c2410c' },
    { name: '已驳回', value: a.rejected, color: '#b91c1c' }
  ]
  // 横向条形图：自下而上，故反转
  const cats = raw.map((x) => x.name).reverse()
  const data = raw
    .map((x) => ({ value: x.value, itemStyle: { color: x.color, borderRadius: [0, 4, 4, 0] } }))
    .reverse()

  chart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 74, right: 40, top: 16, bottom: 22 },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f1f4' } },
        axisLabel: { color: '#8f959e', fontSize: 11 }
      },
      yAxis: {
        type: 'category',
        data: cats,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { color: '#646a73', fontSize: 12 }
      },
      series: [
        {
          type: 'bar',
          data,
          barMaxWidth: 18,
          label: { show: true, position: 'right', color: '#646a73', fontSize: 11 }
        }
      ]
    },
    true
  )
}

// ---------------- 词云 ----------------
const WORD_PALETTE = [
  '#2b5ce6',
  '#12805c',
  '#b45309',
  '#0e7490',
  '#6d28d9',
  '#c2410c',
  '#475569',
  '#15803d',
  '#9333ea',
  '#0369a1'
]

function renderWord() {
  if (!wordOk.value) {
    clearChart('word')
    return
  }
  const w = dash.value.wordCloud
  const chart = getChart('word', wordRef.value)
  if (!chart) return
  chart.setOption(
    {
      tooltip: {
        show: true,
        formatter: (p) => `${p.name} · 出现 ${p.value} 次`
      },
      series: [
        {
          type: 'wordCloud',
          shape: 'circle',
          left: 'center',
          top: 'center',
          width: '92%',
          height: '90%',
          sizeRange: [13, 44],
          rotationRange: [-45, 45],
          rotationStep: 15,
          gridSize: 8,
          drawOutOfBound: false,
          textStyle: {
            fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
            fontWeight: 600
          },
          emphasis: {
            textStyle: { textShadowBlur: 6, textShadowColor: 'rgba(0,0,0,0.14)' }
          },
          data: (w.words || []).map((x, i) => ({
            name: x.name,
            value: x.value,
            textStyle: { color: WORD_PALETTE[i % WORD_PALETTE.length] }
          }))
        }
      ]
    },
    true
  )

  if (!wordClickBound) {
    chart.on('click', (p) => {
      if (p.name) openWordDrill(p.name)
    })
    wordClickBound = true
  }
}

// ---------------- 下钻 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drill = reactive({
  title: '',
  sampleCount: 0,
  allowed: false,
  minSample: 5,
  comments: []
})

async function openTrendDrill(month) {
  if (!month) return
  drawerVisible.value = true
  drawerLoading.value = true
  Object.assign(drill, { title: `${month} 点评明细`, sampleCount: 0, allowed: false, comments: [] })
  try {
    const r = await trendPoint(month, scopeParams())
    Object.assign(drill, {
      title: `${month} 点评明细`,
      sampleCount: r.sampleCount,
      allowed: r.allowed,
      minSample: r.minSample,
      comments: r.comments || []
    })
  } catch (e) {
    /* 已提示 */
  } finally {
    drawerLoading.value = false
  }
}

async function openWordDrill(word) {
  drawerVisible.value = true
  drawerLoading.value = true
  Object.assign(drill, { title: `含「${word}」的点评`, sampleCount: 0, allowed: false, comments: [] })
  try {
    const r = await wordDetail(word, scopeParams())
    Object.assign(drill, {
      title: `含「${word}」的点评`,
      sampleCount: r.sampleCount,
      allowed: r.allowed,
      minSample: r.minSample,
      comments: r.comments || []
    })
  } catch (e) {
    /* 已提示 */
  } finally {
    drawerLoading.value = false
  }
}

async function onRebuild() {
  rebuilding.value = true
  try {
    const r = await rebuild()
    ElMessage.success(`已重算 ${r?.count ?? r?.total ?? ''} 条点评的分析结果`)
    reload()
  } catch (e) {
    /* 已提示 */
  } finally {
    rebuilding.value = false
  }
}

function onResize() {
  Object.values(charts).forEach((c) => {
    if (c && !c.isDisposed()) c.resize()
  })
}

onMounted(async () => {
  await loadOptions()
  await reload()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach((c) => {
    if (c && !c.isDisposed()) c.dispose()
  })
})
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.asof {
  font-size: 12.5px;
  color: var(--mc-text-3);
}
.asof b {
  color: var(--mc-text-2);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.f-item {
  width: 150px;
}
.f-months {
  width: 128px;
}
.f-spacer {
  flex: 1;
}
.method-tag {
  font-size: 12px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 4px 10px;
  border-radius: 6px;
}
.method-tag b {
  color: var(--mc-primary);
  font-family: 'SF Mono', Consolas, monospace;
}

/* KPI */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  padding: 14px 16px;
  position: relative;
  overflow: hidden;
}
.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--mc-border);
}
.kpi.ok::before {
  background: #12805c;
}
.kpi.warn::before {
  background: #d97706;
}
.kpi.info::before {
  background: #0e7490;
}
.kpi.danger::before {
  background: #b91c1c;
}
.kpi-label {
  font-size: 12.5px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: var(--mc-text-1);
}
.kpi-foot {
  font-size: 11.5px;
  color: var(--mc-text-4);
  margin-top: 7px;
}

/* 图表网格 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-card {
  padding: 16px 18px 12px;
  display: flex;
  flex-direction: column;
}
.chart-head {
  margin-bottom: 6px;
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 5px;
}
.chart-body {
  height: 288px;
  position: relative;
}
.chart-canvas {
  width: 100%;
  height: 100%;
}
.chart-foot {
  border-top: 1px dashed var(--mc-border);
  margin-top: 8px;
  padding-top: 10px;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.legend-row {
  display: flex;
  gap: 14px;
}
.lg {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--mc-text-2);
}
.lg i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  display: inline-block;
}
.rate-row {
  font-size: 12px;
  color: var(--mc-text-3);
}
.rate-row b {
  color: var(--mc-text-1);
}
.hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--mc-text-3);
}

/* 抽屉 */
.drawer-body {
  min-height: 200px;
}
.drawer-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--mc-text-3);
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mc-border-weak);
  margin-bottom: 14px;
}
.drawer-summary b {
  color: var(--mc-text-1);
}
.drill-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.drill-item {
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 12px 14px;
  background: #fcfcfd;
}
.di-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.di-topic {
  font-weight: 600;
  color: var(--mc-text-1);
  font-size: 13.5px;
}
.di-meta {
  font-size: 11.5px;
  color: var(--mc-text-3);
  white-space: nowrap;
}
.di-content {
  font-size: 13px;
  line-height: 1.75;
  color: var(--mc-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1280px) {
  .kpi-grid {
    grid-template-columns: repeat(4, 1fr);
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
