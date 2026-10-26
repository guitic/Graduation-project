<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">审核队列</div>
        <div class="mc-page-sub">
          共 <b>{{ total }}</b> 条待审核。「通过」与「发布」是两个维度：可先通过、暂不发布
        </div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="mc-card filter-bar">
      <el-select v-model="query.term" placeholder="全部学期" clearable class="f-item" @change="onSearch">
        <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="query.teacherId" placeholder="全部导师" clearable filterable class="f-item" @change="onSearch">
        <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="搜索主题 / 学生"
        clearable
        class="f-search"
        :prefix-icon="Search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button :icon="Search" @click="onSearch">查询</el-button>
    </div>

    <div class="mc-card table-card" v-loading="loading">
      <el-table :data="rows" stripe row-key="id">
        <el-table-column label="学生" min-width="120">
          <template #default="{ row }">
            <div class="stu">
              <span class="stu-name">{{ row.studentName }}</span>
              <span class="stu-no">{{ row.studentNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="clazzName" label="班级" width="110" />
        <el-table-column prop="teacherName" label="导师" width="100" />
        <el-table-column label="主题" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.topic }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="term" label="学期" width="120" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">
            <span class="mc-mono time">{{ fmtTime(row.submitAt || row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <div class="op-group">
              <el-button text type="primary" size="small" @click="openDetail(row)">查看</el-button>
              <el-button text type="success" size="small" @click="openAction(row, 'approvePublish')">
                通过发布
              </el-button>
              <el-button text type="primary" size="small" @click="openAction(row, 'approveOnly')">
                仅通过
              </el-button>
              <el-button text type="warning" size="small" @click="openAction(row, 'return')">退回</el-button>
              <el-button text type="danger" size="small" @click="openAction(row, 'reject')">驳回</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSearch"
        />
      </div>
      <el-empty v-if="!loading && !rows.length" description="暂无待审核点评" :image-size="80" />
    </div>

    <!-- 审核动作弹窗 -->
    <el-dialog v-model="actionVisible" :title="act.title" width="540px" :close-on-click-modal="false">
      <div class="act-target">
        <div class="at-row"><span class="at-k">学生</span>{{ target?.studentName }} · {{ target?.clazzName }}</div>
        <div class="at-row"><span class="at-k">主题</span>{{ target?.topic }}</div>
        <div class="at-tip" :class="act.tone">{{ act.tip }}</div>
      </div>
      <el-input
        v-model="opinion"
        type="textarea"
        :rows="5"
        :maxlength="500"
        show-word-limit
        :placeholder="act.placeholder"
      />
      <div class="act-count" :class="{ ok: opinion.trim().length >= act.minLen }">
        已输入 <b>{{ opinion.trim().length }}</b> 字 · 至少 <b>{{ act.minLen }}</b> 字
      </div>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button :type="act.btnType" :loading="acting" @click="confirmAction">
          确认{{ act.title }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情 + 分析依据 -->
    <el-drawer v-model="detailVisible" title="点评详情与审核依据" size="600px" destroy-on-close>
      <div v-if="current" class="detail">
        <div class="detail-head">
          <div>
            <div class="d-topic">{{ current.topic }}</div>
            <div class="d-sub">
              {{ current.studentName }} · {{ current.clazzName }} · {{ current.term }} · 导师
              {{ current.teacherName }}
            </div>
          </div>
          <StatusTag :row="current" />
        </div>

        <div class="d-content">{{ current.content }}</div>

        <!-- 可解释性：情感分析依据 -->
        <div class="analysis-box" v-loading="analysisLoading">
          <div class="ab-head">
            <span>情感分析依据</span>
            <el-tag size="small" effect="plain" round>{{ analysis?.methodVersion || 'lexicon-v1' }}</el-tag>
          </div>
          <template v-if="analysis?.live">
            <div class="ab-summary">
              <SentimentTag :label="analysis.live.label" :low-confidence="analysis.live.lowConfidence" />
              <span class="ab-kv">净得分 <b class="mc-mono">{{ analysis.live.score?.toFixed(2) }}</b></span>
              <span class="ab-kv">置信度 <b class="mc-mono">{{ (analysis.live.confidence * 100).toFixed(0) }}%</b></span>
              <span class="ab-kv">正向词 <b class="mc-mono">{{ analysis.live.posHits }}</b></span>
              <span class="ab-kv">负向词 <b class="mc-mono">{{ analysis.live.negHits }}</b></span>
            </div>
            <div v-if="analysis.live.hits?.length" class="hits">
              <span class="hits-label">命中词：</span>
              <span
                v-for="(h, i) in analysis.live.hits"
                :key="i"
                class="hit"
                :class="h.polarity.toLowerCase()"
                :title="`权重 ${h.weight}${h.negated ? ' · 被否定' : ''}`"
              >
                {{ h.word }}
                <em v-if="h.negated">（否定）</em>
              </span>
            </div>
            <div v-else class="hits-empty">未命中情感词，归为中性</div>
            <div class="ab-note">
              说明：情感倾向针对「导师填写的文本」，仅用于总体趋势观察，不构成对导师或学生的评价。
            </div>
          </template>
          <el-empty v-else description="暂无分析数据" :image-size="60" />
        </div>

        <div class="d-section-title">审核记录</div>
        <AuditTimeline :audits="audits" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import StatusTag from '../../components/StatusTag.vue'
import SentimentTag from '../../components/SentimentTag.vue'
import AuditTimeline from '../../components/AuditTimeline.vue'
import { pendingPage, approve, returnBack, reject, history } from '../../api/audit'
import { detail as commentDetail } from '../../api/comment'
import { commentSentiment } from '../../api/report'
import { terms as termsApi, teachers as teachersApi } from '../../api/person'
import { useAuthStore } from '../../store/auth'
import { fmtTime } from '../../utils/status'

const auth = useAuthStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, term: '', teacherId: null, keyword: '' })
const terms = ref([])
const teachers = ref([])

const ACTIONS = {
  approvePublish: {
    title: '通过并发布',
    minLen: 5,
    btnType: 'success',
    tone: 'ok',
    tip: '通过并发布后，学生立即可见，并纳入情感分析与词频统计。',
    placeholder: '请填写通过理由（≥5 字），如：内容完整、表述规范，同意发布。'
  },
  approveOnly: {
    title: '仅通过（待发布）',
    minLen: 10,
    btnType: 'primary',
    tone: 'info',
    tip: '仅通过后进入「待发布」，学生暂不可见、不纳入统计，可在待发布列表再次发布。',
    placeholder: '请填写仅通过、暂不发布的理由（≥10 字），如：内容需进一步核实，先通过但暂不推送学生。'
  },
  return: {
    title: '退回修改',
    minLen: 10,
    btnType: 'warning',
    tone: 'warn',
    tip: '退回后导师可修改并重新提交，进入新一轮审核。',
    placeholder: '请填写退回原因与修改建议（≥10 字），如：请补充具体课程与改进建议，重新提交。'
  },
  reject: {
    title: '驳回',
    minLen: 20,
    btnType: 'danger',
    tone: 'danger',
    tip: '驳回为终态，不发布、不纳入统计。',
    placeholder: '请填写驳回理由（≥20 字），如：内容与学业指导无关或存在不当表述，经复核不予通过。'
  }
}

const actionVisible = ref(false)
const acting = ref(false)
const target = ref(null)
const actionKey = ref('approvePublish')
const opinion = ref('')
const act = computed(() => ACTIONS[actionKey.value])

const detailVisible = ref(false)
const current = ref(null)
const audits = ref([])
const analysis = ref(null)
const analysisLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await pendingPage({ ...query })
    rows.value = r.records || []
    total.value = r.total || 0
  } catch (e) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
  auth.refreshPending()
}

function onSearch() {
  query.page = 1
  load()
}

async function loadOptions() {
  try {
    const [t, te] = await Promise.all([termsApi(), teachersApi()])
    terms.value = t || []
    teachers.value = te || []
  } catch (e) {
    /* 已提示 */
  }
}

function openAction(row, key) {
  target.value = row
  actionKey.value = key
  opinion.value = ''
  actionVisible.value = true
}

async function confirmAction() {
  const len = opinion.value.trim().length
  if (len < act.value.minLen) {
    ElMessage.warning(`审核意见至少 ${act.value.minLen} 字`)
    return
  }
  acting.value = true
  try {
    const id = target.value.id
    const body = { opinion: opinion.value.trim(), publish: actionKey.value === 'approvePublish' }
    if (actionKey.value === 'approvePublish') {
      await approve(id, body)
    } else if (actionKey.value === 'approveOnly') {
      await approve(id, { ...body, publish: false })
    } else if (actionKey.value === 'return') {
      await returnBack(id, { opinion: opinion.value.trim() })
    } else if (actionKey.value === 'reject') {
      await reject(id, { opinion: opinion.value.trim() })
    }
    ElMessage.success(`已${act.value.title}`)
    actionVisible.value = false
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    acting.value = false
  }
}

async function openDetail(row) {
  detailVisible.value = true
  current.value = row
  audits.value = []
  analysis.value = null
  try {
    const d = await commentDetail(row.id)
    current.value = d.comment
    audits.value = d.audits || []
  } catch (e) {
    /* 已提示 */
  }
  analysisLoading.value = true
  try {
    analysis.value = await commentSentiment(row.id)
  } catch (e) {
    /* 已提示 */
  } finally {
    analysisLoading.value = false
  }
}

onMounted(() => {
  loadOptions()
  load()
})
</script>

<style scoped>
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
.f-search {
  width: 220px;
}
.table-card {
  padding: 6px 8px 16px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 8px 0;
}
.stu {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.stu-name {
  font-weight: 500;
}
.stu-no {
  font-size: 11.5px;
  color: var(--mc-text-3);
  font-family: 'SF Mono', Consolas, monospace;
}
.time {
  font-size: 12.5px;
  color: var(--mc-text-2);
}
.op-group {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.act-target {
  background: #fafbfc;
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.at-row {
  font-size: 13px;
  color: var(--mc-text-1);
  margin-bottom: 5px;
  display: flex;
  gap: 10px;
}
.at-k {
  color: var(--mc-text-3);
  width: 34px;
  flex-shrink: 0;
}
.at-tip {
  font-size: 12.5px;
  line-height: 1.6;
  padding: 8px 10px;
  border-radius: 8px;
  margin-top: 6px;
}
.at-tip.ok {
  background: #e6f7ee;
  color: #12805c;
}
.at-tip.info {
  background: #e0f2fe;
  color: #0e7490;
}
.at-tip.warn {
  background: #fef3e2;
  color: #b45309;
}
.at-tip.danger {
  background: #fee2e2;
  color: #b91c1c;
}
.act-count {
  margin-top: 8px;
  font-size: 12px;
  color: var(--mc-text-3);
  text-align: right;
}
.act-count b {
  color: var(--mc-text-1);
}
.act-count.ok b {
  color: #12805c;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mc-border-weak);
}
.d-topic {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 5px;
}
.d-sub {
  font-size: 12.5px;
  color: var(--mc-text-3);
}
.d-content {
  font-size: 14px;
  line-height: 1.9;
  color: var(--mc-text-1);
  white-space: pre-wrap;
  word-break: break-word;
  background: #fcfcfd;
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 14px 16px;
  margin: 14px 0;
}

.analysis-box {
  border: 1px solid #e0e7ff;
  background: #f8faff;
  border-radius: 10px;
  padding: 12px 14px;
}
.ab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}
.ab-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.ab-kv {
  font-size: 12px;
  color: var(--mc-text-3);
}
.ab-kv b {
  color: var(--mc-text-1);
}
.hits {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.hits-label {
  font-size: 12px;
  color: var(--mc-text-3);
}
.hit {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #eef2fe;
  color: #2b5ce6;
}
.hit.positive {
  background: #e6f7ee;
  color: #12805c;
}
.hit.negative {
  background: #fee2e2;
  color: #b91c1c;
}
.hit em {
  font-style: normal;
  opacity: 0.75;
}
.hits-empty {
  font-size: 12px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.ab-note {
  font-size: 11.5px;
  color: var(--mc-text-3);
  line-height: 1.6;
  border-top: 1px dashed #dbe4ff;
  padding-top: 8px;
}
.d-section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 22px 0 10px;
}
</style>
