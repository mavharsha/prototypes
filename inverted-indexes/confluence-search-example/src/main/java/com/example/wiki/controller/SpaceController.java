package com.example.wiki.controller;

import com.example.wiki.dto.SpaceCreateRequest;
import com.example.wiki.model.Space;
import com.example.wiki.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Space createSpace(@Valid @RequestBody SpaceCreateRequest request) {
        return spaceService.createSpace(request);
    }

    @GetMapping
    public List<Space> getAllSpaces() {
        return spaceService.getAllSpaces();
    }

    @GetMapping("/{spaceKey}")
    public Space getSpace(@PathVariable String spaceKey) {
        return spaceService.getSpace(spaceKey);
    }

    @PutMapping("/{spaceKey}")
    public Space updateSpace(
            @PathVariable String spaceKey,
            @RequestParam(required = false) String spaceName,
            @RequestParam(required = false) String description) {
        return spaceService.updateSpace(spaceKey, spaceName, description);
    }

    @DeleteMapping("/{spaceKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpace(@PathVariable String spaceKey) {
        spaceService.deleteSpace(spaceKey);
    }
}
