<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">我的点评</div>
        <div class="mc-page-sub">
          填写学生学业点评，保存草稿或提交教务审核；退回修改后可按意见重提
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建点评</el-button>
    </div>

    <!-- 筛选 -->
    <div class="mc-card filter-bar">
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        class="f-item"
        @change="onSearch"
      >
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select
        v-model="query.term"
        placeholder="全部学期"
        clearable
        class="f-item"
        @change="onSearch"
      >
        <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="搜索主题 / 学生姓名"
        clearable
        class="f-search"
        :prefix-icon="Search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button :icon="Search" @click="onSearch">查询</el-button>
    </div>

    <!-- 表格 -->
    <div v-loading="loading" class="mc-card table-card">
      <!-- 整行可点进详情：列表页最高频的动作是「看看这条写了什么」，
           只让「查看」两个字可点等于逼用户瞄准 -->
      <el-table
        :data="rows"
        stripe
        style="width: 100%"
        row-key="id"
        row-class-name="row-clickable"
        @row-click="openDetail"
      >
        <el-table-column label="学生" min-width="124">
          <template #default="{ row }">
            <div class="stu">
              <span class="stu-name">{{ row.studentName }}</span>
              <span class="stu-no">{{ row.studentNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="clazzName" label="班级" width="100" />
        <el-table-column prop="topic" label="指导主题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="term" label="学期" width="112" />
        <el-table-column label="状态" width="108">
          <template #default="{ row }"><StatusTag :row="row" /></template>
        </el-table-column>
        <el-table-column label="情感" width="104">
          <template #default="{ row }">
            <SentimentTag :label="row.sentimentLabel" :low-confidence="row.lowConfidence" />
          </template>
        </el-table-column>
        <el-table-column label="更新" width="108">
          <template #default="{ row }">
            <span class="mc-mono time">{{ fmtShort(row.updatedAt || row.createdAt) }}</span>
          </template>
        </el-table-column>
        <!-- 查看 / 编辑 / 提交 常驻；删除是破坏性低频操作，收进「更多」，
             避免 4 个文字按钮把列撑到 196px 后被裁掉 -->
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <!-- @click.stop：整行可点后，按钮点击不能再冒泡成一次 openDetail -->
            <div class="op-cell" @click.stop>
              <el-button text type="primary" size="small" @click="openDetail(row)">查看</el-button>
              <el-button
                v-if="editable(row)"
                text
                type="primary"
                size="small"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="editable(row)"
                text
                type="success"
                size="small"
                @click="doSubmit(row)"
              >
                提交
              </el-button>
              <el-dropdown
                v-if="row.status === 'DRAFT'"
                trigger="click"
                @command="(c) => c === 'delete' && doDelete(row)"
              >
                <el-button size="small" :icon="MoreFilled" class="more-btn" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete">删除草稿</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑点评' : '新建点评'"
      width="620px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-row">
          <el-form-item label="学生" prop="studentId" class="grow">
            <el-select
              v-model="form.studentId"
              placeholder="选择指导关系内的学生"
              filterable
              class="w-full"
            >
              <el-option
                v-for="s in students"
                :key="s.id"
                :label="`${s.name}（${s.studentNo} · ${s.clazzName || ''}）`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学期" prop="term" class="w-220">
            <el-select
              v-model="form.term"
              placeholder="选择学期"
              filterable
              allow-create
              class="w-full"
            >
              <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="指导主题" prop="topic">
          <el-input
            v-model="form.topic"
            maxlength="50"
            show-word-limit
            placeholder="如：本学期学业规划与课程选择"
          />
        </el-form-item>
        <el-form-item label="点评内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="请填写对该学生的学业点评（20~2000 字）"
          />
        </el-form-item>

        <!-- 摘要建议：只读辅助，不写入点评正文，也不参与提交 -->
        <div class="ai-bar">
          <el-button size="small" :icon="MagicStick" :loading="aiLoading" @click="genSuggestion">
            {{ aiStatus.llmAvailable ? 'AI 生成摘要建议' : '生成摘要建议' }}
          </el-button>
          <span class="ai-hint">
            {{
              aiStatus.llmAvailable
                ? '由大模型根据正文生成，仅供参考，不会自动写入'
                : '当前未启用大模型，使用规则生成（结果可解释、可复现）'
            }}
          </span>
        </div>

        <div v-if="aiResult" class="ai-card">
          <div class="ai-head">
            <span class="ai-src" :class="aiResult.source === 'LLM' ? 'is-llm' : 'is-rule'">
              {{ aiResult.source === 'LLM' ? '模型生成' : '规则生成' }}
            </span>
            <span class="ai-meta">
              {{ aiResult.model || 'lexicon-v1' }} · {{ aiResult.latencyMs }}ms
            </span>
            <div class="ai-spacer" />
            <el-button text size="small" @click="aiResult = null">收起</el-button>
          </div>
          <div class="ai-summary">{{ aiResult.summary }}</div>
          <ul class="ai-tips">
            <li v-for="(t, i) in aiResult.suggestions" :key="i">{{ t }}</li>
          </ul>
          <div v-if="aiResult.fallbackReason" class="ai-fallback">
            已降级为规则生成：{{ aiResult.fallbackReason }}
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="saveAndSubmit">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" title="点评详情" size="560px" destroy-on-close>
      <div v-if="current" class="detail">
        <div class="detail-head">
          <div>
            <div class="d-topic">{{ current.topic }}</div>
            <div class="d-sub">
              {{ current.studentName }} · {{ current.clazzName }} · {{ current.term }}
            </div>
          </div>
          <StatusTag :row="current" />
        </div>

        <div class="detail-tags">
          <SentimentTag :label="current.sentimentLabel" :low-confidence="current.lowConfidence" />
          <span class="d-meta">提交：{{ fmtTime(current.submitAt) }}</span>
          <span class="d-meta">发布：{{ fmtTime(current.publishedAt) }}</span>
        </div>

        <div class="d-content">{{ current.content }}</div>

        <div v-if="current.lastOpinion" class="last-opinion">
          <el-icon><ChatDotSquare /></el-icon>
          <div>
            <div class="lo-head">最近审核意见 · {{ current.lastAuditor || '—' }}</div>
            <div class="lo-body">{{ current.lastOpinion }}</div>
          </div>
        </div>

        <div class="d-section-title">审核记录</div>
        <AuditTimeline :audits="audits" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, ChatDotSquare, MagicStick, MoreFilled } from '@element-plus/icons-vue'
import StatusTag from '../../components/StatusTag.vue'
import SentimentTag from '../../components/SentimentTag.vue'
import AuditTimeline from '../../components/AuditTimeline.vue'
import { pageTeacher, detail, save, update, submit, remove } from '../../api/comment'
import { myStudents, terms as termsApi } from '../../api/person'
import { summary as aiSummary, status as aiStatusApi } from '../../api/ai'
import { fmtTime, fmtShort } from '../../utils/status'

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '退回修改', value: 'RETURNED' },
  { label: '已驳回', value: 'REJECTED' }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '', term: '', keyword: '' })

