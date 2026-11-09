/**
 * 审核常用理由（前端唯一来源）。
 *
 * <p>为什么不放后端下发：这些是纯界面文案，不参与任何校验。放后端意味着改一个标点都要发一次版本，
 * 而它们又不像"样本门槛"那样会影响数据判定——门槛必须前后端同源，文案不必。
 *
 * <p>为什么取消字数下限后仍要有这一层：真正的质量保障不是逼老师凑够 10 个字
 * （凑出来的多是"内容完整表述规范同意发布"这种填充句），而是把"说清楚依据"变成一次点击。
 * 因此每条理由都写成**可直接留痕的完整句子**，选完不需要再改。
 *
 * kind 用于下拉框分组：agree = 同意类，disagree = 不同意 / 需修改类。
 * 每个动作只给自己的那几类，避免"点了通过却选到驳回理由"这种自相矛盾的组合。
 */
export const REASON_GROUPS = {
  agree: '同意',
  disagree: '不同意 / 需修改'
}

export const AUDIT_REASONS = {
  // 通过并发布
  approvePublish: [
    { kind: 'agree', text: '内容完整、表述规范，同意发布。' },
    { kind: 'agree', text: '指导意见具体可操作，同意发布。' },
    { kind: 'agree', text: '点评客观中肯、无不当表述，同意发布。' }
  ],
  // 仅通过（暂不发布）
  approveOnly: [
    { kind: 'agree', text: '内容无问题，先通过，待统一推送时再发布。' },
    { kind: 'agree', text: '同意通过，暂不发布，等班级整体点评完成后一起推送。' }
  ],
  // 退回修改
  return: [
    { kind: 'disagree', text: '缺少具体课程与改进建议，请补充后重新提交。' },
    { kind: 'disagree', text: '表述过于笼统，请结合该生实际情况写具体。' },
    { kind: 'disagree', text: '存在错别字或语句不通，请校对后重新提交。' },
    { kind: 'disagree', text: '内容偏向评价学生性格，请改为针对学业表现。' }
  ],
  // 驳回（终态）
  reject: [
    { kind: 'disagree', text: '内容与学业指导无关，不予通过。' },
    { kind: 'disagree', text: '存在不当表述，经复核不予通过。' },
    { kind: 'disagree', text: '与已有点评内容高度重复，不予通过。' }
  ],
  // 撤回发布
  unpublish: [
    { kind: 'disagree', text: '内容存在事实性错误，撤回后请导师核实。' },
    { kind: 'disagree', text: '涉及学生隐私信息，先撤回修改。' },
    { kind: 'disagree', text: '学生或家长提出异议，暂撤回复核。' }
  ],
  // 再发布（曾撤回）
  republish: [
    { kind: 'agree', text: '内容已复核无误，恢复发布。' },
    { kind: 'agree', text: '异议已解决，恢复发布。' }
  ]
}

/** 按 kind 分组成下拉框的 option-group，空组不渲染 */
export function reasonGroupsOf(key) {
  const items = AUDIT_REASONS[key] || []
  return ['agree', 'disagree']
    .map((kind) => ({
      kind,
      label: REASON_GROUPS[kind],
      items: items.filter((r) => r.kind === kind)
    }))
    .filter((g) => g.items.length)
}

/**
 * 把选中的理由并进已输入的意见。
 *
 * 已有内容时用「；」拼接而不是覆盖：老师常常先写两句自己的判断，再补一条标准理由。
 * 重复选择同一条则不再追加，避免连点两下出现两遍。
 */
export function appendReason(current, text) {
  const cur = (current || '').trim()
  if (!cur) return text
  if (cur.includes(text)) return cur
  const sep = /[。；;！!？?]$/.test(cur) ? '' : '；'
  return `${cur}${sep}${text}`
}
