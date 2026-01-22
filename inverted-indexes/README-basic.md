# Basic Inverted Index

## What is an Inverted Index?

An inverted index is a data structure used to map content (like words or terms) to the documents that contain them. It's called "inverted" because instead of mapping documents to words (forward index), it maps words to documents.

```
Forward Index:
  Document 1 -> ["the", "quick", "brown", "fox"]
  Document 2 -> ["the", "lazy", "dog"]

Inverted Index:
  "the"   -> [Document 1, Document 2]
  "quick" -> [Document 1]
  "brown" -> [Document 1]
  "fox"   -> [Document 1]
  "lazy"  -> [Document 2]
  "dog"   -> [Document 2]
```

This structure is the backbone of search engines because it allows fast lookups - instead of scanning every document for a word, you simply look up the word and get all documents instantly.

## How This Implementation Works

### Data Structures

```javascript
this.index = new Map();      // word -> Set of doc_ids
this.documents = new Map();  // doc_id -> document text
```

- **index**: Maps each word to a Set of document IDs where that word appears
- **documents**: Stores the original document text for retrieval

### Core Operations

#### 1. Adding Documents (`addDocument`)

```javascript
addDocument(docId, text) {
    this.documents.set(docId, text);
    const words = text.toLowerCase().split(/\s+/);

    for (const word of words) {
        if (!this.index.has(word)) {
            this.index.set(word, new Set());
        }
        this.index.get(word).add(docId);
    }
}
```

Process:
1. Store the original document
2. Split text into words (tokenization)
3. Convert to lowercase for case-insensitive matching
4. For each word, add the document ID to that word's Set

#### 2. Searching (`search`)

```javascript
search(query) {
    const term = query.toLowerCase();
    if (this.index.has(term)) {
        return Array.from(this.index.get(term));
    }
    return [];
}
```

Process:
1. Normalize the query to lowercase
2. Look up the word in the index
3. Return all document IDs associated with that word

### Time Complexity

| Operation | Complexity |
|-----------|------------|
| Add Document | O(n) where n = words in document |
| Search | O(1) average case |
| Get Document | O(1) |

### Limitations

- No relevance ranking (all results treated equally)
- Single-term search only
- No stop word filtering
- Punctuation not handled
- No phrase search support

## Usage

```javascript
const index = new BasicInvertedIndex();

// Add documents
index.addDocument(1, "The quick brown fox");
index.addDocument(2, "The lazy dog");

// Search
const results = index.search("fox");  // Returns [1]

// Retrieve document
const doc = index.getDocument(1);  // Returns "The quick brown fox"
```

## Running the Example

```bash
node basic-inverted-index.js
```
