package com.example.wiki.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lucene configuration - sets up index writer, searcher manager, and analyzer.
 */
@Configuration
public class LuceneConfig {

    @Value("${lucene.index.path:./data/index}")
    private String indexPath;

    private Directory directory;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;

    @Bean
    public Analyzer analyzer() {
        return new StandardAnalyzer();
    }

    @Bean
    public Directory directory() throws IOException {
        Path path = Paths.get(indexPath);
        Files.createDirectories(path);
        this.directory = FSDirectory.open(path);
        return directory;
    }

    @Bean
    public IndexWriter indexWriter(Directory directory, Analyzer analyzer) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        // Commit every 100 docs or 5 seconds (via AsyncIndexer)
        config.setCommitOnClose(true);
        this.indexWriter = new IndexWriter(directory, config);
        return indexWriter;
    }

    @Bean
    public SearcherManager searcherManager(IndexWriter indexWriter) throws IOException {
        this.searcherManager = new SearcherManager(indexWriter, null);
        return searcherManager;
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
