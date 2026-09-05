'use strict';

module.exports = function taskBreakdownAssertion(output, context) {
  const envelope = JSON.parse(output);
  if (!envelope.success) return { pass: false, score: 0, reason: 'Task breakdown request failed' };
  const milestones = envelope.data?.milestones;
  const payload = JSON.parse(context.vars.requestPayload || '{}');
  const minMilestones = payload.detailed ? 3 : 2;
  const maxMilestones = 4;
  if (!Array.isArray(milestones) || milestones.length < minMilestones || milestones.length > maxMilestones) {
    return { pass: false, score: 0, reason: `Expected ${minMilestones}-${maxMilestones} milestones` };
  }
  const names = new Set();
  const startDate = new Date(`${process.env.STAGE3_EVAL_TODAY || new Date().toISOString().slice(0, 10)}T00:00:00Z`);
  const durationMatch = String(payload.duration || '').match(/^(\d+)\s*(天|周|个月|月|年)$/);
  if (!durationMatch) return { pass: false, score: 0, reason: 'Dataset duration is not supported' };
  const durationAmount = Number(durationMatch[1]);
  const durationUnit = durationMatch[2];
  const lastAllowedDate = new Date(startDate);
  if (durationUnit === '天') lastAllowedDate.setUTCDate(lastAllowedDate.getUTCDate() + durationAmount);
  else if (durationUnit === '周') lastAllowedDate.setUTCDate(lastAllowedDate.getUTCDate() + durationAmount * 7);
  else if (durationUnit === '个月' || durationUnit === '月') lastAllowedDate.setUTCMonth(lastAllowedDate.getUTCMonth() + durationAmount);
  else lastAllowedDate.setUTCFullYear(lastAllowedDate.getUTCFullYear() + durationAmount);
  for (const milestone of milestones) {
    if (!milestone || typeof milestone.name !== 'string' || !milestone.name.trim() || milestone.name.length > 100) {
      return { pass: false, score: 0, reason: 'Milestone name is blank or too long' };
    }
    if (!Array.isArray(milestone.tasks)) {
      return { pass: false, score: 0, reason: 'Milestone tasks are missing' };
    }
    const minTasks = payload.detailed ? 4 : 2;
    const maxTasks = payload.detailed ? 6 : 5;
    if (milestone.tasks.length < minTasks || milestone.tasks.length > maxTasks) {
      return { pass: false, score: 0, reason: `Task count must be ${minTasks}-${maxTasks}` };
    }
    for (const task of milestone.tasks) {
      const normalized = String(task?.name || '').trim().toLowerCase();
      if (!normalized || normalized.length > 60 || names.has(normalized)) {
        return { pass: false, score: 0, reason: 'Task title is blank, too long, or duplicated' };
      }
      names.add(normalized);
      if (!Number.isInteger(task.priority) || task.priority < 0 || task.priority > 3) {
        return { pass: false, score: 0, reason: 'Task priority is outside 0-3' };
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(String(task.dueDate || ''))) {
        return { pass: false, score: 0, reason: 'Task due date is not an absolute ISO date' };
      }
      const parsedDueDate = new Date(`${task.dueDate}T00:00:00Z`);
      if (!Number.isFinite(parsedDueDate.getTime()) || parsedDueDate.toISOString().slice(0, 10) !== task.dueDate
          || parsedDueDate < startDate || parsedDueDate > lastAllowedDate) {
        return { pass: false, score: 0, reason: 'Task due date is outside the requested planning interval' };
      }
    }
  }
  return { pass: true, score: 1, reason: 'Task breakdown structure and business invariants passed' };
};
