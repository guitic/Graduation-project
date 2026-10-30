<template>
  <div class="kpi-grid" :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }">
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
 *
 * columns 可调是因为教务看 7 个流程指标、导师只看 5 个（没有"待发布"和"驳回终态"的位置），
 * 写死 7 列会让导师页右侧空两格，看起来像数据没加载出来。
 */
defineProps({
  kpis: { type: Array, default: () => [] },
  columns: { type: Number, default: 7 }
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
  min-width: 0; /* 窄列下数字/标签不允许把卡片撑破 */
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
/* 只有「需要处理」的两类指标给数值上色：一屏扫过去先看到待审积压与退回，
   正常态保持中性——全部上色等于没上色 */
.kpi.warn .kpi-value {
  color: #b45309;
}
.kpi.danger .kpi-value {
  color: #b91c1c;
}
.kpi-foot {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-top: 7px;
}

/* 7 个指标在 1440 下每卡约 150px，仍可读；再窄就换行成 4 列，
   避免每卡挤到 120px 以下导致标签折行 */
@media (max-width: 1360px) {
  .kpi-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
