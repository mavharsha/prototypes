package com.example.wiki.search;

import com.example.wiki.model.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous indexer that queues changes and processes them in batches.
 * Similar to how Confluence processes index updates every ~5 seconds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncIndexer {

    private final WikiIndexer indexer;
    private final BlockingQueue<IndexEvent> queue = new LinkedBlockingQueue<>();

    /**
     * Queues a page for indexing.
     */
    public void queueIndex(Page page) {
        queue.offer(new IndexEvent(IndexEventType.UPDATE, page, null));
        log.debug("Queued page for indexing: {} (queue size: {})", page.getTitle(), queue.size());
    }

    /**
     * Queues a page for deletion from index.
     */
    public void queueDelete(Long contentId) {
        queue.offer(new IndexEvent(IndexEventType.DELETE, null, contentId));
        log.debug("Queued page for deletion: {} (queue size: {})", contentId, queue.size());
    }

    /**
     * Processes the queue every 5 seconds (like Confluence).
     */
    @Scheduled(fixedDelay = 5000)
    public void processQueue() {
        if (queue.isEmpty()) {
            return;
        }

        List<IndexEvent> events = new ArrayList<>();
        queue.drainTo(events, 100); // Process up to 100 events per batch

        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} indexing events", events.size());
        long startTime = System.currentTimeMillis();

        int indexed = 0;
        int deleted = 0;
        int errors = 0;

        for (IndexEvent event : events) {
            try {
                switch (event.type()) {
                    case UPDATE -> {
                        indexer.indexPage(event.page());
                        indexed++;
                    }
                    case DELETE -> {
                        indexer.deletePage(event.contentId());
                        deleted++;
                    }
                }
            } catch (IOException e) {
                log.error("Failed to process index event: {}", event, e);
                errors++;
            }
        }

        // Commit and refresh after batch
        try {
            indexer.commit();
            indexer.refresh();
        } catch (IOException e) {
            log.error("Failed to commit index", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Processed {} events in {}ms (indexed: {}, deleted: {}, errors: {})",
            events.size(), duration, indexed, deleted, errors);
    }

    /**
     * Gets the current queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    // Event types
    private enum IndexEventType {
        UPDATE, DELETE
    }

    // Event record
    private record IndexEvent(IndexEventType type, Page page, Long contentId) {}
}
