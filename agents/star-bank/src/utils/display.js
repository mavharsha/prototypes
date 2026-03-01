import chalk from 'chalk';
import Table from 'cli-table3';

const COMPETENCY_COLORS = {
  'leadership': 'magenta',
  'execution': 'blue',
  'collaboration': 'cyan',
  'technical': 'green',
  'conflict-resolution': 'red',
  'failure-resilience': 'yellow',
  'innovation': 'magentaBright',
  'communication': 'cyanBright',
  'customer-focus': 'blueBright',
  'mentorship': 'greenBright',
  'strategic-thinking': 'yellowBright',
  'data-driven': 'redBright',
};

export function formatCompetency(comp) {
  const color = COMPETENCY_COLORS[comp] || 'white';
  return chalk[color](comp);
}

export function shortId(id) {
  const parts = id.split('_');
  return parts.length >= 3 ? `${parts[0]}_...${parts[2]}` : id.substring(0, 12);
}

export function displayStoryTable(stories) {
  if (stories.length === 0) {
    console.log(chalk.dim('\nNo stories found.\n'));
    return;
  }

  const table = new Table({
    head: [chalk.bold('ID'), chalk.bold('Title'), chalk.bold('Competencies'), chalk.bold('Created')],
    colWidths: [14, 40, 30, 12],
    wordWrap: true,
  });

  for (const story of stories) {
    table.push([
      shortId(story.id),
      story.title,
      story.competencies.map(formatCompetency).join(', '),
      story.created_at.split('T')[0],
    ]);
  }

  console.log('\n' + table.toString() + '\n');
}

export function displayFullStory(story) {
  console.log('\n' + chalk.bold.underline(story.title));
  console.log(chalk.dim(`ID: ${story.id}`));
  if (story.role || story.company) {
    console.log(chalk.dim(`${story.role}${story.company ? ` at ${story.company}` : ''}${story.time_period ? ` (${story.time_period})` : ''}`));
  }
  console.log(chalk.dim(`Created: ${story.created_at.split('T')[0]} | Updated: ${story.updated_at.split('T')[0]}`));

  console.log('\n' + chalk.bold('Competencies: ') + story.competencies.map(formatCompetency).join(', '));
  if (story.tags.length > 0) {
    console.log(chalk.bold('Tags: ') + story.tags.map(t => chalk.dim(`#${t}`)).join(' '));
  }

  console.log('\n' + chalk.bold.yellow('--- Situation ---'));
  console.log(story.situation);

  console.log('\n' + chalk.bold.yellow('--- Task ---'));
  console.log(story.task);

  console.log('\n' + chalk.bold.yellow('--- Action ---'));
  console.log(story.action);

  console.log('\n' + chalk.bold.yellow('--- Result ---'));
  console.log(story.result);

  console.log('\n' + chalk.bold.green('--- Interview Narrative ---'));
  console.log(story.polished_narrative);

  console.log('');
}

export function displayProposal(proposal) {
  console.log('\n' + chalk.bold.underline('Proposed STAR Story'));
  console.log(chalk.bold(`Title: `) + proposal.title);
  if (proposal.role) console.log(chalk.dim(`Role: ${proposal.role}`));
  if (proposal.company) console.log(chalk.dim(`Company: ${proposal.company}`));
  if (proposal.time_period) console.log(chalk.dim(`Period: ${proposal.time_period}`));

  console.log('\n' + chalk.bold.yellow('Situation: ') + proposal.situation);
  console.log('\n' + chalk.bold.yellow('Task: ') + proposal.task);
  console.log('\n' + chalk.bold.yellow('Action: ') + proposal.action);
  console.log('\n' + chalk.bold.yellow('Result: ') + proposal.result);
  console.log('');
}
