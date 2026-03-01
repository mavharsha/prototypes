import chalk from 'chalk';
import { getStory, removeStory } from '../store/store.js';
import { createConversation } from '../utils/conversation.js';

export async function deleteStory(id) {
  const story = await getStory(id);
  if (!story) {
    console.log(chalk.red(`\nNo story found matching ID: "${id}"\n`));
    return;
  }

  const conv = createConversation();
  try {
    console.log(chalk.bold(`\nStory: ${story.title}`));
    const ok = await conv.confirm('Are you sure you want to delete this story?');
    if (!ok) {
      console.log(chalk.dim('\nDeletion cancelled.\n'));
      return;
    }
    await removeStory(story.id);
    console.log(chalk.green('\nStory deleted.\n'));
  } finally {
    conv.close();
  }
}
