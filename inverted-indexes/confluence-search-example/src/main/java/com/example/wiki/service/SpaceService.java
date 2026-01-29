package com.example.wiki.service;

import com.example.wiki.dto.SpaceCreateRequest;
import com.example.wiki.model.Space;
import com.example.wiki.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceService {

    private final SpaceRepository spaceRepository;

    @Transactional
    public Space createSpace(SpaceCreateRequest request) {
        if (spaceRepository.existsBySpaceKey(request.getSpaceKey())) {
            throw new IllegalArgumentException("Space with key '" + request.getSpaceKey() + "' already exists");
        }

        Space space = Space.builder()
            .spaceKey(request.getSpaceKey())
            .spaceName(request.getSpaceName())
            .description(request.getDescription())
            .spaceType(request.getSpaceType())
            .creator(request.getCreator())
            .creationDate(LocalDateTime.now())
            .build();

        space = spaceRepository.save(space);
        log.info("Created space: {} ({})", space.getSpaceName(), space.getSpaceKey());
        return space;
    }

    public Space getSpace(String spaceKey) {
        return spaceRepository.findBySpaceKey(spaceKey)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceKey));
    }

    public Space getSpaceById(Long spaceId) {
        return spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
    }

    public List<Space> getAllSpaces() {
        return spaceRepository.findAll();
    }

    @Transactional
    public Space updateSpace(String spaceKey, String spaceName, String description) {
        Space space = getSpace(spaceKey);
        if (spaceName != null) {
            space.setSpaceName(spaceName);
        }
        if (description != null) {
            space.setDescription(description);
        }
        return spaceRepository.save(space);
    }

    @Transactional
    public void deleteSpace(String spaceKey) {
        Space space = getSpace(spaceKey);
        spaceRepository.delete(space);
        log.info("Deleted space: {}", spaceKey);
    }

    public boolean exists(String spaceKey) {
        return spaceRepository.existsBySpaceKey(spaceKey);
    }
}
