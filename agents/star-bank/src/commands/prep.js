import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import chalk from 'chalk';
import { chat } from '../llm/client.js';
import { PREP_SYSTEM_PROMPT, RECOMMEND_STORIES_TOOL } from '../llm/prompts.js';
import { loadStories } from '../store/store.js';
import { createConversation } from '../utils/conversation.js';

export async function prepStories(description, options) {
  const data = await loadStories();
  if (data.stories.length === 0) {
    console.log(chalk.red('\nNo stories in your bank yet. Run `star-bank add` first.\n'));
    return;
  }

  let jobDescription = '';

  // Get job description from file, argument, or interactive input
  if (options.file) {
    if (!existsSync(options.file)) {
      console.log(chalk.red(`\nFile not found: ${options.file}\n`));
      return;
    }
    jobDescription = await readFile(options.file, 'utf-8');
  } else if (description) {
    jobDescription = description;
  } else {
    const conv = createConversation();
    try {
      jobDescription = await conv.multiLineInput('Paste the job description below:');
    } finally {
      conv.close();
    }
  }

  if (!jobDescription.trim()) {
    console.log(chalk.red('\nNo job description provided.\n'));
    return;
  }

  console.log(chalk.dim('\nAnalyzing job description and matching stories...\n'));

  // Build story summaries for the LLM
  const storySummaries = data.stories.map(s => ({
    id: s.id,
    title: s.title,
    competencies: s.competencies,
    tags: s.tags,
    situation_preview: s.situation.substring(0, 150),
    result_preview: s.result.substring(0, 150),
  }));

  const messages = [
    {
      role: 'user',
      content: `Job Description:\n${jobDescription}\n\n---\n\nStory Bank:\n${JSON.stringify(storySummaries, null, 2)}`,
    },
  ];

  const response = await chat(messages, {
    system: PREP_SYSTEM_PROMPT,
    tools: [RECOMMEND_STORIES_TOOL],
  });

  const toolUse = response.content.find(b => b.type === 'tool_use' && b.name === 'recommend_stories');
  if (!toolUse) {
    console.log(chalk.red('\nFailed to generate recommendations.\n'));
    return;
  }

  const { recommendations } = toolUse.input;
  displayRecommendations(recommendations);
}

function displayRecommendations(recommendations) {
  console.log(chalk.bold.underline('\nInterview Prep Recommendations\n'));

  for (let i = 0; i < recommendations.length; i++) {
    const rec = recommendations[i];
    console.log(chalk.bold.cyan(`Q${i + 1}: "${rec.likely_question}"`));

    if (rec.recommended_story_id === 'none') {
      console.log(chalk.yellow(`  Story: No matching story — ${rec.why}`));
    } else {
      console.log(chalk.green(`  Story: ${rec.recommended_story_title}`));
      console.log(chalk.dim(`  Why: ${rec.why}`));
    }

    if (rec.talking_points && rec.talking_points.length > 0) {
      console.log(chalk.bold('  Talking points:'));
      for (const point of rec.talking_points) {
        console.log(`    - ${point}`);
      }
    }
    console.log('');
  }
}
