import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_PATH = join(__dirname, '..', '..', 'data', 'stories.json');

const EMPTY_STORE = { version: 1, stories: [] };

async function ensureDataFile() {
  if (!existsSync(DATA_PATH)) {
    await mkdir(dirname(DATA_PATH), { recursive: true });
    await writeFile(DATA_PATH, JSON.stringify(EMPTY_STORE, null, 2));
  }
}

export async function loadStories() {
  await ensureDataFile();
  const raw = await readFile(DATA_PATH, 'utf-8');
  return JSON.parse(raw);
}

export async function saveStories(data) {
  await writeFile(DATA_PATH, JSON.stringify(data, null, 2));
}

export async function addStoryToStore(story) {
  const data = await loadStories();
  data.stories.push(story);
  await saveStories(data);
  return story;
}

export async function getStory(id) {
  const data = await loadStories();
  return data.stories.find(s => s.id === id || s.id.startsWith(id));
}

export async function updateStory(id, updates) {
  const data = await loadStories();
  const idx = data.stories.findIndex(s => s.id === id || s.id.startsWith(id));
  if (idx === -1) return null;
  data.stories[idx] = { ...data.stories[idx], ...updates, updated_at: new Date().toISOString() };
  await saveStories(data);
  return data.stories[idx];
}

export async function removeStory(id) {
  const data = await loadStories();
  const idx = data.stories.findIndex(s => s.id === id || s.id.startsWith(id));
  if (idx === -1) return false;
  data.stories.splice(idx, 1);
  await saveStories(data);
  return true;
}

export async function searchStoriesInStore(query) {
  const data = await loadStories();
  const q = query.toLowerCase();
  return data.stories.filter(s => {
    const searchable = [
      s.title, s.situation, s.task, s.action, s.result,
      s.polished_narrative, ...s.competencies, ...s.tags,
    ].join(' ').toLowerCase();
    return searchable.includes(q);
  });
}
