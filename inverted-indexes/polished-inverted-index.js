/**
 * Polished Inverted Index Implementation
 *
 * Features:
 * - Text normalization (lowercase, punctuation removal)
 * - Stop words filtering
 * - TF-IDF scoring for relevance ranking
 * - Boolean search (AND, OR)
 * - Phrase search
 */

class PolishedInvertedIndex {
    constructor(options = {}) {
        this.index = new Map();           // word -> Map(docId -> positions[])
        this.documents = new Map();       // docId -> { text, wordCount }
        this.documentCount = 0;

        this.stopWords = new Set(options.stopWords || [
            'a', 'an', 'the', 'is', 'are', 'was', 'were', 'be', 'been',
            'being', 'have', 'has', 'had', 'do', 'does', 'did', 'will',
            'would', 'could', 'should', 'may', 'might', 'must', 'shall',
            'can', 'need', 'dare', 'ought', 'used', 'to', 'of', 'in',
            'for', 'on', 'with', 'at', 'by', 'from', 'as', 'into', 'through',
            'during', 'before', 'after', 'above', 'below', 'between', 'under',
            'again', 'further', 'then', 'once', 'and', 'but', 'or', 'nor',
            'so', 'yet', 'both', 'either', 'neither', 'not', 'only', 'own',
            'same', 'than', 'too', 'very', 'just', 'all', 'each', 'every',
            'any', 'few', 'more', 'most', 'other', 'some', 'such', 'no'
        ]);

        this.useStopWords = options.useStopWords !== false;
    }

    /**
     * Tokenize and normalize text
     */
    tokenize(text) {
        return text
            .toLowerCase()
            .replace(/[^\w\s]/g, '')  // Remove punctuation
            .split(/\s+/)
            .filter(word => word.length > 0);
    }

    /**
     * Filter out stop words
     */
    filterStopWords(tokens) {
        if (!this.useStopWords) return tokens;
        return tokens.filter(token => !this.stopWords.has(token));
    }

    /**
     * Add a document to the index
     */
    addDocument(docId, text) {
        const tokens = this.tokenize(text);
        const filteredTokens = this.filterStopWords(tokens);

        this.documents.set(docId, {
            text,
            wordCount: tokens.length,
            tokens
        });
        this.documentCount++;

        // Index each token with its position
        filteredTokens.forEach((token, position) => {
            if (!this.index.has(token)) {
                this.index.set(token, new Map());
            }

            const docMap = this.index.get(token);
            if (!docMap.has(docId)) {
                docMap.set(docId, []);
            }
            docMap.get(docId).push(position);
        });
    }

    /**
     * Calculate TF (Term Frequency)
     */
    calculateTF(term, docId) {
        const termData = this.index.get(term);
        if (!termData || !termData.has(docId)) return 0;

        const termCount = termData.get(docId).length;
        const docWordCount = this.documents.get(docId).wordCount;
        return termCount / docWordCount;
    }

    /**
     * Calculate IDF (Inverse Document Frequency)
     */
    calculateIDF(term) {
        const termData = this.index.get(term);
        if (!termData) return 0;

        const docsWithTerm = termData.size;
        return Math.log(this.documentCount / (1 + docsWithTerm));
    }

    /**
     * Calculate TF-IDF score
     */
    calculateTFIDF(term, docId) {
        return this.calculateTF(term, docId) * this.calculateIDF(term);
    }

    /**
     * Basic single-term search with TF-IDF ranking
     */
    search(query) {
        const terms = this.filterStopWords(this.tokenize(query));
        if (terms.length === 0) return [];

        const scores = new Map();

        for (const term of terms) {
            const termData = this.index.get(term);
            if (!termData) continue;

            for (const docId of termData.keys()) {
                const tfidf = this.calculateTFIDF(term, docId);
                scores.set(docId, (scores.get(docId) || 0) + tfidf);
            }
        }

        // Sort by score descending
        return Array.from(scores.entries())
            .sort((a, b) => b[1] - a[1])
            .map(([docId, score]) => ({ docId, score: score.toFixed(4) }));
    }

