import Anthropic from '@anthropic-ai/sdk';

let client = null;

function getClient() {
  if (!client) {
    const apiKey = process.env.ANTHROPIC_API_KEY;
    if (!apiKey) {
      console.error('Error: ANTHROPIC_API_KEY not set. Copy .env.example to .env and add your key.');
      process.exit(1);
    }
    client = new Anthropic({ apiKey });
  }
  return client;
}

export async function chat(messages, { system, tools, model = 'claude-sonnet-4-5-20250929', maxTokens = 4096 } = {}) {
  const anthropic = getClient();
  const params = {
    model,
    max_tokens: maxTokens,
    messages,
  };
  if (system) params.system = system;
  if (tools) params.tools = tools;

  return anthropic.messages.create(params);
}
