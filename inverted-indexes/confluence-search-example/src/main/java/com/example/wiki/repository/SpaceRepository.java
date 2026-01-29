package com.example.wiki.repository;

import com.example.wiki.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {

    Optional<Space> findBySpaceKey(String spaceKey);

    boolean existsBySpaceKey(String spaceKey);
}
