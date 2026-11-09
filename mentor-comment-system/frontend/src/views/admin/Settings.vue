<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">系统设置</div>
        <div class="mc-page-sub">{{ SUBTITLE[tab] }}</div>
      </div>
    </div>

    <SectionTabs v-model="tab" :tabs="tabs" @change="onTabChange" />

    <!--
      两项都是低频配置/审计能力，占两个一级菜单会让侧边栏的重心偏向运维而不是教学业务。
      合并后侧边栏只剩「统计报表 / 审核与发布 / 师生管理 / 系统设置」四项。
    -->
    <AiConfig v-if="tab === 'ai'" embedded />
    <OperLog v-else embedded />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionTabs from '../../components/admin/SectionTabs.vue'
import AiConfig from './AiConfig.vue'
import OperLog from './OperLog.vue'

const SUBTITLE = {
  ai: '大模型是可选增强：不配 Key 系统也能完整运行，导师端自动回落到规则实现。',
  log: '点评提交、审核、发布等关键动作的留痕。只读，不提供修改与删除入口。'
}

const route = useRoute()
const router = useRouter()

const tab = ref(normalize(route.query.tab))

const tabs = [
  { name: 'ai', label: 'AI 配置' },
  { name: 'log', label: '操作日志' }
]

function normalize(v) {
  return ['ai', 'log'].includes(v) ? v : 'ai'
}

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
