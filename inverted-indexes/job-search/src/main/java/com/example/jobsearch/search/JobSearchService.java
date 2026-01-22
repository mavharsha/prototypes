package com.example.jobsearch.search;

import com.example.jobsearch.model.Job;
import com.example.jobsearch.model.SearchRequest;
import com.example.jobsearch.model.SearchResponse;
import com.example.jobsearch.model.SearchResponse.FacetCount;
import com.example.jobsearch.model.SearchResponse.JobHit;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Search service with facets, filtering, sorting, and highlighting
 */
@Service
public class JobSearchService {

    private static final Logger logger = LoggerFactory.getLogger(JobSearchService.class);

    private final SearcherManager searcherManager;
    private final Analyzer analyzer;

    // Fields to search with boosts
    private static final Map<String, Float> SEARCH_FIELDS = Map.of(
            "title", 3.0f,
            "skills", 2.0f,
            "company", 1.5f,
            "description", 1.0f,
            "location", 1.0f
    );

    public JobSearchService(SearcherManager searcherManager, Analyzer analyzer) {
        this.searcherManager = searcherManager;
        this.analyzer = analyzer;
    }

    /**
     * Execute search with filters, facets, and highlighting
     */
    public SearchResponse search(SearchRequest request) throws IOException {
        long startTime = System.currentTimeMillis();

        IndexSearcher searcher = searcherManager.acquire();
        try {
            // Build query
            Query query = buildQuery(request);

            // Determine sort
            Sort sort = buildSort(request.getSortBy());

            // Calculate pagination
            int start = request.getPage() * request.getSize();
            int numHits = start + request.getSize();

            // Execute search
            TopDocs topDocs;
            if (sort != null) {
                topDocs = searcher.search(query, numHits, sort);
            } else {
                topDocs = searcher.search(query, numHits);
            }

            // Build response
            SearchResponse response = new SearchResponse();
            response.setTotalHits(topDocs.totalHits.value);
            response.setPage(request.getPage());
            response.setSize(request.getSize());
            response.setTotalPages((int) Math.ceil((double) topDocs.totalHits.value / request.getSize()));

            // Extract hits with highlighting
            List<JobHit> hits = extractHits(searcher, topDocs, start, request.getQuery());
            response.setHits(hits);

            // Calculate facets
            Map<String, List<FacetCount>> facets = calculateFacets(searcher, query);
            response.setFacets(facets);

            response.setSearchTimeMs(System.currentTimeMillis() - startTime);

            logger.debug("Search completed: {} hits in {}ms",
                    topDocs.totalHits.value, response.getSearchTimeMs());

            return response;

        } catch (ParseException e) {
            throw new IOException("Invalid search query", e);
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Get a single job by ID
     */
    public Optional<Job> getJob(String jobId) throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query query = new TermQuery(new Term("id", jobId));
            TopDocs topDocs = searcher.search(query, 1);

            if (topDocs.totalHits.value == 0) {
                return Optional.empty();
            }

            Document doc = searcher.doc(topDocs.scoreDocs[0].doc);
            return Optional.of(documentToJob(doc));

        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Build Lucene query from search request
     */
    private Query buildQuery(SearchRequest request) throws ParseException {
        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

        // Text query
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            String[] fields = SEARCH_FIELDS.keySet().toArray(new String[0]);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, SEARCH_FIELDS);
            parser.setDefaultOperator(MultiFieldQueryParser.Operator.OR);
            Query textQuery = parser.parse(request.getQuery());
            boolQuery.add(textQuery, BooleanClause.Occur.MUST);
        } else {
            boolQuery.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        }

        // Location filter
        if (request.getLocations() != null && !request.getLocations().isEmpty()) {
            BooleanQuery.Builder locationQuery = new BooleanQuery.Builder();
            for (String location : request.getLocations()) {
                locationQuery.add(
                        new TermQuery(new Term("location_facet", location.toLowerCase())),
                        BooleanClause.Occur.SHOULD
                );
            }
            boolQuery.add(locationQuery.build(), BooleanClause.Occur.FILTER);
        }

        // Job type filter
        if (request.getJobTypes() != null && !request.getJobTypes().isEmpty()) {
            BooleanQuery.Builder typeQuery = new BooleanQuery.Builder();
            for (Job.JobType type : request.getJobTypes()) {
                typeQuery.add(
                        new TermQuery(new Term("jobType", type.name())),
                        BooleanClause.Occur.SHOULD
                );
            }
            boolQuery.add(typeQuery.build(), BooleanClause.Occur.FILTER);
        }

        // Experience level filter
        if (request.getExperienceLevels() != null && !request.getExperienceLevels().isEmpty()) {
            BooleanQuery.Builder expQuery = new BooleanQuery.Builder();
            for (Job.ExperienceLevel level : request.getExperienceLevels()) {
                expQuery.add(
                        new TermQuery(new Term("experienceLevel", level.name())),
                        BooleanClause.Occur.SHOULD
                );
            }
            boolQuery.add(expQuery.build(), BooleanClause.Occur.FILTER);
        }

        // Salary range filter
        if (request.getSalaryMin() != null) {
            boolQuery.add(
                    IntPoint.newRangeQuery("salaryMax", request.getSalaryMin(), Integer.MAX_VALUE),
                    BooleanClause.Occur.FILTER
            );
        }
        if (request.getSalaryMax() != null) {
            boolQuery.add(
                    IntPoint.newRangeQuery("salaryMin", 0, request.getSalaryMax()),
                    BooleanClause.Occur.FILTER
            );
        }

        // Remote filter
        if (request.getRemote() != null) {
            boolQuery.add(
                    new TermQuery(new Term("remote", String.valueOf(request.getRemote()))),
                    BooleanClause.Occur.FILTER
            );
        }

        // Skills filter
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            BooleanQuery.Builder skillsQuery = new BooleanQuery.Builder();
            for (String skill : request.getSkills()) {
                skillsQuery.add(
                        new TermQuery(new Term("skills_facet", skill.toLowerCase())),
                        BooleanClause.Occur.SHOULD
                );
            }
            boolQuery.add(skillsQuery.build(), BooleanClause.Occur.FILTER);
        }

