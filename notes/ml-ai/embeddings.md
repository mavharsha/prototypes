# Embeddings — The Complete Picture

> You already know how to represent structured data — integers, strings, foreign keys. Embeddings are how machine learning represents *concepts* as data. Once you see them as a learned lookup table, everything else — Word2Vec, BERT, recommendation engines, vector databases — becomes a variation on the same theme.

---

## 1. What Is an Embedding?

An embedding is a **dense vector of floating-point numbers** that represents something (a word, a sentence, an image, a user, a product — anything) in a continuous vector space where **proximity encodes similarity**.

```
// What you're used to as a developer:
user_id = 42                        // an integer — no meaning beyond identity
category = "electronics"            // a string — no similarity information

// What an embedding gives you:
user_42_embedding  = [0.12, -0.34, 0.78, 0.55, ...]    // 128 floats
user_87_embedding  = [0.11, -0.31, 0.76, 0.53, ...]    // similar user = nearby vector
product_embedding  = [0.82, 0.14, -0.55, 0.23, ...]    // different concept = far away
```

**The core contract:** things that are similar in the real world end up *close together* in vector space. Things that are different end up *far apart*.

### Why Not Just Use IDs or Strings?

| Representation | Can compute similarity? | Fixed vocabulary? | Size |
|---------------|------------------------|-------------------|------|
| Integer ID | No (ID 42 vs 43 means nothing) | Yes | 4 bytes |
| One-hot vector | No (all pairs equally distant) | Yes | V bytes (sparse) |
| String | Sort of (edit distance) | No | Variable |
| **Embedding** | **Yes (cosine similarity, dot product)** | **Depends on model** | **D floats** |

The one-hot vector problem is worth understanding. If you have 10,000 words, a one-hot vector is a 10,000-dimensional vector with a single 1 and 9,999 zeros. Every word is equidistant from every other word. "king" is just as far from "queen" as from "banana." Embeddings compress that into, say, 300 dimensions where distance is meaningful.

```
One-hot (10,000 dims, sparse, no similarity info):
king  = [0, 0, 0, ..., 1, ..., 0, 0, 0]   position 4,521
queen = [0, 0, 0, ..., 1, ..., 0, 0, 0]   position 7,833
                                            distance = sqrt(2) for ALL pairs

Embedding (300 dims, dense, similarity preserved):
king  = [0.12, 0.45, 0.78, ...]
queen = [0.11, 0.43, 0.76, ...]            distance = 0.03 (very close!)
banana = [0.92, -0.3, 0.11, ...]           distance = 1.47 (far away)
```

---

## 2. The Embedding Layer — It's Just a Matrix Lookup

If you've used a HashMap or a database index, you already understand embedding layers.

An embedding layer is a **matrix of shape V x D** where:
- **V** = vocabulary size (number of things you can embed)
- **D** = embedding dimension (how many numbers represent each thing)

Looking up an embedding = grabbing a row from this matrix.

```
Vocabulary: {cat: 0, dog: 1, king: 2, queen: 3, ...}

Embedding Matrix (V=10000, D=4 for simplicity):
         dim0   dim1   dim2   dim3
cat   [  0.21,  0.55, -0.12,  0.83 ]   <- row 0
dog   [  0.19,  0.58, -0.10,  0.79 ]   <- row 1
king  [  0.90,  0.70,  0.80,  0.80 ]   <- row 2
queen [  0.89,  0.71,  0.79,  0.28 ]   <- row 3
...

embed("king") = matrix[2] = [0.90, 0.70, 0.80, 0.80]
```

**That's literally it.** An embedding lookup is an array index operation. The magic is in how those numbers get *learned* during training.

### In Code (PyTorch)

```python
import torch
import torch.nn as nn

# Create an embedding layer: 10000 words, 300 dimensions each
embedding = nn.Embedding(num_embeddings=10000, embedding_dim=300)

# Look up embedding for word at index 42
word_index = torch.tensor([42])
vector = embedding(word_index)   # shape: [1, 300]

# Look up a batch of words at once
sentence = torch.tensor([42, 7, 1893, 55])
vectors = embedding(sentence)    # shape: [4, 300]

# Under the hood, this is just:
# vectors = embedding.weight[sentence]    # matrix row lookup
```

### In Code (TensorFlow/Keras)

