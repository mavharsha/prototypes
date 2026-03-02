import "dotenv/config";

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required env var: ${name}`);
    process.exit(1);
  }
  return value;
}

export const config = {
  anthropicApiKey: required("ANTHROPIC_API_KEY"),
  resendApiKey: required("RESEND_API_KEY"),
  emailFrom: required("EMAIL_FROM"),
  emailRecipients: required("EMAIL_RECIPIENTS")
    .split(",")
    .map((e) => e.trim()),
  cronSchedule: process.env.CRON_SCHEDULE || "0 */6 * * *",
};
