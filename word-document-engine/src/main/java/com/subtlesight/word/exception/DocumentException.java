package com.subtlesight.word.exception;

import com.subtlesight.word.model.enums.ErrorCode;

/**
 * 文档操作异常基类，包含结构化错误信息。
 * <p>
 * 对应 PRD 7.3.E 节结构化错误，包含对用户或调用方可执行的建议。
 * </p>
 */
public class DocumentException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String suggestion;

    public DocumentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.suggestion = null;
    }

    public DocumentException(ErrorCode errorCode, String message, String suggestion) {
        super(message);
        this.errorCode = errorCode;
        this.suggestion = suggestion;
    }

    public DocumentException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestion = null;
    }

    public DocumentException(ErrorCode errorCode, String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestion = suggestion;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getSuggestion() {
        return suggestion;
    }

    @Override
    public String toString() {
        return "DocumentException{" +
                "errorCode=" + errorCode +
                ", message=" + getMessage() +
                ", suggestion='" + suggestion + '\'' +
                '}';
    }
}