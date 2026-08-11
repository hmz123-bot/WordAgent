package com.subtlesight.word.model.enums;

/**
 * 结构化错误码，对应 PRD 7.3.E 节。
 */
public enum ErrorCode {
    NOT_FOUND,
    INVALID_ANCHOR,
    INVALID_VALUE,
    UNSUPPORTED_OPERATION,
    VERSION_CONFLICT,
    VALIDATION_FAILED,
    FILE_CORRUPTED,
    ACCESS_DENIED,
    FILE_TOO_LARGE,
    MALICIOUS_CONTENT
}