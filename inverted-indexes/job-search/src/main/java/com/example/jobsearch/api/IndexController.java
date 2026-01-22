package com.example.jobsearch.api;

import com.example.jobsearch.indexer.AsyncIndexer;
import com.example.jobsearch.indexer.BatchIndexer;
import com.example.jobsearch.indexer.JobIndexer;
import com.example.jobsearch.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * REST API for index management
 */
@RestController
@RequestMapping("/api/index")
@CrossOrigin(origins = "*")
public class IndexController {

    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    private final JobIndexer jobIndexer;
    private final BatchIndexer batchIndexer;
    private final AsyncIndexer asyncIndexer;

    public IndexController(JobIndexer jobIndexer, BatchIndexer batchIndexer, AsyncIndexer asyncIndexer) {
        this.jobIndexer = jobIndexer;
        this.batchIndexer = batchIndexer;
        this.asyncIndexer = asyncIndexer;
    }

    /**
     * Index a single job synchronously
     *
     * POST /api/index/job
     */
    @PostMapping("/job")
    public ResponseEntity<Map<String, Object>> indexJob(@RequestBody Job job) {
        try {
            jobIndexer.indexJob(job);
            jobIndexer.commit();
            return ResponseEntity.ok(Map.of(
                    "status", "indexed",
                    "id", job.getId()
            ));
        } catch (IOException e) {
            logger.error("Failed to index job", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Index a job asynchronously
     *
     * POST /api/index/job/async
     */
    @PostMapping("/job/async")
    public ResponseEntity<Map<String, Object>> indexJobAsync(@RequestBody Job job) {
        boolean queued = asyncIndexer.submitForIndexing(job);
        if (queued) {
            return ResponseEntity.accepted().body(Map.of(
                    "status", "queued",
                    "id", job.getId(),
                    "queueSize", asyncIndexer.getQueueSize()
            ));
        } else {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "queue_full",
                    "message", "Indexing queue is full, try again later"
            ));
        }
    }

    /**
     * Delete a job from index
     *
     * DELETE /api/index/job/{id}
     */
    @DeleteMapping("/job/{id}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable String id) {
        try {
            jobIndexer.deleteJob(id);
            jobIndexer.commit();
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "id", id
            ));
        } catch (IOException e) {
            logger.error("Failed to delete job", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete a job asynchronously
     *
     * DELETE /api/index/job/{id}/async
     */
    @DeleteMapping("/job/{id}/async")
    public ResponseEntity<Map<String, Object>> deleteJobAsync(@PathVariable String id) {
        boolean queued = asyncIndexer.submitForDeletion(id);
        if (queued) {
            return ResponseEntity.accepted().body(Map.of(
                    "status", "queued_for_deletion",
                    "id", id
            ));
        } else {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "queue_full",
                    "message", "Queue is full, try again later"
            ));
        }
    }

    /**
     * Trigger full reindex
     *
     * POST /api/index/reindex
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        try {
            int count = batchIndexer.reindex();
            return ResponseEntity.ok(Map.of(
                    "status", "reindexed",
                    "count", count
            ));
        } catch (IOException e) {
            logger.error("Reindex failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Clear entire index
     *
     * DELETE /api/index
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearIndex() {
        try {
            jobIndexer.clearIndex();
            return ResponseEntity.ok(Map.of(
                    "status", "cleared"
            ));
        } catch (IOException e) {
            logger.error("Failed to clear index", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get index status
     *
     * GET /api/index/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "asyncQueueSize", asyncIndexer.getQueueSize(),
                "status", "running"
        ));
    }
}
