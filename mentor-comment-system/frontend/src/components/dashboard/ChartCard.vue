<template>
  <div class="mc-card chart-card">
    <div class="chart-head">
      <div class="chart-title">{{ title }}</div>
      <div v-if="caliber" class="mc-caliber">{{ caliber }}</div>
    </div>
    <div class="chart-body">
      <slot />
    </div>
    <div v-if="$slots.foot && footVisible" class="chart-foot">
      <slot name="foot" />
    </div>
  </div>
</template>

<script setup>
/**
 * 图表卡外壳：标题 + 口径行 + 图体 + 页脚。
 * 四张图的口径行必须与后端统计口径一致，因此统一由调用方传入而不是卡片内部拼。
 *
 * footVisible 单独控制页脚：空态（样本不足）时页脚里的占比/提示没有意义，
 * 但留一个只有虚线的空盒子看起来像渲染失败。
 */
defineProps({
  title: { type: String, required: true },
  caliber: { type: String, default: '' },
  footVisible: { type: Boolean, default: true }
})
</script>

<style scoped>
.chart-card {
  padding: 16px 18px 12px;
  display: flex;
  flex-direction: column;
}
.chart-head {
  margin-bottom: 6px;
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 5px;
}
.chart-body {
  height: 288px;
  position: relative;
}
.chart-foot {
  border-top: 1px dashed var(--mc-border);
  margin-top: 8px;
  padding-top: 10px;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
