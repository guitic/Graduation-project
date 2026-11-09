<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">我的点评</div>
        <div class="mc-page-sub">查看导师已发布给你的学业点评</div>
      </div>
      <el-select
        v-model="query.term"
        placeholder="全部学期"
        clearable
        class="term-select"
        @change="onSearch"
      >
        <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
      </el-select>
    </div>

    <!--
      单列阅读流，而不是 3 列卡片网格。
      学生通常只有 1–5 条点评：3 列网格会让 2/3 屏幕空着，而卡片又把正文截断到 3 行，
      该给内容的地方给了留白、该留白的地方塞了字。点评是长文本，横向越宽越难读，
      因此限制在 880px 并居左，日期做锚点。
    -->
    <div v-loading="loading" class="read-list">
      <article v-for="c in rows" :key="c.id" class="mc-card read-card" @click="openDetail(c)">
        <div class="date-col">
          <b class="mc-mono">{{ mdOf(c.publishedAt) }}</b>
          <span>{{ yearOf(c.publishedAt) }}</span>
        </div>
        <div class="rc-body">
          <h3 class="rc-title">{{ c.topic }}</h3>
          <div class="rc-meta">
            <span class="rc-teacher">
              <el-icon><User /></el-icon>{{ c.teacherName || '导师' }}
            </span>
            <span class="rc-term">{{ c.term }}</span>
            <!-- 0 是合法值（帮助不大），不能用真值判断 -->
            <span
              v-if="c.feedbackHelpful !== null && c.feedbackHelpful !== undefined"
              class="rc-fb"
            >
              <el-icon><ChatDotSquare /></el-icon>已反馈
            </span>
          </div>
          <p class="rc-text">{{ c.content }}</p>
          <div class="rc-more">
            查看完整点评<el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </article>

      <div v-if="!loading && !rows.length" class="mc-card">
        <EmptyHint
          icon="Document"
          title="还没有收到点评"
          desc="导师提交并经教务审核通过后，点评会发布到这里。可以先看看其他同学常见的问题。"
        />
      </div>
    </div>

    <div v-if="total > query.size" class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </div>

    <!-- 详情：仅正文 / 导师 / 发布时间 / 学期 / 主题 -->
    <el-dialog v-model="detailVisible" width="600px" destroy-on-close>
      <template #header>
        <div class="dlg-title">{{ detail?.topic }}</div>
      </template>
      <div v-if="detail" class="detail-body">
        <div class="db-meta">
          <span
            ><el-icon><User /></el-icon>{{ detail.teacherName }}</span
          >
          <span
            ><el-icon><Calendar /></el-icon>{{ detail.term }}</span
          >
          <span
            ><el-icon><Clock /></el-icon>{{ fmtTime(detail.publishedAt) }}</span
          >
        </div>
        <div class="db-content">{{ detail.content }}</div>

        <!--
          反馈区：对象是「这条点评对我的帮助」，不是「对导师的评价」。
          所以文案刻意避免"满意/不满意"这类打分措辞，也不展示任何聚合排名。
        -->
        <section v-if="fbReady" class="fb-block">
          <div class="fb-head">
            <span class="fb-title">这条点评对你有帮助吗？</span>
            <span class="fb-note">仅用于了解点评是否有帮助，不作为对导师的评价</span>
          </div>

          <template v-if="fbEditing">
            <div class="fb-choices">
              <button
                type="button"
                :class="['fb-choice', { on: fbForm.helpful === 1 }]"
                @click="fbForm.helpful = 1"
              >
                <el-icon><Select /></el-icon>有帮助
              </button>
              <button
                type="button"
                :class="['fb-choice', { on: fbForm.helpful === 0 }]"
                @click="fbForm.helpful = 0"
              >
                <el-icon><CloseBold /></el-icon>帮助不大
              </button>
            </div>
            <el-input
              v-model="fbForm.content"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              resize="none"
              placeholder="想补充说明或提问（选填）"
            />
            <div class="fb-actions">
              <el-button v-if="hasFeedback" text size="small" @click="cancelEdit">取消</el-button>
              <el-button type="primary" size="small" :loading="fbSubmitting" @click="submitFb">
                提交反馈
              </el-button>
            </div>
          </template>

          <div v-else class="fb-done">
            <div class="fb-done-row">
              <el-tag :type="fb?.helpful === 1 ? 'success' : 'info'" effect="plain" size="small">
                {{ fb?.helpful === 1 ? '有帮助' : '帮助不大' }}
              </el-tag>
              <span class="fb-time">{{ fmtTime(fb?.createdAt) }}</span>
              <el-button link type="primary" size="small" @click="startEdit">修改</el-button>
            </div>
            <p v-if="fb?.content" class="fb-text">{{ fb.content }}</p>
            <p v-else class="fb-text muted">（未填写补充说明）</p>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User,
  ArrowRight,
  Calendar,
  Clock,
  ChatDotSquare,
  Select,
  CloseBold
} from '@element-plus/icons-vue'
import EmptyHint from '../../components/EmptyHint.vue'
import { pageStudent, studentDetail, submitFeedback } from '../../api/comment'
import { terms as termsApi } from '../../api/person'
import { fmtTime } from '../../utils/status'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 9, term: '' })
const terms = ref([])

const detailVisible = ref(false)
const detail = ref(null)

