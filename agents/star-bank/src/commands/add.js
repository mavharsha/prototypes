import chalk from 'chalk';
import { chat } from '../llm/client.js';
import { INTAKE_SYSTEM_PROMPT, POLISH_SYSTEM_PROMPT, PROPOSE_STAR_TOOL, FINALIZE_STORY_TOOL } from '../llm/prompts.js';
import { createStory } from '../models/story.js';
import { addStoryToStore } from '../store/store.js';
import { createConversation } from '../utils/conversation.js';
import { displayProposal, displayFullStory } from '../utils/display.js';

export async function addStory() {
  const conv = createConversation();

  console.log(chalk.bold('\n=== STAR Story Builder ==='));
  console.log(chalk.dim('I\'ll coach you through building an interview-ready STAR story.\n'));

  const messages = [];
  const rawConversation = [];

  try {
    // Phase 1: Conversational intake
    const proposal = await runIntake(conv, messages, rawConversation);
    if (!proposal) {
      console.log(chalk.dim('\nStory creation cancelled.\n'));
      return;
    }

    // Phase 2: Confirm proposal
    const confirmed = await confirmProposal(conv, messages, rawConversation, proposal);
    if (!confirmed) {
      console.log(chalk.dim('\nStory creation cancelled.\n'));
      return;
    }

    // Phase 3: Polish and save
    console.log(chalk.dim('\nPolishing your story...\n'));
    const polished = await polishStory(confirmed);
    if (!polished) {
      console.log(chalk.red('\nFailed to polish story. Saving raw version.\n'));
      const story = createStory({ ...confirmed, raw_conversation: rawConversation });
      await addStoryToStore(story);
      console.log(chalk.green(`Story saved with ID: ${story.id}\n`));
      return;
    }

    const story = createStory({
      title: confirmed.title,
      situation: polished.polished_situation,
      task: polished.polished_task,
      action: polished.polished_action,
      result: polished.polished_result,
      polished_narrative: polished.polished_narrative,
      competencies: polished.competencies,
      tags: polished.tags,
      role: confirmed.role || '',
      company: confirmed.company || '',
      time_period: confirmed.time_period || '',
      raw_conversation: rawConversation,
    });

    await addStoryToStore(story);
    displayFullStory(story);
    console.log(chalk.green.bold(`Story saved with ID: ${story.id}\n`));
  } finally {
    conv.close();
  }
}

async function runIntake(conv, messages, rawConversation) {
  // Get the initial prompt from the LLM
  const initialResponse = await chat(
    [{ role: 'user', content: 'Start the conversation. Ask me about an experience.' }],
    { system: INTAKE_SYSTEM_PROMPT, tools: [PROPOSE_STAR_TOOL] }
  );

  // Replace the seed message with the real conversation
  messages.length = 0;
  messages.push({ role: 'user', content: 'Start the conversation. Ask me about an experience.' });

  const assistantText = extractText(initialResponse);
  messages.push({ role: 'assistant', content: initialResponse.content });
  rawConversation.push({ role: 'assistant', content: assistantText });

  const userInput = await conv.ask(assistantText);
  if (isQuit(userInput)) return null;

  messages.push({ role: 'user', content: userInput });
  rawConversation.push({ role: 'user', content: userInput });

  // Continue conversation until the LLM proposes a STAR story
  const MAX_TURNS = 10;
  for (let i = 0; i < MAX_TURNS; i++) {
    const response = await chat(messages, {
      system: INTAKE_SYSTEM_PROMPT,
      tools: [PROPOSE_STAR_TOOL],
    });

    // Check if the LLM used the propose_star tool
    const toolUse = response.content.find(b => b.type === 'tool_use' && b.name === 'propose_star');
    if (toolUse) {
      // There might be text before the tool call
      const textBefore = extractText(response);
      if (textBefore) {
        conv.display(textBefore);
        rawConversation.push({ role: 'assistant', content: textBefore });
      }
      return toolUse.input;
    }

    // Regular text response — continue conversation
    const text = extractText(response);
    messages.push({ role: 'assistant', content: response.content });
    rawConversation.push({ role: 'assistant', content: text });

    const input = await conv.ask(text);
    if (isQuit(input)) return null;

    messages.push({ role: 'user', content: input });
    rawConversation.push({ role: 'user', content: input });
  }

  console.log(chalk.yellow('\nReached maximum conversation turns. Let me propose a story with what I have.\n'));

  // Force a proposal
  messages.push({ role: 'user', content: 'Please propose a STAR story now with what you have.' });
  const finalResponse = await chat(messages, {
    system: INTAKE_SYSTEM_PROMPT,
    tools: [PROPOSE_STAR_TOOL],
  });

  const toolUse = finalResponse.content.find(b => b.type === 'tool_use' && b.name === 'propose_star');
  return toolUse ? toolUse.input : null;
}

async function confirmProposal(conv, messages, rawConversation, proposal) {
  displayProposal(proposal);

  const ok = await conv.confirm('Does this look right?');
  if (ok) return proposal;

  // Let the user refine
  conv.display('What would you like to change? Tell me what needs fixing.');
  const feedback = await conv.ask();
  if (isQuit(feedback)) return null;

  rawConversation.push({ role: 'user', content: `Feedback on proposal: ${feedback}` });

  // Send the proposal + feedback back to the LLM for a revised proposal
  const revisionMessages = [
    ...messages,
    {
      role: 'assistant',
      content: [
        { type: 'tool_use', id: 'propose_1', name: 'propose_star', input: proposal },
      ],
    },
    {
      role: 'user',
      content: [
        { type: 'tool_result', tool_use_id: 'propose_1', content: `User feedback: ${feedback}. Please revise and propose again.` },
      ],
    },
  ];

  const response = await chat(revisionMessages, {
    system: INTAKE_SYSTEM_PROMPT,
    tools: [PROPOSE_STAR_TOOL],
  });

  const toolUse = response.content.find(b => b.type === 'tool_use' && b.name === 'propose_star');
  if (toolUse) {
    const revised = toolUse.input;
    displayProposal(revised);
    const ok2 = await conv.confirm('How about now?');
    return ok2 ? revised : null;
  }

  return null;
}

async function polishStory(proposal) {
  const polishMessages = [
    {
      role: 'user',
      content: `Polish this STAR story for interviews:\n\nTitle: ${proposal.title}\nRole: ${proposal.role || 'Not specified'}\nCompany: ${proposal.company || 'Not specified'}\nPeriod: ${proposal.time_period || 'Not specified'}\n\nSituation: ${proposal.situation}\n\nTask: ${proposal.task}\n\nAction: ${proposal.action}\n\nResult: ${proposal.result}`,
    },
  ];

  const response = await chat(polishMessages, {
    system: POLISH_SYSTEM_PROMPT,
    tools: [FINALIZE_STORY_TOOL],
  });

  const toolUse = response.content.find(b => b.type === 'tool_use' && b.name === 'finalize_story');
  return toolUse ? toolUse.input : null;
}

function extractText(response) {
  return response.content
    .filter(b => b.type === 'text')
    .map(b => b.text)
    .join('\n')
    .trim();
}

function isQuit(input) {
  return ['quit', 'exit', 'q', 'cancel'].includes(input.toLowerCase());
}
