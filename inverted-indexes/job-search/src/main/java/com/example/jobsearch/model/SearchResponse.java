package com.example.jobsearch.model;

import java.util.List;
import java.util.Map;

/**
 * Search response with results and facets
 */
public class SearchResponse {

    private List<JobHit> hits;
    private long totalHits;
    private int page;
    private int size;
    private int totalPages;
    private Map<String, List<FacetCount>> facets;
    private long searchTimeMs;

    /**
     * Individual job hit with highlighting
     */
    public static class JobHit {
        private Job job;
        private float score;
        private Map<String, String> highlights;

        public JobHit() {}

        public JobHit(Job job, float score, Map<String, String> highlights) {
            this.job = job;
            this.score = score;
            this.highlights = highlights;
        }

        public Job getJob() { return job; }
        public void setJob(Job job) { this.job = job; }

        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }

        public Map<String, String> getHighlights() { return highlights; }
        public void setHighlights(Map<String, String> highlights) { this.highlights = highlights; }
    }

    /**
     * Facet count for filtering
     */
    public static class FacetCount {
        private String value;
        private long count;

        public FacetCount() {}

        public FacetCount(String value, long count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    // Default constructor
    public SearchResponse() {}

    // Getters and Setters
    public List<JobHit> getHits() { return hits; }
    public void setHits(List<JobHit> hits) { this.hits = hits; }

    public long getTotalHits() { return totalHits; }
    public void setTotalHits(long totalHits) { this.totalHits = totalHits; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public Map<String, List<FacetCount>> getFacets() { return facets; }
    public void setFacets(Map<String, List<FacetCount>> facets) { this.facets = facets; }

    public long getSearchTimeMs() { return searchTimeMs; }
    public void setSearchTimeMs(long searchTimeMs) { this.searchTimeMs = searchTimeMs; }
}
