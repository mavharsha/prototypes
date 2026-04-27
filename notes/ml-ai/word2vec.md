# Word2Vec - From Words to Numbers

> Think of this like learning a new database paradigm. You already know how to store structured data in rows and columns. Word2Vec is about storing *meaning* in rows and columns. Once that clicks, the rest follows naturally.

---

## 1. What Problem Are We Solving?

As a developer, you've probably stored text as strings. But strings are terrible for comparison — `"king"` and `"queen"` are just two unrelated character arrays to a computer. There's no way to express that they're related concepts.

**Word2Vec solves this.** It converts words into arrays of floating-point numbers (vectors) where the *position* in that number space encodes meaning. Words with similar meaning end up near each other.

```
// What you're used to:
"king"  ->  just characters: [107, 105, 110, 103]   // no meaning here

// What Word2Vec gives you:
"king"  ->  meaning vector: [0.9, 0.7, 0.8, 0.80, 0.6, ...]   // 300 floats
"queen" ->  meaning vector: [0.9, 0.7, 0.8, 0.28, 0.6, ...]   // nearby!
```

**Important clarification:** Word2Vec is a **whole-word** model. It treats `"running"` as one atomic token — it does NOT break words into pieces like `"run"` + `"ning"`. That's a different family of techniques (BPE, WordPiece) used in modern models like GPT and BERT. We'll compare them later.

---

## 2. The Two Architectures: CBOW and Skip-gram

Word2Vec has two training strategies. Think of them as two ways to learn vocabulary from context — similar to how you'd learn a new programming language by either reading code or writing it.

### CBOW (Continuous Bag of Words) — "Fill in the blank"

Given the surrounding words, predict the missing center word. Like reading someone else's code and guessing what a variable name should be.

```
Input:  [The, cat, ___, on, the, mat]
Output: sat

It's like: "Given this context, what word fits here?"
```

### Skip-gram — "Generate context from a word"

Given one word, predict what words are likely to appear around it. Like knowing a function name and predicting what parameters and return type it probably has.

```
Input:  sat
Output: [The, cat, on, the, mat]

It's like: "Given this word, what context would you expect?"
```

```
CBOW:                          Skip-gram:
context words -> target word   target word -> context words

  The  ──┐                        ┌── The
  cat  ──┤                        ├── cat
         ├──> sat                sat ──┤
  on   ──┤                        ├── on
  the  ──┤                        ├── the
  mat  ──┘                        └── mat
```

**Which is better?** Skip-gram handles rare words better (like how you learn niche library functions — even seeing them once in context helps). CBOW is faster and works well for common words. In practice, Skip-gram is more widely used.

---

## 3. The Weight Matrix — This Is Just a Lookup Table

Here's where your database intuition pays off. After training, Word2Vec produces a matrix. Think of it as a table:

```
             D=300 columns (features the model learned)
          ┌──────────────────────────────────────────┐
  "king"  │ 0.12   0.45   0.78   0.80   ...  0.33  │  <- row 0
  "queen" │ 0.11   0.43   0.76   0.28   ...  0.31  │  <- row 1
  "man"   │ 0.08   0.52   0.41   0.82   ...  0.19  │  <- row 2
    ...   │                  ...                     │
  V rows  │ 0.33   0.21   0.55   0.67   ...  0.44  │  <- row 2,999,999
          └──────────────────────────────────────────┘
```

**The dimensions:**
- **Rows (V)** = vocabulary size = number of unique words
- **Columns (D)** = embedding dimension = a hyperparameter you choose

For Google's pretrained model:
- **V = ~3,000,000** words (from Google News corpus, ~100 billion words of text)
- **D = 300** dimensions
- That's a **3,000,000 x 300 matrix** (~3.6 GB of floats)

To get the embedding for a word, you just look up its row. Literally an array index lookup — O(1). If you've used a HashMap, you've already got the mental model. The word maps to an integer index, and you grab that row from the matrix.

### What Are the 300 Columns?

Nobody explicitly programs what each column means. The model discovers features automatically during training. But if you inspect them, you'll find columns that roughly correspond to things like:
- Gender (male ↔ female)
- Royalty (commoner ↔ royal)
- Tense (past ↔ present)
- Animacy (living ↔ non-living)

Most columns encode subtle combinations that aren't easily named.

### Technically, There Are Two Matrices

