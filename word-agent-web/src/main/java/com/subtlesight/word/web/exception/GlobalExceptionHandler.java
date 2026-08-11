package com.subtlesight.word.web.exception;

import com.subtlesight.word.exception.DocumentException;
import com.subtlesight.word.exception.VersionConflictException;
import com.subtlesight.word.web.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一返回 ApiResponse 格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentException(DocumentException e) {
        int httpStatus;
        switch (e.getErrorCode()) {
            case NOT_FOUND:
                httpStatus = 404;
                break;
            case VERSION_CONFLICT:
                httpStatus = 409;
                break;
            case INVALID_ANCHOR:
            case INVALID_VALUE:
            case UNSUPPORTED_OPERATION:
                httpStatus = 400;
                break;
            case VALIDATION_FAILED:
            case FILE_CORRUPTED:
            case MALICIOUS_CONTENT:
                httpStatus = 422;
                break;
            case ACCESS_DENIED:
                httpStatus = 403;
                break;
            case FILE_TOO_LARGE:
                httpStatus = 413;
                break;
            default:
                httpStatus = 500;
        }
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(httpStatus, e.getMessage()));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionConflict(VersionConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, "文件大小超出限制"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("未预期的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误: " + e.getMessage()));
    }
}