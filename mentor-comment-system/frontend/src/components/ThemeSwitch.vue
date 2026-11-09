<template>
  <!--
    主题切换：亮色 / 暗色 / 跟随系统 三档合一。
    做成下拉而不是「点一下轮换」：轮换看不出当前是哪档，也看不出下一档是什么，
    三档循环尤其容易让人以为按钮坏了。
  -->
  <el-dropdown trigger="click" placement="bottom-end" popper-class="theme-menu" @command="setMode">
    <button
      type="button"
      class="theme-btn"
      :title="`主题：${current.label}`"
      :aria-label="`主题：${current.label}，点击切换`"
    >
      <el-icon :size="17">
        <component :is="current.icon" />
      </el-icon>
    </button>

    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="o in OPTIONS"
          :key="o.value"
          :command="o.value"
          :class="{ 'is-active': mode === o.value }"
        >
          <el-icon><component :is="o.icon" /></el-icon>
          <span>{{ o.label }}</span>
          <el-icon v-if="mode === o.value" class="tm-check"><Check /></el-icon>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { Sunny, Moon, Monitor, Check } from '@element-plus/icons-vue'
import { useTheme } from '../composables/useTheme'

const OPTIONS = [
  { value: 'light', label: '亮色', icon: Sunny },
  { value: 'dark', label: '暗色', icon: Moon },
  { value: 'auto', label: '跟随系统', icon: Monitor }
]

const { mode, setMode } = useTheme()

// 按钮图标反映「当前选的是哪一档」，不是「点了会变成什么」
const current = computed(() => OPTIONS.find((o) => o.value === mode.value) || OPTIONS[2])
</script>
