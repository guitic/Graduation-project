<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">发布管理</div>
        <div class="mc-page-sub">
          「已通过」与「已发布」是两个维度：通过后可暂不发布；已发布的点评也可撤回
        </div>
      </div>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </div>

    <div class="mc-card tabs-card">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 待发布 -->
        <el-tab-pane name="unpublished">
          <template #label>
            <span class="tab-label"
              >待发布 <span class="tab-count">{{ unpubTotal }}</span></span
            >
          </template>
          <el-table v-loading="unpubLoading" :data="unpubRows" stripe row-key="id">
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
            <el-table-column prop="topic" label="主题" min-width="150" show-overflow-tooltip />
            <el-table-column prop="term" label="学期" width="120" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }"><StatusTag :row="row" /></template>
            </el-table-column>
            <el-table-column label="通过时间" width="160">
              <template #default="{ row }">
                <span class="mc-mono time">{{ fmtTime(row.lastAuditAt || row.createdAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openPublish(row)"
                  >发布</el-button
                >
              </template>
            </el-table-column>

            <!-- 空态只出现一次：接管表格内置的「暂无数据」，且给出下一步 -->
            <template #empty>
              <EmptyHint
                icon="Promotion"
                title="当前没有待发布的点评"
                desc="「通过」与「发布」是两个维度：已通过的点评会先停在这里，确认后再推送给学生。"
              >
                <el-button type="primary" plain size="small" @click="goAudit">
                  去审核队列{{ auth.pendingCount ? `处理 ${auth.pendingCount} 条` : '' }}
                </el-button>
                <el-button size="small" @click="reload">刷新</el-button>
              </EmptyHint>
            </template>
          </el-table>
          <div v-if="unpubTotal > 0" class="pager">
            <el-pagination
              v-model:current-page="unpubQuery.page"
              v-model:page-size="unpubQuery.size"
              :total="unpubTotal"
              layout="total, prev, pager, next"
              background
              @current-change="loadUnpub"
            />
          </div>
        </el-tab-pane>

        <!-- 已发布 -->
        <el-tab-pane name="published">
          <template #label>
            <span class="tab-label"
              >已发布 <span class="tab-count">{{ pubTotal }}</span></span
            >
          </template>
          <el-table v-loading="pubLoading" :data="pubRows" stripe row-key="id">
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
            <el-table-column prop="topic" label="主题" min-width="150" show-overflow-tooltip />
            <el-table-column label="发布时间" width="160">
              <template #default="{ row }">
                <span class="mc-mono time">{{ fmtTime(row.publishedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button text type="warning" size="small" @click="openUnpublish(row)"
                  >撤回</el-button
                >
              </template>
            </el-table-column>

            <template #empty>
              <EmptyHint
                icon="Finished"
                title="还没有已发布的点评"
                desc="点评在「待发布」确认发布后会出现在这里；已发布的点评可随时撤回，撤回后学生立即不可见。"
              >
                <el-button size="small" @click="tab = 'unpublished'">查看待发布</el-button>
                <el-button size="small" @click="reload">刷新</el-button>
              </EmptyHint>
            </template>
          </el-table>
          <div v-if="pubTotal > 0" class="pager">
            <el-pagination
              v-model:current-page="pubQuery.page"
              v-model:page-size="pubQuery.size"
              :total="pubTotal"
              layout="total, prev, pager, next"
              background
              @current-change="loadPub"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 发布 / 撤回 弹窗 -->
    <el-dialog v-model="dlgVisible" :title="dlg.title" width="520px" :close-on-click-modal="false">
      <div class="dlg-target">
        <div class="dt-row">
          <span class="dt-k">学生</span>{{ target?.studentName }} · {{ target?.clazzName }}
        </div>
        <div class="dt-row"><span class="dt-k">主题</span>{{ target?.topic }}</div>
      </div>
      <div class="dlg-tip" :class="dlg.tone">{{ dlg.tip }}</div>
      <el-input
        v-model="reason"
        type="textarea"
        :rows="4"
        :maxlength="300"
        show-word-limit
        :placeholder="dlg.placeholder"
      />
      <div class="dlg-count" :class="{ ok: reason.trim().length >= dlg.minLen }">
        已输入 <b>{{ reason.trim().length }}</b> 字 ·
        <template v-if="dlg.minLen > 0"
          >至少 <b>{{ dlg.minLen }}</b> 字</template
        >
        <template v-else>首次发布无需理由</template>
      </div>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button :type="dlg.btnType" :loading="acting" @click="confirm">{{
          dlg.confirmText
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import EmptyHint from '../../components/EmptyHint.vue'
import StatusTag from '../../components/StatusTag.vue'
import { unpublishedPage, publishedPage, publish, unpublish } from '../../api/audit'
import { fmtTime } from '../../utils/status'
import { useAuthStore } from '../../store/auth'
import { limits, ensureLimits } from '../../utils/limits'

const auth = useAuthStore()
const router = useRouter()

const tab = ref('unpublished')

const unpubRows = ref([])
const unpubTotal = ref(0)
const unpubLoading = ref(false)
const unpubQuery = reactive({ page: 1, size: 10 })

const pubRows = ref([])
const pubTotal = ref(0)
const pubLoading = ref(false)
const pubQuery = reactive({ page: 1, size: 10 })

const dlgVisible = ref(false)
const acting = ref(false)
const target = ref(null)
const mode = ref('publish')
const reason = ref('')

const dlg = computed(() => {
  const o = limits.opinion
  if (mode.value === 'publish') {
    const rePublish = !!target.value?.everWithdrawn
    return {
      title: rePublish ? '再发布（曾撤回）' : '发布点评',
      minLen: rePublish ? o.republish : 0,
      btnType: 'primary',
      tone: 'info',
      confirmText: '确认发布',
      tip: rePublish
        ? `该点评曾被撤回，再次发布属于「再发布」，需填写理由（≥${o.republish} 字）说明为何放回。`
        : '首次发布无需理由。发布后学生立即可见，并纳入统计与分析。',
      placeholder: rePublish
        ? `请填写再发布理由（≥${o.republish} 字），如：内容已复核无误，恢复发布。`
        : '（可选）填写发布备注'
    }
  }
  return {
    title: '撤回发布',
    minLen: o.unpublish,
    btnType: 'warning',
    tone: 'warn',
    confirmText: '确认撤回',
    tip: `撤回后学生立即不可见、不再纳入统计，点评回到「待发布」。需填写理由（≥${o.unpublish} 字）。`,
    placeholder: `请填写撤回理由（≥${o.unpublish} 字），如：内容存在事实性错误，需导师核实后重新发布。`
  }
})

async function loadUnpub() {
  unpubLoading.value = true
  try {
    const r = await unpublishedPage({ ...unpubQuery })
    unpubRows.value = r.records || []
    unpubTotal.value = r.total || 0
  } catch (e) {
    /* 已提示 */
  } finally {
    unpubLoading.value = false
  }
}

async function loadPub() {
  pubLoading.value = true
  try {
    const r = await publishedPage({ ...pubQuery })
    pubRows.value = r.records || []
    pubTotal.value = r.total || 0
  } catch (e) {
    /* 已提示 */
  } finally {
    pubLoading.value = false
  }
}

function reload() {
  loadUnpub()
  loadPub()
  auth.refreshPending()
}

function onTabChange() {
  if (tab.value === 'published') loadPub()
  else loadUnpub()
}

/** 空态里的出路：待发布为空通常意味着「审核队列还没处理」 */
function goAudit() {
  router.push('/audit')
}

function openPublish(row) {
  target.value = row
  mode.value = 'publish'
  reason.value = ''
  dlgVisible.value = true
}

function openUnpublish(row) {
  target.value = row
  mode.value = 'unpublish'
  reason.value = ''
  dlgVisible.value = true
}

async function confirm() {
  const len = reason.value.trim().length
  if (len < dlg.value.minLen) {
    ElMessage.warning(`理由至少 ${dlg.value.minLen} 字`)
    return
  }
  acting.value = true
  try {
    if (mode.value === 'publish') {
      await publish(target.value.id, reason.value.trim())
      ElMessage.success('已发布')
    } else {
      await unpublish(target.value.id, reason.value.trim())
      ElMessage.success('已撤回')
    }
    dlgVisible.value = false
    reload()
  } catch (e) {
    /* 已提示 */
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  ensureLimits()
  loadUnpub()
  loadPub()
})
</script>

<style scoped>
.tabs-card {
  padding: 8px 16px 16px;
}
.tab-label {
  font-weight: 500;
}
.tab-count {
  margin-left: 6px;
  font-size: 11px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 1px 7px;
  border-radius: 8px;
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
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 4px 0;
}
.dlg-target {
  background: #fafbfc;
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.dt-row {
  font-size: 13px;
  margin-bottom: 5px;
  display: flex;
  gap: 10px;
}
.dt-k {
  color: var(--mc-text-3);
  width: 34px;
  flex-shrink: 0;
}
.dlg-tip {
  font-size: 12.5px;
  line-height: 1.6;
  padding: 9px 11px;
  border-radius: 8px;
  margin-bottom: 12px;
}
.dlg-tip.info {
  background: #e0f2fe;
  color: #0e7490;
}
.dlg-tip.warn {
  background: #fef3e2;
  color: #b45309;
}
.dlg-count {
  margin-top: 8px;
  font-size: 12px;
  color: var(--mc-text-3);
  text-align: right;
}
.dlg-count b {
  color: var(--mc-text-1);
}
.dlg-count.ok b {
  color: #12805c;
}
</style>
