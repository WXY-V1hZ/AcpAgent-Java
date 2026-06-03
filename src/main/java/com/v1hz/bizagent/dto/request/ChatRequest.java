package com.v1hz.bizagent.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.List;

@Data
public class ChatRequest {

    @NotBlank
    private String message;

    @Nullable
    private List<Attachment> attachments;

    @NotBlank
    private String sessionId;

    @Data
    public static class Attachment {
        private String type;         // "image", "file", "video"
        private String mimeType;     // "image/png", "application/pdf"
        private String path;         // 文件路径
    }
}
