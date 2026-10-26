<template>
  <div class="mc-gate">
    <div class="gate-icon">
      <el-icon><Lock /></el-icon>
    </div>
    <div class="gate-title">{{ title }}</div>
    <div class="gate-desc">{{ desc }}</div>
    <div v-if="sampleCount != null" class="gate-sample">
      当前样本 <b>{{ sampleCount }}</b> 条 · 门槛 <b>{{ minSample }}</b> 条
    </div>
  </div>
</template>

<script setup>
// 样本不足时的统一空态：明确告诉用户「不是没数据，而是数据不足以展示」
defineProps({
  title: { type: String, default: '样本量不足，暂不展示' },
  desc: {
    type: String,
    default:
      '为避免从少量点评中反推出个人信息，样本量低于阈值时聚合结果不予展示。补充点评后自动恢复。'
  },
  sampleCount: { type: [Number, String], default: null },
  minSample: { type: [Number, String], default: 5 }
})
</script>

<style scoped>
.gate-sample {
  font-size: 12px;
  color: var(--mc-text-3);
}
.gate-sample b {
  color: var(--mc-text-2);
  font-variant-numeric: tabular-nums;
}
</style>
