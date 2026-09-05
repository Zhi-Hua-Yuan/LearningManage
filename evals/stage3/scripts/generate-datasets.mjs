import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const datasetDir = path.join(root, 'datasets');
fs.mkdirSync(datasetDir, { recursive: true });

const VERSION = '1.1.0';
const TASKS = Object.freeze({
  personalPending: [930001, 930002, 930003, 930004],
  personalCompleted: [930005, 930006],
  teamPending: [930101, 930102, 930103, 930104],
  teamCompleted: [930105],
  outsider: [930201]
});

const quality = { development: [], regression: [], holdout: [] };
const failures = [];

function qualitySplit(index, developmentCount, regressionCount) {
  if (index < developmentCount) return 'development';
  if (index < developmentCount + regressionCount) return 'regression';
  return 'holdout';
}

function addQuality(scene, prefix, index, split, requestPayload, options = {}) {
  const sequence = String(index + 1).padStart(3, '0');
  quality[split].push({
    caseId: `${prefix}-${split === 'development' ? 'DEV' : split === 'regression' ? 'REG' : 'HOLD'}-${sequence}`,
    scene,
    split,
    tags: options.tags || ['quality', scene],
    actor: options.actor || 'owner',
    fixtureKey: options.fixtureKey || 'personal-standard',
    requestPayload,
    expectedInvariants: { expectSuccess: true, expectNoDegradation: true, ...(options.expectedInvariants || {}) },
    semanticRubric: options.semanticRubric,
    allowedResourceIds: options.allowedResourceIds || [],
    datasetVersion: VERSION,
    labelVersion: options.labelVersion || 1
  });
}

const domains = [
  ['Java 后端开发', '完成一个具备鉴权、测试和部署文档的 Spring Boot 项目'],
  ['英语六级', '词汇基础一般，重点提高听力和阅读'],
  ['研究生考试', '覆盖数学、英语和专业课的复习与模拟'],
  ['半程马拉松', '当前可以连续跑五公里，需要循序渐进'],
  ['年度阅读', '完成十二本非虚构图书并输出读书笔记'],
  ['后端求职', '完善简历、项目讲解、算法和系统设计'],
  ['毕业论文', '完成调研、实验、写作、修改和答辩'],
  ['产品 MVP', '完成需求验证、原型、开发、测试和发布'],
  ['数据分析', '掌握 SQL、Python、可视化并完成作品集'],
  ['摄影学习', '掌握曝光、构图、后期并完成主题作品'],
  ['日语 N2', '从 N3 水平提升词汇、语法、听力和阅读'],
  ['开源贡献', '选择项目、理解代码、提交并维护首个 PR']
];
const durations = ['1周', '1个月', '3个月', '6个月', '12周'];

for (let index = 0; index < 60; index += 1) {
  const [domain, description] = domains[index % domains.length];
  const detailed = index % 2 === 1;
  const split = qualitySplit(index, 36, 12);
  const boundaryTarget = index === 59 ? `${domain}${'阶段目标'.repeat(11)}`.slice(0, 100) : domain;
  addQuality('task-breakdown', 'TB', index, split, {
    target: boundaryTarget,
    description: index % 7 === 0 ? null : description,
    duration: durations[index % durations.length],
    detailed
  }, {
    tags: ['quality', 'task-breakdown', detailed ? 'detailed' : 'default', `domain-${index % domains.length + 1}`],
    labelVersion: 2,
    semanticRubric: [
      'The milestones cover the stated goal and form a sensible progression.',
      'Tasks are concrete, executable, independently checkable, and avoid unnecessary duplication.',
      'The amount of work and deadlines are plausible for the requested duration.'
    ]
  });
}