        return boolQuery.build();
    }

    /**
     * Build sort from sort parameter
     */
    private Sort buildSort(String sortBy) {
        if (sortBy == null) return null;

        return switch (sortBy) {
            case "date" -> new Sort(new SortField("postedDate_sort", SortField.Type.LONG, true));
            case "salaryAsc" -> new Sort(new SortField("salaryMin_sort", SortField.Type.LONG, false));
            case "salaryDesc" -> new Sort(new SortField("salaryMax_sort", SortField.Type.LONG, true));
            case "relevance" -> null; // Use default relevance scoring
            default -> null;
        };
    }

    /**
     * Extract hits with highlighting
     */
    private List<JobHit> extractHits(IndexSearcher searcher, TopDocs topDocs,
                                      int start, String queryText) throws IOException {
        List<JobHit> hits = new ArrayList<>();

        // Setup highlighter
        Highlighter highlighter = null;
        if (queryText != null && !queryText.isBlank()) {
            try {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[]{"title", "description"},
                        analyzer
                );
                Query highlightQuery = parser.parse(queryText);
                QueryScorer scorer = new QueryScorer(highlightQuery);
                highlighter = new Highlighter(
                        new SimpleHTMLFormatter("<mark>", "</mark>"),
                        scorer
                );
                highlighter.setTextFragmenter(new SimpleFragmenter(150));
            } catch (ParseException e) {
                logger.debug("Could not create highlighter", e);
            }
        }

        // Process hits
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        for (int i = start; i < scoreDocs.length && i < start + 10; i++) {
            Document doc = searcher.doc(scoreDocs[i].doc);
            Job job = documentToJob(doc);

            Map<String, String> highlights = new HashMap<>();
            if (highlighter != null) {
                try {
                    String titleHighlight = highlighter.getBestFragment(analyzer, "title", job.getTitle());
                    if (titleHighlight != null) {
                        highlights.put("title", titleHighlight);
                    }

                    String descHighlight = highlighter.getBestFragment(analyzer, "description", job.getDescription());
                    if (descHighlight != null) {
                        highlights.put("description", descHighlight);
                    }
                } catch (InvalidTokenOffsetsException e) {
                    logger.debug("Could not highlight", e);
                }
            }

            hits.add(new JobHit(job, scoreDocs[i].score, highlights));
        }

        return hits;
    }

    /**
     * Calculate facets for the current query
     */
    private Map<String, List<FacetCount>> calculateFacets(IndexSearcher searcher, Query query)
            throws IOException {

        Map<String, List<FacetCount>> facets = new HashMap<>();

        // For simplicity, we'll count facets by iterating over results
        // In production, use Lucene's FacetsCollector for better performance
        TopDocs allDocs = searcher.search(query, 1000); // Limit for facet counting

        Map<String, Integer> locationCounts = new HashMap<>();
        Map<String, Integer> jobTypeCounts = new HashMap<>();
        Map<String, Integer> experienceCounts = new HashMap<>();
        Map<String, Integer> skillsCounts = new HashMap<>();

        for (ScoreDoc scoreDoc : allDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);

            // Count locations
            String location = doc.get("location");
            if (location != null) {
                locationCounts.merge(location, 1, Integer::sum);
            }

            // Count job types
            String jobType = doc.get("jobType");
            if (jobType != null) {
                jobTypeCounts.merge(jobType, 1, Integer::sum);
            }

            // Count experience levels
            String expLevel = doc.get("experienceLevel");
            if (expLevel != null) {
                experienceCounts.merge(expLevel, 1, Integer::sum);
            }

            // Count skills
            String[] skills = doc.getValues("skills");
            for (String skill : skills) {
                skillsCounts.merge(skill, 1, Integer::sum);
            }
        }

        facets.put("location", toFacetList(locationCounts));
        facets.put("jobType", toFacetList(jobTypeCounts));
        facets.put("experienceLevel", toFacetList(experienceCounts));
        facets.put("skills", toFacetList(skillsCounts));

        return facets;
    }

    private List<FacetCount> toFacetList(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> new FacetCount(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Convert Lucene document to Job object
     */
    private Job documentToJob(Document doc) {
        Job job = new Job();
        job.setId(doc.get("id"));
        job.setTitle(doc.get("title"));
        job.setCompany(doc.get("company"));
        job.setDescription(doc.get("description"));
        job.setLocation(doc.get("location"));

        String jobType = doc.get("jobType");
        if (jobType != null) {
            job.setJobType(Job.JobType.valueOf(jobType));
        }

        String expLevel = doc.get("experienceLevel");
        if (expLevel != null) {
            job.setExperienceLevel(Job.ExperienceLevel.valueOf(expLevel));
        }

        // Get stored numeric fields
        Number salaryMin = doc.getField("salaryMin").numericValue();
        Number salaryMax = doc.getField("salaryMax").numericValue();
        job.setSalaryMin(salaryMin != null ? salaryMin.intValue() : 0);
        job.setSalaryMax(salaryMax != null ? salaryMax.intValue() : 0);

        job.setSkills(doc.getValues("skills"));

        String postedDate = doc.get("postedDate");
        if (postedDate != null) {
            job.setPostedDate(LocalDate.parse(postedDate));
        }

        job.setRemote(Boolean.parseBoolean(doc.get("remote")));

        return job;
    }
}
