package com.example.jobsearch.api;

import com.example.jobsearch.model.Job;
import com.example.jobsearch.model.SearchRequest;
import com.example.jobsearch.model.SearchResponse;
import com.example.jobsearch.search.JobSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * REST API for job search
 */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final JobSearchService searchService;

    public SearchController(JobSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Search jobs with filters
     *
     * POST /api/jobs/search
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        try {
            SearchResponse response = searchService.search(request);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Search failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Simple search with query parameter
     *
     * GET /api/jobs/search?q=java+developer&page=0&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchGet(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> location,
            @RequestParam(required = false) List<Job.JobType> jobType,
            @RequestParam(required = false) List<Job.ExperienceLevel> experienceLevel,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) List<String> skill,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        SearchRequest request = new SearchRequest();
        request.setQuery(q);
        request.setLocations(location);
        request.setJobTypes(jobType);
        request.setExperienceLevels(experienceLevel);
        request.setSalaryMin(salaryMin);
        request.setSalaryMax(salaryMax);
        request.setRemote(remote);
        request.setSkills(skill);
        request.setSortBy(sortBy);
        request.setPage(page);
        request.setSize(size);

        try {
            SearchResponse response = searchService.search(request);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Search failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get job by ID
     *
     * GET /api/jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        try {
            return searchService.getJob(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IOException e) {
            logger.error("Failed to get job: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
