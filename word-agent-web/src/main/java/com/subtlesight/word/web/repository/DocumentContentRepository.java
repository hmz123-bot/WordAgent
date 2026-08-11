package com.subtlesight.word.web.repository;

import com.subtlesight.word.web.entity.DocumentContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentContentRepository extends JpaRepository<DocumentContentEntity, Long> {

    Optional<DocumentContentEntity> findByDocumentIdAndContentTypeAndVersionNumber(
            String documentId, String contentType, Integer versionNumber);

    /**
     * 查找某文档某类型的所有内容（如所有 RAW 快照）
     */
    List<DocumentContentEntity> findByDocumentIdAndContentType(String documentId, String contentType);

    void deleteByDocumentId(String documentId);
}
