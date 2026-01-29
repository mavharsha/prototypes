package com.example.wiki.controller;

import com.example.wiki.dto.PageCreateRequest;
import com.example.wiki.dto.PageUpdateRequest;
import com.example.wiki.model.Page;
import com.example.wiki.service.PageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Page createPage(@Valid @RequestBody PageCreateRequest request) {
        return pageService.createPage(request);
    }

    @GetMapping("/{pageId}")
    public Page getPage(@PathVariable Long pageId) {
        return pageService.getPageWithDetails(pageId);
    }

    @GetMapping("/{pageId}/storage-format")
    public String getStorageFormat(@PathVariable Long pageId) {
        Page page = pageService.getPageWithDetails(pageId);
        return page.getBody() != null ? page.getBody().getBody() : "";
    }

    @PutMapping("/{pageId}")
    public Page updatePage(@PathVariable Long pageId, @RequestBody PageUpdateRequest request) {
        return pageService.updatePage(pageId, request);
    }

    @DeleteMapping("/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePage(@PathVariable Long pageId) {
        pageService.deletePage(pageId);
    }

    @GetMapping("/space/{spaceKey}")
    public List<Page> getPagesBySpace(@PathVariable String spaceKey) {
        return pageService.getPagesBySpace(spaceKey);
    }

    @GetMapping("/{pageId}/children")
    public List<Page> getChildPages(@PathVariable Long pageId) {
        return pageService.getChildPages(pageId);
    }
}
