<template>
  <div v-loading="loading" class="mc-page">
    <!-- 页头 -->
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">我的点评画像</div>
        <div class="mc-page-sub">
          仅统计本人提交的已发布点评；样本不足时聚合结果不展示，避免从少量文本反推学生情况
        </div>
      </div>
      <div class="head-actions">
        <span v-if="dash?.asOf" class="asof">
          统计截至 <b class="mc-mono">{{ fmtTime(dash.asOf) }}</b>
        </span>
      </div>
    </div>

    <!-- 筛选：只有学期与时间窗，没有导师/班级 —— 这是个人视角，不是管理视角 -->
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

    <!-- KPI：5 项，不含"待发布"（教务待办）与"已驳回"（终态统计归教务） -->
    <KpiGrid :kpis="kpis" :columns="5" />

    <div class="chart-grid">
      <SentimentCard :data="dash?.sentiment" :min-sample="minSample" />
      <TrendCard :data="dash?.trend" :min-sample="minSample" :drillable="false" />
      <WordCloudCard :data="dash?.wordCloud" :min-sample="minSample" :drillable="false" />
    </div>

    <div class="mc-card note-card">
      <div class="note-title">口径说明</div>
      <ul class="note-list">
        <li>{{ dash?.scopeNote || '仅统计本人已发布点评。' }}</li>
        <li>
          情感分析针对的是<b>点评文本本身</b>的倾向，不是对学生的评价结论；负向占比偏高通常意味着近期点评更多在指出问题。
        </li>
        <li>本页不提供跨导师明细下钻——明细涉及学生个人信息，只在教务侧按样本门槛开放。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { RefreshLeft } from '@element-plus/icons-vue'
import KpiGrid from '../../components/dashboard/KpiGrid.vue'
import SentimentCard from '../../components/dashboard/SentimentCard.vue'
import TrendCard from '../../components/dashboard/TrendCard.vue'
import WordCloudCard from '../../components/dashboard/WordCloudCard.vue'
import { myDashboard } from '../../api/report'
import { terms } from '../../api/person'
import { fmtTime } from '../../utils/status'
import { limits } from '../../utils/limits'

/**
 * 导师端个人画像。
 *
 * 与教务看板共用同一批图表组件，但数据源是 `/api/report/my-dashboard`：
 * teacherId 由后端从登录态取，前端无法指定。这不是"少传一个参数"，
 * 而是让越权在协议层面就不成立。
 */
const loading = ref(false)
const dash = ref(null)

const filters = reactive({ term: '', months: 6 })
const options = reactive({ terms: [] })

// 门槛以接口下发的 minSample 为准，本地常量只是兜底
const minSample = computed(() => dash.value?.sentiment?.minSample ?? limits.minSample)

const kpis = computed(() => {
  const k = dash.value?.kpi
  if (!k) return []
  return [
    {
      key: 'published',
      label: '已发布',
      value: k.published,
      foot: '学生可见·纳入统计',
      tone: 'ok'
    },
    { key: 'draft', label: '草稿', value: k.draft, foot: '尚未提交审核', tone: '' },
    { key: 'pending', label: '待审核', value: k.pending, foot: '等待教务处理', tone: 'warn' },
    { key: 'returned', label: '退回修改', value: k.returned, foot: '需按意见重提', tone: 'warn' },
    {
      key: 'lowConfidence',
      label: '待复核',
      value: k.lowConfidence,
      foot: '情感落在灰区',
      tone: 'info'
    }
  ]
})

function scopeParams() {
  const p = { months: filters.months }
  if (filters.term) p.term = filters.term
  return p
}

async function loadOptions() {
  try {
    options.terms = (await terms()) || []
  } catch (e) {
    /* 选项失败不阻塞主报表 */
  }
}

async function reload() {
  loading.value = true
  try {
    dash.value = await myDashboard(scopeParams())
  } catch (e) {
    /* 错误提示已统一处理 */
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.term = ''
  filters.months = 6
  reload()
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

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-grid > :last-child {
  grid-column: 1 / -1;
}

.note-card {
  margin-top: 16px;
  padding: 14px 18px;
}
.note-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--mc-text-2);
}
.note-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--mc-text-3);
}
.note-list b {
  color: var(--mc-text-2);
}

@media (max-width: 1280px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .chart-grid > :last-child {
    grid-column: auto;
  }
}
</style>
