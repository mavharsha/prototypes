import cron from "node-cron";
import { config } from "./config.js";
import { runAgent } from "./agent.js";

console.log("Price Agent starting up");
console.log(`  Schedule : ${config.cronSchedule}`);
console.log(`  Recipients: ${config.emailRecipients.join(", ")}`);
console.log(`  From     : ${config.emailFrom}`);
console.log();

// Run once immediately on startup
runAgent().catch((err) => console.error("Initial run failed:", err));

// Schedule recurring runs
cron.schedule(config.cronSchedule, () => {
  runAgent().catch((err) => console.error("Scheduled run failed:", err));
});

console.log("Cron scheduled. Waiting for next tick...");
