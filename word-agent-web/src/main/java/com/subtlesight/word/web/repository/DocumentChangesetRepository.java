package com.subtlesight.word.web.repository;

import com.subtlesight.word.web.entity.DocumentChangesetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChangesetRepository extends JpaRepository<DocumentChangesetEntity, Long> {

    Optional<DocumentChangesetEntity> findByChangesetId(String changesetId);

    List<DocumentChangesetEntity> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);

    void deleteByChangesetId(String changesetId);
}
