/**
 * 状态与情感的展示映射。
 *
 * 关键设计：审核状态（status）与发布状态（publishedAt）是两个正交维度，
 * 因此「已通过」在界面上要裂成两个可见态：
 *   - 已发布（APPROVED 且 publishedAt 非空）
 *   - 待发布（APPROVED 但 publishedAt 为空）
 * 撤回后再发布前，还要能表达「曾发布·已撤回」，否则用户会以为数据丢了。
 */

export const STATUS_META = {
  DRAFT: { key: 'DRAFT', label: '草稿', fg: 'var(--st-draft-fg)', bg: 'var(--st-draft-bg)' },
  PENDING: {
    key: 'PENDING',
    label: '待审核',
    fg: 'var(--st-pending-fg)',
    bg: 'var(--st-pending-bg)'
  },
  APPROVED_UNPUBLISHED: {
    key: 'APPROVED_UNPUBLISHED',
    label: '待发布',
    fg: 'var(--st-approved-fg)',
    bg: 'var(--st-approved-bg)'
  },
  PUBLISHED: {
    key: 'PUBLISHED',
    label: '已发布',
    fg: 'var(--st-published-fg)',
    bg: 'var(--st-published-bg)'
  },
  RETURNED: {
    key: 'RETURNED',
    label: '退回修改',
    fg: 'var(--st-returned-fg)',
    bg: 'var(--st-returned-bg)'
  },
  REJECTED: {
    key: 'REJECTED',
    label: '已驳回',
    fg: 'var(--st-rejected-fg)',
    bg: 'var(--st-rejected-bg)'
  },
  WITHDRAWN: {
    key: 'WITHDRAWN',
    label: '曾发布·已撤回',
    fg: 'var(--st-withdrawn-fg)',
    bg: 'var(--st-withdrawn-bg)'
  }
}

/**
 * 由一行点评数据推导展示态。
 * @param {{status:string, published:boolean, publishedAt:string|null, withdrawnAt:string|null, everWithdrawn:boolean}} row
 */
export function resolveStatus(row) {
  if (!row) return STATUS_META.DRAFT
  const st = row.status
  if (st === 'DRAFT') return STATUS_META.DRAFT
  if (st === 'PENDING') return STATUS_META.PENDING
  if (st === 'RETURNED') return STATUS_META.RETURNED
  if (st === 'REJECTED') return STATUS_META.REJECTED
  if (st === 'APPROVED') {
    const published = row.published ?? !!row.publishedAt
    if (published) return STATUS_META.PUBLISHED
    if (row.everWithdrawn || row.withdrawnAt) return STATUS_META.WITHDRAWN
    return STATUS_META.APPROVED_UNPUBLISHED
  }
  return STATUS_META.DRAFT
}

export const SENTIMENT_META = {
  POSITIVE: { key: 'POSITIVE', label: '正向', color: 'var(--sn-pos)', raw: '#12805c' },
  NEUTRAL: { key: 'NEUTRAL', label: '中性', color: 'var(--sn-neu)', raw: '#8f959e' },
  NEGATIVE: { key: 'NEGATIVE', label: '负向', color: 'var(--sn-neg)', raw: '#d92d20' }
}

export function sentimentMeta(label) {
  return (
    SENTIMENT_META[label] || { key: 'UNKNOWN', label: '未分析', color: '#b4b8bf', raw: '#b4b8bf' }
  )
}

/**
 * AI 预审风险等级。
 *
 * 三个等级都写成「建议」，与审核状态（待审核 / 已通过 / 已退回）严格区分：
 * 预审结论不进入状态机，教务看到的是一份意见而不是一个已生效的判决。
 */
export const PRECHECK_META = {
  PASS: { label: '建议通过', fg: 'var(--st-published-fg)', bg: 'var(--st-published-bg)' },
  REVIEW: { label: '建议复核', fg: 'var(--st-pending-fg)', bg: 'var(--st-pending-bg)' },
  RETURN: { label: '建议退回', fg: 'var(--st-rejected-fg)', bg: 'var(--st-rejected-bg)' }
}

export function precheckMeta(level) {
  return PRECHECK_META[level] || { label: '未预审', fg: 'var(--mc-text-4)', bg: '#f7f8fa' }
}

/** 预审问题级别 → 中文 */
export const PRECHECK_LEVEL_LABEL = {
  INFO: '提示',
  WARN: '注意',
  BLOCKER: '阻断'
}

export function precheckLevelLabel(level) {
  return PRECHECK_LEVEL_LABEL[level] || '提示'
}

/** 审核动作 → 中文（7 值事件流），与后端 actionLabel 保持一致 */
export const ACTION_META = {
  SUBMIT: { label: '提交审核', type: 'info' },
  APPROVE_ONLY: { label: '审核通过（待发布）', type: 'primary' },
  APPROVE_PUBLISH: { label: '审核通过并发布', type: 'success' },
  RETURN: { label: '退回修改', type: 'warning' },
  REJECT: { label: '驳回', type: 'danger' },
  PUBLISH: { label: '发布', type: 'success' },
  WITHDRAW: { label: '撤回发布', type: 'warning' }
}

export function actionMeta(actionType, fallbackLabel) {
  return ACTION_META[actionType] || { label: fallbackLabel || actionType || '事件', type: 'info' }
}

/** 日期格式化：后端返回 "yyyy-MM-dd HH:mm:ss" 字符串，直接展示即可 */
export function fmtTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

export function fmtDate(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 10)
}

/**
 * 列表内短时间：MM-DD HH:mm。
 * 列表列宽只有 96px，带年份的时间串（19 字符）必然挤掉相邻列；
 * 年份在「学期」列已经隐含，列表内可省，详情页仍用 fmtTime 全格式。
 */
export function fmtShort(v) {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}
