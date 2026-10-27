<template>
  <ChartCard
    title="点评数量与情感趋势"
    :caliber="`口径：按发布月份归集 · 当月为未完结月（斜纹）· 比率线需整图样本 ≥ ${minSample}`"
  >
    <div ref="el" class="chart-canvas" />

    <template #foot>
      <span class="hint">
        <el-icon><Pointer /></el-icon> 点击任一月份柱，查看该月点评明细（按桶级样本量校验）
      </span>
    </template>
  </ChartCard>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Pointer } from '@element-plus/icons-vue'
import ChartCard from './ChartCard.vue'
import { useChart } from '../../composables/useChart'

/**
 * 数量 + 情感趋势。
 *
 * 两条规则必须同时可见：
 * 1. 数量柱永远可画（数量本身不敏感）；比率线在整图样本不足时整条不画。
 * 2. 当月是"未完结月"，用虚线描边 + markArea 标注，避免读者把当月低值当下降趋势。
 */
const props = defineProps({
  data: { type: Object, default: null },
  minSample: { type: Number, default: 5 }
})
const emit = defineEmits(['drill'])

const el = ref(null)

const POS = '#12805c'
const NEU = '#8f959e'
const NEG = '#d92d20'

const hasData = computed(() => !!props.data?.months?.length)

function buildOption() {
  const t = props.data
  if (!t || !t.months?.length) return null

  const inProgIdx = (t.inProgress || []).findIndex(Boolean)
  const barData = (arr, color) =>
    (arr || []).map((v, i) =>
      i === inProgIdx
        ? {
            value: v,
            itemStyle: {
              color,
              opacity: 0.45,
              borderColor: color,
              borderWidth: 1.5,
              borderType: 'dashed'
            }
          }
        : v
    )

  const series = [
    {
      name: '正向',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.positiveCount, POS),
      itemStyle: { color: POS },
      markArea:
        inProgIdx >= 0
          ? {
              silent: true,
              itemStyle: { color: 'rgba(43,92,230,0.06)' },
              label: {
                show: true,
                position: 'top',
                formatter: '当月·更新中',
                color: '#2b5ce6',
                fontSize: 11
              },
              data: [[{ xAxis: t.months[inProgIdx] }, { xAxis: t.months[inProgIdx] }]]
            }
          : undefined
    },
    {
      name: '中性',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.neutralCount, NEU),
      itemStyle: { color: NEU }
    },
    {
      name: '负向',
      type: 'bar',
      stack: 'cnt',
      barMaxWidth: 28,
      data: barData(t.negativeCount, NEG),
      itemStyle: { color: NEG, borderRadius: [3, 3, 0, 0] }
    }
  ]

  const yAxis = [
    {
      type: 'value',
      name: '条数',
      nameTextStyle: { color: '#b4b8bf', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f1f4' } },
      axisLabel: { color: '#8f959e', fontSize: 11 }
    }
  ]

  if (t.rateAvailable) {
    series.push({
      name: '正向率',
      type: 'line',
      yAxisIndex: 1,
      data: t.positiveRate,
      connectNulls: false,
      symbol: 'circle',
      symbolSize: 6,
      smooth: false,
      lineStyle: { width: 2, color: '#2b5ce6' },
      itemStyle: { color: '#2b5ce6' }
    })
    yAxis.push({
      type: 'value',
      name: '正向率',
      min: 0,
      max: 1,
      nameTextStyle: { color: '#b4b8bf', fontSize: 11 },
      splitLine: { show: false },
      axisLabel: {
        color: '#8f959e',
        fontSize: 11,
        formatter: (v) => Math.round(v * 100) + '%'
      }
    })
  }

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      top: 0,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#646a73', fontSize: 11 }
    },
    grid: { left: 46, right: t.rateAvailable ? 50 : 18, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: t.months,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#8f959e', fontSize: 11 }
    },
    yAxis,
    series
  }
}

const { render } = useChart(el, () => (hasData.value ? buildOption() : null), {
  // 点击柱子下钻：是否放行由后端按该桶样本量判定，前端只负责把月份传过去
  onClick: (p) => {
    if (p?.componentType !== 'series') return
    emit('drill', p.name)
  }
})

watch(
  () => props.data,
  () => nextTick(render),
  { deep: true }
)
</script>