const students = ref([])
const terms = ref([])

const formVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ studentId: null, term: '', topic: '', content: '' })
const rules = {
  studentId: [{ required: true, message: '请选择学生', trigger: 'change' }],
  term: [{ required: true, message: '请填写学期', trigger: 'change' }],
  topic: [{ required: true, message: '请填写指导主题', trigger: 'blur' }],
  content: [
    { required: true, message: '请填写点评内容', trigger: 'blur' },
    { min: 20, max: 2000, message: '点评内容需 20~2000 字', trigger: 'blur' }
  ]
}

const detailVisible = ref(false)
const current = ref(null)
const audits = ref([])

// ---------------- 摘要建议 ----------------
const aiLoading = ref(false)
const aiResult = ref(null)
const aiStatus = ref({ llmAvailable: false })

async function loadAiStatus() {
  try {
    aiStatus.value = await aiStatusApi()
  } catch (e) {
    // 状态接口失败不影响写点评，按"未启用模型"处理
    aiStatus.value = { llmAvailable: false }
  }
}

async function genSuggestion() {
  const text = (form.content || '').trim()
  if (text.length < 10) {
    ElMessage.warning('先写点评内容，至少 10 个字再生成摘要')
    return
  }
  aiLoading.value = true
  try {
    aiResult.value = await aiSummary({ content: text, topic: form.topic })
  } catch (e) {
    /* 已提示 */
  } finally {
    aiLoading.value = false
  }
}

