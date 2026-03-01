# star-bank

CLI agent that helps you build interview-ready STAR stories through conversational AI coaching. It guides you through extracting experiences, polishes them with strong action verbs and quantified results, and keeps a searchable library tagged by competency.

## Setup

```bash
npm install
cp .env.example .env
# Add your Anthropic API key to .env
```

## Usage

```bash
# Build a new story (conversational intake)
node bin/star-bank.js add

# List all stories
node bin/star-bank.js list

# Filter by competency
node bin/star-bank.js list -c leadership

# Full-text search
node bin/star-bank.js search "microservices"

# View a full story
node bin/star-bank.js show <id>

# Get interview prep for a job description
node bin/star-bank.js prep
node bin/star-bank.js prep -f job-description.txt

# Edit or re-polish a story
node bin/star-bank.js edit <id>

# Delete a story
node bin/star-bank.js delete <id>

# Export all stories to markdown
node bin/star-bank.js export
node bin/star-bank.js export -o my-stories.md
```

## How It Works

### Adding a Story

The `add` command runs a 3-phase flow:

1. **Conversational intake** — The AI coach asks open-ended questions about your experience. It probes for scope, your specific role, challenges, metrics, and decisions. Feels like talking to a career coach, not filling in a form.

2. **Structured extraction** — Once it has enough detail, it proposes a STAR breakdown (Situation, Task, Action, Result). You review, request changes, or approve.

3. **Polish** — The AI rewrites your story with strong action verbs, quantified results, and a flowing interview narrative. It auto-tags competencies and keywords.

### Interview Prep

The `prep` command takes a job description and:
- Identifies 5-8 likely behavioral interview questions
- Matches each question to your best story
- Explains why each story fits
- Suggests talking points to emphasize

### Competencies

Stories are tagged with competencies from a fixed list for reliable filtering:

`leadership` · `execution` · `collaboration` · `technical` · `conflict-resolution` · `failure-resilience` · `innovation` · `communication` · `customer-focus` · `mentorship` · `strategic-thinking` · `data-driven`

## Data

Stories are stored locally in `data/stories.json`. Each story includes:

- Structured STAR fields (situation, task, action, result)
- A polished interview narrative
- Competency tags and freeform keyword tags
- Role, company, and time period
- The raw conversation from intake

## Project Structure

```
star-bank/
  bin/star-bank.js          # CLI entry point
  src/
    commands/               # One file per CLI command
    llm/client.js           # Anthropic SDK wrapper
    llm/prompts.js          # System prompts and tool schemas
    store/store.js          # JSON file CRUD
    models/story.js         # Data model and competency list
    utils/conversation.js   # Readline helper
    utils/display.js        # Terminal formatting
  data/stories.json         # Story bank (created at runtime)
```

## Requirements

- Node.js >= 20
- Anthropic API key
