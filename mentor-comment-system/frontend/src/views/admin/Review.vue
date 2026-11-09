<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">审核与发布</div>
        <div class="mc-page-sub">{{ SUBTITLE[tab] }}</div>
      </div>
    </div>

    <SectionTabs v-model="tab" :tabs="tabs" @change="onTabChange" />

    <!--
      三个 tab 是同一条流程的三段：待审核 → 待发布 → 已发布。
      拆成三个菜单时，教务每走一步都要换一次侧边栏；合并后是一条可以连续走的路径。
    -->
    <AuditQueue v-if="tab === 'pending'" embedded />
    <Unpublished v-else :key="tab" embedded :force-tab="tab" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionTabs from '../../components/admin/SectionTabs.vue'
import AuditQueue from './AuditQueue.vue'
import Unpublished from './Unpublished.vue'
import { useAuthStore } from '../../store/auth'

const SUBTITLE = {
  pending: '导师提交的点评在这里审核。「通过」与「发布」是两个维度：可先通过、暂不发布。',
  unpublished: '已通过但尚未推送给学生的点评。确认发布后学生立即可见，并纳入统计与分析。',
  published: '已发布给学生的点评。可随时撤回，撤回后学生立即不可见、不再纳入统计。'
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const tab = ref(normalize(route.query.tab))

/** 只有「待审核」带角标：它是有明确行动指向的一栏，其余两栏的数字看了也不会驱动操作 */
const tabs = computed(() => [
  { name: 'pending', label: '待审核', count: auth.pendingCount },
  { name: 'unpublished', label: '待发布' },
  { name: 'published', label: '已发布' }
])

function normalize(v) {
  return ['pending', 'unpublished', 'published'].includes(v) ? v : 'pending'
}

/** tab 状态写进 query：刷新页面、复制链接、前进后退都停在原处 */
function onTabChange(name) {
  router.replace({ query: { ...route.query, tab: name } })
}

watch(
  () => route.query.tab,
  (v) => {
    const next = normalize(v)
    if (next !== tab.value) tab.value = next
  }
)
</script>
