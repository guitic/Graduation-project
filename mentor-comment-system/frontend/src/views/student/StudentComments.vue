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
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { User, ArrowRight, Calendar, Clock } from '@element-plus/icons-vue'
import EmptyHint from '../../components/EmptyHint.vue'
import { pageStudent, studentDetail } from '../../api/comment'
import { terms as termsApi } from '../../api/person'
import { fmtTime } from '../../utils/status'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 9, term: '' })
const terms = ref([])

const detailVisible = ref(false)
const detail = ref(null)

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
  detail.value = c
  try {
    const d = await studentDetail(c.id)
    detail.value = d
  } catch (e) {
    /* 已提示 */
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
  border-color: #d7e0fb;
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
