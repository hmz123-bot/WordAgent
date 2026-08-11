package com.subtlesight.word.web.repository;

import com.subtlesight.word.web.entity.DocumentChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChangeRepository extends JpaRepository<DocumentChangeEntity, Long> {

    Optional<DocumentChangeEntity> findByChangeId(String changeId);

    List<DocumentChangeEntity> findByChangeset_ChangesetId(String changesetId);

    void deleteByChangeset_ChangesetId(String changesetId);
}
