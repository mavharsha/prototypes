import { writeFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import chalk from 'chalk';
import { loadStories } from '../store/store.js';

function storyToMarkdown(story) {
  const lines = [`# ${story.title}\n`];

  const meta = [];
  if (story.role) meta.push(`**Role:** ${story.role}`);
  if (story.company) meta.push(`**Company:** ${story.company}`);
  if (story.time_period) meta.push(`**Period:** ${story.time_period}`);
  if (meta.length > 0) lines.push(meta.join(' | ') + '\n');

  if (story.competencies.length > 0) {
    lines.push(`**Competencies:** ${story.competencies.join(', ')}\n`);
  }
  if (story.tags.length > 0) {
    lines.push(`**Tags:** ${story.tags.map(t => `\`${t}\``).join(', ')}\n`);
  }

  lines.push('## Situation\n');
  lines.push(story.situation + '\n');

  lines.push('## Task\n');
  lines.push(story.task + '\n');

  lines.push('## Action\n');
  lines.push(story.action + '\n');

  lines.push('## Result\n');
  lines.push(story.result + '\n');

  lines.push('## Interview Narrative\n');
  lines.push(story.polished_narrative + '\n');

  return lines.join('\n');
}

function slugify(title) {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export async function exportStories(options) {
  const data = await loadStories();
  if (data.stories.length === 0) {
    console.log(chalk.red('\nNo stories to export.\n'));
    return;
  }

  const dir = options.output;
  await mkdir(dir, { recursive: true });

  for (const story of data.stories) {
    const filename = `${slugify(story.title)}.md`;
    await writeFile(join(dir, filename), storyToMarkdown(story));
  }

  console.log(chalk.green(`\nExported ${data.stories.length} stories to ${dir}/\n`));
}
