import chalk from 'chalk';
import { searchStoriesInStore } from '../store/store.js';
import { displayStoryTable } from '../utils/display.js';

export async function searchStories(query) {
  const results = await searchStoriesInStore(query);
  console.log(chalk.bold(`\nSearch results for "${query}" (${results.length} found)`));
  displayStoryTable(results);
}
