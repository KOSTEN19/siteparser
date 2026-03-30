package com.parsesite.indexer.repository;

import com.parsesite.common.entity.RawDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawDocumentRepository extends JpaRepository<RawDocument, Long> {
}
