package com.example.wiki;

import com.example.wiki.dto.PageCreateRequest;
import com.example.wiki.dto.SpaceCreateRequest;
import com.example.wiki.model.Page;
import com.example.wiki.repository.PageRepository;
import com.example.wiki.search.WikiIndexer;
import com.example.wiki.service.PageService;
import com.example.wiki.service.SpaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads sample data on application startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final SpaceService spaceService;
    private final PageService pageService;
    private final PageRepository pageRepository;
    private final WikiIndexer indexer;
    private final ObjectMapper objectMapper;

    @Value("${wiki.index.on-startup:true}")
    private boolean indexOnStartup;

    @Value("${wiki.data.path:sample-pages.json}")
    private String dataPath;

    @Override
    public void run(String... args) throws Exception {
        // Skip if data already exists
        if (pageRepository.count() > 0) {
            log.info("Data already exists, skipping data load");

            // Still reindex if requested
            if (indexOnStartup) {
                reindexAll();
            }
            return;
        }

        log.info("Loading sample data from {}", dataPath);

        try (InputStream is = new ClassPathResource(dataPath).getInputStream()) {
            JsonNode root = objectMapper.readTree(is);

            // Load spaces
            JsonNode spaces = root.get("spaces");
            if (spaces != null && spaces.isArray()) {
                for (JsonNode spaceNode : spaces) {
                    createSpace(spaceNode);
                }
            }

            // Load pages
            JsonNode pages = root.get("pages");
            if (pages != null && pages.isArray()) {
                for (JsonNode pageNode : pages) {
                    createPage(pageNode);
                }
            }

            log.info("Sample data loaded successfully");

            // Index all pages
            if (indexOnStartup) {
                reindexAll();
            }

        } catch (Exception e) {
            log.error("Failed to load sample data", e);
        }
    }

    private void createSpace(JsonNode node) {
        try {
            String spaceKey = node.get("spaceKey").asText();

            if (spaceService.exists(spaceKey)) {
                log.debug("Space {} already exists, skipping", spaceKey);
                return;
            }

            SpaceCreateRequest request = SpaceCreateRequest.builder()
                .spaceKey(spaceKey)
                .spaceName(node.get("spaceName").asText())
                .description(node.has("description") ? node.get("description").asText() : null)
                .spaceType(node.has("spaceType") ? node.get("spaceType").asText() : "global")
                .creator(node.has("creator") ? node.get("creator").asText() : "admin")
                .build();

            spaceService.createSpace(request);
            log.info("Created space: {}", spaceKey);

        } catch (Exception e) {
            log.error("Failed to create space: {}", node, e);
        }
    }

    private void createPage(JsonNode node) {
        try {
            String spaceKey = node.get("spaceKey").asText();
            String title = node.get("title").asText();

            // Extract labels
            List<String> labels = new ArrayList<>();
            JsonNode labelsNode = node.get("labels");
            if (labelsNode != null && labelsNode.isArray()) {
                for (JsonNode labelNode : labelsNode) {
                    labels.add(labelNode.asText());
                }
            }

            PageCreateRequest request = PageCreateRequest.builder()
                .spaceKey(spaceKey)
                .title(title)
                .body(node.has("body") ? node.get("body").asText() : "")
                .storageFormat(true) // Body is already in XHTML format
                .labels(labels)
                .creator(node.has("creator") ? node.get("creator").asText() : "admin")
                .build();

            pageService.createPage(request);
            log.info("Created page: {} in space {}", title, spaceKey);

        } catch (Exception e) {
            log.error("Failed to create page: {}", node, e);
        }
    }

    private void reindexAll() {
        try {
            log.info("Reindexing all pages...");
            List<Page> pages = pageRepository.findAllCurrentPagesWithDetails();
            indexer.clearIndex();
            indexer.indexPages(pages);
            indexer.refresh();
            log.info("Indexed {} pages", pages.size());
        } catch (Exception e) {
            log.error("Failed to reindex", e);
        }
    }
}
