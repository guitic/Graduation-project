<template>
  <div class="mc-page">
    <div class="mc-page-head" :class="{ 'head-compact': embedded }">
      <div v-if="!embedded">
        <div class="mc-page-title">操作日志</div>
        <div class="mc-page-sub">
          点评提交、审核、退回、驳回、发布、撤回等关键动作的留痕。只读，不提供修改与删除入口
        </div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="mc-card filter-bar">
      <el-select
        v-model="query.action"
        placeholder="全部动作"
        clearable
        filterable
        class="f-item"
        @change="onSearch"
      >
        <el-option v-for="a in actionOptions" :key="a" :label="actionLabel(a)" :value="a" />
      </el-select>
      <el-select
        v-model="query.role"
        placeholder="全部角色"
        clearable
        class="f-item f-role"
        @change="onSearch"
      >
        <el-option label="教务管理员" value="ADMIN" />
        <el-option label="学业导师" value="TEACHER" />
        <el-option label="学生" value="STUDENT" />
      </el-select>
      <el-input
        v-model="query.username"
        placeholder="搜索操作人账号"
        clearable
        class="f-search"
        :prefix-icon="Search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        range-separator="至"
        class="f-date"
        @change="onSearch"
      />
      <el-button :icon="Search" @click="onSearch">查询</el-button>
      <el-button v-if="isFiltered" text @click="clearFilters">清除筛选</el-button>
    </div>

    <div v-loading="loading" class="mc-card table-card">
      <el-table :data="rows" stripe row-key="id">
        <el-table-column label="时间" width="164">
          <template #default="{ row }">
            <span class="mc-mono time">{{ fmtTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">
            <span v-if="row.username">{{ row.username }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="104">
          <template #default="{ row }">
            <el-tag v-if="row.role" :type="roleTagType(row.role)" effect="plain" size="small">
              {{ roleLabel(row.role) }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="150">
          <template #default="{ row }">
            <span :class="['act', { rejected: isRejected(row.action) }]">
              {{ actionLabel(row.action) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="120">
          <template #default="{ row }">
            <span v-if="row.targetType" class="mc-mono target">
              {{ targetLabel(row.targetType)
              }}<template v-if="row.targetId"> #{{ row.targetId }}</template>
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="详情" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.detail }">{{ row.detail || '—' }}</span>
          </template>
        </el-table-column>

        <!-- 空态区分「没日志」与「筛没了」：两者对用户的含义完全不同 -->
        <template #empty>
          <EmptyHint
            v-if="isFiltered"
            icon="Search"
            title="没有匹配的操作记录"
            desc="当前筛选条件下没有日志。换个动作或操作人，也可以清除筛选看全部。"
          >
            <el-button type="primary" plain size="small" @click="clearFilters">清除筛选</el-button>
          </EmptyHint>
          <EmptyHint
            v-else
            icon="Tickets"
            title="还没有操作记录"
            desc="点评提交、审核、发布等关键动作发生后，会在这里留下可追溯的痕迹。"
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
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import * as operLogApi from '../../api/operLog'
import EmptyHint from '../../components/EmptyHint.vue'
import { fmtTime, ACTION_META } from '../../utils/status'

defineProps({ embedded: { type: Boolean, default: false } })

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const actionOptions = ref([])
const dateRange = ref([])

const query = reactive({
  action: '',
  username: '',
  role: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 20
})

const isFiltered = computed(
  () => !!(query.action || query.username || query.role || query.startDate || query.endDate)
)

const ROLE_LABEL = { ADMIN: '教务管理员', TEACHER: '学业导师', STUDENT: '学生' }
const ROLE_TAG = { ADMIN: 'info', TEACHER: 'success', STUDENT: '' }
const TARGET_LABEL = {
  COMMENT: '点评',
  USER: '账号',
  TEACHER: '导师',
  STUDENT: '学生',
  BIND: '指导关系'
}

function roleLabel(r) {
  return ROLE_LABEL[r] || r
}
function roleTagType(r) {
  return ROLE_TAG[r] || ''
}
function targetLabel(t) {
  return TARGET_LABEL[t] || t
}
/** 「(拒绝)」后缀由写入侧生成，代表越权或校验失败被拦下——这类记录要能一眼挑出来 */
function isRejected(action) {
  return String(action || '').includes('(拒绝)')
}

/**
 * 动作名转中文。
 *
 * 日志表存的是 SUBMIT / RETURN / PUBLISH 这类常量（与审核事件流同一套），
 * 直接展示等于让教务看代码；「修改密码」这类中文动作不在映射表里，原样返回。
 */
function actionLabel(action) {
  const raw = String(action || '')
  const rejected = raw.includes('(拒绝)')
  const base = raw.replace('(拒绝)', '')
  const label = ACTION_META[base]?.label || base || '—'
  return rejected ? `${label}（拒绝）` : label
}

async function load() {
  loading.value = true
  try {
    const r = await operLogApi.page({
      action: query.action || undefined,
      username: query.username || undefined,
      role: query.role || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}

async function loadActions() {
  try {
    actionOptions.value = await operLogApi.actions()
  } catch (e) {
    // 下拉候选拉取失败不影响主列表，保留空候选（仍可手输）
    actionOptions.value = []
  }
}

function syncDateRange() {
  if (dateRange.value && dateRange.value.length === 2) {
    query.startDate = dateRange.value[0]
    query.endDate = dateRange.value[1]
  } else {
    query.startDate = ''
    query.endDate = ''
  }
}

function onSearch() {
  syncDateRange()
  query.page = 1
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

function clearFilters() {
  query.action = ''
  query.username = ''
  query.role = ''
  query.startDate = ''
  query.endDate = ''
  dateRange.value = []
  query.page = 1
  load()
}

onMounted(() => {
  load()
  loadActions()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.f-item {
  width: 168px;
}
.f-role {
  width: 132px;
}
.f-search {
  width: 200px;
}
.f-date {
  width: 260px;
}
.table-card {
  padding: 4px 4px 12px;
}
.time {
  font-size: 12.5px;
  color: var(--mc-text-2);
}
.act {
  font-size: 13px;
  color: var(--mc-text-1);
}
.act.rejected {
  color: var(--st-rejected-fg);
  font-weight: 600;
}
.target {
  font-size: 12.5px;
  color: var(--mc-text-2);
}
.muted {
  color: var(--mc-text-4);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 4px;
}
</style>
