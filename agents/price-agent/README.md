# Price Agent

An agentic workflow that scrapes the BTC price every 6 hours and emails a summary. Built as a learning project for the core agent pattern: LLM + tool use + agentic loop.

## How it works

```
cron fires → runAgent() → toolRunner starts
  → Claude calls scrape_price → fetches CoinGecko → returns price JSON
  → Claude composes email, calls send_email → Resend API → returns status
  → Claude writes final summary → toolRunner returns
```

## Setup

```bash
npm install
cp .env.example .env
```

Fill in `.env`:

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | From [console.anthropic.com](https://console.anthropic.com) |
| `RESEND_API_KEY` | From [resend.com](https://resend.com) |
| `EMAIL_FROM` | Must be a verified sender domain in Resend |
| `EMAIL_RECIPIENTS` | Comma-separated list of email addresses |
| `CRON_SCHEDULE` | Cron expression (default: `0 */6 * * *` = every 6 hours) |

## Running

**Single run** (fetch price + send email once):

```bash
npx tsx src/agent.ts
```

**With cron scheduling** (runs once on startup, then on schedule):

```bash
npx tsx src/index.ts
```

**Quick test** — set a 2-minute schedule to verify the loop works:

```bash
CRON_SCHEDULE="*/2 * * * *" npx tsx src/index.ts
```

## Stack

- **Runtime**: TypeScript / Node.js (ESM)
- **LLM**: Claude (Sonnet 4.5) via `@anthropic-ai/sdk` with `betaZodTool` + `toolRunner`
- **Price data**: CoinGecko free API (no key needed)
- **Email**: Resend
- **Scheduling**: node-cron
