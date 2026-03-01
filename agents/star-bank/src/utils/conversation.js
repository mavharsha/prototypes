import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import chalk from 'chalk';

export function createConversation() {
  const rl = createInterface({ input: stdin, output: stdout });

  return {
    async ask(prompt) {
      if (prompt) {
        console.log(chalk.green('\nCoach: ') + prompt + '\n');
      }
      const answer = await rl.question(chalk.cyan('You: '));
      return answer.trim();
    },

    display(message) {
      console.log(chalk.green('\nCoach: ') + message + '\n');
    },

    async confirm(message) {
      const answer = await rl.question(chalk.yellow(`\n${message} (y/n): `));
      return answer.trim().toLowerCase().startsWith('y');
    },

    async multiLineInput(prompt) {
      console.log(chalk.yellow(`\n${prompt}`));
      console.log(chalk.dim('(Paste your text, then press Enter twice to finish)\n'));
      const lines = [];
      let emptyCount = 0;
      while (true) {
        const line = await rl.question('');
        if (line.trim() === '') {
          emptyCount++;
          if (emptyCount >= 2) break;
          lines.push('');
        } else {
          emptyCount = 0;
          lines.push(line);
        }
      }
      return lines.join('\n').trim();
    },

    close() {
      rl.close();
    },
  };
}