```python
from tensorflow.keras.layers import Embedding

# Same concept: 10000 words, 300 dimensions
embedding = Embedding(input_dim=10000, output_dim=300)

# The layer takes integer indices and returns vectors
# input shape: (batch_size, sequence_length) of ints
# output shape: (batch_size, sequence_length, 300) of floats
```

---

## 3. Types of Embeddings

Embeddings aren't just for words. Here's a taxonomy of what you'll encounter:

### 3.1 Word Embeddings

Map individual words to vectors. The OG embeddings.

| Model | Type | Key Idea |
|-------|------|----------|
| **Word2Vec** (2013) | Static | Predict word from context (or vice versa) |
| **GloVe** (2014) | Static | Factorize global word co-occurrence matrix |
| **FastText** (2016) | Static | Word2Vec + character n-grams for OOV handling |

**Static** = each word gets exactly ONE vector, regardless of context.
- "I went to the **bank** to deposit money" → bank = [0.3, 0.5, ...]
- "I sat on the river **bank**" → bank = [0.3, 0.5, ...] (SAME vector!)

This is the main limitation of static embeddings.

### 3.2 Contextual Embeddings

The same word gets DIFFERENT vectors depending on context. This was the breakthrough.

| Model | Type | Key Idea |
|-------|------|----------|
| **ELMo** (2018) | Contextual | Bidirectional LSTM over characters |
| **BERT** (2018) | Contextual | Masked language model, transformer |
| **GPT** (2018+) | Contextual | Autoregressive transformer |

```
// Static (Word2Vec):
"bank" → always [0.3, 0.5, 0.2, ...]

// Contextual (BERT):
"deposit money in the bank" → bank = [0.8, 0.1, 0.5, ...]  (financial)
"sit on the river bank"     → bank = [0.2, 0.9, 0.3, ...]  (geographical)
```

Think of it like function overloading vs dynamic dispatch. Static embeddings are like a fixed mapping. Contextual embeddings compute the representation at runtime based on the full input.

### 3.3 Sentence / Document Embeddings

Embed entire chunks of text, not just individual words.

| Model | What It Embeds | Typical Dim |
|-------|---------------|-------------|
| **Doc2Vec** | Paragraphs/documents | 100-300 |
| **Sentence-BERT** | Sentences | 384-768 |
| **OpenAI text-embedding-3-small** | Text up to 8K tokens | 1536 |
| **Cohere embed-v3** | Text up to 512 tokens | 1024 |

These are what you'll use most in production for search, RAG, and recommendations.

```python
# Using OpenAI's embedding API:
from openai import OpenAI
client = OpenAI()

response = client.embeddings.create(
    model="text-embedding-3-small",
    input="How do I reset my password?"
)

vector = response.data[0].embedding   # list of 1536 floats
```

### 3.4 Image Embeddings

Same idea, applied to images. A CNN or Vision Transformer produces a vector that captures visual content.

| Model | What It Embeds | Typical Dim |
|-------|---------------|-------------|
| **ResNet** (feature extraction) | Images | 2048 |
| **CLIP** | Images AND text (shared space) | 512-768 |
| **DINOv2** | Images (self-supervised) | 384-1536 |

**CLIP is particularly interesting:** it puts images and text into the *same* vector space. So you can compare an image vector with a text vector directly. This powers things like "search photos using text descriptions."

### 3.5 Multimodal Embeddings

Modern models embed different data types into a shared space:

```
                    Shared Embedding Space
                    ┌─────────────────────┐
  "a cat sleeping"  │     * text          │
                    │        * image      │   <- these are CLOSE together
  [photo of cat]    │                     │
                    │              * text  │   <- "a car racing" is FAR
  "a car racing"    │                     │
                    └─────────────────────┘
```

### 3.6 Non-NLP Embeddings (Collaborative Filtering, Graphs, etc.)

Embeddings aren't limited to language. You'll find them everywhere:

| Domain | What's Embedded | Use Case |
|--------|----------------|----------|
| **Recommendations** | Users + Products | Netflix/Spotify: user and movie vectors in same space |
| **Graphs** | Nodes in a graph | Node2Vec, GraphSAGE: social network analysis |
| **Code** | Source code | CodeBERT: semantic code search |
| **Music** | Audio segments | Music recommendation |
| **Genomics** | DNA sequences | Protein structure prediction |

