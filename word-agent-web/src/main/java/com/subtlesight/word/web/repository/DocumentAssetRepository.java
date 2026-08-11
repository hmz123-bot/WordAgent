package com.subtlesight.word.web.repository;

import com.subtlesight.word.web.entity.DocumentAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAssetRepository extends JpaRepository<DocumentAssetEntity, Long> {

    Optional<DocumentAssetEntity> findByDocumentId(String documentId);

    List<DocumentAssetEntity> findByStatus(String status);

    void deleteByDocumentId(String documentId);
}
