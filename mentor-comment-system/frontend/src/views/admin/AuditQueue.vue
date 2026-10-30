<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">审核队列</div>
        <div class="mc-page-sub">
          {{
            isFiltered ? `筛选出 ${total} 条待审核` : `共 ${total} 条待审核`
          }}。「通过」与「发布」是两个维度：可先通过、暂不发布。AI 预审只给建议，不改变点评状态
        </div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="mc-card filter-bar">
      <el-select
        v-model="query.term"
        placeholder="全部学期"
        clearable
        class="f-item"
        @change="onSearch"
      >
        <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="query.teacherId"
        placeholder="全部导师"
        clearable
        filterable
        class="f-item"
        @change="onSearch"
      >
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

    <div v-loading="loading" class="mc-card table-card">
      <!--
        列宽预算：固定列合计 640px，只留「主题」一个弹性列吃剩余空间。
        1440 视口下表格可用宽度约 1124px，旧版 8 列合计 1222px 必然溢出，
        右侧 fixed 列会把「AI 预审」压掉一半——这正是修复前的问题。
      -->
      <el-table
        :data="rows"
        stripe
        row-key="id"
        row-class-name="row-clickable"
        @row-click="openDetail"
      >
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
        <el-table-column label="主题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mc-link" @click.stop="openDetail(row)">{{ row.topic }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="term" label="学期" width="110" />
        <el-table-column label="提交" width="108">
          <template #default="{ row }">
            <span class="mc-mono time">{{ fmtShort(row.submitAt || row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预审" width="76" align="center">
          <template #default="{ row }">
            <el-tooltip :content="precheckTip(row)" placement="top">
              <span class="pre-cell"><PrecheckTag :view="preMap[row.id]" compact /></span>
            </el-tooltip>
          </template>
        </el-table-column>
        <!-- 操作：1 个主操作 + 更多。同排 5 个文字按钮必然折行，且四种颜色并列难分辨 -->
        <el-table-column label="操作" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <!-- @click.stop：整行可点后，按钮点击不能再冒泡成一次 openDetail -->
            <div class="op-cell" @click.stop>
              <el-button type="primary" size="small" @click="openAction(row, 'approvePublish')">
                通过发布
              </el-button>
              <el-dropdown trigger="click" @command="(c) => openAction(row, c)">
                <el-button size="small" :icon="MoreFilled" class="more-btn" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="approveOnly">仅通过（待发布）</el-dropdown-item>
                    <el-dropdown-item command="return">退回修改</el-dropdown-item>
                    <el-dropdown-item command="reject" divided>驳回</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>

        <!-- 空态只出现一次：接管表格内置的「暂无数据」。
             筛选后为空 ≠ 队列为空，两种空态必须分开说，否则用户会以为点评被清空了 -->
        <template #empty>
          <EmptyHint
            v-if="isFiltered"
            icon="Search"
            title="没有匹配的点评"
            desc="当前筛选条件下没有待审核记录。换个关键词，或清除筛选看全部待审。"
          >
            <el-button type="primary" plain size="small" @click="clearFilters">清除筛选</el-button>
          </EmptyHint>
          <EmptyHint
            v-else
            icon="DocumentChecked"
            title="当前没有待审核的点评"
            desc="导师提交点评后会出现在这里。可以先在「待发布 / 已撤回」确认已通过的点评是否需要推送。"
          >
            <el-button size="small" @click="load">刷新</el-button>
          </EmptyHint>
        </template>
      </el-table>

      <div v-if="total > 0" class="pager">
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
    </div>

    <!-- 审核动作弹窗 -->
    <el-dialog
      v-model="actionVisible"
      :title="act.title"
      width="540px"
      :close-on-click-modal="false"
    >
      <div class="act-target">
        <div class="at-row">
          <span class="at-k">学生</span>{{ target?.studentName }} · {{ target?.clazzName }}
        </div>
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

        <!-- 正文经 escapeHtml 转义后再插入定位标记，contentHtml 里不拼接任何未转义内容 -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="d-content" v-html="contentHtml" />

        <!-- AI 预审建议：规则引擎产出，只给建议，不改变点评状态 -->
        <div v-loading="precheckLoading" class="precheck-box">
          <div class="pb-head">
            <span>AI 预审建议</span>
            <span class="pb-ver">
              规则引擎 {{ precheck?.ruleVersion || 'rule-v1.0' }}
              <el-button text type="primary" size="small" @click="runPrecheck(true)">
                重新预审
              </el-button>
            </span>
          </div>
          <template v-if="precheck">
            <div class="pb-summary">
              <PrecheckTag :view="precheck" show-score />
              <span class="ab-kv"
                >建议动作 <b>{{ precheck.suggestActionLabel }}</b></span
              >
              <span class="ab-kv"
                >耗时 <b class="mc-mono">{{ precheck.costMs }} ms</b></span
              >
              <el-tag v-if="precheck.cached" size="small" effect="plain" round>复用缓存</el-tag>
            </div>

            <ul v-if="precheck.issues?.length" class="pb-issues">
              <li v-for="(it, i) in precheck.issues" :key="i" :class="it.level.toLowerCase()">
                <span class="pi-level">{{ levelLabel(it.level) }}</span>
                <span class="pi-msg">{{ it.message }}</span>
              </li>
            </ul>
            <div v-else class="pb-clean">未发现明显问题</div>

            <div class="pb-draft">
              <div class="pbd-head">
                <span>建议意见（草稿）</span>
                <el-button size="small" type="primary" plain @click="applyDraft">
                  填入审核意见
                </el-button>
              </div>
              <div class="pbd-text">{{ precheck.suggestOpinion }}</div>
            </div>

            <div class="ab-note">
              说明：AI
              预审只提供建议，不改变点评状态；最终处理由教务确认，审核意见需人工改写后提交。
            </div>
          </template>
          <EmptyHint
            v-else
            compact
            icon="MagicStick"
            title="暂无预审结果"
            desc="规则引擎在提交时自动预审，也可点右上角「重新预审」现场计算"
          />
        </div>

        <!-- 可解释性：情感分析依据 -->
        <div v-loading="analysisLoading" class="analysis-box">
          <div class="ab-head">
            <span>情感分析依据</span>
            <el-tag size="small" effect="plain" round>{{
              analysis?.methodVersion || 'lexicon-v1'
            }}</el-tag>
          </div>
          <template v-if="analysis?.live">
            <div class="ab-summary">
              <SentimentTag
                :label="analysis.live.label"
                :low-confidence="analysis.live.lowConfidence"
              />
              <span class="ab-kv"
                >净得分 <b class="mc-mono">{{ analysis.live.score?.toFixed(2) }}</b></span
              >
              <span class="ab-kv"
                >置信度
                <b class="mc-mono">{{ (analysis.live.confidence * 100).toFixed(0) }}%</b></span
              >
              <span class="ab-kv"
                >正向词 <b class="mc-mono">{{ analysis.live.posHits }}</b></span
              >
              <span class="ab-kv"
                >负向词 <b class="mc-mono">{{ analysis.live.negHits }}</b></span
              >
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
          <EmptyHint
            v-else
            compact
            icon="DataAnalysis"
            title="暂无分析数据"
            desc="情感分析在点评发布时执行，未发布的点评不参与统计"
          />
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
import { Refresh, Search, MoreFilled } from '@element-plus/icons-vue'
import StatusTag from '../../components/StatusTag.vue'
import SentimentTag from '../../components/SentimentTag.vue'
import AuditTimeline from '../../components/AuditTimeline.vue'
import PrecheckTag from '../../components/PrecheckTag.vue'
import EmptyHint from '../../components/EmptyHint.vue'
import { pendingPage, approve, returnBack, reject } from '../../api/audit'
import { precheckOne, precheckBatch } from '../../api/precheck'
import { detail as commentDetail } from '../../api/comment'
import { commentSentiment } from '../../api/report'
import { terms as termsApi, teachers as teachersApi } from '../../api/person'
import { useAuthStore } from '../../store/auth'
import { fmtShort, precheckLevelLabel } from '../../utils/status'
import { limits, ensureLimits } from '../../utils/limits'

const auth = useAuthStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, term: '', teacherId: null, keyword: '' })
const terms = ref([])
const teachers = ref([])

const ACTIONS = computed(() => {
  const o = limits.opinion
  return {
    approvePublish: {
      title: '通过并发布',
      minLen: o.approvePublish,
      btnType: 'success',
      tone: 'ok',
      tip: '通过并发布后，学生立即可见，并纳入情感分析与词频统计。',
      placeholder: `请填写通过理由（≥${o.approvePublish} 字），如：内容完整、表述规范，同意发布。`
    },
    approveOnly: {
      title: '仅通过（待发布）',
      minLen: o.approveOnly,
      btnType: 'primary',
      tone: 'info',
      tip: '仅通过后进入「待发布」，学生暂不可见、不纳入统计，可在待发布列表再次发布。',
      placeholder: `请填写仅通过、暂不发布的理由（≥${o.approveOnly} 字），如：内容需进一步核实，先通过但暂不推送学生。`
    },
    return: {
      title: '退回修改',
      minLen: o.return,
      btnType: 'warning',
      tone: 'warn',
      tip: '退回后导师可修改并重新提交，进入新一轮审核。',
      placeholder: `请填写退回原因与修改建议（≥${o.return} 字），如：请补充具体课程与改进建议，重新提交。`
    },
    reject: {
      title: '驳回',
      minLen: o.reject,
      btnType: 'danger',
      tone: 'danger',
      tip: '驳回为终态，不发布、不纳入统计。',
      placeholder: `请填写驳回理由（≥${o.reject} 字），如：内容与学业指导无关或存在不当表述，经复核不予通过。`
    }
  }
})

const actionVisible = ref(false)
const acting = ref(false)
const target = ref(null)
const actionKey = ref('approvePublish')
const opinion = ref('')
const act = computed(() => ACTIONS.value[actionKey.value])

const detailVisible = ref(false)
const current = ref(null)
const audits = ref([])
const analysis = ref(null)
const analysisLoading = ref(false)

/* AI 预审：列表页一次批量预审当前页，详情抽屉复用同一份结果 */
const preMap = reactive({})
const precheck = ref(null)
const precheckLoading = ref(false)

/* 建议动作 → 已有审核按钮。预审不新增任何动作，只决定"把哪个按钮先点亮" */
const DRAFT_ACTION = {
  APPROVE_PUBLISH: 'approvePublish',
  APPROVE_ONLY: 'approveOnly',
  RETURN: 'return',
  REJECT: 'reject'
}

function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/**
 * 正文 + 预审问题高亮。
 *
 * 先转义再插入 mark：正文是用户输入，直接把 span 拼进 HTML 等于开一个 XSS 口子。
 * 重叠区间按"先到先得"跳过，避免嵌套 mark 导致高亮错乱。
 */
const contentHtml = computed(() => {
  const text = current.value?.content || ''
  const spans = (precheck.value?.issues || [])
    .filter((i) => i.start >= 0 && i.end > i.start && i.end <= text.length)
    .sort((a, b) => a.start - b.start)
  if (!spans.length) return escapeHtml(text)

  let out = ''
  let cursor = 0
  for (const s of spans) {
    if (s.start < cursor) continue
    out += escapeHtml(text.slice(cursor, s.start))
    out += `<mark class="ai-mark ${String(s.level).toLowerCase()}">${escapeHtml(
      text.slice(s.start, s.end)
    )}</mark>`
    cursor = s.end
  }
  return out + escapeHtml(text.slice(cursor))
})

function levelLabel(level) {
  return precheckLevelLabel(level)
}

/** 列表里的预审列只放分数，完整结论（等级 + 建议动作 + 耗时）放 tooltip */
function precheckTip(row) {
  const v = preMap[row.id]
  if (!v) return '尚未预审，打开详情可现场预审'
  const level =
    { PASS: '建议通过', REVIEW: '建议复核', RETURN: '建议退回' }[v.riskLevel] || '预审完成'
  return `${level} · 风险分 ${v.riskScore} · 建议动作「${v.suggestActionLabel || '—'}」`
}

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
  refreshPrecheck()
  auth.refreshPending()
}

/** 批量预审当前页：静默执行，失败不打断列表（预审是旁路能力） */
async function refreshPrecheck() {
  const ids = rows.value.map((r) => r.id)
  if (!ids.length) return
  try {
    const list = await precheckBatch(ids)
    ;(list || []).forEach((v) => {
      preMap[v.commentId] = v
    })
  } catch (e) {
    /* 已提示 */
  }
}

async function runPrecheck(force = false) {
  if (!current.value) return
  precheckLoading.value = true
  try {
    const v = await precheckOne(current.value.id, force)
    precheck.value = v
    if (v) preMap[v.commentId] = v
  } catch (e) {
    /* 已提示 */
  } finally {
    precheckLoading.value = false
  }
}

/** 把建议意见填入审核弹窗。只填不提交：字数校验与人工确认照旧 */
function applyDraft() {
  if (!precheck.value || !current.value) return
  const key = DRAFT_ACTION[precheck.value.suggestAction] || 'approveOnly'
  detailVisible.value = false
  openAction(current.value, key)
  opinion.value = precheck.value.suggestOpinion || ''
}

function onSearch() {
  query.page = 1
  load()
}

/** 是否处于筛选态：决定「共 N 条」还是「筛选出 N 条」，也决定空态文案 */
const isFiltered = computed(() => !!(query.term || query.teacherId || (query.keyword || '').trim()))

function clearFilters() {
  query.term = ''
  query.teacherId = null
  query.keyword = ''
  onSearch()
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
  precheck.value = preMap[row.id] || null
  try {
    const d = await commentDetail(row.id)
    current.value = d.comment
    audits.value = d.audits || []
  } catch (e) {
    /* 已提示 */
  }
  // 预审与情感分析互不依赖：预审命中缓存时毫秒返回，未预审过则现场计算
  runPrecheck(false)
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
  ensureLimits()
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
/* 预审列：胶囊居中，整列只有色点 + 分数 */
.pre-cell {
  display: inline-flex;
}
.more-btn {
  margin-left: 4px;
  padding: 5px 6px;
}
.op-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
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

/* AI 预审建议卡：与"情感分析依据"（靛蓝）区分，这里用中性灰底，避免暗示两者同权重 */
.precheck-box {
  border: 1px solid #e6e9f0;
  background: #f9fafc;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.pb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}
.pb-ver {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--mc-text-3);
}
.pb-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.pb-issues {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
}
.pb-issues li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--mc-text-2);
  border-bottom: 1px dashed var(--mc-border-weak);
}
.pb-issues li:last-child {
  border-bottom: none;
}
.pi-level {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  border-radius: 4px;
  background: #eef2fe;
  color: var(--mc-primary);
}
.pb-issues li.warn .pi-level {
  background: #fef3e2;
  color: #b45309;
}
.pb-issues li.blocker .pi-level {
  background: #fee2e2;
  color: #b91c1c;
}
.pi-msg {
  flex: 1;
}
.pb-clean {
  margin-bottom: 10px;
  font-size: 12.5px;
  color: #12805c;
}
.pb-draft {
  padding-top: 10px;
  border-top: 1px dashed #e3e8f0;
}
.pbd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12.5px;
  color: var(--mc-text-3);
}
.pbd-text {
  padding: 10px 12px;
  margin-bottom: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--mc-text-1);
  background: #fff;
  border: 1px solid var(--mc-border-weak);
  border-radius: 8px;
}
/* 预审问题在原文中的定位高亮（v-html 内容需要 :deep 才命中） */
.d-content :deep(.ai-mark) {
  padding: 0 1px;
  background: #fff3bf;
  border-radius: 3px;
}
.d-content :deep(.ai-mark.blocker) {
  background: #ffd8d8;
}
</style>
