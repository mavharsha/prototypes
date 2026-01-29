package com.example.wiki.controller;

import com.example.wiki.model.Page;
import com.example.wiki.repository.PageRepository;
import com.example.wiki.search.AsyncIndexer;
import com.example.wiki.search.WikiIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/index")
@RequiredArgsConstructor
@Slf4j
public class IndexController {

    private final WikiIndexer indexer;
    private final AsyncIndexer asyncIndexer;
    private final PageRepository pageRepository;

    /**
     * Get index status and statistics.
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("indexedDocuments", indexer.getDocCount());
        status.put("pendingIndexEvents", asyncIndexer.getQueueSize());
        status.put("totalPages", pageRepository.countCurrentPages());
        return status;
    }

    /**
     * Reindex all pages.
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindexAll() throws IOException {
        long startTime = System.currentTimeMillis();

        // Clear existing index
        indexer.clearIndex();

        // Fetch all current pages with details
        List<Page> pages = pageRepository.findAllCurrentPagesWithDetails();

        // Index all pages
        indexer.indexPages(pages);
        indexer.refresh();

        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("indexed", pages.size());
        result.put("durationMs", duration);
        result.put("status", "completed");

        log.info("Reindexed {} pages in {}ms", pages.size(), duration);
        return result;
    }

    /**
     * Clear the entire index.
     */
    @DeleteMapping
    public Map<String, Object> clearIndex() throws IOException {
        indexer.clearIndex();
        indexer.refresh();

        Map<String, Object> result = new HashMap<>();
        result.put("status", "cleared");
        result.put("indexedDocuments", 0);

        log.info("Cleared search index");
        return result;
    }

    /**
     * Manually trigger processing of the index queue.
     */
    @PostMapping("/process-queue")
    public Map<String, Object> processQueue() {
        int queueSize = asyncIndexer.getQueueSize();
        asyncIndexer.processQueue();

        Map<String, Object> result = new HashMap<>();
        result.put("processed", queueSize);
        result.put("remainingInQueue", asyncIndexer.getQueueSize());
        return result;
    }
}
