<template>
  <ChartCard
    title="点评高频词词云"
    :caliber="`口径：已发布点评分词词频 · 已过滤停用词与姓名/学号 · 样本 < ${minSample} 不展示`"
    :foot-visible="enough"
  >
    <GateEmpty
      v-if="!enough"
      :sample-count="data?.sampleCount ?? 0"
      :min-sample="minSample"
      title="词云样本不足"
      desc="已发布点评数量低于隐私门槛，暂不展示词云。2~4 条点评的高频词几乎等于原文关键词。"
    />
    <div v-else ref="el" class="chart-canvas" />

    <template #foot>
      <span v-if="drillable" class="hint">
        <el-icon><Pointer /></el-icon> 点击词语，查看包含该词的点评明细（受样本门槛约束）
      </span>
      <span v-else class="hint">词语大小表示出现频次；个人画像页不提供跨导师明细下钻</span>
    </template>
  </ChartCard>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Pointer } from '@element-plus/icons-vue'
import 'echarts-wordcloud'
import ChartCard from './ChartCard.vue'
import GateEmpty from '../GateEmpty.vue'
import { useChart } from '../../composables/useChart'

/**
 * 词云。
 *
 * 门槛与情感图共用同一个 `analysis.min-sample`：2~4 条点评的高频词几乎就是原文关键词，
 * 放宽门槛等于把原文贴在页面上，"展示更好看"换不来任何分析价值。
 */
const props = defineProps({
  data: { type: Object, default: null },
  minSample: { type: Number, default: 5 },
  /** 是否允许点击下钻。教师端个人画像页关闭，避免点了没反应 */
  drillable: { type: Boolean, default: true }
})
const emit = defineEmits(['drill'])

const el = ref(null)
const enough = computed(() => !!props.data?.enough)

/**
 * 词云配色：单一主色相 + 按词频调明度。
 *
 * 原来用 10 色彩虹，但系统里红=负向、绿=正向，词云出现红绿会暗示情感含义，
 * 而词云只表达频次、不表达倾向。同色系深浅既能区分主次，又不会和情感色撞语义。
 */
function wordColor(i, n) {
  const t = n <= 1 ? 0 : i / (n - 1) // 0 = 最高频
  // 30% → 54%：最深给高频词，最浅一级对白底仍有 ~4.5:1，
  // 低频词字号只有 13px，再浅就读不清了
  return `hsl(224, 66%, ${30 + t * 24}%)`
}

function buildOption() {
  const w = props.data
  if (!w) return null
  return {
    tooltip: {
      show: true,
      formatter: (p) => `${p.name} · 出现 ${p.value} 次`
    },
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        left: 'center',
        top: 'center',
        width: '92%',
        height: '90%',
        sizeRange: [13, 44],
        rotationRange: [-45, 45],
        rotationStep: 15,
        gridSize: 8,
        drawOutOfBound: false,
        textStyle: {
          fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
          fontWeight: 600
        },
        emphasis: {
          textStyle: { textShadowBlur: 6, textShadowColor: 'rgba(0,0,0,0.14)' }
        },
        data: (w.words || []).map((x, i) => ({
          name: x.name,
          value: x.value,
          textStyle: { color: wordColor(i, w.words.length) }
        }))
      }
    ]
  }
}

const { render } = useChart(el, () => (enough.value ? buildOption() : null), {
  onClick: (p) => props.drillable && p?.name && emit('drill', p.name)
})

watch(
  () => props.data,
  () => nextTick(render),
  { deep: true }
)
</script>
