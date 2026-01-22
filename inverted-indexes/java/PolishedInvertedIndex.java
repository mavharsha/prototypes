import java.util.*;
import java.util.stream.*;

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
public class PolishedInvertedIndex {

    // word -> (docId -> positions[])
    private Map<String, Map<Integer, List<Integer>>> index;

    // docId -> DocumentData
    private Map<Integer, DocumentData> documents;

    private Set<String> stopWords;
    private boolean useStopWords;
    private int documentCount;

    /**
     * Stores document metadata
     */
    private static class DocumentData {
        String text;
        int wordCount;
        List<String> tokens;

        DocumentData(String text, int wordCount, List<String> tokens) {
            this.text = text;
            this.wordCount = wordCount;
            this.tokens = tokens;
        }
    }

    /**
     * Search result with score
     */
    public static class SearchResult {
        public final int docId;
        public final double score;

        SearchResult(int docId, double score) {
            this.docId = docId;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("{docId: %d, score: %.4f}", docId, score);
        }
    }

    public PolishedInvertedIndex() {
        this(true);
    }

    public PolishedInvertedIndex(boolean useStopWords) {
        this.index = new HashMap<>();
        this.documents = new HashMap<>();
        this.useStopWords = useStopWords;
        this.documentCount = 0;
        this.stopWords = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "shall",
            "can", "need", "dare", "ought", "used", "to", "of", "in",
            "for", "on", "with", "at", "by", "from", "as", "into", "through",
            "during", "before", "after", "above", "below", "between", "under",
            "again", "further", "then", "once", "and", "but", "or", "nor",
            "so", "yet", "both", "either", "neither", "not", "only", "own",
            "same", "than", "too", "very", "just", "all", "each", "every",
            "any", "few", "more", "most", "other", "some", "such", "no"
        ));
    }

    /**
     * Tokenize and normalize text
     */
    private List<String> tokenize(String text) {
        String cleaned = text.toLowerCase().replaceAll("[^\\w\\s]", "");
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Filter out stop words
     */
    private List<String> filterStopWords(List<String> tokens) {
        if (!useStopWords) return tokens;
        return tokens.stream()
                .filter(token -> !stopWords.contains(token))
                .collect(Collectors.toList());
    }

    /**
     * Add a document to the index
     */
    public void addDocument(int docId, String text) {
        List<String> tokens = tokenize(text);
        List<String> filteredTokens = filterStopWords(tokens);

        documents.put(docId, new DocumentData(text, tokens.size(), tokens));
        documentCount++;

        // Index each token with its position
        for (int position = 0; position < filteredTokens.size(); position++) {
            String token = filteredTokens.get(position);

            index.computeIfAbsent(token, k -> new HashMap<>())
                 .computeIfAbsent(docId, k -> new ArrayList<>())
                 .add(position);
        }
    }

    /**
     * Calculate TF (Term Frequency)
     */
    private double calculateTF(String term, int docId) {
        Map<Integer, List<Integer>> termData = index.get(term);
        if (termData == null || !termData.containsKey(docId)) return 0;

        int termCount = termData.get(docId).size();
        int docWordCount = documents.get(docId).wordCount;
        return (double) termCount / docWordCount;
    }

    /**
     * Calculate IDF (Inverse Document Frequency)
     */
    private double calculateIDF(String term) {
        Map<Integer, List<Integer>> termData = index.get(term);
        if (termData == null) return 0;

        int docsWithTerm = termData.size();
        return Math.log((double) documentCount / (1 + docsWithTerm));
    }

    /**
     * Calculate TF-IDF score
     */
    private double calculateTFIDF(String term, int docId) {
        return calculateTF(term, docId) * calculateIDF(term);
    }

    /**
     * Basic single-term search with TF-IDF ranking
     */
    public List<SearchResult> search(String query) {
        List<String> terms = filterStopWords(tokenize(query));
        if (terms.isEmpty()) return new ArrayList<>();

        Map<Integer, Double> scores = new HashMap<>();

        for (String term : terms) {
            Map<Integer, List<Integer>> termData = index.get(term);
            if (termData == null) continue;

            for (int docId : termData.keySet()) {
                double tfidf = calculateTFIDF(term, docId);
                scores.merge(docId, tfidf, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .map(e -> new SearchResult(e.getKey(), e.getValue()))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());
    }

    /**
     * Boolean AND search - documents must contain ALL terms
     */
    public List<SearchResult> searchAND(String query) {
        List<String> terms = filterStopWords(tokenize(query));
        if (terms.isEmpty()) return new ArrayList<>();

        Set<Integer> resultSet = null;

        for (String term : terms) {
            Map<Integer, List<Integer>> termData = index.get(term);
            if (termData == null) return new ArrayList<>();

            Set<Integer> docIds = termData.keySet();

            if (resultSet == null) {
                resultSet = new HashSet<>(docIds);
            } else {
                resultSet.retainAll(docIds);
            }
        }

        return rankResults(resultSet != null ? resultSet : new HashSet<>(), terms);
    }

    /**
     * Boolean OR search - documents contain ANY term
     */
    public List<SearchResult> searchOR(String query) {
        List<String> terms = filterStopWords(tokenize(query));
        if (terms.isEmpty()) return new ArrayList<>();

        Set<Integer> resultSet = new HashSet<>();

        for (String term : terms) {
            Map<Integer, List<Integer>> termData = index.get(term);
            if (termData != null) {
                resultSet.addAll(termData.keySet());
            }
        }

        return rankResults(resultSet, terms);
    }

    /**
     * Phrase search - find exact phrase in documents
     */
    public List<SearchResult> searchPhrase(String phrase) {
        List<String> tokens = tokenize(phrase);
        if (tokens.isEmpty()) return new ArrayList<>();

        List<Integer> results = new ArrayList<>();

        for (Map.Entry<Integer, DocumentData> entry : documents.entrySet()) {
            int docId = entry.getKey();
            List<String> docTokens = entry.getValue().tokens;

            // Search for phrase in document tokens
            outer:
            for (int i = 0; i <= docTokens.size() - tokens.size(); i++) {
                for (int j = 0; j < tokens.size(); j++) {
                    if (!docTokens.get(i + j).equals(tokens.get(j))) {
                        continue outer;
                    }
                }
                results.add(docId);
                break;
            }
        }

        return rankResults(new HashSet<>(results), tokens);
    }

    /**
     * Rank results by TF-IDF score
     */
    private List<SearchResult> rankResults(Set<Integer> docIds, List<String> terms) {
        return docIds.stream()
                .map(docId -> {
                    double score = terms.stream()
                            .mapToDouble(term -> calculateTFIDF(term, docId))
                            .sum();
                    return new SearchResult(docId, score);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());
    }

    /**
     * Get document by ID
     */
    public String getDocument(int docId) {
        DocumentData doc = documents.get(docId);
        return doc != null ? doc.text : null;
    }

    /**
     * Get index statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("documentCount", documentCount);
        stats.put("uniqueTerms", index.size());
        stats.put("stopWordsEnabled", useStopWords);
        stats.put("stopWordsCount", stopWords.size());
        return stats;
    }

    public static void main(String[] args) {
        PolishedInvertedIndex index = new PolishedInvertedIndex();

        // Add sample documents
        index.addDocument(1, "The quick brown fox jumps over the lazy dog.");
        index.addDocument(2, "A quick brown dog runs in the park.");
        index.addDocument(3, "The lazy cat sleeps all day long.");
        index.addDocument(4, "Fox and dog are best friends forever.");
        index.addDocument(5, "The brown fox is quick and clever.");
        index.addDocument(6, "Dogs love to play in parks and gardens.");

        System.out.println("=== Polished Inverted Index ===\n");
        System.out.println("Index Statistics: " + index.getStats());

        System.out.println("\n--- Basic Search (TF-IDF ranked) ---");
        System.out.println("Search 'fox': " + index.search("fox"));
        System.out.println("Search 'quick brown': " + index.search("quick brown"));

        System.out.println("\n--- Boolean AND Search ---");
        System.out.println("Search 'quick AND brown': " + index.searchAND("quick brown"));
        System.out.println("Search 'fox AND dog': " + index.searchAND("fox dog"));

        System.out.println("\n--- Boolean OR Search ---");
        System.out.println("Search 'cat OR dog': " + index.searchOR("cat dog"));

        System.out.println("\n--- Phrase Search ---");
        System.out.println("Search phrase 'quick brown': " + index.searchPhrase("quick brown"));
        System.out.println("Search phrase 'brown fox': " + index.searchPhrase("brown fox"));
        System.out.println("Search phrase 'lazy dog': " + index.searchPhrase("lazy dog"));

        System.out.println("\n--- Document Retrieval ---");
        System.out.println("Document 1: " + index.getDocument(1));
    }
}