During training, the network has:

| Matrix | Shape | Role |
|--------|-------|------|
| **W** (input → hidden) | V x D | Maps words to embeddings |
| **W'** (hidden → output) | D x V | Maps embeddings to predictions |

After training, **we throw away W'** and keep only W. Each row of W becomes that word's embedding vector. Think of W' as scaffolding — necessary for building, removed when done.

---

## 4. Training — How the Model Learns

If you've ever trained a machine learning model, this flow will feel familiar. If not, think of it like an optimizer finding the best configuration.

### Step by Step

**1. Build the vocabulary.** Scan the entire corpus, assign each unique word an integer index. Just like building a `Map<String, Integer>`.

**2. Generate training pairs using a sliding window.** The window size is a hyperparameter (typically 5-10).

```
Corpus: "The cat sat on the mat"
Window size = 2

For Skip-gram, we generate (center, context) pairs:
  (cat, The), (cat, sat), (cat, on)
  (sat, The), (sat, cat), (sat, on), (sat, the)
  (on, cat), (on, sat), (on, the), (on, mat)
  ...
```

**3. Feed each pair through the network** and backpropagate. The network learns to predict context from words (or words from context).

**4. The key insight:** Words that appear in similar contexts end up with similar vectors. "cat" and "dog" both appear near "pet", "food", "cute" — so their vectors converge. "cat" and "algebra" never share context — so their vectors stay far apart.

### Negative Sampling — The Performance Trick

Here's a problem you'll appreciate: computing softmax over 3 million output neurons for every training example is absurdly expensive. That's like doing a full table scan on every insert.

**Negative Sampling** is the fix. Instead of updating all 3M outputs, update only:
- The correct word (positive sample) — push it closer
- 5-20 random wrong words (negative samples) — push them away

This turns an O(V) operation into O(k) where k is typically 5-20. Massive speedup, minimal quality loss.

---

## 5. Vector Arithmetic — The "King - Man + Woman = Queen" Trick

This is what made Word2Vec famous. You can do *algebra on meaning*.

### The Intuition

If the model learns a consistent "gender direction" in vector space, then:
- King and Queen differ mainly in gender
- Man and Woman differ mainly in gender
- So: King - Man + Woman should land near Queen

### Step by Step with Numbers

Let's use simplified 5D vectors (real ones have 300 dimensions, but the math is identical):

```
king  = [0.9,  0.7,  0.8,  0.80, 0.6]
queen = [0.9,  0.7,  0.8,  0.28, 0.6]
man   = [0.2,  0.1,  0.3,  0.82, 0.1]
woman = [0.2,  0.1,  0.3,  0.25, 0.1]
```

Notice column 4 — it's ~0.80 for male words and ~0.25-0.28 for female words. The model learned a "gender feature" there.

**Step 1:** King - Man (subtract element-wise)
```
[0.9-0.2, 0.7-0.1, 0.8-0.3, 0.80-0.82, 0.6-0.1]
= [0.7, 0.6, 0.5, -0.02, 0.5]
```
This result captures "royalty, but with gender removed."

**Step 2:** Add Woman
```
[0.7+0.2, 0.6+0.1, 0.5+0.3, -0.02+0.25, 0.5+0.1]
= [0.9, 0.7, 0.8, 0.23, 0.6]
```

**Step 3:** Find the nearest neighbor in the vocabulary — it's Queen `[0.9, 0.7, 0.8, 0.28, 0.6]`.

### Direction vs Distance — A Subtle but Important Distinction

This trips people up, so let's be precise.

**Direction** = the angle of the offset vector. It encodes the *type* of relationship.
**Distance** = the magnitude (length) of the offset. It's *not guaranteed* to be consistent.

```
King - Queen  ≈ [0.0, 0.0, 0.0, 0.52, 0.0]  magnitude: 0.52
Dad  - Mom    ≈ [0.0, 0.0, 0.0, 0.50, 0.0]  magnitude: 0.50
Man  - Woman  ≈ [0.0, 0.0, 0.0, 0.57, 0.0]  magnitude: 0.57
```

The *directions* are nearly identical (all pointing along the gender axis). The *magnitudes* differ slightly (0.52, 0.50, 0.57). So: **the distance between "king" and "queen" is NOT guaranteed to be the same as between "dad" and "mom"** — but the direction of offset is.

