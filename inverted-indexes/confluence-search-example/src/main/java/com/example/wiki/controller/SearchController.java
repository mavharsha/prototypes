package com.example.wiki.controller;

import com.example.wiki.dto.SearchRequest;
import com.example.wiki.dto.SearchResult;
import com.example.wiki.search.WikiSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final WikiSearchService searchService;

    /**
     * Simple GET search with query parameters.
     */
    @GetMapping
    public SearchResult search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String spaceKey,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> labels,
            @RequestParam(required = false) String creator,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .spaceKey(spaceKey)
            .contentType(type)
            .labels(labels)
            .creator(creator)
            .sort(sort)
            .order(order)
            .page(page)
            .size(size)
            .build();

        return searchService.search(request);
    }

    /**
     * Advanced POST search with full request body.
     */
    @PostMapping
    public SearchResult searchAdvanced(@RequestBody SearchRequest request) throws IOException {
        return searchService.search(request);
    }
}
