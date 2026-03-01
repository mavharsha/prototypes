import chalk from 'chalk';
import { loadStories } from '../store/store.js';
import { displayStoryTable } from '../utils/display.js';
import { COMPETENCIES } from '../models/story.js';

export async function listStories(options) {
  const data = await loadStories();
  let stories = data.stories;

  if (options.competency) {
    const comp = options.competency.toLowerCase();
    if (!COMPETENCIES.includes(comp)) {
      console.log(chalk.red(`\nUnknown competency: "${comp}"`));
      console.log(chalk.dim(`Valid competencies: ${COMPETENCIES.join(', ')}\n`));
      return;
    }
    stories = stories.filter(s => s.competencies.includes(comp));
  }

  console.log(chalk.bold(`\nSTAR Story Bank (${stories.length} ${stories.length === 1 ? 'story' : 'stories'})`));
  displayStoryTable(stories);
}