function editable(row) {
  return row.status === 'DRAFT' || row.status === 'RETURNED'
}

async function load() {
  loading.value = true
  try {
    const r = await pageTeacher({ ...query })
    rows.value = r.records || []
    total.value = r.total || 0
  } catch (e) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

async function loadOptions() {
  try {
    const [s, t] = await Promise.all([myStudents(), termsApi()])
    students.value = s || []
    terms.value = t || []
  } catch (e) {
    /* 已提示 */
  }
}

function resetForm() {
  form.studentId = null
  form.term = terms.value[0] || ''
  form.topic = ''
  form.content = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  aiResult.value = null
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.studentId = row.studentId
  form.term = row.term
  form.topic = row.topic
  form.content = row.content
  aiResult.value = null
  formVisible.value = true
}

async function persist(submitFlag) {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, submit: submitFlag }
      if (editingId.value) {
        await update(editingId.value, payload)
      } else {
        await save(payload)
      }
      ElMessage.success(submitFlag ? '已提交审核' : '草稿已保存')
      formVisible.value = false
      load()
    } catch (e) {
      /* 已提示 */
    } finally {
      saving.value = false
    }
  })
}

const saveDraft = () => persist(false)
const saveAndSubmit = () => persist(true)

async function doSubmit(row) {
  try {
    await ElMessageBox.confirm('提交后进入待审核，审核期间不可修改。确认提交？', '提交审核', {
      type: 'info',
      confirmButtonText: '提交',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await submit(row.id)
    ElMessage.success('已提交审核')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除该草稿？', '删除草稿', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

async function openDetail(row) {
  detailVisible.value = true
  current.value = row
  audits.value = []
  try {
    const d = await detail(row.id)
    current.value = d.comment
    audits.value = d.audits || []
  } catch (e) {
    /* 已提示 */
  }
}

onMounted(() => {
  loadOptions()
  loadAiStatus()
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

/* ---------------- 摘要建议 ---------------- */
.ai-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: -8px 0 14px;
}
.ai-hint {
  font-size: 12px;
  color: var(--mc-text-3);
  line-height: 1.5;
}
.ai-card {
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  background: var(--mc-bg);
  padding: 12px 14px;
  margin-bottom: 6px;
}
.ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.ai-src {
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 5px;
}
.ai-src.is-llm {
  color: #2b5ce6;
  background: rgba(43, 92, 230, 0.1);
}
.ai-src.is-rule {
  color: #b45309;
  background: rgba(180, 83, 9, 0.1);
}
.ai-meta {
  font-size: 11.5px;
  color: var(--mc-text-3);
  font-family: 'SF Mono', Consolas, monospace;
}
.ai-spacer {
  flex: 1;
}
.ai-summary {
  font-size: 13px;
  line-height: 1.7;
  color: var(--mc-text-1);
  margin-bottom: 6px;
}
.ai-tips {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.85;
  color: var(--mc-text-2);
}
.ai-fallback {
  margin-top: 8px;
  font-size: 11.5px;
  color: #b45309;
}
.f-search {
  width: 240px;
}
.table-card {
  padding: 6px 8px 16px;
}
/* 操作列里 3 个文字按钮默认左右各 8px 内边距 + 12px 间距，
   176px 装不下会折行；收紧到 6px / 8px 后单行放得下，行高恢复一致 */
.table-card :deep(.el-button.is-text) {
  padding-left: 6px;
  padding-right: 6px;
}
.table-card :deep(.el-button + .el-button) {
  margin-left: 8px;
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
.more-btn {
  margin-left: 4px;
  padding: 5px 6px;
}
.op-cell {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.form-row {
  display: flex;
  gap: 14px;
}
.grow {
  flex: 1;
}
.w-220 {
  width: 220px;
}
.w-full {
  width: 100%;
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
.detail-tags {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 0;
}
.d-meta {
  font-size: 12px;
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
}
.last-opinion {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  padding: 12px 14px;
  color: #9a3412;
}
.last-opinion .el-icon {
  margin-top: 2px;
}
.lo-head {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
}
.lo-body {
  font-size: 13px;
  line-height: 1.7;
  color: #7c2d12;
}
.d-section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 22px 0 10px;
}
</style>
