const express = require("express");
const { randomInt } = require("crypto");
const os = require("os");

const app = express();
const PORT = process.env.PORT || 3000;

app.get("/random", (req, res) => {
  const min = parseInt(req.query.min) || 1;
  const max = parseInt(req.query.max) || 1000;

  if (min >= max) {
    return res.status(400).json({ error: "min must be less than max" });
  }

  res.json({
    number: randomInt(min, max),
    min,
    max,
    instance: os.hostname(),
    timestamp: new Date().toISOString(),
  });
});

app.get("/health", (req, res) => {
  res.json({ status: "ok" });
});

app.listen(PORT, () => {
  console.log(`Random number API running on port ${PORT}`);
});
