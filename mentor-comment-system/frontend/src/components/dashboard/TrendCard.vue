<template>
  <ChartCard
    title="点评数量与情感趋势"
    :caliber="`口径：按发布月份归集 · 当月为未完结月（斜纹）· 比率线需整图样本 ≥ ${minSample}`"
  >
    <div ref="el" class="chart-canvas" />

    <template #foot>
      <!-- 图例下沉到页脚：ECharts 的 legend 默认占据右上角，会和右轴名「正向率」、
           当月标注三者叠在一起。样式系统里本来就有 .legend-row，只是没用上。 -->
      <div class="legend-row">
        <span class="lg"><i style="background: #12805c" />正向</span>
        <span class="lg"><i style="background: #8f959e" />中性</span>
        <span class="lg"><i style="background: #d92d20" />负向</span>
        <span v-if="data?.rateAvailable" class="lg">
          <i style="background: #2b5ce6; border-radius: 50%" />正向率
        </span>
      </div>
      <span v-if="drillable" class="hint">
        <el-icon><Pointer /></el-icon> 点击月份柱查看明细
      </span>
      <span v-else class="hint">当月为未完结月，柱体以虚线标注；个人画像页不提供明细下钻</span>
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
  minSample: { type: Number, default: 5 },
  /** 是否允许点击下钻。教师端个人画像页关闭，避免点了没反应 */
  drillable: { type: Boolean, default: true }
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
              itemStyle: { color: 'rgba(43,92,230,0.05)' },
              // 不写字：label 默认贴在区域顶部，会和图例/轴名抢右上角，
              // 「更新中」改由 x 轴标签后缀表达，不再额外占空间
              label: { show: false },
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
      nameTextStyle: { color: '#6b7280', fontSize: 11 },
      // 取整刻度：默认 splitNumber 会给出 21 / 18 / 15 这类非整数格线
      splitNumber: 4,
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f1f4' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
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
      // 轴名贴住轴线顶端，避免和图表上沿的其它元素抢位
      nameGap: 14,
      nameTextStyle: { color: '#6b7280', fontSize: 11, align: 'left' },
      splitLine: { show: false },
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        formatter: (v) => Math.round(v * 100) + '%'
      }
    })
  }

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    // 图例交给 ChartCard 页脚手写，见模板 #foot
    legend: { show: false },
    // top 留 32px：两根 Y 轴的轴名都贴在轴顶端，22px 会被画布上沿切掉半个字
    grid: { left: 46, right: t.rateAvailable ? 50 : 18, top: 32, bottom: 34 },
    xAxis: {
      type: 'category',
      data: t.months,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        lineHeight: 15,
        // 未完结月直接在刻度上标注，替代原来会压字的 markArea label
        formatter: (v, i) => (i === inProgIdx ? `${v}\n更新中` : v)
      }
    },
    yAxis,
    series
  }
}

const { render } = useChart(el, () => (hasData.value ? buildOption() : null), {
  // 点击柱子下钻：是否放行由后端按该桶样本量判定，前端只负责把月份传过去
  onClick: (p) => {
    if (!props.drillable || p?.componentType !== 'series') return
    emit('drill', p.name)
  }
})

watch(
  () => props.data,
  () => nextTick(render),
  { deep: true }
)
</script>