| Property | Consistent across pairs? | Why? |
|----------|--------------------------|------|
| Direction | Yes (approximately) | Same type of relationship |
| Distance | No | Depends on word frequency, context diversity |

### More Analogy Examples
```
Paris - France + Italy     ≈ Rome        (capital ↔ country)
Walking - Walk + Swim      ≈ Swimming    (present participle)
Bigger - Big + Small       ≈ Smaller     (comparative form)
Japan - Sushi + Pizza      ≈ Italy       (country ↔ food)
```

---

## 6. Tokenization Comparison — Where Word2Vec Fits

As a developer, you'll encounter different tokenization strategies in different models. Here's how they compare, using the same input words:

### "running" — a common word

| Method | Tokens | How It Works |
|--------|--------|-------------|
| **Word2Vec** | `["running"]` | Whole word, single lookup |
| **BPE** (GPT) | `["run", "ning"]` | Splits into learned sub-word units |
| **WordPiece** (BERT) | `["run", "##ning"]` | `##` means "continuation of previous" |
| **SentencePiece** (T5/LLaMA) | `["_run", "ning"]` | `_` means "start of word" |
| **FastText** | `["running"]` + n-grams: `<ru, run, unn, nni, nin, ing, ng>` | Word + character pieces |

### "rupee" — a rare/domain-specific word

| Method | Tokens | Notes |
|--------|--------|-------|
| **Word2Vec** | `<UNK>` or missing | **OOV problem** — word simply doesn't exist |
| **BPE** | `["ru", "pee"]` | Always produces something |
| **WordPiece** | `["ru", "##pe", "##e"]` | Breaks into known pieces |
| **SentencePiece** | `["_ru", "pe", "e"]` | Always produces something |
| **FastText** | `["rupee"]` + `<ru, rup, upe, pee, ee>` | Can infer meaning from n-gram overlaps |

### The Key Takeaway

Think of it like type systems:
- **Word2Vec/GloVe** = strict typing. If the word isn't in your vocabulary, it doesn't compile. (OOV = crash)
- **BPE/WordPiece/SentencePiece** = flexible typing. Any input can be represented by composing smaller pieces.
- **FastText** = Word2Vec with duck typing. It stores whole words but also understands their internal character patterns.

---

## 7. Vocabulary — What Words Does It Actually Know?

Word2Vec doesn't ship with a fixed word list. The vocabulary is entirely determined by the training corpus. The famous Google News model includes ~3 million tokens, including:

**Regular words by category:**
- Royalty: king, queen, prince, princess, emperor, duchess
- Animals: dog, cat, horse, elephant, dolphin, eagle
- Countries: India, France, Japan, Brazil, Germany, Kenya
- Professions: doctor, engineer, teacher, lawyer, pilot
- Food: pizza, sushi, bread, rice, chocolate, coffee
- Tech: computer, software, internet, algorithm, database

**Surprises:**
- Proper nouns: `Barack_Obama`, `New_York`, `Google`
- Multi-word phrases: `New_York_Times`, `ice_cream` (joined with underscores)
- Misspellings: if "teh" appears enough times in Google News, it gets an embedding
- No `<UNK>` token by default — unknown words are simply skipped

### Standard Evaluation Datasets (for benchmarking)
- **questions-words.txt** — Google's analogy test: ~19,544 questions (capitals, currencies, family, tenses)
- **WordSim-353** — 353 word pairs with human similarity scores
- **SimLex-999** — 999 pairs (distinguishes similarity from mere relatedness)
- **MEN** — 3,000 pairs rated by humans
- **BATS** — Balanced analogy set covering morphological + semantic relationships

---

## 8. Bias — The Elephant in the Room

Here's something that matters in production. Word2Vec faithfully learns whatever associations exist in its training data — including societal biases:

```
Man - Woman ≈ Computer_Programmer - Homemaker
Man - Woman ≈ Doctor - Nurse
```

If you're building a system that uses word embeddings for search, recommendations, or any user-facing feature, these biases will propagate into your product. This sparked important research on **debiasing word embeddings** (Bolukbasi et al., 2016).

**As a developer, remember:** your embedding model is only as unbiased as its training data. Always audit for bias before deploying.

---

## 9. Word2Vec in the Bigger Picture

Here's the evolutionary timeline. Think of it like the progression from monoliths to microservices — each step solved a real limitation.

