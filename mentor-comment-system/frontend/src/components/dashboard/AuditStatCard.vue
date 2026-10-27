<template>
  <ChartCard title="审核状态分布" caliber="口径：全量点评 · 「已发布 / 待发布」为两个独立态">
    <div ref="el" class="chart-canvas" />
  </ChartCard>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import ChartCard from './ChartCard.vue'
import { useChart } from '../../composables/useChart'

/**
 * 审核状态分布（横向条形）。
 *
 * 这一张是全量口径，不走隐私门槛：状态计数不暴露文本内容，
 * 而且"待审积压多少"正是教务最需要看到的运营指标。
 */
const props = defineProps({
  data: { type: Object, default: null }
})

const el = ref(null)

function buildOption() {
  const a = props.data
  if (!a) return null

  const raw = [
    { name: '草稿', value: a.draft, color: '#8f959e' },
    { name: '待审核', value: a.pending, color: '#d97706' },
    { name: '已发布', value: a.approved, color: '#12805c' },
    { name: '待发布', value: a.unpublished, color: '#0e7490' },
    { name: '退回修改', value: a.returned, color: '#c2410c' },
    { name: '已驳回', value: a.rejected, color: '#b91c1c' }
  ]
  // 横向条形图：自下而上，故反转
  const cats = raw.map((x) => x.name).reverse()
  const data = raw
    .map((x) => ({ value: x.value, itemStyle: { color: x.color, borderRadius: [0, 4, 4, 0] } }))
    .reverse()

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 74, right: 40, top: 16, bottom: 22 },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f1f4' } },
      axisLabel: { color: '#8f959e', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: cats,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#646a73', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        data,
        barMaxWidth: 18,
        label: { show: true, position: 'right', color: '#646a73', fontSize: 11 }
      }
    ]
  }
}

const { render } = useChart(el, () => buildOption())

watch(
  () => props.data,
  () => nextTick(render),
  { deep: true }
)
</script>
