# Polished Inverted Index

## Overview

This is an enhanced inverted index implementation that adds several features commonly found in real search engines: relevance ranking with TF-IDF, stop word filtering, and multiple search modes.

## Features

| Feature | Description |
|---------|-------------|
| Text Normalization | Lowercase conversion, punctuation removal |
| Stop Words | Filters common words (the, is, and, etc.) |
| TF-IDF Scoring | Ranks results by relevance |
| Boolean Search | AND/OR operations |
| Phrase Search | Find exact word sequences |
| Position Tracking | Stores word positions for phrase matching |

## Key Concepts

### 1. Text Normalization

Before indexing, text is cleaned and standardized:

```javascript
tokenize(text) {
    return text
        .toLowerCase()                 // "The Fox" -> "the fox"
        .replace(/[^\w\s]/g, '')       // "hello!" -> "hello"
        .split(/\s+/)                  // "a  b" -> ["a", "b"]
        .filter(word => word.length > 0);
}
```

This ensures "Fox", "fox", and "FOX" all match the same documents.

### 2. Stop Words

Stop words are common words that appear in almost every document and provide little search value:

```
a, an, the, is, are, was, were, be, to, of, in, for, on, with...
```

Filtering these:
- Reduces index size
- Improves search relevance
- Speeds up queries

### 3. TF-IDF Scoring

**TF-IDF** (Term Frequency - Inverse Document Frequency) measures how important a word is to a document in a collection.

#### Term Frequency (TF)
How often a term appears in a document, normalized by document length:

```
TF = (times term appears in document) / (total words in document)
```

A document with "fox" appearing 3 times in 100 words: TF = 0.03

#### Inverse Document Frequency (IDF)
How rare a term is across all documents:

```
IDF = log(total documents / documents containing term)
```

- Common words (appear everywhere) → low IDF
- Rare words (appear in few docs) → high IDF

#### TF-IDF Score

```
TF-IDF = TF × IDF
```

This gives high scores to:
- Words that appear frequently in a specific document (high TF)
- Words that are rare across all documents (high IDF)

**Example:**
- "the" appears often but is in every document → low TF-IDF
- "algorithm" appears a few times but only in technical docs → high TF-IDF

### 4. Position Tracking

Unlike the basic index, this stores WHERE each word appears:

```javascript
// Basic: word -> Set of docIds
"fox" -> {1, 4, 5}

// Polished: word -> Map(docId -> positions[])
"fox" -> {
    1: [3],      // "fox" is at position 3 in doc 1
    4: [0],      // "fox" is at position 0 in doc 4
    5: [1, 7]    // "fox" is at positions 1 and 7 in doc 5
}
```

This enables phrase search by checking if words appear consecutively.

## Search Modes

### Basic Search
Returns all documents containing any search term, ranked by TF-IDF:

```javascript
index.search("quick brown");
// Returns docs with "quick" OR "brown", ranked by relevance
```

### Boolean AND
Documents must contain ALL terms:

```javascript
index.searchAND("quick brown");
// Only returns docs with BOTH "quick" AND "brown"
```

### Boolean OR
Documents contain ANY term (explicit OR):

```javascript
index.searchOR("cat dog");
// Returns docs with "cat" OR "dog" (or both)
```

### Phrase Search
Find exact sequences of words:

```javascript
index.searchPhrase("quick brown fox");
// Only returns docs with "quick brown fox" in that exact order
```

The algorithm:
1. Tokenize the phrase
2. For each document, scan through tokens
3. Check if the phrase tokens appear consecutively
4. Return matching documents

## Data Structures

```javascript
// Main index: term -> (docId -> positions)
this.index = new Map();

// Document storage
this.documents = new Map();  // docId -> { text, wordCount, tokens }

// Configuration
this.stopWords = new Set([...]);
this.useStopWords = true;
```

## Usage Examples

```javascript
const index = new PolishedInvertedIndex();

// Add documents
index.addDocument(1, "The quick brown fox jumps over the lazy dog.");
index.addDocument(2, "A quick brown dog runs in the park.");
index.addDocument(3, "The lazy cat sleeps all day.");

// Basic search with ranking
index.search("fox");
// [{ docId: 1, score: "0.1234" }]

// AND search
index.searchAND("quick dog");
// [{ docId: 2, score: "0.0987" }]

// Phrase search
index.searchPhrase("quick brown");
// [{ docId: 1, score: "..." }, { docId: 2, score: "..." }]

// Get statistics
index.getStats();
// { documentCount: 3, uniqueTerms: 15, stopWordsEnabled: true, ... }
```

## Configuration Options

```javascript
const index = new PolishedInvertedIndex({
    useStopWords: false,  // Disable stop word filtering
    stopWords: ['custom', 'list']  // Use custom stop words
});
```

## Time Complexity

| Operation | Complexity |
|-----------|------------|
| Add Document | O(n) where n = words in document |
| Basic Search | O(k × m) where k = terms, m = matching docs |
| AND Search | O(k × m) |
| OR Search | O(k × m) |
| Phrase Search | O(d × n) where d = docs, n = avg doc length |
| TF-IDF Calculation | O(1) per term-document pair |

## Comparison with Basic Implementation

| Feature | Basic | Polished |
|---------|-------|----------|
| Tokenization | Split on whitespace | Normalize + clean |
| Stop Words | No | Yes |
| Ranking | None | TF-IDF |
| Multi-term Search | No | Yes |
| Boolean Operators | No | AND, OR |
| Phrase Search | No | Yes |
| Position Tracking | No | Yes |

## Running the Example

```bash
node polished-inverted-index.js
```