| Model | Year | Key Innovation | OOV Handling |
|-------|------|---------------|--------------|
| **Word2Vec** | 2013 | First efficient dense embeddings | None |
| **GloVe** | 2014 | Uses global co-occurrence statistics | None |
| **FastText** | 2016 | Adds character n-grams to Word2Vec | Yes (via n-grams) |
| **ELMo** | 2018 | Context-dependent vectors (bidirectional LSTM) | Partial (char-level) |
| **BERT** | 2018 | Bidirectional transformer + WordPiece tokenization | Yes (sub-word) |
| **GPT** | 2018+ | Autoregressive transformer + BPE tokenization | Yes (sub-word) |

```
Word2Vec (static, whole word)
    │   "Every word has exactly ONE vector, regardless of context"
    │   Problem: "bank" (river) and "bank" (finance) have the SAME vector
    v
FastText (static, sub-word aware)
    │   "Still one vector per word, but can handle unseen words via character n-grams"
    │   Problem: still no context sensitivity
    v
ELMo (contextual)
    │   "Same word gets DIFFERENT vectors in different sentences"
    │   Now "bank" near "river" ≠ "bank" near "money"
    v
BERT / GPT (transformer-based, fully contextual)
        "Deep bidirectional/autoregressive context, sub-word tokenization"
        Current state of the art
```

**Why learn Word2Vec if BERT/GPT exist?** Same reason you learn arrays before learning databases. Word2Vec embeddings are the foundation. Every modern model still uses the core idea — dense vector representations of tokens. Understanding Word2Vec makes transformers, attention, and modern LLMs far easier to grasp.

---

## 10. Key Hyperparameters

When training your own Word2Vec model, these are the knobs you turn:

| Parameter | Typical Values | What It Controls | Developer Analogy |
|-----------|---------------|-----------------|-------------------|
| **Embedding dim (D)** | 100, 200, 300 | Vector size per word | Like choosing VARCHAR length — too small loses info, too large wastes resources |
| **Window size** | 5-10 | How many neighbors to consider | Like join depth — wider = more context but slower |
| **Min count** | 5-10 | Minimum word frequency to include | Like a rate limiter — filters noise from rare words |
| **Negative samples** | 5-20 | Wrong examples per training step | Like test cases — more = better quality, slower training |
| **Learning rate** | 0.025 | Step size during optimization | Standard SGD parameter |
| **Epochs** | 5-15 | Passes over the full corpus | Like retries — more passes, better convergence |

---

## 11. Similarity — How to Compare Vectors

When you have two word vectors and want to know "how similar are these?", use **cosine similarity**:

```
cosine_sim(A, B) = (A . B) / (|A| * |B|)
```

- **1.0** = identical direction (synonyms)
- **0.0** = orthogonal (unrelated)
- **-1.0** = opposite directions (antonyms, sometimes)

**Why cosine instead of Euclidean distance?** Think about it this way: a word that appears 1000x more frequently might have a vector with larger magnitude, but it should still be "semantically close" to its synonyms. Cosine normalizes away the magnitude and compares only the direction. It's like comparing unit vectors — pure angle comparison.

```python
# In practice (using gensim):
from gensim.models import KeyedVectors

model = KeyedVectors.load_word2vec_format('GoogleNews-vectors-negative300.bin', binary=True)

model.similarity('king', 'queen')      # ~0.65
model.similarity('king', 'algebra')    # ~0.08
model.most_similar('python')           # ['snake', 'pythons', 'Python', ...]
model.most_similar(positive=['king', 'woman'], negative=['man'])  # ['queen', ...]
```

---

## Quick Reference Card

```
Word2Vec at a glance:
  - Converts words to dense float vectors (embeddings)
  - Whole-word only — no sub-word splitting
  - Two architectures: CBOW (fast) and Skip-gram (better for rare words)
  - Matrix shape: V x D (vocab_size x embedding_dim)
  - Google News model: 3M words x 300 dimensions
  - Famous trick: King - Man + Woman = Queen
  - Key limitation: OOV words have no representation
  - Successor: FastText (adds character n-grams)
  - Compare vectors with: cosine similarity (not Euclidean)
  - Direction encodes relationship type; distance varies
  - Captures societal biases — audit before deploying
  - Foundation for ALL modern NLP — learn this first
```
