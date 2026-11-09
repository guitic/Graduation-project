<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="620px"
    destroy-on-close
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="emit('closed')"
  >
    <div v-loading="loading" class="kd-body">
      <div class="kd-head">
        <div class="kd-count">
          共 <b class="mc-mono">{{ total }}</b> 条
          <div v-if="detail?.caliber" class="kd-caliber">口径：{{ detail.caliber }}</div>
        </div>
        <!-- 有对应处理页面的指标才给跳转：退回/驳回没有独立列表页，抽屉就是它们的唯一入口 -->
        <el-button v-if="jump" type="primary" plain size="small" @click="goPage">
          {{ jump.text }}
        </el-button>
      </div>

      <div v-if="rows.length" class="kd-list">
        <div v-for="c in rows" :key="c.id" class="kd-item">
          <div class="kdi-head">
            <span class="kdi-topic">{{ c.topic }}</span>
            <StatusTag :row="c" />
          </div>
          <div class="kdi-meta">
            <span>{{ c.studentName }} · {{ c.clazzName }} · 导师 {{ c.teacherName }}</span>
            <span class="kdi-time mc-mono">{{ fmtTime(timeOf(c)) }}</span>
          </div>
          <div class="kdi-content">{{ c.content }}</div>
          <div v-if="c.sentimentLabel || c.lastOpinion" class="kdi-tags">
            <!-- 未分析的点评不挂「未分析」胶囊：待审/退回本来就没有情感结果，标出来只是噪音 -->
            <SentimentTag
              v-if="c.sentimentLabel"
              :label="c.sentimentLabel"
              :low-confidence="c.lowConfidence"
            />
            <span v-if="c.lastOpinion" class="kdi-opinion" :title="c.lastOpinion">
              审核意见：{{ c.lastOpinion }}
            </span>
          </div>
        </div>
      </div>

      <EmptyHint
        v-else-if="!loading"
        compact
        :icon="emptyIcon"
        :title="emptyTitle"
        :desc="emptyDesc"
      />

      <div v-if="total > size" class="kd-pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StatusTag from '../StatusTag.vue'
import SentimentTag from '../SentimentTag.vue'
import EmptyHint from '../EmptyHint.vue'
import { kpiDetail } from '../../api/report'
import { fmtTime } from '../../utils/status'

/**
 * KPI 卡明细抽屉：点「待审核 8」就在这里列出这 8 条。
 *
 * 口径不在前端算：label / caliber / total / records 全部由后端按「卡片同一套条件」返回，
 * 因此这里的条数与卡片数字恒等。前端只负责呈现与翻页。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 当前点击的 KPI 卡：{ key, label, value } */
  kpi: { type: Object, default: null },
  /** 与 dashboard 相同的筛选范围（term / teacherId / clazzId） */
  scope: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'closed'])

const router = useRouter()

const loading = ref(false)
const detail = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

/** 只有存在对应处理页面的指标才给「去处理」，其余靠抽屉看即可 */
const JUMP = {
  pending: { tab: 'pending', text: '去审核队列处理' },
  unpublished: { tab: 'unpublished', text: '去待发布' },
  published: { tab: 'published', text: '去已发布管理' },
  lowConfidence: { tab: 'published', text: '去已发布核对' }
}

const jump = computed(() => JUMP[props.kpi?.key] || null)
const title = computed(() => `${detail.value?.label || props.kpi?.label || '指标'}明细`)

const emptyIcon = computed(() => (props.kpi?.key === 'pending' ? 'DocumentChecked' : 'Document'))

/** 空态标题按指标写成人话：「点评总数」直接套模板会变成「没有点评总数点评」 */
const EMPTY_TITLE = {
  total: '当前没有任何点评',
  published: '当前没有已发布点评',
  pending: '当前没有待审核点评',
  unpublished: '当前没有待发布点评',
  returned: '当前没有退回修改的点评',
  rejected: '当前没有被驳回的点评',
  lowConfidence: '当前没有低置信点评'
}
const emptyTitle = computed(() => EMPTY_TITLE[props.kpi?.key] || '当前没有匹配的点评')

const emptyDesc = computed(() => {
  const key = props.kpi?.key
  if (key === 'pending')
    return '导师提交点评后会出现在这里；可先确认「待发布 / 已发布」两栏是否有积压'
  if (key === 'unpublished') return '已通过的点评会先停在待发布，确认后才会推送给学生'
  if (key === 'lowConfidence')
    return '低置信来自情感分析的灰区判定，本次统计范围内没有落在灰区的已发布点评'
  if (key === 'returned' || key === 'rejected')
    return '这两种状态由教务在审核队列中产生，当前没有记录'
  return '换个学期 / 导师 / 班级再试，或点右上角刷新统计'
})

/** 不同状态的时间含义不同：已发布看发布时间，待审看提交时间，其余看最后更新时间 */
function timeOf(c) {
  return c.publishedAt || c.submitAt || c.updatedAt
}

/* 竞态保护：连续点两张卡片时，只认最后一次请求的结果 */
let seq = 0

async function load() {
  if (!props.kpi?.key) return
  const my = ++seq
  loading.value = true
  try {
    const r = await kpiDetail(props.kpi.key, props.scope, page.value, size.value)
    if (my !== seq) return
    detail.value = r
    rows.value = r.records || []
    total.value = r.total || 0
  } catch (e) {
    /* 已提示 */
  } finally {
    if (my === seq) loading.value = false
  }
}

function onSizeChange() {
  page.value = 1
  load()
}

function goPage() {
  if (!jump.value) return
  emit('update:modelValue', false)
  router.push({ path: '/review', query: { tab: jump.value.tab } })
}

// 打开时从第一页开始
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    page.value = 1
    load()
  }
)

// 抽屉开着时点了另一张卡：换指标必须回到第一页，且先清空上一类的行，
// 否则 loading 期间会短暂显示"待审核的列表 + 已发布的标题"
watch(
  () => props.kpi?.key,
  (key) => {
    if (!props.modelValue || !key) return
    page.value = 1
    detail.value = null
    rows.value = []
    total.value = 0
    load()
  }
)

// 抽屉开着时改了学期/导师/班级：卡片数字已经变了，明细必须跟着变，否则两边对不上
watch(
  () => props.scope,
  () => {
    if (!props.modelValue) return
    page.value = 1
    load()
  },
  { deep: true }
)
</script>

<style scoped>
.kd-body {
  min-height: 220px;
}
.kd-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mc-border-weak);
  margin-bottom: 14px;
}
.kd-count {
  font-size: 13px;
  color: var(--mc-text-2);
}
.kd-count b {
  color: var(--mc-text-1);
  font-size: 15px;
}
.kd-caliber {
  margin-top: 5px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--mc-text-3);
}
.kd-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kd-item {
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--mc-surface-2);
}
.kdi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
}
.kdi-topic {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--mc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kdi-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.kdi-time {
  white-space: nowrap;
}
/* 明细只给「这批是什么」的判断依据，长文截断——完整正文在审核详情里看 */
.kdi-content {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--mc-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.kdi-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 9px;
  flex-wrap: wrap;
}
.kdi-opinion {
  font-size: 11.5px;
  color: var(--mc-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.kd-pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px 2px 0;
}
</style>
