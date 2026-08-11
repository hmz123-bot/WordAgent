package com.subtlesight.word.exception;

import com.subtlesight.word.model.enums.ErrorCode;

/**
 * 版本冲突异常，当变更集预期版本与当前文档版本不一致时抛出。
 * <p>
 * 对应 PRD 7.3.B 节：版本不一致返回 version_conflict，
 * 并附当前版本与可重新读取的节点信息。
 * </p>
 */
public class VersionConflictException extends DocumentException {

    private final int expectedVersion;
    private final int currentVersion;
    private final String documentId;

    public VersionConflictException(String documentId, int expectedVersion, int currentVersion) {
        super(
                ErrorCode.VERSION_CONFLICT,
                "版本冲突：期望版本 " + expectedVersion + "，当前版本 " + currentVersion,
                "请重新读取文档最新版本（版本 " + currentVersion + "）后重试提交。"
        );
        this.documentId = documentId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public String getDocumentId() {
        return documentId;
    }
}