```
Recommendation System:
  user_42   = [0.8, 0.3, -0.1, 0.5]     // likes action, dislikes romance
  movie_A   = [0.7, 0.4, -0.2, 0.6]     // action movie -> CLOSE to user_42
  movie_B   = [-0.3, 0.9, 0.8, -0.1]    // romance movie -> FAR from user_42

  Recommendation: dot_product(user_42, movie_A) > dot_product(user_42, movie_B)
                  → Recommend movie_A
```

---

## 4. How Embeddings Are Learned

Three main strategies. As a developer, think of these as different training paradigms.

### 4.1 Self-Supervised (from raw data, no labels)

The model creates its own training signal from unlabeled data. This is how most text embeddings are trained.

**Word2Vec / Skip-gram:**
```
Training signal: predict surrounding words from center word
"The cat sat on the mat"
  (cat → The), (cat → sat), (cat → on)   // self-generated pairs
```

**BERT / Masked Language Model:**
```
Training signal: predict masked words
"The cat [MASK] on the mat" → predict "sat"
```

**Contrastive Learning (modern sentence embeddings):**
```
Training signal: similar texts should have similar vectors
  Positive pair: ("How do I reset my password?", "Password reset instructions")
  Negative pair: ("How do I reset my password?", "Best pizza in NYC")
```

### 4.2 Supervised (from labeled data)

Train with explicit labels. The embedding layer learns representations that help solve the downstream task.

```
Task: Sentiment classification
Input: "This movie was fantastic!" → Label: positive
Input: "Terrible waste of time"   → Label: negative

The embedding layer learns that "fantastic" and "great" should be nearby
(both predict "positive"), while "terrible" and "fantastic" should be far apart.
```

### 4.3 Fine-Tuned (pretrained + adapted)

Start with pretrained embeddings and adjust them for your specific task. This is the most common approach in practice.

```
1. Start with pretrained BERT embeddings (learned from all of Wikipedia)
2. Fine-tune on your specific data (e.g., customer support tickets)
3. Result: embeddings that understand general language AND your domain
```

Think of it like transfer learning: don't learn everything from scratch when someone already did the hard work.

---

## 5. Measuring Similarity — Your Three Options

Once you have vectors, you need to compare them. Three common metrics:

### Cosine Similarity (most common for text)

```
cosine_sim(A, B) = (A · B) / (|A| × |B|)
```

- Range: -1 to 1
- Measures the *angle* between vectors, ignoring magnitude
- Best for: text similarity, semantic search

```
A = [1, 2, 3]
B = [2, 4, 6]    cosine_sim = 1.0   (same direction, different magnitude)
C = [3, 2, 1]    cosine_sim = 0.71  (somewhat similar)
D = [-1, -2, -3] cosine_sim = -1.0  (opposite direction)
```

**Why cosine?** It normalizes away vector length. A word appearing 10x more often might have a 10x larger vector, but cosine sees them as identical in direction. Magnitude is noise; direction is signal.

### Dot Product (fast, used in recommendations)

```
dot(A, B) = A[0]*B[0] + A[1]*B[1] + ... + A[n]*B[n]
```

- Range: -∞ to +∞
- Considers both direction AND magnitude
- Best for: recommendations (magnitude = confidence/popularity)

**When to use dot product over cosine:** When magnitude matters. In recommendations, a user who rates lots of movies might have a larger vector, and you *want* that to influence results.

### Euclidean Distance (L2)

```
L2(A, B) = sqrt(sum((A[i] - B[i])^2))
```

- Range: 0 to +∞
- Measures straight-line distance
- Best for: spatial/geometric tasks, clustering

**Gotcha:** Lower = more similar (opposite of cosine). Some APIs return squared L2 to avoid the sqrt.

### Quick Decision Guide

```
Text similarity / semantic search  → Cosine Similarity
Recommendations / ranking          → Dot Product
Clustering / spatial analysis      → Euclidean Distance
Not sure                           → Start with Cosine
```

---

## 6. Embedding Dimensions — How to Choose

The embedding dimension D is a key hyperparameter. Here's how to think about it:

