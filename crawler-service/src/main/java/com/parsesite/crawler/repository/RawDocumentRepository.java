package com.parsesite.crawler.repository;

import com.parsesite.common.entity.RawDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RawDocumentRepository extends JpaRepository<RawDocument, Long> {
    Optional<RawDocument> findTopByUrlOrderByFetchedAtDesc(String url);
}
