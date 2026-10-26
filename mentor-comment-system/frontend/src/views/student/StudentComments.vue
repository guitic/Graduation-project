<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">我的点评</div>
        <div class="mc-page-sub">查看导师已发布给你的学业点评</div>
      </div>
      <el-select v-model="query.term" placeholder="全部学期" clearable class="term-select" @change="onSearch">
        <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
      </el-select>
    </div>

    <div v-loading="loading" class="cards">
      <div
        v-for="c in rows"
        :key="c.id"
        class="mc-card comment-card"
        @click="openDetail(c)"
      >
        <div class="cc-head">
          <div class="cc-topic">{{ c.topic }}</div>
          <div class="cc-time mc-mono">{{ fmtDate(c.publishedAt) }}</div>
        </div>
        <div class="cc-meta">
          <span class="cc-teacher">
            <el-icon><User /></el-icon>{{ c.teacherName || '导师' }}
          </span>
          <span class="cc-term">{{ c.term }}</span>
        </div>
        <div class="cc-content">{{ c.content }}</div>
        <div class="cc-foot">
          <span>查看完整点评</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <el-empty
        v-if="!loading && !rows.length"
        description="暂无已发布的点评"
        :image-size="96"
      />
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
          <span><el-icon><User /></el-icon>{{ detail.teacherName }}</span>
          <span><el-icon><Calendar /></el-icon>{{ detail.term }}</span>
          <span><el-icon><Clock /></el-icon>{{ fmtTime(detail.publishedAt) }}</span>
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
import { pageStudent, studentDetail } from '../../api/comment'
import { terms as termsApi } from '../../api/person'
import { fmtTime, fmtDate } from '../../utils/status'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 9, term: '' })
const terms = ref([])

const detailVisible = ref(false)
const detail = ref(null)

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
.cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  min-height: 200px;
}
.comment-card {
  padding: 18px 18px 14px;
  cursor: pointer;
  transition: all 0.16s ease;
  display: flex;
  flex-direction: column;
  min-height: 190px;
}
.comment-card:hover {
  box-shadow: var(--mc-shadow-hover);
  transform: translateY(-2px);
  border-color: #d7e0fb;
}
.cc-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}
.cc-topic {
  font-size: 15px;
  font-weight: 600;
  color: var(--mc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-time {
  font-size: 11.5px;
  color: var(--mc-text-3);
  flex-shrink: 0;
}
.cc-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.cc-teacher {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--mc-text-2);
}
.cc-term {
  font-size: 11.5px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 1px 8px;
  border-radius: 8px;
}
.cc-content {
  font-size: 13px;
  line-height: 1.75;
  color: var(--mc-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.cc-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  font-size: 12px;
  color: var(--mc-primary);
  margin-top: 10px;
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
@media (max-width: 1200px) {
  .cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 760px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>
