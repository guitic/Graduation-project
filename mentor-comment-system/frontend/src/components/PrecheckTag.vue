<template>
  <!--
    compact 模式只显示风险分：审核队列一行要塞 8 列，
    「建议通过 · 27」这样的完整标签会把预审列撑到 132px，
    而分数本身已经能区分轻重缓急，全文放进 tooltip 即可。
  -->
  <span v-if="view" class="mc-pill" :style="{ color: meta.fg, background: meta.bg }">
    <i class="dot" />
    <template v-if="compact">{{ view.riskScore }}</template>
    <template v-else
      >{{ meta.label }}<template v-if="showScore"> · {{ view.riskScore }}</template></template
    >
  </span>
  <span v-else class="mc-pill idle">未预审</span>
</template>

<script setup>
import { computed } from 'vue'
import { precheckMeta } from '../utils/status'

const props = defineProps({
  view: { type: Object, default: null },
  showScore: { type: Boolean, default: false },
  /** 只显示分数（列表页用），全文交给外部 tooltip */
  compact: { type: Boolean, default: false }
})

const meta = computed(() => precheckMeta(props.view?.riskLevel))
</script>

<style scoped>
.idle {
  color: var(--mc-text-4);
  background: #f7f8fa;
}
</style>
