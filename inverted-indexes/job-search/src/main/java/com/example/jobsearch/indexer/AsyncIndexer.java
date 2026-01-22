package com.example.jobsearch.indexer;

import com.example.jobsearch.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async indexer using an in-memory queue
 *
 * In production, this would typically be:
 * - Kafka consumer
 * - RabbitMQ listener
 * - AWS SQS processor
 */
@Service
public class AsyncIndexer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncIndexer.class);

    private final JobIndexer jobIndexer;
    private final BlockingQueue<IndexEvent> eventQueue;
    private final AtomicBoolean running;
    private Thread workerThread;

    /**
     * Index event types
     */
    public enum EventType {
        INDEX, DELETE
    }

    /**
     * Represents an indexing event
     */
    public static class IndexEvent {
        private final EventType type;
        private final Job job;
        private final String jobId;

        private IndexEvent(EventType type, Job job, String jobId) {
            this.type = type;
            this.job = job;
            this.jobId = jobId;
        }

        public static IndexEvent index(Job job) {
            return new IndexEvent(EventType.INDEX, job, job.getId());
        }

        public static IndexEvent delete(String jobId) {
            return new IndexEvent(EventType.DELETE, null, jobId);
        }
    }

    public AsyncIndexer(JobIndexer jobIndexer) {
        this.jobIndexer = jobIndexer;
        this.eventQueue = new LinkedBlockingQueue<>(10000); // Max 10k pending events
        this.running = new AtomicBoolean(false);
    }

    @PostConstruct
    public void start() {
        running.set(true);
        workerThread = new Thread(this::processEvents, "async-indexer");
        workerThread.setDaemon(true);
        workerThread.start();
        logger.info("Async indexer started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Async indexer stopped");
    }

    /**
     * Submit a job for async indexing
     */
    public boolean submitForIndexing(Job job) {
        boolean added = eventQueue.offer(IndexEvent.index(job));
        if (added) {
            logger.debug("Queued job for indexing: {}", job.getId());
        } else {
            logger.warn("Queue full, could not submit job: {}", job.getId());
        }
        return added;
    }

    /**
     * Submit a job for async deletion
     */
    public boolean submitForDeletion(String jobId) {
        boolean added = eventQueue.offer(IndexEvent.delete(jobId));
        if (added) {
            logger.debug("Queued job for deletion: {}", jobId);
        } else {
            logger.warn("Queue full, could not submit deletion: {}", jobId);
        }
        return added;
    }

    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return eventQueue.size();
    }

    /**
     * Process events from the queue
     */
    private void processEvents() {
        int batchSize = 0;
        long lastCommit = System.currentTimeMillis();
        final int COMMIT_BATCH_SIZE = 100;
        final long COMMIT_INTERVAL_MS = 1000; // Commit at least every second

        while (running.get()) {
            try {
                IndexEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);

                if (event != null) {
                    processEvent(event);
                    batchSize++;
                }

                // Commit if batch is large enough or enough time has passed
                boolean shouldCommit = batchSize >= COMMIT_BATCH_SIZE ||
                        (batchSize > 0 && System.currentTimeMillis() - lastCommit > COMMIT_INTERVAL_MS);

                if (shouldCommit) {
                    jobIndexer.commit();
                    logger.debug("Committed {} index events", batchSize);
                    batchSize = 0;
                    lastCommit = System.currentTimeMillis();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                logger.error("Error processing index event", e);
            }
        }

        // Final commit on shutdown
        if (batchSize > 0) {
            try {
                jobIndexer.commit();
                logger.info("Final commit of {} events on shutdown", batchSize);
            } catch (IOException e) {
                logger.error("Error during final commit", e);
            }
        }
    }

    private void processEvent(IndexEvent event) throws IOException {
        switch (event.type) {
            case INDEX:
                jobIndexer.indexJob(event.job);
                break;
            case DELETE:
                jobIndexer.deleteJob(event.jobId);
                break;
        }
    }
}
