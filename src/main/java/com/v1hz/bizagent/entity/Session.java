package com.v1hz.bizagent.entity;

import com.v1hz.bizagent.constants.enums.SessionStatusEnum;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @NonNull
    private String sessionId;
    @NonNull
    private String title;
    @NonNull
    private String cwd;
    @NonNull
    private SessionStatusEnum status;
    @NonNull
    private String modeId;
    @NonNull
    private String modelId;
    @NonNull
    private LocalDateTime createdAt;
    @NonNull
    private LocalDateTime updatedAt;
}
