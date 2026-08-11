package com.subtlesight.word.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文档导入请求。实际文件内容通过 multipart/form-data 上传，
 * 该 DTO 用于 JSON body 部分的元数据。
 */
public class ImportDocumentRequest {

    @NotBlank(message = "文档名称不能为空")
    private String fileName;

    private String description;

    /** 上传者 ID */
    private String authorId;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}