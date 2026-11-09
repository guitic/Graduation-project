<template>
  <div class="kpi-grid" :style="{ '--kpi-cols': columns }">
    <!--
      可点与不可点用同一个节点类型差异表达：clickable 时渲染成 <button>，
      键盘 Tab / Enter / Space 天生可用，不需要自己补 keydown。
      导师端（MyStats）没有对应明细接口，不传 clickable 就仍是纯展示的 <div>。
    -->
    <component
      :is="clickable ? 'button' : 'div'"
      v-for="k in kpis"
      :key="k.key"
      class="mc-card kpi"
      :class="[k.tone, { clickable, active: clickable && k.key === activeKey }]"
      :type="clickable ? 'button' : null"
      :aria-label="clickable ? `${k.label} ${k.value}，查看明细` : null"
      @click="onClick(k)"
    >
      <div class="kpi-label">{{ k.label }}</div>
      <div class="kpi-value mc-mono">{{ k.value }}</div>
      <div class="kpi-foot">{{ k.foot }}</div>
      <!-- 悬停才出现的「明细 ›」：静止时保持 KPI 的干净读数，指针移上去才提示这里能点 -->
      <span v-if="clickable" class="kpi-go" aria-hidden="true">明细 ›</span>
    </component>
  </div>
</template>

<script setup>
/**
 * KPI 卡组。数值由父组件按同一份 dashboard 响应计算，
 * 避免"卡片自己再请求一次"导致 KPI 与图表不同步。
 *
 * columns 可调是因为教务看 7 个流程指标、导师只看 5 个（没有"待发布"和"驳回终态"的位置），
 * 写死 7 列会让导师页右侧空两格，看起来像数据没加载出来。
 *
 * clickable / activeKey 是「点卡片看明细」的开关：数字本身是入口，不再额外加一行按钮——
 * 卡片已经是这个页面最大的可读单元，再放按钮会变成两层点击目标。
 */
const props = defineProps({
  kpis: { type: Array, default: () => [] },
  columns: { type: Number, default: 7 },
  /** 卡片是否可点。只有接了 select 事件的页面才传 true */
  clickable: { type: Boolean, default: false },
  /** 当前打开明细的卡片 key：用于把「正在看哪一类」画在卡片上 */
  activeKey: { type: String, default: '' }
})

const emit = defineEmits(['select'])

function onClick(k) {
  if (!props.clickable) return
  emit('select', k)
}
</script>

<style scoped>
.kpi-grid {
  display: grid;
  /*
   * 列数走自定义属性而不是内联 grid-template-columns：
   * 内联样式的优先级高于样式表，写死 repeat(7) 会让下面两个断点永远不生效，
   * 1280 视口下 7 列被压到每列 90px，「点评总数」直接竖排成四行。
   */
  grid-template-columns: repeat(var(--kpi-cols, 7), 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  padding: 14px 16px;
  position: relative;
  overflow: hidden;
  min-width: 0; /* 窄列下数字/标签不允许把卡片撑破 */
}
.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--mc-border);
}
.kpi.ok::before {
  background: var(--st-published-fg);
}
.kpi.warn::before {
  background: var(--st-pending-fg);
}
.kpi.info::before {
  background: var(--st-approved-fg);
}
.kpi.danger::before {
  background: var(--st-rejected-fg);
}
.kpi-label {
  font-size: 12.5px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: var(--mc-text-1);
}
/* 只有「需要处理」的两类指标给数值上色：一屏扫过去先看到待审积压与退回，
   正常态保持中性——全部上色等于没上色 */
.kpi.warn .kpi-value {
  color: var(--st-pending-fg);
}
.kpi.danger .kpi-value {
  color: var(--st-rejected-fg);
}
.kpi-foot {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-top: 7px;
}

/* ---- 可点状态 ---- */
.kpi.clickable {
  /* button 重置：不改这三行，卡片会带上浏览器默认的灰底、居中与系统字体 */
  font: inherit;
  text-align: left;
  color: inherit;
  width: 100%;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}
.kpi.clickable:hover {
  border-color: var(--mc-hover-border);
  box-shadow: var(--mc-shadow-hover);
  transform: translateY(-1px);
}
/* 正在查看的那一类：主色描边 + 焦点环，与抽屉保持视觉对应 */
.kpi.active {
  border-color: var(--mc-primary);
  box-shadow: var(--mc-focus);
}
.kpi-go {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 11px;
  color: var(--mc-text-3);
  opacity: 0;
  transition:
    opacity 0.15s,
    color 0.15s;
}
.kpi.clickable:hover .kpi-go,
.kpi.clickable:focus-visible .kpi-go {
  opacity: 1;
  color: var(--mc-primary);
}

/* 7 个指标在 1440 下每卡约 150px，仍可读；再窄就换行成 4 列，
   避免每卡挤到 120px 以下导致标签折行 */
@media (max-width: 1360px) {
  .kpi-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
