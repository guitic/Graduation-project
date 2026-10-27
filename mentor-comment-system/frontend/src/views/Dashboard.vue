<template>
  <div v-loading="loading" class="mc-page">
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
    <KpiGrid :kpis="kpis" />

    <!-- 图表区：每张图自己管理实例与门槛，口径行由卡片下发 -->
    <div class="chart-grid">
      <SentimentCard :data="dash?.sentiment" :min-sample="minSample" />
      <TrendCard :data="dash?.trend" :min-sample="minSample" @drill="openTrendDrill" />
      <AuditStatCard :data="dash?.auditStat" />
      <WordCloudCard :data="dash?.wordCloud" :min-sample="minSample" @drill="openWordDrill" />
    </div>

    <DrillDrawer v-model="drawerVisible" :drill="drill" :loading="drawerLoading" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft } from '@element-plus/icons-vue'
import KpiGrid from '../components/dashboard/KpiGrid.vue'
import SentimentCard from '../components/dashboard/SentimentCard.vue'
import TrendCard from '../components/dashboard/TrendCard.vue'
import AuditStatCard from '../components/dashboard/AuditStatCard.vue'
import WordCloudCard from '../components/dashboard/WordCloudCard.vue'
import DrillDrawer from '../components/dashboard/DrillDrawer.vue'
import { dashboard, trendPoint, wordDetail, rebuild } from '../api/report'
import { terms, teachers, clazzOptions } from '../api/person'
import { fmtTime } from '../utils/status'
import { limits } from '../utils/limits'

const loading = ref(false)
const rebuilding = ref(false)
const dash = ref(null)

const filters = reactive({ term: '', teacherId: null, clazzId: null, months: 6 })
const options = reactive({ terms: [], teachers: [], clazz: [] })

// 门槛以接口下发的 minSample 为准；本地 limits 只是接口未返回时的兜底，不写死数字
const minSample = computed(() => dash.value?.sentiment?.minSample ?? limits.minSample)

const kpis = computed(() => {
  const k = dash.value?.kpi
  if (!k) return []
  return [
    { key: 'total', label: '点评总数', value: k.total, foot: '含草稿/待审/退回/驳回', tone: '' },
    { key: 'published', label: '已发布', value: k.published, foot: '纳入统计与分析', tone: 'ok' },
    { key: 'pending', label: '待审核', value: k.pending, foot: '等待教务处理', tone: 'warn' },
    {
      key: 'unpublished',
      label: '待发布',
      value: k.unpublished,
      foot: '已通过·未推送',
      tone: 'info'
    },
    { key: 'returned', label: '退回修改', value: k.returned, foot: '待导师修改重提', tone: 'warn' },
    { key: 'rejected', label: '已驳回', value: k.rejected, foot: '终态·不发布', tone: 'danger' },
    {
      key: 'lowConfidence',
      label: '低置信',
      value: k.lowConfidence,
      foot: '建议人工复核',
      tone: 'info'
    }
  ]
})

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
    // 一次请求拿全四张图的数据：四张图共用同一口径，分四次请求必然出现"饼图 56、趋势 58"
    dash.value = await dashboard(scopeParams())
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

// ---------------- 桶级下钻 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drill = reactive({
  title: '',
  sampleCount: 0,
  allowed: false,
  minSample: limits.minSample,
  comments: []
})

async function openDrill(title, fetcher) {
  drawerVisible.value = true
  drawerLoading.value = true
  Object.assign(drill, { title, sampleCount: 0, allowed: false, comments: [] })
  try {
    const r = await fetcher()
    Object.assign(drill, {
      title,
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

function openTrendDrill(month) {
  if (!month) return
  openDrill(`${month} 点评明细`, () => trendPoint(month, scopeParams()))
}

function openWordDrill(word) {
  openDrill(`含「${word}」的点评`, () => wordDetail(word, scopeParams()))
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

onMounted(async () => {
  await loadOptions()
  await reload()
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

/* 图表网格 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 1280px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
