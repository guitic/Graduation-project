import { onBeforeUnmount, onMounted } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 实例生命周期封装。
 *
 * 抽出来的原因不是"少写几行"，而是这四处逻辑原来各写一遍：
 * init 判空、setOption 前判 disposed、window resize 绑定/解绑、卸载 dispose。
 * 其中任何一处漏掉都不报错——只是窗口缩放后图表变形、或路由来回切换后内存泄漏。
 *
 * @param {import('vue').Ref<HTMLElement|null>} elRef 图表容器
 * @param {() => object|null} buildOption 返回 null 表示"当前不画"（样本不足/无数据）
 * @param {{ onClick?: (params: object) => void }} [options]
 */
export function useChart(elRef, buildOption, options = {}) {
  let chart = null
  let chartEl = null
  let clickBound = false

  function ensure() {
    const el = elRef.value
    if (!el) return null
    // 容器被 v-if 摘掉再挂回来时是**新的 DOM 节点**：只判 isDisposed 会把旧实例继续用在
    // 已经脱离文档的节点上，表现是"图不见了但不报错"。因此还要比对节点本身。
    if (!chart || chart.isDisposed() || chartEl !== el) {
      if (chart && !chart.isDisposed()) chart.dispose()
      chart = echarts.init(el)
      chartEl = el
      clickBound = false
    }
    return chart
  }

  /** 数据变化后调用；容器被 v-if 摘掉时只清空不报错 */
  function render() {
    const option = buildOption()
    if (!option) {
      if (chart && !chart.isDisposed()) chart.clear()
      return
    }
    const c = ensure()
    if (!c) return
    c.setOption(option, true)
    if (options.onClick && !clickBound) {
      c.on('click', options.onClick)
      clickBound = true
    }
  }

  function resize() {
    if (chart && !chart.isDisposed()) chart.resize()
  }

  onMounted(() => {
    window.addEventListener('resize', resize)
    render()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    if (chart && !chart.isDisposed()) chart.dispose()
  })

  return { render, resize }
}
