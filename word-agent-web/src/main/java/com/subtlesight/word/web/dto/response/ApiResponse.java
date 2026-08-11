package com.subtlesight.word.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 统一 API 响应格式。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.code = 200;
        resp.message = "success";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> resp = ok(data);
        resp.message = message;
        return resp;
    }

    public static <T> ApiResponse<T> created(T data) {
        ApiResponse<T> resp = ok(data);
        resp.code = 201;
        resp.message = "created";
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.code = code;
        resp.message = message;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        ApiResponse<T> resp = error(code, message);
        resp.data = data;
        return resp;
    }

    // ====== Getters ======

    public boolean isSuccess() { return success; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public long getTimestamp() { return timestamp; }
}