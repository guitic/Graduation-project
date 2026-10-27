<template>
  <div class="kpi-grid">
    <div v-for="k in kpis" :key="k.key" class="mc-card kpi" :class="k.tone">
      <div class="kpi-label">{{ k.label }}</div>
      <div class="kpi-value mc-mono">{{ k.value }}</div>
      <div class="kpi-foot">{{ k.foot }}</div>
    </div>
  </div>
</template>

<script setup>
/**
 * KPI 卡组。数值由父组件按同一份 dashboard 响应计算，
 * 避免"卡片自己再请求一次"导致 KPI 与图表不同步。
 */
defineProps({
  kpis: { type: Array, default: () => [] }
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  padding: 14px 16px;
  position: relative;
  overflow: hidden;
}
.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--mc-border);
}
.kpi.ok::before {
  background: #12805c;
}
.kpi.warn::before {
  background: #d97706;
}
.kpi.info::before {
  background: #0e7490;
}
.kpi.danger::before {
  background: #b91c1c;
}
.kpi-label {
  font-size: 12.5px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: var(--mc-text-1);
}
.kpi-foot {
  font-size: 11.5px;
  color: var(--mc-text-4);
  margin-top: 7px;
}

@media (max-width: 1280px) {
  .kpi-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
