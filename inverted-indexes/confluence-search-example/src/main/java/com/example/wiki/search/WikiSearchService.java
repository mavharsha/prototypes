package com.example.wiki.search;

import com.example.wiki.dto.SearchRequest;
import com.example.wiki.dto.SearchResult;
import com.example.wiki.dto.PageHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.*;

import static com.example.wiki.search.IndexFields.*;

/**
 * Search service using Lucene.
 * Provides full-text search with filtering, faceting, and highlighting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WikiSearchService {

    private final SearcherManager searcherManager;
    private final Analyzer analyzer;

    /**
     * Executes a search query and returns results with highlights.
     */
    public SearchResult search(SearchRequest request) throws IOException {
        long startTime = System.currentTimeMillis();

        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query query = buildQuery(request);
            Sort sort = buildSort(request);

            // Execute search
            int maxResults = request.getPage() * request.getSize() + request.getSize();
            TopDocs topDocs;
            if (sort != null) {
                topDocs = searcher.search(query, maxResults, sort);
            } else {
                topDocs = searcher.search(query, maxResults);
            }

            // Calculate pagination
            int start = request.getPage() * request.getSize();
            int end = Math.min(start + request.getSize(), topDocs.scoreDocs.length);

            // Build results with highlighting
            List<PageHit> hits = new ArrayList<>();
            Highlighter highlighter = createHighlighter(query);

            for (int i = start; i < end; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                PageHit hit = new PageHit();
                hit.setContentId(Long.parseLong(doc.get(CONTENT_ID)));
                hit.setTitle(doc.get(TITLE));
                hit.setSpaceKey(doc.get(SPACE_KEY));
                hit.setSpaceName(doc.get(SPACE_NAME));
                hit.setContentType(doc.get(CONTENT_TYPE));
                hit.setLabels(doc.get(LABEL_TEXT));
                hit.setScore(scoreDoc.score);

                // Get stored dates
                String createdStr = doc.get(CREATED + "_stored");
                if (createdStr != null) {
                    hit.setCreated(Long.parseLong(createdStr));
                }
                String modifiedStr = doc.get(MODIFIED + "_stored");
                if (modifiedStr != null) {
                    hit.setModified(Long.parseLong(modifiedStr));
                }

                // Generate highlights
                hit.setHighlights(generateHighlights(highlighter, doc, searcher.getIndexReader(), scoreDoc.doc));

                hits.add(hit);
            }

            // Build facets
            Map<String, List<SearchResult.FacetValue>> facets = buildFacets(searcher, query);

            long searchTime = System.currentTimeMillis() - startTime;

            return SearchResult.builder()
                .hits(hits)
                .totalHits(topDocs.totalHits.value)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages((int) Math.ceil((double) topDocs.totalHits.value / request.getSize()))
                .facets(facets)
                .searchTimeMs(searchTime)
                .build();

        } catch (ParseException e) {
            log.error("Failed to parse search query: {}", request.getQuery(), e);
            throw new IOException("Invalid search query: " + e.getMessage(), e);
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Builds the Lucene query from the search request.
     */
    private Query buildQuery(SearchRequest request) throws ParseException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Main text query with boosting
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                DEFAULT_SEARCH_FIELDS,
                analyzer,
                BOOSTS
            );
            parser.setDefaultOperator(MultiFieldQueryParser.OR_OPERATOR);
            Query textQuery = parser.parse(escapeQuery(request.getQuery()));
            builder.add(textQuery, BooleanClause.Occur.MUST);
        } else {
            // Match all if no query specified
            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        }

        // Space filter
        if (request.getSpaceKey() != null && !request.getSpaceKey().isBlank()) {
            builder.add(
                new TermQuery(new Term(SPACE_KEY, request.getSpaceKey())),
                BooleanClause.Occur.FILTER
            );
        }

        // Content type filter
        if (request.getContentType() != null && !request.getContentType().isBlank()) {
            builder.add(
                new TermQuery(new Term(CONTENT_TYPE, request.getContentType())),
                BooleanClause.Occur.FILTER
            );
        }

        // Label filter (OR - match any label)
        if (request.getLabels() != null && !request.getLabels().isEmpty()) {
            BooleanQuery.Builder labelQuery = new BooleanQuery.Builder();
            for (String label : request.getLabels()) {
                labelQuery.add(
                    new TermQuery(new Term(LABEL_FACET, label.toLowerCase())),
                    BooleanClause.Occur.SHOULD
                );
            }
            builder.add(labelQuery.build(), BooleanClause.Occur.FILTER);
        }

        // Creator filter
        if (request.getCreator() != null && !request.getCreator().isBlank()) {
            builder.add(
                new TermQuery(new Term(CREATOR, request.getCreator())),
                BooleanClause.Occur.FILTER
            );
        }

        // Date range filters
        if (request.getCreatedAfter() != null) {
            long epoch = request.getCreatedAfter().toEpochSecond(ZoneOffset.UTC);
            builder.add(
                LongPoint.newRangeQuery(CREATED, epoch, Long.MAX_VALUE),
                BooleanClause.Occur.FILTER
            );
        }

        if (request.getModifiedAfter() != null) {
            long epoch = request.getModifiedAfter().toEpochSecond(ZoneOffset.UTC);
            builder.add(
                LongPoint.newRangeQuery(MODIFIED, epoch, Long.MAX_VALUE),
                BooleanClause.Occur.FILTER
            );
        }

        // Ancestor filter (search within subtree)
        if (request.getAncestorId() != null) {
            builder.add(
                new TermQuery(new Term(ANCESTOR_IDS, String.valueOf(request.getAncestorId()))),
                BooleanClause.Occur.FILTER
            );
        }

        // Always filter to current content (not deleted/draft)
        builder.add(
            new TermQuery(new Term(CONTENT_STATUS, "current")),
            BooleanClause.Occur.FILTER
        );

        return builder.build();
    }

    /**
     * Builds sort criteria.
     */
    private Sort buildSort(SearchRequest request) {
        if (request.getSort() == null || request.getSort().isBlank()) {
            return null; // Use relevance scoring
        }

        boolean reverse = "desc".equalsIgnoreCase(request.getOrder());

        return switch (request.getSort().toLowerCase()) {
            case "modified", "date" -> new Sort(new SortField(MODIFIED, SortField.Type.LONG, reverse));
            case "created" -> new Sort(new SortField(CREATED, SortField.Type.LONG, reverse));
            case "title" -> new Sort(new SortField(TITLE, SortField.Type.STRING, reverse));
            default -> null;
        };
    }

    /**
     * Creates a highlighter for search results.
     */
    private Highlighter createHighlighter(Query query) {
        QueryScorer scorer = new QueryScorer(query);
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<mark>", "</mark>");
        Highlighter highlighter = new Highlighter(formatter, scorer);
        highlighter.setTextFragmenter(new SimpleFragmenter(150));
        return highlighter;
    }

    /**
     * Generates highlight fragments for matching fields.
     */
    private Map<String, String> generateHighlights(Highlighter highlighter, Document doc,
                                                    IndexReader reader, int docId) {
        Map<String, String> highlights = new HashMap<>();

        try {
            // Highlight title
            String title = doc.get(TITLE);
            if (title != null) {
                String highlighted = highlighter.getBestFragment(analyzer, TITLE, title);
                if (highlighted != null) {
                    highlights.put("title", highlighted);
                }
            }

            // Note: Body content is not stored, so we can't highlight it directly
            // In a real implementation, you might re-fetch from database

        } catch (Exception e) {
            log.debug("Failed to generate highlights", e);
        }

        return highlights;
    }

    /**
     * Builds facet counts for the search results.
     */
    private Map<String, List<SearchResult.FacetValue>> buildFacets(IndexSearcher searcher, Query query)
            throws IOException {
        Map<String, List<SearchResult.FacetValue>> facets = new HashMap<>();

        // Space facet
        facets.put("space", collectFacet(searcher, query, SPACE_KEY));

        // Content type facet
        facets.put("contentType", collectFacet(searcher, query, CONTENT_TYPE));

        return facets;
    }

    /**
     * Collects facet values and counts.
     */
    private List<SearchResult.FacetValue> collectFacet(IndexSearcher searcher, Query baseQuery, String field)
            throws IOException {
        // Simplified facet collection - in production use Lucene's FacetsCollector
        Map<String, Integer> counts = new HashMap<>();

        TopDocs topDocs = searcher.search(baseQuery, 1000);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            String value = doc.get(field);
            if (value != null) {
                counts.merge(value, 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .map(e -> new SearchResult.FacetValue(e.getKey(), e.getValue()))
            .toList();
    }

    /**
     * Escapes special characters in the query string.
     */
    private String escapeQuery(String query) {
        // Escape Lucene special characters but allow basic operators
        return query
            .replace("\\", "\\\\")
            .replace("+", "\\+")
            .replace("-", "\\-")
            .replace("!", "\\!")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("^", "\\^")
            .replace("~", "\\~")
            .replace(":", "\\:")
            .replace("/", "\\/");
    }
}
