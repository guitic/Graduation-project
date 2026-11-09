<template>
  <ChartCard title="审核状态分布" caliber="口径：全量点评 · 「已发布 / 待发布」为两个独立态">
    <div ref="el" class="chart-canvas" />
  </ChartCard>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import ChartCard from './ChartCard.vue'
import { useChart } from '../../composables/useChart'
import { useChartTheme } from '../../composables/useChartTheme'

/**
 * 审核状态分布（横向条形）。
 *
 * 这一张是全量口径，不走隐私门槛：状态计数不暴露文本内容，
 * 而且"待审积压多少"正是教务最需要看到的运营指标。
 *
 * 配色直接取 --st-*-fg：与表格里的状态胶囊同一套语义色，
 * 暗色主题下自动换成浅色系，不需要在这里写第二套色值。
 */
const props = defineProps({
  data: { type: Object, default: null }
})

const el = ref(null)
const palette = useChartTheme()

function buildOption() {
  const a = props.data
  if (!a) return null
  const c = palette.value
  const st = c.status

  const raw = [
    { name: '草稿', value: a.draft, color: st.draft },
    { name: '待审核', value: a.pending, color: st.pending },
    { name: '已发布', value: a.approved, color: st.published },
    { name: '待发布', value: a.unpublished, color: st.approved },
    { name: '退回修改', value: a.returned, color: st.returned },
    { name: '已驳回', value: a.rejected, color: st.rejected }
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
      splitLine: { lineStyle: { color: c.grid } },
      axisLabel: { color: c.axis, fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: cats,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: c.text, fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        data,
        barMaxWidth: 18,
        label: { show: true, position: 'right', color: c.text, fontSize: 11 }
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

watch(palette, () => nextTick(render))
</script>
