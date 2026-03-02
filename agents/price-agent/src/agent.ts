import Anthropic from "@anthropic-ai/sdk";
import { config } from "./config.js";
import { tools, fetchAndStorePrice } from "./tools.js";

const client = new Anthropic({ apiKey: config.anthropicApiKey });

const SYSTEM_PROMPT = `You are a price reporting agent. Your job:

1. Analyze the provided BTC price data to determine sentiment:
   - If there are multiple data points, note the trend (rising, falling, stable).
   - Calculate the day's high, low, and percent change from the first reading.
   - If there's only one data point, simply note that it's the first reading of the day.
2. Compose a concise, well-formatted HTML email that includes:
   - The current price and timestamp.
   - A sentiment summary based on the day's price movements.
   - The day's high/low and number of readings if multiple data points exist.
   - Keep it professional and brief.
3. Send the email using the send_email tool.

Do all of this autonomously — no need to ask for confirmation.`;

export async function runAgent(): Promise<void> {
  console.log(`[${new Date().toISOString()}] Agent run starting...`);

  const priceData = await fetchAndStorePrice();
  console.log(
    `[${new Date().toISOString()}] Fetched BTC price: $${priceData.current.price_usd}`
  );

  const finalMessage = await client.beta.messages.toolRunner({
    model: "claude-haiku-4-5-20251001",
    max_tokens: 4096,
    system: SYSTEM_PROMPT,
    tools,
    messages: [
      {
        role: "user",
        content: `Here is the latest BTC price data. Analyze it and email a summary to the recipients.\n\n${JSON.stringify(priceData, null, 2)}`,
      },
    ],
  });

  const textBlock = finalMessage.content.find((b) => b.type === "text");
  console.log(
    `[${new Date().toISOString()}] Agent done: ${textBlock?.text ?? "(no text)"}`
  );
}

// Allow running directly: npx tsx src/agent.ts
const isDirectRun =
  process.argv[1]?.endsWith("agent.ts") ||
  process.argv[1]?.endsWith("agent.js");
if (isDirectRun) {
  runAgent().catch((err) => {
    console.error("Agent failed:", err);
    process.exit(1);
  });
}
