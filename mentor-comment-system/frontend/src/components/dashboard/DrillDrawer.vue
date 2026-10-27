<template>
  <el-drawer
    :model-value="modelValue"
    :title="drill.title"
    size="560px"
    destroy-on-close
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="drawer-body">
      <div class="drawer-summary">
        <span>
          样本量 <b class="mc-mono">{{ drill.sampleCount }}</b> 条 · 门槛
          <b class="mc-mono">{{ drill.minSample }}</b> 条
        </span>
        <el-tag v-if="drill.allowed" type="success" size="small" effect="plain" round>
          已通过门禁
        </el-tag>
        <el-tag v-else type="warning" size="small" effect="plain" round>样本不足</el-tag>
      </div>

      <GateEmpty
        v-if="!loading && !drill.allowed"
        :sample-count="drill.sampleCount"
        :min-sample="drill.minSample"
        title="该分组样本不足"
        desc="即使整体数据充足，单个时间桶/词语下的点评过少仍会暴露个人信息，因此该层下钻被禁用。"
      />

      <div v-else class="drill-list">
        <div v-for="c in drill.comments" :key="c.id" class="drill-item">
          <div class="di-head">
            <span class="di-topic">{{ c.topic }}</span>
            <span class="di-meta">
              {{ c.studentName }} · {{ c.term }} · {{ fmtDate(c.publishedAt) }}
            </span>
          </div>
          <div class="di-content">{{ c.content }}</div>
        </div>
        <el-empty
          v-if="!loading && !drill.comments.length"
          description="暂无明细"
          :image-size="72"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import GateEmpty from '../GateEmpty.vue'
import { fmtDate } from '../../utils/status'
import { limits } from '../../utils/limits'

/**
 * 桶级下钻抽屉：展示某个月份 / 某个词的点评明细。
 *
 * 注意 allowed 由后端按"该桶样本量"判定并下发，前端不做二次判断——
 * 前端判断只能防误点，防不住构造请求。
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  drill: {
    type: Object,
    default: () => ({
      title: '',
      sampleCount: 0,
      allowed: false,
      minSample: limits.minSample,
      comments: []
    })
  }
})
const emit = defineEmits(['update:modelValue'])
</script>

<style scoped>
.drawer-body {
  min-height: 200px;
}
.drawer-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--mc-text-3);
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mc-border-weak);
  margin-bottom: 14px;
}
.drawer-summary b {
  color: var(--mc-text-1);
}
.drill-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.drill-item {
  border: 1px solid var(--mc-border-weak);
  border-radius: 10px;
  padding: 12px 14px;
  background: #fcfcfd;
}
.di-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.di-topic {
  font-weight: 600;
  color: var(--mc-text-1);
  font-size: 13.5px;
}
.di-meta {
  font-size: 11.5px;
  color: var(--mc-text-3);
  white-space: nowrap;
}
.di-content {
  font-size: 13px;
  line-height: 1.75;
  color: var(--mc-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