```
Too small (D=10):
  Not enough capacity. "king" and "queen" might get squished together
  with "president" and "chancellor" — can't distinguish fine differences.
  Like using VARCHAR(10) for addresses.

Too large (D=10000):
  Overfitting. Wastes memory and compute. Vectors become sparse.
  Like using VARCHAR(10000) for names.

Sweet spot (D=100-1536):
  Depends on vocabulary size, dataset size, and task complexity.
```

### Common Dimensions in Practice

| Model / Use Case | Typical D | Why |
|-------------------|----------|-----|
| Word2Vec | 100-300 | Small-medium vocabulary, simple relationships |
| GloVe | 50-300 | Same as Word2Vec |
| BERT (base) | 768 | Complex contextual relationships |
| BERT (large) | 1024 | Even more capacity |
| GPT-2 | 768-1600 | Scales with model size |
| OpenAI text-embedding-3-small | 1536 | Production text embedding |
| OpenAI text-embedding-3-large | 3072 | Maximum fidelity |

### Rule of Thumb

A widely-used heuristic: **D ≈ V^(1/4)** (fourth root of vocabulary size).
- 10,000 words → D ≈ 10 (too small in practice, use at least 50)
- 100,000 words → D ≈ 18 (use at least 100)
- 3,000,000 words → D ≈ 41 (Word2Vec uses 300 — more is better with enough data)

In practice: **just use what the pretrained model gives you.** Don't overthink this unless you're training from scratch.

---

## 7. Vector Databases — Where Embeddings Live in Production

This is where embeddings become a systems engineering problem — your wheelhouse.

Once you have millions of embeddings, you need to search them efficiently. "Find the 10 nearest vectors to this query vector" is called **Approximate Nearest Neighbor (ANN)** search.

### The Problem

Brute-force cosine similarity over 1M vectors of dimension 1536:
- 1M × 1536 multiplications per query = ~1.5 billion FLOPs
- At ~30ms per query, that's too slow for real-time applications

### The Solution: ANN Indexes

Like database indexes trade storage for query speed, ANN indexes trade perfect accuracy for massive speedup.

| Algorithm | Analogy | How It Works |
|-----------|---------|-------------|
| **IVF** (Inverted File Index) | Like sharding — partition vectors into clusters, only search relevant clusters | Cluster vectors with k-means, search only the closest clusters |
| **HNSW** (Hierarchical Navigable Small World) | Like a skip list — multi-layer graph for fast traversal | Build a graph where nearby vectors are connected, navigate it layer by layer |
| **PQ** (Product Quantization) | Like compression — encode vectors in fewer bytes | Split each vector into sub-vectors, quantize each independently |

### Vector Database Options

| Database | Type | Best For |
|----------|------|----------|
| **Pinecone** | Managed cloud | Quick start, no ops overhead |
| **Weaviate** | Open source | Hybrid search (vector + keyword) |
| **Milvus** | Open source | Large-scale, high throughput |
| **Qdrant** | Open source | Rust-based, filtering support |
| **pgvector** | PostgreSQL extension | Already using Postgres? Add vectors to it |
| **Chroma** | Open source | Lightweight, great for prototyping |
| **FAISS** | Library (Meta) | Not a DB — an in-memory index library |

### Example: pgvector (Postgres)

If you're already using Postgres, this is the lowest friction option:

```sql
-- Enable the extension
CREATE EXTENSION vector;

-- Create a table with a vector column
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    content TEXT,
    embedding VECTOR(1536)    -- 1536-dimensional vector
);

-- Insert a document with its embedding
INSERT INTO documents (content, embedding)
VALUES ('How to reset password', '[0.12, -0.34, 0.78, ...]');

-- Find the 5 most similar documents to a query vector
SELECT content, embedding <=> '[0.11, -0.31, 0.76, ...]' AS distance
FROM documents
ORDER BY embedding <=> '[0.11, -0.31, 0.76, ...]'
LIMIT 5;

-- Create an index for faster search (HNSW)
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops);
```

The `<=>` operator is cosine distance. `<->` is L2 distance. `<#>` is negative inner product.

---

## 8. Embeddings in Practice — Common Patterns

### 8.1 Semantic Search

Replace keyword search with meaning-based search.

