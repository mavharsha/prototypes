import java.util.*;

/**
 * Basic Inverted Index Implementation
 *
 * A simple inverted index that maps words to the documents they appear in.
 */
public class BasicInvertedIndex {

    private Map<String, Set<Integer>> index;      // word -> set of doc_ids
    private Map<Integer, String> documents;       // doc_id -> document text

    public BasicInvertedIndex() {
        this.index = new HashMap<>();
        this.documents = new HashMap<>();
    }

    /**
     * Add a document to the index
     */
    public void addDocument(int docId, String text) {
        documents.put(docId, text);
        String[] words = text.toLowerCase().split("\\s+");

        for (String word : words) {
            index.computeIfAbsent(word, k -> new HashSet<>()).add(docId);
        }
    }

    /**
     * Search for documents containing the query word
     */
    public List<Integer> search(String query) {
        String term = query.toLowerCase();
        if (index.containsKey(term)) {
            return new ArrayList<>(index.get(term));
        }
        return new ArrayList<>();
    }

    /**
     * Retrieve a document by its ID
     */
    public String getDocument(int docId) {
        return documents.get(docId);
    }

    public static void main(String[] args) {
        BasicInvertedIndex index = new BasicInvertedIndex();

        // Add sample documents
        index.addDocument(1, "The quick brown fox jumps over the lazy dog");
        index.addDocument(2, "A quick brown dog runs in the park");
        index.addDocument(3, "The lazy cat sleeps all day");
        index.addDocument(4, "Fox and dog are friends");

        // Search examples
        System.out.println("=== Basic Inverted Index ===\n");
        System.out.println("Documents containing 'quick': " + index.search("quick"));
        System.out.println("Documents containing 'lazy': " + index.search("lazy"));
        System.out.println("Documents containing 'fox': " + index.search("fox"));
        System.out.println("Documents containing 'cat': " + index.search("cat"));
        System.out.println("Documents containing 'elephant': " + index.search("elephant"));
    }
}
