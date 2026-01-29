package com.example.wiki.service;

import com.example.wiki.model.Label;
import com.example.wiki.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;

    /**
     * Gets or creates labels by name.
     */
    @Transactional
    public Set<Label> getOrCreateLabels(List<String> labelNames) {
        Set<Label> labels = new HashSet<>();

        if (labelNames == null || labelNames.isEmpty()) {
            return labels;
        }

        for (String name : labelNames) {
            String normalizedName = name.toLowerCase().trim();
            if (normalizedName.isBlank()) {
                continue;
            }

            Label label = labelRepository.findByName(normalizedName)
                .orElseGet(() -> {
                    Label newLabel = Label.builder()
                        .name(normalizedName)
                        .build();
                    return labelRepository.save(newLabel);
                });

            labels.add(label);
        }

        return labels;
    }

    public Label getLabel(String name) {
        return labelRepository.findByName(name.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Label not found: " + name));
    }

    public List<Label> getAllLabels() {
        return labelRepository.findAll();
    }
}