```
Traditional search:
  Query: "how to fix login issues"
  Finds: documents containing "fix", "login", "issues"
  Misses: "authentication troubleshooting guide"  (different words, same meaning)

Embedding search:
  Query embedding ≈ [0.12, 0.45, ...]
  Finds: nearest vectors, regardless of exact wording
  Catches: "authentication troubleshooting guide"  (similar meaning = nearby vector)
```

### 8.2 RAG (Retrieval-Augmented Generation)

The most common production pattern right now. Use embeddings to find relevant documents, then feed them to an LLM.

```
User question: "What's our refund policy?"
         │
         v
    ┌─────────────┐
    │ Embed query  │  → query_vector
    └─────────────┘
         │
         v
    ┌─────────────────────┐
    │ Vector DB: find top  │  → "Refund policy doc", "Returns FAQ"
    │ 5 nearest documents  │
    └─────────────────────┘
         │
         v
    ┌──────────────────────────────────┐
    │ LLM prompt:                       │
    │ "Given these documents: [...]     │
    │  Answer: What's our refund policy?"│
    └──────────────────────────────────┘
         │
         v
    Grounded, accurate answer
```

### 8.3 Clustering / Topic Discovery

Group similar items without predefined categories.

```python
from sklearn.cluster import KMeans

# Assume embeddings is a numpy array of shape (n_documents, 1536)
kmeans = KMeans(n_clusters=10)
clusters = kmeans.fit_predict(embeddings)

# Now documents in the same cluster are semantically related
# Inspect each cluster to discover topics
```

### 8.4 Anomaly / Outlier Detection

Find things that don't belong.

```
Normal support tickets cluster together:
  "Can't login" ──┐
  "Password reset" ──┤── cluster A
  "Login error"  ──┘

Anomaly (far from all clusters):
  "I want to invest in stocks" ──── way out in vector space
```

### 8.5 Deduplication

Find near-duplicate content without exact string matching.

```
"How do I reset my password?"
"How can I change my password?"
"Password reset help"

All three have cosine similarity > 0.9 → likely duplicates
```

---

## 9. Embedding Gotchas — Things That Bite You in Production

### 9.1 Embedding Models Are Not Interchangeable

You **cannot** mix embeddings from different models. Each model has its own vector space.

```
OpenAI's vector for "king":    [0.12, -0.34, 0.78, ...]    1536 dims
Cohere's vector for "king":    [0.55, 0.23, -0.11, ...]    1024 dims

These are INCOMPARABLE. Different dimensions, different spaces.
```

If you switch embedding models, you must re-embed your entire dataset. Plan for this.

### 9.2 Garbage In, Garbage Out

Embeddings capture what's in the text — including noise.

```
"          How do I     reset my PASSWORD???!!!!"
vs
"How do I reset my password?"

These might produce slightly different embeddings.
Clean your input text before embedding.
```

### 9.3 Chunk Size Matters

For document embedding, how you split text dramatically affects quality.

```
Too small: "reset" → loses context
Too large: entire 50-page manual → too much averaged together, loses specifics
Sweet spot: ~200-500 tokens per chunk, with overlap between chunks
```

### 9.4 Dimensionality Reduction Can Help (or Hurt)

Sometimes you want to reduce dimensions for storage/speed:

```python
from sklearn.decomposition import PCA

# Reduce 1536 dims to 256
pca = PCA(n_components=256)
reduced = pca.fit_transform(embeddings)    # 6x smaller, ~5-10% quality loss
```

OpenAI's `text-embedding-3` models support native dimension reduction via the `dimensions` parameter — this is the preferred approach when available.

### 9.5 Staleness

Embeddings are a snapshot. If your documents change, re-embed them. If a new embedding model comes out that's much better, re-embed everything. Build your pipeline with re-embedding in mind.

---

## 10. The Embedding Pipeline — A Developer's Checklist

When building an embedding-powered feature, here's your standard pipeline:

```
1. CHOOSE your embedding model
   └─ OpenAI, Cohere, open-source (sentence-transformers), or custom

2. PREPROCESS your data
   └─ Clean text, chunk documents, handle special characters

3. EMBED your corpus
   └─ Batch API calls, handle rate limits, store vectors

4. INDEX for search
   └─ Vector DB (Pinecone, pgvector, etc.) with appropriate ANN index

5. QUERY at runtime
   └─ Embed the query → search the index → return top-K results

6. EVALUATE quality
   └─ Precision@K, recall, user feedback, A/B testing

7. MAINTAIN over time
   └─ Re-embed on model updates, handle data changes, monitor drift
```

