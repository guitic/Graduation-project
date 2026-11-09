<template>
  <span v-if="label" class="mc-pill" :style="{ color: meta.color, background: bg }">
    <i class="dot" />
    {{ meta.label }}<template v-if="lowConfidence"> · 低置信</template>
  </span>
  <span v-else class="mc-pill" style="color: var(--mc-text-4); background: var(--mc-surface-3)"
    >未分析</span
  >
</template>

<script setup>
import { computed } from 'vue'
import { sentimentMeta } from '../utils/status'

const props = defineProps({
  label: { type: String, default: null },
  lowConfidence: { type: Boolean, default: false }
})

const meta = computed(() => sentimentMeta(props.label))
const bg = computed(() => {
  const map = {
    POSITIVE: 'var(--sn-pos-bg)',
    NEUTRAL: 'var(--sn-neu-bg)',
    NEGATIVE: 'var(--sn-neg-bg)'
  }
  return map[props.label] || 'var(--mc-surface-3)'
})
</script>