const reflections = [
  '本周按计划完成了核心功能，但测试补充得比较晚，下周要先写验收条件。',
  '学习时间被临时事务打断，实际完成量低于计划，需要减少并行任务。',
  '完成任务后及时复盘效果很好，难点是对复杂问题估时偏乐观。',
  '本周没有填写主观反思，请基于任务事实给出客观总结。',
  '团队协作顺畅，但接口联调时发现需求理解存在偏差。'
];
for (let index = 0; index < 30; index += 1) {
  const split = qualitySplit(index, 18, 6);
  addQuality('weekly-polish', 'WP', index, split, {
    taskIds: index % 3 === 0 ? TASKS.personalCompleted : [TASKS.personalCompleted[index % 2]],
    reflection: index % 6 === 0 ? '' : reflections[index % reflections.length]
  }, {
    tags: ['quality', 'weekly-polish', index % 6 === 0 ? 'empty-reflection' : 'with-reflection'],
    allowedResourceIds: TASKS.personalCompleted,
    semanticRubric: [
      'The review is grounded only in the supplied completed-task facts and reflection.',
      'It covers progress, problems, and causes without inventing project data.',
      'The Chinese writing is specific, concise, constructive, and useful for the next review.'
    ]
  });
}

const orderStrategies = ['balanced', 'benefit_first', 'quick_win'];
for (let index = 0; index < 30; index += 1) {
  const split = qualitySplit(index, 18, 6);
  const allowed = index % 2 === 0 ? TASKS.personalPending : TASKS.teamPending;
  const selected = allowed.slice(0, 2 + (index % 3));
  addQuality('today-order', 'TO', index, split, {
    taskIds: selected,
    timezone: 'Asia/Shanghai',
    now: `2026-09-05T${String(8 + (index % 10)).padStart(2, '0')}:30:00`,
    strategy: orderStrategies[index % orderStrategies.length],
    limit: 20
  }, {
    fixtureKey: index % 2 === 0 ? 'personal-standard' : 'team-standard',
    tags: ['quality', 'today-order', orderStrategies[index % orderStrategies.length]],
    allowedResourceIds: selected,
    semanticRubric: [
      'The recommended order balances urgency, priority, effort, and benefit according to the selected strategy.',
      'Reasons refer to the supplied task facts and do not invent dependencies.',
      'Estimated durations are plausible for the task titles and descriptions.'
    ]
  });
}

for (let index = 0; index < 25; index += 1) {
  const split = qualitySplit(index, 15, 5);
  const allowed = index % 2 === 0 ? TASKS.personalPending : TASKS.teamPending;
  const selected = allowed.slice(0, 1 + (index % 4));
  addQuality('daily-rename', 'DR', index, split, {
    reviewDate: '2026-09-05',
    strategy: index % 2 === 0 ? 'balanced' : 'clarity_first',
    maxEdits: Math.max(1, selected.length - (index % 2)),
    taskIds: selected
  }, {
    fixtureKey: index % 2 === 0 ? 'personal-standard' : 'team-standard',
    tags: ['quality', 'daily-rename', index % 2 === 0 ? 'balanced' : 'clarity-first'],
    allowedResourceIds: selected,
    semanticRubric: [
      'Every suggested title preserves the original task intent and does not expand its scope.',
      'Suggested titles are action-oriented, specific, and easier to execute.',
      'The response avoids renaming tasks that are already clear.'
    ]
  });
}

for (let index = 0; index < 25; index += 1) {
  const split = qualitySplit(index, 15, 5);
  const personal = index % 2 === 0;
  addQuality('list-replan', 'LR', index, split, { listId: personal ? 920001 : 920002 }, {
    fixtureKey: personal ? 'personal-standard' : 'team-standard',
    tags: ['quality', 'list-replan', personal ? 'personal' : 'team'],
    allowedResourceIds: personal ? TASKS.personalPending : TASKS.teamPending,
    semanticRubric: [
      'Priority, title, and due-date changes are justified by the supplied execution history.',
      'Reasons are consistent with the actual old and new due dates.',
      'The plan is conservative for tasks that may already have started and avoids unnecessary churn.'
    ]
  });
}