---

## 11. Embeddings vs. Feature Engineering

If you've done any ML, you might ask: "How is this different from feature engineering?"

| Traditional Features | Embeddings |
|---------------------|-----------|
| Hand-crafted (word count, TF-IDF, regex) | Automatically learned |
| Domain expertise required | Data-driven |
| Sparse, high-dimensional | Dense, fixed-dimensional |
| Interpretable | Opaque (but powerful) |
| Brittle to new data | Generalizes to unseen inputs |

Think of embeddings as **automated feature engineering** — the model discovers what features matter by learning from data. You give up interpretability but gain generalization.

### TF-IDF vs. Embeddings — A Concrete Comparison

```
Documents:
  A: "The cat sat on the mat"
  B: "The dog sat on the rug"
  C: "Stock prices rose today"

TF-IDF similarity(A, B):
  Shared words: "The", "sat", "on", "the" → high overlap → similarity ≈ 0.7

TF-IDF similarity(A, C):
  Shared words: "The" only → similarity ≈ 0.1

Embedding similarity(A, B):
  Both about animals sitting on floor coverings → similarity ≈ 0.85

Embedding similarity(A, C):
  Completely different topics → similarity ≈ 0.05

Key difference: TF-IDF knows "cat" and "dog" share zero characters.
Embeddings know they're both animals.
```

---

## 12. Historical Timeline — How We Got Here

```
2003  ──  Neural Language Model (Bengio)
          First neural approach to word representations.
          "What if we learn word features instead of hand-crafting them?"

2008  ──  Collobert & Weston
          Showed pretrained word embeddings improve NLP tasks.

2013  ──  Word2Vec (Mikolov, Google)
          Made training fast (negative sampling).
          King - Man + Woman = Queen moment.

2014  ──  GloVe (Stanford)
          Combined global statistics with local context windows.
          "Best of both worlds."

2016  ──  FastText (Facebook)
          Word2Vec + character n-grams.
          Solved the OOV problem for static embeddings.

2018  ──  ELMo (Allen AI)
          First contextual embeddings. Same word, different contexts,
          different vectors. "bank" is finally disambiguated.

2018  ──  BERT (Google)
          Transformer-based contextual embeddings.
          Bidirectional context. Dominated NLP benchmarks.

2018  ──  GPT (OpenAI)
          Autoregressive transformer.
          Showed scaling embeddings + generation = powerful.

2020  ──  Sentence-BERT
          Efficient sentence-level embeddings from BERT.
          Made semantic search practical.

2022+ ──  text-embedding-ada-002, embed-v3, etc.
          API-based production embedding models.
          Focus: quality, cost, speed for real applications.

2023+ ──  Multimodal embeddings (CLIP, ImageBind)
          Text, images, audio in the same vector space.
```

---

## Quick Reference Card

```
Embeddings at a glance:

What:     Dense float vectors representing concepts
Why:      Enable similarity computation (search, recommendations, clustering)
How:      Learned automatically from data (self-supervised, supervised, fine-tuned)

Types:
  Static word     → Word2Vec, GloVe, FastText (one vector per word)
  Contextual      → ELMo, BERT, GPT (context-dependent vectors)
  Sentence/Doc    → Sentence-BERT, OpenAI embeddings (whole text chunks)
  Image           → ResNet, CLIP, DINO (visual content)
  Multimodal      → CLIP (text + images in same space)
  Domain-specific → User/product vectors, graph nodes, code, DNA

Similarity metrics:
  Cosine similarity  → text (angle-based, ignores magnitude)
  Dot product        → recommendations (direction + magnitude)
  Euclidean distance → clustering (straight-line distance)

Production stack:
  1. Embedding model (OpenAI, Cohere, sentence-transformers)
  2. Vector database (pgvector, Pinecone, Qdrant, Milvus)
  3. ANN index (HNSW, IVF) for fast search
  4. Pipeline for re-embedding on updates

Key gotchas:
  - Never mix vectors from different models
  - Chunk size dramatically affects quality
  - Clean text before embedding
  - Plan for re-embedding when models change
  - Embeddings capture bias from training data
```
