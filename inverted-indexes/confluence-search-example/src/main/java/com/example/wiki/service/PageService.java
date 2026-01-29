package com.example.wiki.service;

import com.example.wiki.dto.PageCreateRequest;
import com.example.wiki.dto.PageUpdateRequest;
import com.example.wiki.model.Label;
import com.example.wiki.model.Page;
import com.example.wiki.model.PageBody;
import com.example.wiki.model.Space;
import com.example.wiki.repository.PageRepository;
import com.example.wiki.search.AsyncIndexer;
import com.example.wiki.storage.StorageFormatBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageService {

    private final PageRepository pageRepository;
    private final SpaceService spaceService;
    private final LabelService labelService;
    private final AsyncIndexer asyncIndexer;
    private final StorageFormatBuilder storageFormatBuilder;

    @Transactional
    public Page createPage(PageCreateRequest request) {
        Space space = spaceService.getSpace(request.getSpaceKey());

        // Check for duplicate title in space
        pageRepository.findBySpaceSpaceKeyAndTitleAndContentStatus(
            request.getSpaceKey(), request.getTitle(), "current"
        ).ifPresent(p -> {
            throw new IllegalArgumentException(
                "Page with title '" + request.getTitle() + "' already exists in space " + request.getSpaceKey()
            );
        });

        // Build page
        Page page = Page.builder()
            .title(request.getTitle())
            .contentType(request.getContentType() != null ? request.getContentType() : "PAGE")
            .space(space)
            .creator(request.getCreator())
            .lastModifier(request.getCreator())
            .creationDate(LocalDateTime.now())
            .lastModDate(LocalDateTime.now())
            .version(1)
            .contentStatus("current")
            .build();

        // Set parent if specified
        if (request.getParentId() != null) {
            Page parent = getPage(request.getParentId());
            page.setParent(parent);
        }

        // Build body content
        String bodyContent = request.getBody();
        if (bodyContent != null && !request.isStorageFormat()) {
            // Convert plain text to storage format
            bodyContent = storageFormatBuilder.fromPlainText(bodyContent);
        }

        PageBody body = PageBody.builder()
            .body(bodyContent != null ? bodyContent : "")
            .bodyType(2) // XHTML storage format
            .build();
        page.setBody(body);

        // Set labels
        if (request.getLabels() != null && !request.getLabels().isEmpty()) {
            Set<Label> labels = labelService.getOrCreateLabels(request.getLabels());
            page.setLabels(labels);
        }

        page = pageRepository.save(page);
        log.info("Created page: {} in space {} (ID: {})", page.getTitle(), request.getSpaceKey(), page.getContentId());

        // Queue for indexing
        asyncIndexer.queueIndex(page);

        return page;
    }

    @Transactional
    public Page updatePage(Long pageId, PageUpdateRequest request) {
        Page page = getPageWithDetails(pageId);

        // Update title if provided
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            page.setTitle(request.getTitle());
        }

        // Update body if provided
        if (request.getBody() != null) {
            String bodyContent = request.getBody();
            if (!request.isStorageFormat()) {
                bodyContent = storageFormatBuilder.fromPlainText(bodyContent);
            }
            page.getBody().setBody(bodyContent);
        }

        // Update labels if provided
        if (request.getLabels() != null) {
            Set<Label> labels = labelService.getOrCreateLabels(request.getLabels());
            page.setLabels(labels);
        }

        // Update metadata
        page.setVersion(page.getVersion() + 1);
        page.setLastModifier(request.getModifier());
        page.setLastModDate(LocalDateTime.now());
        page.setVersionComment(request.getVersionComment());

        page = pageRepository.save(page);
        log.info("Updated page: {} (version {})", page.getTitle(), page.getVersion());

        // Queue for re-indexing
        asyncIndexer.queueIndex(page);

        return page;
    }

    public Page getPage(Long pageId) {
        return pageRepository.findById(pageId)
            .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));
    }

    public Page getPageWithDetails(Long pageId) {
        return pageRepository.findByIdWithBodyAndLabels(pageId)
            .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));
    }

    public List<Page> getPagesBySpace(String spaceKey) {
        return pageRepository.findBySpaceSpaceKeyAndContentStatus(spaceKey, "current");
    }

    public List<Page> getChildPages(Long parentId) {
        Page parent = getPage(parentId);
        return pageRepository.findByParentAndContentStatus(parent, "current");
    }

    public List<Page> getAllCurrentPages() {
        return pageRepository.findAllCurrentPages();
    }

    public List<Page> getAllCurrentPagesWithDetails() {
        return pageRepository.findAllCurrentPagesWithDetails();
    }

    @Transactional
    public void deletePage(Long pageId) {
        Page page = getPage(pageId);
        page.setContentStatus("deleted");
        page.setLastModDate(LocalDateTime.now());
        pageRepository.save(page);
        log.info("Deleted page: {} (ID: {})", page.getTitle(), pageId);

        // Queue for removal from index
        asyncIndexer.queueDelete(pageId);
    }

    @Transactional
    public void permanentlyDeletePage(Long pageId) {
        Page page = getPage(pageId);
        pageRepository.delete(page);
        log.info("Permanently deleted page: {} (ID: {})", page.getTitle(), pageId);

        // Queue for removal from index
        asyncIndexer.queueDelete(pageId);
    }

    public long countCurrentPages() {
        return pageRepository.countCurrentPages();
    }
}
