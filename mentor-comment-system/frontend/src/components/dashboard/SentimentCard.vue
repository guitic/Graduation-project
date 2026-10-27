<template>
  <ChartCard
    title="情感倾向分布"
    :caliber="`口径：仅已发布点评 · 样本 < ${minSample} 条不展示占比`"
    :foot-visible="enough"
  >
    <GateEmpty
      v-if="!enough"
      :sample-count="data?.total ?? 0"
      :min-sample="minSample"
      title="情感样本不足"
      desc="已发布点评数量低于隐私门槛，暂不展示情感占比。补充发布点评后自动恢复。"
    />
    <div v-else ref="el" class="chart-canvas" />

    <template #foot>
      <div class="legend-row">
        <span class="lg"><i style="background: #12805c" />正向 {{ data.positive }}</span>
        <span class="lg"><i style="background: #8f959e" />中性 {{ data.neutral }}</span>
        <span class="lg"><i style="background: #d92d20" />负向 {{ data.negative }}</span>
      </div>
      <div class="rate-row">
        正向率
        <b class="mc-mono">{{ pct(data.positiveRate) }}</b> · 负向率
        <b class="mc-mono">{{ pct(data.negativeRate) }}</b>
      </div>
    </template>
  </ChartCard>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import ChartCard from './ChartCard.vue'
import GateEmpty from '../GateEmpty.vue'
import { useChart } from '../../composables/useChart'

/**
 * 情感分布环图。
 * 中心数字是"已发布样本量"，占比只在样本达门槛时展示——占比字段本身由后端返回 null，
 * 前端拿到 null 会显示 "—"，不会出现"0.0%"这种看似正常实则错误的数。
 */
const props = defineProps({
  data: { type: Object, default: null },
  minSample: { type: Number, default: 5 }
})

const el = ref(null)
const enough = computed(() => !!props.data?.enough)

function pct(v) {
  if (v == null) return '—'
  return (v * 100).toFixed(1) + '%'
}

function buildOption() {
  const s = props.data
  if (!s) return null
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>${p.value} 条 · ${p.percent}%`
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#646a73', fontSize: 12 }
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '36%',
        style: {
          text: String(s.total),
          fontSize: 28,
          fontWeight: 700,
          fill: '#1f2329',
          textAlign: 'center'
        }
      },
      {
        type: 'text',
        left: 'center',
        top: '47%',
        style: { text: '已发布', fontSize: 12, fill: '#8f959e', textAlign: 'center' }
      }
    ],
    series: [
      {
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: true, scaleSize: 4 },
        data: [
          { value: s.positive, name: '正向', itemStyle: { color: '#12805c' } },
          { value: s.neutral, name: '中性', itemStyle: { color: '#8f959e' } },
          { value: s.negative, name: '负向', itemStyle: { color: '#d92d20' } }
        ]
      }
    ]
  }
}

const { render } = useChart(el, () => (enough.value ? buildOption() : null))

watch(
  () => props.data,
  () => nextTick(render),
  { deep: true }
)
</script>
