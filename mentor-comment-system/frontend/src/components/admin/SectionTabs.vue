<template>
  <div class="mc-card section-tabs">
    <el-tabs v-model="inner" @tab-change="onChange">
      <el-tab-pane v-for="t in tabs" :key="t.name" :name="t.name">
        <template #label>
          <span class="tab-label">
            {{ t.label }}
            <!-- count 为 0 时不显示角标：一个写着「0」的灰色气泡只是噪音 -->
            <span v-if="t.count > 0" class="tab-count">{{ t.count }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

/**
 * 二级 tab 栏。
 *
 * 一级菜单只保留「审核与发布」「系统设置」两个入口，内部再用这个组件分栏。
 *
 * 内部用 ref 而不是直接把 model-value 交给 el-tabs：
 * 纯受控写法下，点击 tab 时组件内部的 active 不变，`tab-change` 也就不会触发，
 * 表现为「点了没反应」。这里让 el-tabs 自己管状态，再把变化同步出去，
 * 父级改 modelValue（如路由前进后退）时通过 watch 反向同步回来。
 */
const props = defineProps({
  modelValue: { type: String, required: true },
  /** [{ name, label, count? }] */
  tabs: { type: Array, required: true }
})
const emit = defineEmits(['update:modelValue', 'change'])

const inner = ref(props.modelValue)

watch(
  () => props.modelValue,
  (v) => {
    if (v !== inner.value) inner.value = v
  }
)

function onChange(name) {
  emit('update:modelValue', name)
  emit('change', name)
}
</script>

<style scoped>
.section-tabs {
  padding: 6px 16px 0;
  margin-bottom: 14px;
}
.section-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.section-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.section-tabs :deep(.el-tabs__item) {
  height: 44px;
  font-size: 13.5px;
}
.tab-label {
  font-weight: 500;
}
.tab-count {
  margin-left: 6px;
  font-size: 11px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 1px 7px;
  border-radius: 8px;
}
</style>
