package com.example.wiki.search;

import com.example.wiki.model.Label;
import com.example.wiki.model.Page;
import com.example.wiki.storage.StorageFormatParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

import static com.example.wiki.search.IndexFields.*;

/**
 * Indexes wiki pages into Lucene.
 * Converts Page entities to Lucene Documents with appropriate field types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WikiIndexer {

    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private final StorageFormatParser parser;

    /**
     * Creates a Lucene Document from a Page entity.
     */
    public Document createDocument(Page page) {
        Document doc = new Document();

        // ===== Identifier Fields (StringField - stored, exact match) =====
        doc.add(new StringField(CONTENT_ID, String.valueOf(page.getContentId()), Field.Store.YES));
        doc.add(new StringField(CONTENT_TYPE, page.getContentType(), Field.Store.YES));

        if (page.getSpace() != null) {
            doc.add(new StringField(SPACE_KEY, page.getSpace().getSpaceKey(), Field.Store.YES));
            // Also add space name as searchable text
            if (page.getSpace().getSpaceName() != null) {
                doc.add(new TextField(SPACE_NAME, page.getSpace().getSpaceName(), Field.Store.YES));
            }
        }

        // ===== Text Fields (TextField - full-text searchable) =====
        doc.add(new TextField(TITLE, page.getTitle(), Field.Store.YES));

        // Extract plain text from XHTML body for indexing
        String bodyText = "";
        if (page.getBody() != null && page.getBody().getBody() != null) {
            bodyText = parser.extractText(page.getBody().getBody());
            doc.add(new TextField(CONTENT, bodyText, Field.Store.NO));
        }

        // Labels as searchable text
        String labelText = page.getLabels().stream()
            .map(Label::getName)
            .collect(Collectors.joining(" "));
        if (!labelText.isBlank()) {
            doc.add(new TextField(LABEL_TEXT, labelText, Field.Store.YES));
        }

        // Combined "all" field for simple searches
        StringBuilder allText = new StringBuilder();
        allText.append(page.getTitle()).append(" ");
        allText.append(bodyText).append(" ");
        allText.append(labelText).append(" ");
        if (page.getSpace() != null && page.getSpace().getSpaceName() != null) {
            allText.append(page.getSpace().getSpaceName());
        }
        doc.add(new TextField(ALL, allText.toString(), Field.Store.NO));

        // ===== Filter Fields (StringField - not stored, for filtering) =====
        doc.add(new StringField(CONTENT_STATUS, page.getContentStatus(), Field.Store.NO));

        if (page.getCreator() != null) {
            doc.add(new StringField(CREATOR, page.getCreator(), Field.Store.NO));
        }
        if (page.getLastModifier() != null) {
            doc.add(new StringField(LAST_MODIFIER, page.getLastModifier(), Field.Store.NO));
        }

        // ===== Date Fields (LongPoint for range queries) =====
        if (page.getCreationDate() != null) {
            long createdEpoch = page.getCreationDate().toEpochSecond(ZoneOffset.UTC);
            doc.add(new LongPoint(CREATED, createdEpoch));
            doc.add(new StoredField(CREATED + "_stored", createdEpoch));
        }

        if (page.getLastModDate() != null) {
            long modifiedEpoch = page.getLastModDate().toEpochSecond(ZoneOffset.UTC);
            doc.add(new LongPoint(MODIFIED, modifiedEpoch));
            doc.add(new StoredField(MODIFIED + "_stored", modifiedEpoch));
            // NumericDocValuesField for sorting
            doc.add(new NumericDocValuesField(MODIFIED, modifiedEpoch));
        }

        // ===== Hierarchical Fields =====
        if (page.getParent() != null) {
            doc.add(new StringField(PARENT_ID, String.valueOf(page.getParent().getContentId()), Field.Store.NO));
        }

        // Store all ancestors for filtering by subtree
        addAncestors(doc, page);

        // ===== Facet Fields =====
        if (page.getSpace() != null) {
            doc.add(new StringField(SPACE_KEY_FACET, page.getSpace().getSpaceKey().toLowerCase(), Field.Store.NO));
        }
        doc.add(new StringField(CONTENT_TYPE_FACET, page.getContentType().toLowerCase(), Field.Store.NO));

        // Individual labels for faceted filtering
        for (Label label : page.getLabels()) {
            doc.add(new StringField(LABEL_FACET, label.getName().toLowerCase(), Field.Store.NO));
        }

        return doc;
    }

    /**
     * Adds ancestor page IDs for hierarchical filtering.
     */
    private void addAncestors(Document doc, Page page) {
        Page parent = page.getParent();
        while (parent != null) {
            doc.add(new StringField(ANCESTOR_IDS, String.valueOf(parent.getContentId()), Field.Store.NO));
            parent = parent.getParent();
        }
    }

    /**
     * Indexes a page (insert or update).
     */
    public void indexPage(Page page) throws IOException {
        Document doc = createDocument(page);
        Term idTerm = new Term(CONTENT_ID, String.valueOf(page.getContentId()));
        indexWriter.updateDocument(idTerm, doc);
        log.debug("Indexed page: {} ({})", page.getTitle(), page.getContentId());
    }

    /**
     * Indexes multiple pages in a batch.
     */
    public void indexPages(Iterable<Page> pages) throws IOException {
        for (Page page : pages) {
            indexPage(page);
        }
        commit();
    }

    /**
     * Removes a page from the index.
     */
    public void deletePage(Long contentId) throws IOException {
        Term idTerm = new Term(CONTENT_ID, String.valueOf(contentId));
        indexWriter.deleteDocuments(idTerm);
        log.debug("Deleted page from index: {}", contentId);
    }

    /**
     * Commits pending changes to the index.
     */
    public void commit() throws IOException {
        indexWriter.commit();
    }

    /**
     * Refreshes the searcher to see recent changes.
     */
    public void refresh() throws IOException {
        searcherManager.maybeRefresh();
    }

    /**
     * Clears the entire index.
     */
    public void clearIndex() throws IOException {
        indexWriter.deleteAll();
        indexWriter.commit();
        log.info("Cleared search index");
    }

    /**
     * Returns the number of documents in the index.
     */
    public int getDocCount() {
        return indexWriter.getDocStats().numDocs;
    }
}
