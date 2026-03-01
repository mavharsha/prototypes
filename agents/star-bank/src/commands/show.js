import chalk from 'chalk';
import { getStory } from '../store/store.js';
import { displayFullStory } from '../utils/display.js';

export async function showStory(id) {
  const story = await getStory(id);
  if (!story) {
    console.log(chalk.red(`\nNo story found matching ID: "${id}"\n`));
    return;
  }
  displayFullStory(story);
}
