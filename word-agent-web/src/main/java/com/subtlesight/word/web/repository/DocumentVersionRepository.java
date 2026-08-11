package com.subtlesight.word.web.repository;

import com.subtlesight.word.web.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, Long> {

    List<DocumentVersionEntity> findByDocumentIdOrderByVersionNumberDesc(String documentId);

    List<DocumentVersionEntity> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
