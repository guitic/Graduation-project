import { computed } from 'vue'
import { useTheme } from './useTheme'

/**
 * 图表调色板。
 *
 * ECharts 拿不到 CSS 变量，只能在构建 option 时把当前主题下的颜色读成实值。
 * 依赖 isDark，主题切换时 computed 失效 → 各图表 watch(palette) 重新 render，
 * 否则会出现「页面黑了、图表还是白底黑字」。
 */
export function useChartTheme() {
  const { isDark } = useTheme()

  return computed(() => {
    void isDark.value // 建立依赖，切换主题时重新求值
    const cs = getComputedStyle(document.documentElement)
    const v = (name, fallback) => cs.getPropertyValue(name).trim() || fallback
    return {
      text: v('--chart-text', '#646a73'),
      strong: v('--chart-strong', '#1f2329'),
      axis: v('--chart-axis', '#8f959e'),
      grid: v('--chart-grid', '#f0f1f4'),
      surface: v('--chart-surface', '#ffffff'),
      line: v('--chart-line', '#e5e6eb'),
      band: v('--chart-band', 'rgba(43,92,230,0.05)'),
      pos: v('--sn-pos', '#12805c'),
      neu: v('--sn-neu', '#8f959e'),
      neg: v('--sn-neg', '#d92d20'),
      primary: v('--mc-primary', '#2b5ce6'),
      // 审核状态分布用状态色，跟表格里的胶囊保持同一套
      status: {
        draft: v('--st-draft-fg', '#6b7280'),
        pending: v('--st-pending-fg', '#b45309'),
        approved: v('--st-approved-fg', '#0e7490'),
        published: v('--st-published-fg', '#12805c'),
        returned: v('--st-returned-fg', '#c2410c'),
        rejected: v('--st-rejected-fg', '#b91c1c')
      },
      // 词云明度区间：亮色用深蓝、暗色用浅蓝，否则暗底上全糊成一块
      wordFrom: Number(v('--word-l-from', '30')),
      wordTo: Number(v('--word-l-to', '54'))
    }
  })
}
