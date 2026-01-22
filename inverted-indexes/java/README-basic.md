# Basic Inverted Index (Java)

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

```java
private Map<String, Set<Integer>> index;      // word -> set of doc_ids
private Map<Integer, String> documents;       // doc_id -> document text
```

- **index**: Maps each word to a Set of document IDs where that word appears
- **documents**: Stores the original document text for retrieval

### Core Operations

#### 1. Adding Documents

```java
public void addDocument(int docId, String text) {
    documents.put(docId, text);
    String[] words = text.toLowerCase().split("\\s+");

    for (String word : words) {
        index.computeIfAbsent(word, k -> new HashSet<>()).add(docId);
    }
}
```

Process:
1. Store the original document
2. Split text into words (tokenization)
3. Convert to lowercase for case-insensitive matching
4. For each word, add the document ID to that word's Set

The `computeIfAbsent` method elegantly handles creating new Sets for first-time words.

#### 2. Searching

```java
public List<Integer> search(String query) {
    String term = query.toLowerCase();
    if (index.containsKey(term)) {
        return new ArrayList<>(index.get(term));
    }
    return new ArrayList<>();
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
| Search | O(1) average case (HashMap lookup) |
| Get Document | O(1) |

### Limitations

- No relevance ranking (all results treated equally)
- Single-term search only
- No stop word filtering
- Punctuation not handled
- No phrase search support

## Usage

```java
BasicInvertedIndex index = new BasicInvertedIndex();

// Add documents
index.addDocument(1, "The quick brown fox");
index.addDocument(2, "The lazy dog");

// Search
List<Integer> results = index.search("fox");  // Returns [1]

// Retrieve document
String doc = index.getDocument(1);  // Returns "The quick brown fox"
```

## Compiling and Running

```bash
# Compile
javac BasicInvertedIndex.java

# Run
java BasicInvertedIndex
```

## Java-Specific Notes

- Uses `HashMap` for O(1) average lookups
- Uses `HashSet` to prevent duplicate document IDs
- `computeIfAbsent` simplifies the "get or create" pattern
- Returns new `ArrayList` copies to prevent external modification of internal state
