/**
 * Basic Inverted Index Implementation
 *
 * A simple inverted index that maps words to the documents they appear in.
 */

class BasicInvertedIndex {
    constructor() {
        this.index = new Map();      // word -> Set of doc_ids
        this.documents = new Map();  // doc_id -> document text
    }

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

    search(query) {
        const term = query.toLowerCase();
        if (this.index.has(term)) {
            return Array.from(this.index.get(term));
        }
        return [];
    }

    getDocument(docId) {
        return this.documents.get(docId);
    }
}

// Example usage
const index = new BasicInvertedIndex();

// Add sample documents
index.addDocument(1, "The quick brown fox jumps over the lazy dog");
index.addDocument(2, "A quick brown dog runs in the park");
index.addDocument(3, "The lazy cat sleeps all day");
index.addDocument(4, "Fox and dog are friends");

// Search examples
console.log("=== Basic Inverted Index ===\n");
console.log("Documents containing 'quick':", index.search("quick"));
console.log("Documents containing 'lazy':", index.search("lazy"));
console.log("Documents containing 'fox':", index.search("fox"));
console.log("Documents containing 'cat':", index.search("cat"));
console.log("Documents containing 'elephant':", index.search("elephant"));