    /**
     * Boolean AND search - documents must contain ALL terms
     */
    searchAND(query) {
        const terms = this.filterStopWords(this.tokenize(query));
        if (terms.length === 0) return [];

        let resultSet = null;

        for (const term of terms) {
            const termData = this.index.get(term);
            if (!termData) return []; // Term not found, AND fails

            const docIds = new Set(termData.keys());

            if (resultSet === null) {
                resultSet = docIds;
            } else {
                resultSet = new Set([...resultSet].filter(id => docIds.has(id)));
            }
        }

        return this.rankResults(Array.from(resultSet || []), terms);
    }

    /**
     * Boolean OR search - documents contain ANY term
     */
    searchOR(query) {
        const terms = this.filterStopWords(this.tokenize(query));
        if (terms.length === 0) return [];

        const resultSet = new Set();

        for (const term of terms) {
            const termData = this.index.get(term);
            if (termData) {
                for (const docId of termData.keys()) {
                    resultSet.add(docId);
                }
            }
        }

        return this.rankResults(Array.from(resultSet), terms);
    }

    /**
     * Phrase search - find exact phrase in documents
     */
    searchPhrase(phrase) {
        const tokens = this.tokenize(phrase);
        if (tokens.length === 0) return [];

        const results = [];

        for (const [docId, docData] of this.documents) {
            const docTokens = docData.tokens;

            // Search for phrase in document tokens
            for (let i = 0; i <= docTokens.length - tokens.length; i++) {
                let match = true;
                for (let j = 0; j < tokens.length; j++) {
                    if (docTokens[i + j] !== tokens[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    results.push(docId);
                    break;
                }
            }
        }

        return this.rankResults(results, tokens);
    }

    /**
     * Rank results by TF-IDF score
     */
    rankResults(docIds, terms) {
        const scores = docIds.map(docId => {
            let score = 0;
            for (const term of terms) {
                score += this.calculateTFIDF(term, docId);
            }
            return { docId, score: score.toFixed(4) };
        });

        return scores.sort((a, b) => b.score - a.score);
    }

    /**
     * Get document by ID
     */
    getDocument(docId) {
        const doc = this.documents.get(docId);
        return doc ? doc.text : null;
    }

    /**
     * Get index statistics
     */
    getStats() {
        return {
            documentCount: this.documentCount,
            uniqueTerms: this.index.size,
            stopWordsEnabled: this.useStopWords,
            stopWordsCount: this.stopWords.size
        };
    }
}

// Example usage
const index = new PolishedInvertedIndex();

// Add sample documents
index.addDocument(1, "The quick brown fox jumps over the lazy dog.");
index.addDocument(2, "A quick brown dog runs in the park.");
index.addDocument(3, "The lazy cat sleeps all day long.");
index.addDocument(4, "Fox and dog are best friends forever.");
index.addDocument(5, "The brown fox is quick and clever.");
index.addDocument(6, "Dogs love to play in parks and gardens.");

console.log("=== Polished Inverted Index ===\n");
console.log("Index Statistics:", index.getStats());

console.log("\n--- Basic Search (TF-IDF ranked) ---");
console.log("Search 'fox':", index.search("fox"));
console.log("Search 'quick brown':", index.search("quick brown"));

console.log("\n--- Boolean AND Search ---");
console.log("Search 'quick AND brown':", index.searchAND("quick brown"));
console.log("Search 'fox AND dog':", index.searchAND("fox dog"));

console.log("\n--- Boolean OR Search ---");
console.log("Search 'cat OR dog':", index.searchOR("cat dog"));

console.log("\n--- Phrase Search ---");
console.log("Search phrase 'quick brown':", index.searchPhrase("quick brown"));
console.log("Search phrase 'brown fox':", index.searchPhrase("brown fox"));
console.log("Search phrase 'lazy dog':", index.searchPhrase("lazy dog"));

console.log("\n--- Document Retrieval ---");
console.log("Document 1:", index.getDocument(1));
