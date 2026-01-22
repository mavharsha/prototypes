package com.example.jobsearch.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lucene configuration - sets up index directory, analyzer, and writer
 */
@Configuration
public class LuceneConfig {

    @Value("${lucene.index.path:./data/index}")
    private String indexPath;

    private Directory directory;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;

    @Bean
    public Path indexDirectory() throws IOException {
        Path path = Paths.get(indexPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    @Bean
    public Analyzer analyzer() {
        return new StandardAnalyzer();
    }

    @Bean
    public Directory directory(Path indexDirectory) throws IOException {
        // Use MMapDirectory for better performance on 64-bit systems
        this.directory = MMapDirectory.open(indexDirectory);
        return this.directory;
    }

    @Bean
    public IndexWriterConfig indexWriterConfig(Analyzer analyzer) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        // Commit changes more frequently for near real-time search
        config.setCommitOnClose(true);
        return config;
    }

    @Bean
    public IndexWriter indexWriter(Directory directory, IndexWriterConfig config) throws IOException {
        this.indexWriter = new IndexWriter(directory, config);
        return this.indexWriter;
    }

    @Bean
    public SearcherManager searcherManager(IndexWriter indexWriter) throws IOException {
        this.searcherManager = new SearcherManager(indexWriter, null);
        return this.searcherManager;
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (indexWriter != null) {
            indexWriter.close();
        }
        if (directory != null) {
            directory.close();
        }
    }
}