// ---------------- 反馈 ----------------
const fb = ref(null)
const fbEditing = ref(false)
const fbSubmitting = ref(false)
const fbReady = ref(false)
const fbForm = reactive({ helpful: null, content: '' })
const hasFeedback = computed(() => !!fb.value)

/** 把详情里带回的反馈同步到本地表单：未反馈直接进编辑态，已反馈先进只读态 */
function syncFb() {
  fb.value = detail.value?.myFeedback || null
  fbEditing.value = !fb.value
  fbForm.helpful = fb.value ? fb.value.helpful : null
  fbForm.content = fb.value?.content || ''
}

function startEdit() {
  fbEditing.value = true
}

function cancelEdit() {
  fbEditing.value = false
  fbForm.helpful = fb.value ? fb.value.helpful : null
  fbForm.content = fb.value?.content || ''
}

async function submitFb() {
  if (fbForm.helpful === null) {
    ElMessage.warning('请选择这条点评是否对你有帮助')
    return
  }
  fbSubmitting.value = true
  try {
    await submitFeedback(detail.value.id, {
      helpful: fbForm.helpful === 1,
      content: fbForm.content || null
    })
    ElMessage.success('反馈已提交')
    // 重新拉详情：时间戳与内容以服务端为准（空白内容会被归一成 null）
    detail.value = await studentDetail(detail.value.id)
    syncFb()
    // 列表上的「已反馈」标记也要跟着变
    load()
  } catch (e) {
    /* 错误提示已由请求拦截器统一弹出 */
  } finally {
    fbSubmitting.value = false
  }
}

/** 日期锚点：左边大号 MM-DD，下面年份，正文里不再重复时间 */
function mdOf(v) {
  return v ? String(v).replace('T', ' ').slice(5, 10) : '—'
}
function yearOf(v) {
  return v ? String(v).replace('T', ' ').slice(0, 4) : ''
}

async function load() {
  loading.value = true
  try {
    const r = await pageStudent({ ...query })
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

async function loadTerms() {
  try {
    terms.value = (await termsApi()) || []
  } catch (e) {
    /* 已提示 */
  }
}

async function openDetail(c) {
  detailVisible.value = true
  // 先用列表行渲染正文，避免弹窗空白；反馈区等详情回来再显示，
  // 否则会先闪一下「未反馈」的按钮，再跳成「已反馈」的只读态。
  detail.value = c
  fbReady.value = false
  try {
    const d = await studentDetail(c.id)
    detail.value = d
  } catch (e) {
    /* 已提示 */
  } finally {
    syncFb()
    fbReady.value = true
  }
}

onMounted(() => {
  loadTerms()
  load()
})
</script>

<style scoped>
.term-select {
  width: 160px;
}
.read-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 880px;
  min-height: 200px;
}
.read-card {
  display: flex;
  gap: 16px;
  padding: 18px 20px;
  cursor: pointer;
  transition:
    box-shadow 0.16s ease,
    border-color 0.16s ease;
}
.read-card:hover {
  box-shadow: var(--mc-shadow-hover);
  border-color: var(--mc-hover-border);
}
.date-col {
  flex: 0 0 52px;
  text-align: center;
  line-height: 1.35;
  padding-top: 2px;
}
.date-col b {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--mc-text-2);
  letter-spacing: 0.2px;
}
.date-col span {
  font-size: 11.5px;
  color: var(--mc-text-3);
}
.rc-body {
  flex: 1;
  min-width: 0;
}
.rc-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--mc-text-1);
  margin: 0 0 6px;
  line-height: 1.45;
}
.rc-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.rc-teacher {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--mc-text-2);
}
.rc-term {
  font-size: 11.5px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 1px 8px;
  border-radius: var(--mc-radius-pill);
}
.rc-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--mc-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rc-more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 10px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--mc-primary);
}
.pager {
  display: flex;
  justify-content: center;
  padding-top: 24px;
}
.dlg-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--mc-text-1);
}
.db-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mc-border-weak);
  margin-bottom: 16px;
}
.db-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--mc-text-3);
}
.db-content {
  font-size: 14px;
  line-height: 1.95;
  color: var(--mc-text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 列表上的「已反馈」标记 */
.rc-fb {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  color: var(--sn-pos);
  background: var(--mc-bg);
  padding: 1px 8px;
  border-radius: var(--mc-radius-pill);
}

/* 反馈区 */
.fb-block {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px dashed var(--mc-border-weak);
}
.fb-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.fb-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mc-text-1);
}
.fb-note {
  font-size: 11.5px;
  color: var(--mc-text-4);
}
.fb-choices {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.fb-choice {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: 1px solid var(--mc-border);
  border-radius: var(--mc-radius-pill);
  background: var(--mc-card);
  color: var(--mc-text-2);
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    color 0.15s,
    background 0.15s;
}
.fb-choice:hover {
  border-color: var(--mc-primary);
  color: var(--mc-primary);
}
.fb-choice.on {
  border-color: var(--mc-primary);
  background: rgba(43, 92, 230, 0.08);
  color: var(--mc-primary);
  font-weight: 600;
}
.fb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.fb-done-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.fb-time {
  font-size: 11.5px;
  color: var(--mc-text-4);
}
.fb-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--mc-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
.fb-text.muted {
  color: var(--mc-text-4);
}
@media (max-width: 760px) {
  .read-card {
    padding: 16px;
    gap: 12px;
  }
  .date-col {
    flex: 0 0 44px;
  }
}
</style>
