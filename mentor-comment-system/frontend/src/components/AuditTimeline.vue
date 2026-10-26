<template>
  <div class="audit-timeline">
    <el-timeline v-if="audits && audits.length">
      <el-timeline-item
        v-for="a in audits"
        :key="a.id"
        :timestamp="fmtTime(a.auditAt)"
        placement="top"
        :type="actionMeta(a.actionType, a.resultLabel).type"
        :hollow="a.actionType === 'SUBMIT'"
      >
        <div class="tl-head">
          <span class="tl-title">{{ actionMeta(a.actionType, a.resultLabel).label }}</span>
          <span v-if="a.round" class="tl-round">第 {{ a.round }} 轮</span>
          <span class="tl-who">{{ a.auditorName || '—' }}</span>
        </div>
        <div v-if="a.opinion" class="tl-opinion">
          <el-icon class="q"><ChatLineSquare /></el-icon>
          <span>{{ a.opinion }}</span>
        </div>
        <el-collapse v-if="a.beforeContent" class="tl-before">
          <el-collapse-item :title="'查看审核时的内容快照'">
            <div class="before-content">{{ a.beforeContent }}</div>
          </el-collapse-item>
        </el-collapse>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无审核记录" :image-size="64" />
  </div>
</template>

<script setup>
import { actionMeta, fmtTime } from '../utils/status'

defineProps({
  audits: { type: Array, default: () => [] }
})
</script>

<style scoped>
.audit-timeline {
  padding-top: 6px;
}
.tl-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.tl-title {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--mc-text-1);
}
.tl-round {
  font-size: 11px;
  color: var(--mc-text-3);
  background: var(--mc-bg);
  padding: 1px 7px;
  border-radius: 8px;
}
.tl-who {
  font-size: 12px;
  color: var(--mc-text-3);
}
.tl-opinion {
  margin-top: 8px;
  display: flex;
  gap: 7px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--mc-text-2);
  background: #fafbfc;
  border: 1px solid var(--mc-border-weak);
  border-radius: 8px;
  padding: 9px 12px;
}
.tl-opinion .q {
  color: var(--mc-primary);
  margin-top: 3px;
  flex-shrink: 0;
}
.tl-before {
  margin-top: 6px;
  border: none;
}
.tl-before :deep(.el-collapse-item__header) {
  font-size: 12px;
  color: var(--mc-text-3);
  height: 30px;
  border-bottom: none;
}
.tl-before :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.before-content {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--mc-text-3);
  white-space: pre-wrap;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 12px;
}
</style>
