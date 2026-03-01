#!/usr/bin/env node
import { config } from 'dotenv';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '..', '.env') });

import { program } from 'commander';
import { addStory } from '../src/commands/add.js';
import { listStories } from '../src/commands/list.js';
import { searchStories } from '../src/commands/search.js';
import { showStory } from '../src/commands/show.js';
import { prepStories } from '../src/commands/prep.js';
import { editStory } from '../src/commands/edit.js';
import { deleteStory } from '../src/commands/delete.js';
import { exportStories } from '../src/commands/export.js';

program
  .name('star-bank')
  .description('STAR Story Bank Builder — build interview-ready STAR stories with AI coaching')
  .version('1.0.0');

program
  .command('add')
  .description('Start conversational intake to create a new STAR story')
  .action(addStory);

program
  .command('list')
  .description('List all stories')
  .option('-c, --competency <name>', 'Filter by competency')
  .action(listStories);

program
  .command('search <query>')
  .description('Full-text search across stories')
  .action(searchStories);

program
  .command('show <id>')
  .description('Display a full story')
  .action(showStory);

program
  .command('prep [description]')
  .description('Recommend stories for a job description')
  .option('-f, --file <path>', 'Read job description from file')
  .action(prepStories);

program
  .command('edit <id>')
  .description('Re-polish or update an existing story')
  .action(editStory);

program
  .command('delete <id>')
  .description('Remove a story')
  .action(deleteStory);

program
  .command('export')
  .description('Export all stories to markdown')
  .option('-o, --output <dir>', 'Output directory', 'exports')
  .action(exportStories);

program.parse();
