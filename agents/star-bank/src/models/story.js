import { randomBytes } from 'node:crypto';

export const COMPETENCIES = [
  'leadership',
  'execution',
  'collaboration',
  'technical',
  'conflict-resolution',
  'failure-resilience',
  'innovation',
  'communication',
  'customer-focus',
  'mentorship',
  'strategic-thinking',
  'data-driven',
];

export function generateId() {
  const hex = randomBytes(2).toString('hex');
  return `s_${Date.now()}_${hex}`;
}

export function createStory({ title, situation, task, action, result, polished_narrative, competencies, tags, role, company, time_period, raw_conversation }) {
  return {
    id: generateId(),
    title,
    situation,
    task,
    action,
    result,
    polished_narrative,
    competencies: competencies || [],
    tags: tags || [],
    role: role || '',
    company: company || '',
    time_period: time_period || '',
    raw_conversation: raw_conversation || [],
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  };
}
