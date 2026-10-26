<template>
  <span class="mc-pill" :style="{ color: meta.fg, background: meta.bg }">
    <i class="dot" />
    {{ meta.label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { resolveStatus } from '../utils/status'

const props = defineProps({
  row: { type: Object, required: true }
})
const meta = computed(() => resolveStatus(props.row))
</script>
