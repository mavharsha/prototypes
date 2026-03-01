import chalk from 'chalk';
import { getStory, updateStory } from '../store/store.js';
import { chat } from '../llm/client.js';
import { POLISH_SYSTEM_PROMPT, FINALIZE_STORY_TOOL } from '../llm/prompts.js';
import { createConversation } from '../utils/conversation.js';
import { displayFullStory } from '../utils/display.js';

export async function editStory(id) {
  const story = await getStory(id);
  if (!story) {
    console.log(chalk.red(`\nNo story found matching ID: "${id}"\n`));
    return;
  }

  displayFullStory(story);

  const conv = createConversation();
  try {
    console.log(chalk.bold('What would you like to do?'));
    console.log('  1. Re-polish with AI (keeps content, improves wording)');
    console.log('  2. Edit fields manually, then re-polish');
    console.log('  3. Cancel\n');

    const choice = await conv.ask();

    if (choice === '1') {
      await repolish(story, conv);
    } else if (choice === '2') {
      await manualEdit(story, conv);
    } else {
      console.log(chalk.dim('\nEdit cancelled.\n'));
    }
  } finally {
    conv.close();
  }
}

async function repolish(story, conv) {
  console.log(chalk.dim('\nRe-polishing story...\n'));
  const polished = await polishFields(story);
  if (!polished) {
    console.log(chalk.red('Failed to re-polish. Story unchanged.\n'));
    return;
  }

  const updated = await updateStory(story.id, {
    situation: polished.polished_situation,
    task: polished.polished_task,
    action: polished.polished_action,
    result: polished.polished_result,
    polished_narrative: polished.polished_narrative,
    competencies: polished.competencies,
    tags: polished.tags,
  });

  displayFullStory(updated);
  console.log(chalk.green('Story re-polished and saved.\n'));
}

async function manualEdit(story, conv) {
  console.log(chalk.dim('\nPress Enter to keep the current value, or type a replacement.\n'));

  const fields = ['title', 'situation', 'task', 'action', 'result', 'role', 'company', 'time_period'];
  const updates = {};

  for (const field of fields) {
    console.log(chalk.bold(`\n${field.toUpperCase()}:`));
    console.log(chalk.dim(story[field] || '(empty)'));
    const input = await conv.ask('New value (Enter to keep):');
    if (input && input.trim()) {
      updates[field] = input.trim();
    }
  }

  if (Object.keys(updates).length === 0) {
    console.log(chalk.dim('\nNo changes made.\n'));
    return;
  }

  // Merge updates into story for re-polishing
  const merged = { ...story, ...updates };

  console.log(chalk.dim('\nRe-polishing with your edits...\n'));
  const polished = await polishFields(merged);
  if (polished) {
    updates.situation = polished.polished_situation;
    updates.task = polished.polished_task;
    updates.action = polished.polished_action;
    updates.result = polished.polished_result;
    updates.polished_narrative = polished.polished_narrative;
    updates.competencies = polished.competencies;
    updates.tags = polished.tags;
  }

  const updated = await updateStory(story.id, updates);
  displayFullStory(updated);
  console.log(chalk.green('Story updated and saved.\n'));
}

async function polishFields(story) {
  const messages = [
    {
      role: 'user',
      content: `Polish this STAR story for interviews:\n\nTitle: ${story.title}\nRole: ${story.role || 'Not specified'}\nCompany: ${story.company || 'Not specified'}\nPeriod: ${story.time_period || 'Not specified'}\n\nSituation: ${story.situation}\n\nTask: ${story.task}\n\nAction: ${story.action}\n\nResult: ${story.result}`,
    },
  ];

  const response = await chat(messages, {
    system: POLISH_SYSTEM_PROMPT,
    tools: [FINALIZE_STORY_TOOL],
  });

  const toolUse = response.content.find(b => b.type === 'tool_use' && b.name === 'finalize_story');
  return toolUse ? toolUse.input : null;
}
