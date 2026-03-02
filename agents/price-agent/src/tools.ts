import { betaZodTool } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod";
import { Resend } from "resend";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "./config.js";

const resend = new Resend(config.resendApiKey);

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "..", "data");

interface PriceEntry {
  price_usd: number;
  fetched_at: string;
}

function todayDateString(): string {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD
}

async function loadDailyPrices(date: string): Promise<PriceEntry[]> {
  const filePath = join(DATA_DIR, `${date}.json`);
  try {
    const raw = await readFile(filePath, "utf-8");
    return JSON.parse(raw) as PriceEntry[];
  } catch {
    return [];
  }
}

async function saveDailyPrices(
  date: string,
  entries: PriceEntry[]
): Promise<void> {
  await mkdir(DATA_DIR, { recursive: true });
  const filePath = join(DATA_DIR, `${date}.json`);
  await writeFile(filePath, JSON.stringify(entries, null, 2));
}

export async function fetchAndStorePrice(): Promise<{
  current: PriceEntry;
  today_prices: PriceEntry[];
}> {
  const res = await fetch(
    "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
  );
  if (!res.ok) {
    throw new Error(`CoinGecko API error: ${res.status} ${res.statusText}`);
  }
  const data = (await res.json()) as { bitcoin: { usd: number } };

  const entry: PriceEntry = {
    price_usd: data.bitcoin.usd,
    fetched_at: new Date().toISOString(),
  };

  const today = todayDateString();
  const dailyPrices = await loadDailyPrices(today);
  dailyPrices.push(entry);
  await saveDailyPrices(today, dailyPrices);

  return { current: entry, today_prices: dailyPrices };
}

export const sendEmail = betaZodTool({
  name: "send_email",
  description:
    "Send an HTML email to the configured recipient list. You compose the subject and body.",
  inputSchema: z.object({
    subject: z.string().describe("Email subject line"),
    body: z.string().describe("Email body in HTML format"),
  }),
  run: async ({ subject, body }) => {
    const results = await Promise.allSettled(
      config.emailRecipients.map((to) =>
        resend.emails.send({
          from: config.emailFrom,
          to,
          subject,
          html: body,
        })
      )
    );

    const summary = results.map((r, i) => ({
      recipient: config.emailRecipients[i],
      status: r.status === "fulfilled" ? "sent" : "failed",
      ...(r.status === "rejected" && { error: String(r.reason) }),
    }));

    return JSON.stringify(summary);
  },
});

export const tools = [sendEmail];
