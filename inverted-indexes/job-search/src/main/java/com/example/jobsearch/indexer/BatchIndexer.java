package com.example.jobsearch.indexer;

import com.example.jobsearch.model.Job;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Batch indexer for initial data load
 */
@Service
public class BatchIndexer {

    private static final Logger logger = LoggerFactory.getLogger(BatchIndexer.class);

    private final JobIndexer jobIndexer;
    private final ObjectMapper objectMapper;

    @Value("${jobsearch.data.path:jobs.json}")
    private String dataPath;

    @Value("${jobsearch.index.on-startup:true}")
    private boolean indexOnStartup;

    public BatchIndexer(JobIndexer jobIndexer) {
        this.jobIndexer = jobIndexer;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Index on application startup if enabled
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (indexOnStartup) {
            try {
                indexFromClasspath();
            } catch (IOException e) {
                logger.error("Failed to index on startup", e);
            }
        }
    }

    /**
     * Index jobs from classpath resource
     */
    public int indexFromClasspath() throws IOException {
        logger.info("Loading jobs from classpath: {}", dataPath);

        ClassPathResource resource = new ClassPathResource(dataPath);
        if (!resource.exists()) {
            logger.warn("Data file not found: {}", dataPath);
            return 0;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Job> jobs = objectMapper.readValue(is, new TypeReference<List<Job>>() {});
            return indexJobs(jobs);
        }
    }

    /**
     * Index jobs from file path
     */
    public int indexFromFile(Path filePath) throws IOException {
        logger.info("Loading jobs from file: {}", filePath);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }

        List<Job> jobs = objectMapper.readValue(filePath.toFile(), new TypeReference<List<Job>>() {});
        return indexJobs(jobs);
    }

    /**
     * Index jobs from JSON string
     */
    public int indexFromJson(String json) throws IOException {
        List<Job> jobs = objectMapper.readValue(json, new TypeReference<List<Job>>() {});
        return indexJobs(jobs);
    }

    /**
     * Index a list of jobs
     */
    public int indexJobs(List<Job> jobs) throws IOException {
        logger.info("Starting batch index of {} jobs", jobs.size());
        long startTime = System.currentTimeMillis();

        int indexed = jobIndexer.indexJobs(jobs);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Batch indexing complete: {} jobs in {}ms ({} jobs/sec)",
                indexed, duration, indexed * 1000 / Math.max(1, duration));

        return indexed;
    }

    /**
     * Reindex - clear and reload
     */
    public int reindex() throws IOException {
        logger.info("Starting full reindex");
        jobIndexer.clearIndex();
        return indexFromClasspath();
    }
}
