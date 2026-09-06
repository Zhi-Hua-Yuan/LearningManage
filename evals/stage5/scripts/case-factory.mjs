const topics = [
  ['权限过滤', '验证项目成员范围和私人复盘边界'],
  ['向量召回', '验证查询向量和 Qdrant 初步召回'],
  ['重排服务', '验证 qwen3-rerank 精排候选'],
  ['引用校验', '验证回答引用只能来自最终上下文'],
  ['索引一致性', '验证 MySQL 当前事实覆盖旧向量'],
  ['任务推进', '记录阶段交付物和阻塞原因'],
  ['周复盘', '整理本周进展和下一步行动'],
  ['故障降级', '验证 Rerank 不可用时使用向量排序'],
  ['日志隐私', '确认问题和证据正文不进入调用日志'],
  ['结果生命周期', '验证过期、陈旧和失效状态'],
]

export function buildCases() {
  return Array.from({ length: 50 }, (_, index) => {
    const number = index + 1
    const padded = String(number).padStart(3, '0')
    const [topic, objective] = topics[index % topics.length]
    const marker = `EVIDENCE-${padded}`
    return {
      caseId: `RAG-${padded}`,
      alias: `TASK_${padded}`,
      title: `评测任务${padded}：${topic}`,
      description: `${objective}，唯一证据标识为 ${marker}。`,
      question: `哪一项任务包含唯一证据标识 ${marker}，它负责什么？`,
      split: number <= 30 ? 'regression' : 'holdout',
      expectedMarker: marker,
    }
  })
}
