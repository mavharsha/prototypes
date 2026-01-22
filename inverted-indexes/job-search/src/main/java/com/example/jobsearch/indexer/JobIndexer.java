package com.example.jobsearch.indexer;

import com.example.jobsearch.model.Job;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneOffset;

/**
 * Core indexer that converts Job objects to Lucene documents
 */
@Service
public class JobIndexer {

    private static final Logger logger = LoggerFactory.getLogger(JobIndexer.class);

    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;

    public JobIndexer(IndexWriter indexWriter, SearcherManager searcherManager) {
        this.indexWriter = indexWriter;
        this.searcherManager = searcherManager;
    }

    /**
     * Index a single job
     */
    public void indexJob(Job job) throws IOException {
        Document doc = createDocument(job);

        // Use updateDocument to handle both insert and update
        indexWriter.updateDocument(new Term("id", job.getId()), doc);
        logger.debug("Indexed job: {}", job.getId());
    }

    /**
     * Index multiple jobs in batch
     */
    public int indexJobs(Iterable<Job> jobs) throws IOException {
        int count = 0;
        for (Job job : jobs) {
            indexJob(job);
            count++;
        }
        commit();
        logger.info("Batch indexed {} jobs", count);
        return count;
    }

    /**
     * Delete a job from index
     */
    public void deleteJob(String jobId) throws IOException {
        indexWriter.deleteDocuments(new Term("id", jobId));
        logger.debug("Deleted job: {}", jobId);
    }

    /**
     * Commit changes and refresh searcher
     */
    public void commit() throws IOException {
        indexWriter.commit();
        searcherManager.maybeRefresh();
    }

    /**
     * Refresh searcher for near real-time search
     */
    public void refresh() throws IOException {
        searcherManager.maybeRefresh();
    }

    /**
     * Clear entire index
     */
    public void clearIndex() throws IOException {
        indexWriter.deleteAll();
        indexWriter.commit();
        searcherManager.maybeRefresh();
        logger.info("Cleared entire index");
    }

    /**
     * Convert Job to Lucene Document
     */
    private Document createDocument(Job job) {
        Document doc = new Document();

        // ID - stored and indexed as keyword (exact match)
        doc.add(new StringField("id", job.getId(), Field.Store.YES));

        // Title - full text searchable with stored value
        doc.add(new TextField("title", job.getTitle(), Field.Store.YES));

        // Company - full text and keyword
        doc.add(new TextField("company", job.getCompany(), Field.Store.YES));
        doc.add(new StringField("company_exact", job.getCompany().toLowerCase(), Field.Store.NO));

        // Description - full text searchable
        doc.add(new TextField("description", job.getDescription(), Field.Store.YES));

        // Location - keyword for filtering, text for searching
        doc.add(new TextField("location", job.getLocation(), Field.Store.YES));
        doc.add(new StringField("location_facet", job.getLocation().toLowerCase(), Field.Store.NO));

        // Job Type - keyword for filtering
        if (job.getJobType() != null) {
            doc.add(new StringField("jobType", job.getJobType().name(), Field.Store.YES));
        }

        // Experience Level - keyword for filtering
        if (job.getExperienceLevel() != null) {
            doc.add(new StringField("experienceLevel", job.getExperienceLevel().name(), Field.Store.YES));
        }

        // Salary - numeric fields for range queries
        doc.add(new IntPoint("salaryMin", job.getSalaryMin()));
        doc.add(new StoredField("salaryMin", job.getSalaryMin()));
        doc.add(new IntPoint("salaryMax", job.getSalaryMax()));
        doc.add(new StoredField("salaryMax", job.getSalaryMax()));

        // For sorting by salary
        doc.add(new NumericDocValuesField("salaryMin_sort", job.getSalaryMin()));
        doc.add(new NumericDocValuesField("salaryMax_sort", job.getSalaryMax()));

        // Skills - multi-valued field
        if (job.getSkills() != null) {
            for (String skill : job.getSkills()) {
                doc.add(new TextField("skills", skill, Field.Store.YES));
                doc.add(new StringField("skills_facet", skill.toLowerCase(), Field.Store.NO));
            }
        }

        // Posted Date - for sorting and range queries
        if (job.getPostedDate() != null) {
            long epochDay = job.getPostedDate().toEpochDay();
            doc.add(new LongPoint("postedDate", epochDay));
            doc.add(new StoredField("postedDate", job.getPostedDate().toString()));
            doc.add(new NumericDocValuesField("postedDate_sort", epochDay));
        }

        // Remote - boolean as keyword
        doc.add(new StringField("remote", String.valueOf(job.isRemote()), Field.Store.YES));

        // Combined searchable field for general queries
        StringBuilder allText = new StringBuilder();
        allText.append(job.getTitle()).append(" ");
        allText.append(job.getCompany()).append(" ");
        allText.append(job.getDescription()).append(" ");
        allText.append(job.getLocation()).append(" ");
        if (job.getSkills() != null) {
            for (String skill : job.getSkills()) {
                allText.append(skill).append(" ");
            }
        }
        doc.add(new TextField("all", allText.toString(), Field.Store.NO));

        return doc;
    }
}
