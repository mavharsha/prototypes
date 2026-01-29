package com.example.wiki.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a label/tag that can be attached to pages.
 * Equivalent to Confluence's LABEL table.
 */
@Entity
@Table(name = "labels", indexes = {
    @Index(name = "idx_label_name", columnList = "name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long labelId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String owner;  // User who created the label

    @ManyToMany(mappedBy = "labels", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Page> pages = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Label label)) return false;
        return labelId != null && labelId.equals(label.labelId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
