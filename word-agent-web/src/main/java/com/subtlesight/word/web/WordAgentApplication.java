package com.subtlesight.word.web;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.model.WordDocumentAsset;
import com.subtlesight.word.service.*;
import com.subtlesight.word.service.impl.*;
import com.subtlesight.word.service.search.DocumentSearchService;
import com.subtlesight.word.service.search.DocumentSearchServiceImpl;
import com.subtlesight.word.web.repository.*;
import com.subtlesight.word.web.service.DatabaseWordDocumentService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Word Agent Web 应用主入口 ── 数据库版本。
 * <p>
 * 所有文档数据通过 JPA (H2/MySQL) 持久化，服务器重启后数据不丢失。
 * </p>
 */
@SpringBootApplication
public class WordAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordAgentApplication.class, args);
    }

    @Bean
    public WordDocumentAdapter wordDocumentAdapter() {
        return new PoiDocumentAdapter();
    }

    /**
     * 数据库版文档服务 ── 所有核心操作都通过此类完成。
     */
    @Bean
    public DatabaseWordDocumentService wordDocumentService(
            WordDocumentAdapter adapter,
            DocumentAssetRepository assetRepo,
            DocumentChangesetRepository changesetRepo,
            DocumentChangeRepository changeRepo,
            DocumentVersionRepository versionRepo,
            DocumentContentRepository contentRepo) {
        return new DatabaseWordDocumentService(
                adapter, assetRepo, changesetRepo, changeRepo, versionRepo, contentRepo);
    }

    /**
     * 共享的文档资产映射 ── 与 wordDocumentService 共用一个 Map，
     * 供 ImportService 等需要直接访问文档映射的组件使用。
     */
    @Bean
    public Map<String, WordDocumentAsset> documentStore(DatabaseWordDocumentService wordDocumentService) {
        return wordDocumentService.getDocuments();
    }

    @Bean
    public DocumentImportService documentImportService(
            WordDocumentAdapter adapter,
            Map<String, WordDocumentAsset> documentStore) {
        return new DocumentImportServiceImpl(adapter, documentStore);
    }

    @Bean
    public DocumentExportService documentExportService(
            WordDocumentAdapter adapter) {
        return new DocumentExportServiceImpl(adapter);
    }

    @Bean
    public DocumentValidationService documentValidationService() {
        return new DocumentValidationServiceImpl();
    }

    @Bean
    public DocumentSearchService documentSearchService(
            DatabaseWordDocumentService wordDocumentService,
            WordDocumentAdapter adapter) {
        return new DocumentSearchServiceImpl(
                wordDocumentService.getDocuments(),
                wordDocumentService.getNodeTrees(),
                wordDocumentService.getRawContents(),
                adapter);
    }
}
