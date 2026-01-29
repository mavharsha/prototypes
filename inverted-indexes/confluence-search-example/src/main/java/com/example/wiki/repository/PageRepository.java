package com.example.wiki.repository;

import com.example.wiki.model.Page;
import com.example.wiki.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findBySpaceAndContentStatus(Space space, String contentStatus);

    List<Page> findBySpaceSpaceKeyAndContentStatus(String spaceKey, String contentStatus);

    Optional<Page> findBySpaceSpaceKeyAndTitleAndContentStatus(String spaceKey, String title, String contentStatus);

    List<Page> findByParentAndContentStatus(Page parent, String contentStatus);

    @Query("SELECT p FROM Page p WHERE p.contentStatus = 'current'")
    List<Page> findAllCurrentPages();

    @Query("SELECT p FROM Page p LEFT JOIN FETCH p.body LEFT JOIN FETCH p.labels WHERE p.contentId = :id")
    Optional<Page> findByIdWithBodyAndLabels(@Param("id") Long id);

    @Query("SELECT p FROM Page p LEFT JOIN FETCH p.body LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.space WHERE p.contentStatus = 'current'")
    List<Page> findAllCurrentPagesWithDetails();

    @Query("SELECT COUNT(p) FROM Page p WHERE p.contentStatus = 'current'")
    long countCurrentPages();
}
