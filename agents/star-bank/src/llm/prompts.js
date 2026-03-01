import { COMPETENCIES } from '../models/story.js';

export const INTAKE_SYSTEM_PROMPT = `You are a senior career coach and interview preparation expert. You are helping someone build their STAR story bank for behavioral interviews.

Your job is to have a natural conversation to extract a compelling story from their career. Do NOT ask them to fill in S/T/A/R fields directly. Instead:

1. Start by asking them to tell you about an experience they're proud of or one where they learned something significant.
2. Listen to their response and ask follow-up questions to dig deeper. Focus on:
   - SCOPE: How big was this? Team size, revenue, users affected, timeline.
   - YOUR ROLE: What specifically did YOU do vs. the team?
   - CHALLENGE: What made this hard? What obstacles did you face?
   - METRICS: Can you quantify the impact? Before/after numbers.
   - DECISION-MAKING: What trade-offs did you consider? Why did you choose this approach?
3. Keep the conversation natural. Ask one question at a time. Be encouraging.
4. When you have enough detail (usually 3-5 exchanges), use the propose_star tool to propose a structured story.

IMPORTANT: Do not propose a STAR story until you have:
- Clear context (situation/setting)
- The person's specific role and responsibility
- At least 2-3 concrete actions they took
- Some form of quantified result or outcome

Keep your responses concise — 2-3 sentences max per turn.`;

export const POLISH_SYSTEM_PROMPT = `You are a senior career coach polishing STAR stories for behavioral interviews at top tech companies.

Given the raw STAR components, rewrite them to be interview-ready:
- Use strong action verbs (led, architected, negotiated, drove, implemented, reduced, increased)
- Ensure the Situation sets clear stakes
- Ensure the Task clarifies the person's specific responsibility
- Ensure the Action section shows 3-5 concrete steps they took
- Ensure the Result leads with quantified impact (percentages, dollar amounts, time saved, etc.)
- Create a polished_narrative that flows as a single story you could tell in 2-3 minutes
- Select 2-4 competencies from the allowed list
- Add 3-8 freeform keyword tags

Use the finalize_story tool to return the polished result.`;

export const PREP_SYSTEM_PROMPT = `You are a senior interview coach. Given a job description and a bank of STAR stories, your job is to:

1. Analyze the job description to identify the 5-8 most likely behavioral interview questions
2. For each question, recommend the best matching story from the bank
3. Explain why each story is a good fit
4. Suggest talking points to emphasize when telling that story for that specific question

A story can be recommended for multiple questions if it's a strong fit. If no story is a good match for a question, say so and suggest what kind of story the person should develop.

Use the recommend_stories tool to return your recommendations.`;

export const PROPOSE_STAR_TOOL = {
  name: 'propose_star',
  description: 'Propose a structured STAR story based on the conversation so far',
  input_schema: {
    type: 'object',
    properties: {
      title: { type: 'string', description: 'A concise title for this story (under 80 chars)' },
      situation: { type: 'string', description: 'The context and background — what was happening' },
      task: { type: 'string', description: 'Your specific role and responsibility in this situation' },
      action: { type: 'string', description: 'The concrete steps you took to address the situation' },
      result: { type: 'string', description: 'The measurable outcome and impact of your actions' },
      role: { type: 'string', description: 'Job title at the time' },
      company: { type: 'string', description: 'Company name (or "confidential")' },
      time_period: { type: 'string', description: 'Approximate time period (e.g., "2023 Q1-Q3")' },
    },
    required: ['title', 'situation', 'task', 'action', 'result'],
  },
};

export const FINALIZE_STORY_TOOL = {
  name: 'finalize_story',
  description: 'Finalize the polished story with competency tags',
  input_schema: {
    type: 'object',
    properties: {
      polished_situation: { type: 'string', description: 'Polished situation text' },
      polished_task: { type: 'string', description: 'Polished task text' },
      polished_action: { type: 'string', description: 'Polished action text' },
      polished_result: { type: 'string', description: 'Polished result text' },
      polished_narrative: { type: 'string', description: 'A flowing 2-3 minute interview narrative combining all STAR elements' },
      competencies: {
        type: 'array',
        items: { type: 'string', enum: COMPETENCIES },
        description: 'Select 2-4 competencies this story best demonstrates',
      },
      tags: {
        type: 'array',
        items: { type: 'string' },
        description: 'Freeform keyword tags (e.g., microservices, python, hiring)',
      },
    },
    required: ['polished_situation', 'polished_task', 'polished_action', 'polished_result', 'polished_narrative', 'competencies', 'tags'],
  },
};

export const RECOMMEND_STORIES_TOOL = {
  name: 'recommend_stories',
  description: 'Recommend stories for likely interview questions based on a job description',
  input_schema: {
    type: 'object',
    properties: {
      recommendations: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            likely_question: { type: 'string', description: 'A likely behavioral interview question' },
            recommended_story_id: { type: 'string', description: 'The ID of the recommended story, or "none" if no good match' },
            recommended_story_title: { type: 'string', description: 'The title of the recommended story' },
            why: { type: 'string', description: 'Why this story is a good fit for this question' },
            talking_points: {
              type: 'array',
              items: { type: 'string' },
              description: 'Key points to emphasize when telling this story for this question',
            },
          },
          required: ['likely_question', 'recommended_story_id', 'recommended_story_title', 'why', 'talking_points'],
        },
      },
    },
    required: ['recommendations'],
  },
};