const faultMatrix = {
  'task-breakdown': ['invalid-json', 'empty', 'missing-choices', 'http-500', 'timeout', 'invalid-structure', 'markdown-wrapped', 'missing-usage'],
  'weekly-polish': ['invalid-json', 'empty', 'missing-choices', 'http-429', 'timeout', 'invalid-structure', 'markdown-wrapped', 'missing-usage'],
  'today-order': ['invalid-json', 'empty', 'missing-choices', 'http-500', 'timeout', 'unknown-id', 'duplicate-id', 'missing-usage'],
  'daily-rename': ['invalid-json', 'empty', 'missing-choices', 'http-429', 'timeout', 'unauthorized-id', 'overlong-title', 'missing-usage'],
  'list-replan': ['invalid-json', 'empty', 'missing-choices', 'http-500', 'timeout', 'completed-id', 'invalid-date', 'missing-usage']
};

function failurePayload(scene, fault, index) {
  const marker = `[[STAGE3_STUB:${fault}]]`;
  if (scene === 'task-breakdown') return { target: `${marker} 评测目标`, description: '仅用于隔离评测', duration: '1个月', detailed: false };
  if (scene === 'weekly-polish') return { taskIds: TASKS.personalCompleted, reflection: `${marker} 本周评测反思` };
  if (scene === 'today-order') return { taskIds: [931000 + index], timezone: 'Asia/Shanghai', now: '2026-09-05T10:00:00', strategy: 'balanced', limit: 20 };
  if (scene === 'daily-rename') return { reviewDate: '2026-09-05', strategy: 'balanced', maxEdits: 1, taskIds: [932000 + index] };
  return { listId: 921000 + index };
}

let failureIndex = 0;
for (const [scene, faults] of Object.entries(faultMatrix)) {
  faults.forEach((fault, sceneIndex) => {
    const sanitizable = fault === 'markdown-wrapped' || fault === 'missing-usage';
    const supportsRuleFallback = ['today-order', 'daily-rename', 'list-replan'].includes(scene);
    const expectSuccess = sanitizable || supportsRuleFallback;
    const expectedInvariants = { expectSuccess };
    const technicalFailure = ['invalid-json', 'empty', 'missing-choices', 'http-429', 'http-500', 'timeout', 'invalid-structure'].includes(fault);
    const degradationExpected = ['unknown-id', 'duplicate-id'].includes(fault);
    const safelyNormalized = ['unauthorized-id', 'overlong-title', 'completed-id', 'invalid-date'].includes(fault);
    if (supportsRuleFallback && (technicalFailure || degradationExpected)) expectedInvariants.expectDegraded = true;
    if (sanitizable || safelyNormalized || (supportsRuleFallback && !technicalFailure && !degradationExpected)) {
      expectedInvariants.expectNoDegradation = true;
    }
    const allowedResourceIds = scene === 'today-order' ? [931000 + sceneIndex]
      : scene === 'daily-rename' ? [932000 + sceneIndex]
        : scene === 'list-replan' ? [933000 + sceneIndex]
          : scene === 'weekly-polish' ? TASKS.personalCompleted : [];
    failures.push({
      caseId: `FI-${String(++failureIndex).padStart(3, '0')}`,
      scene,
      split: 'failure-injection',
      tags: ['failure-injection', scene, fault],
      actor: 'owner',
      fixtureKey: `failure-${sceneIndex}`,
      requestPayload: failurePayload(scene, fault, sceneIndex),
      expectedInvariants,
      semanticRubric: ['The system follows the expected safe failure or deterministic degradation contract.'],
      allowedResourceIds,
      datasetVersion: VERSION,
      labelVersion: safelyNormalized ? 2 : 1
    });
  });
}

function writeJsonl(name, items) {
  fs.writeFileSync(path.join(datasetDir, name), `${items.map((item) => JSON.stringify(item)).join('\n')}\n`, 'utf8');
}

writeJsonl('development.jsonl', quality.development);
writeJsonl('regression.jsonl', quality.regression);
writeJsonl('holdout.jsonl', quality.holdout);
writeJsonl('failure-injection.jsonl', failures);

const counts = Object.fromEntries(Object.entries(quality).map(([key, value]) => [key, value.length]));
console.log(JSON.stringify({ ...counts, quality: Object.values(counts).reduce((a, b) => a + b, 0), failures: failures.length }));
