<template>
  <!--
    统一空态。原来各页混用 el-empty（Element 灰色插画）与 GateEmpty（自绘圆形图标），
    同一次演示里出现两种空态风格，像是两个团队做的。
    这里只保留自绘一套：图标 + 标题 + 说明 +（可选）下一步动作。
  -->
  <div class="mc-gate" :class="{ compact }">
    <div class="gate-icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div class="gate-title">{{ title }}</div>
    <div v-if="desc" class="gate-desc">{{ desc }}</div>
    <div v-if="$slots.default" class="gate-actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** Element Plus 图标组件名，如 'Document' / 'ChatDotSquare' */
  icon: { type: String, default: 'Document' },
  title: { type: String, default: '暂无数据' },
  desc: { type: String, default: '' },
  /** 抽屉、弹窗等窄容器里用紧凑版：图标更小、上下留白更少 */
  compact: { type: Boolean, default: false }
})
</script>
