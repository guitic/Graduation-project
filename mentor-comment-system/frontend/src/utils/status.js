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
  PENDING: { key: 'PENDING', label: '待审核', fg: 'var(--st-pending-fg)', bg: 'var(--st-pending-bg)' },
  APPROVED_UNPUBLISHED: {
    key: 'APPROVED_UNPUBLISHED',
    label: '待发布',
    fg: 'var(--st-approved-fg)',
    bg: 'var(--st-approved-bg)'
  },
  PUBLISHED: { key: 'PUBLISHED', label: '已发布', fg: 'var(--st-published-fg)', bg: 'var(--st-published-bg)' },
  RETURNED: { key: 'RETURNED', label: '退回修改', fg: 'var(--st-returned-fg)', bg: 'var(--st-returned-bg)' },
  REJECTED: { key: 'REJECTED', label: '已驳回', fg: 'var(--st-rejected-fg)', bg: 'var(--st-rejected-bg)' },
  WITHDRAWN: { key: 'WITHDRAWN', label: '曾发布·已撤回', fg: 'var(--st-withdrawn-fg)', bg: 'var(--st-withdrawn-bg)' }
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
  return SENTIMENT_META[label] || { key: 'UNKNOWN', label: '未分析', color: '#b4b8bf', raw: '#b4b8bf' }
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
