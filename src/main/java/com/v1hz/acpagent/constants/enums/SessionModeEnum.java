package com.v1hz.acpagent.constants.enums;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.NonNull;

import java.util.Arrays;
import java.util.Optional;

@Getter
@AllArgsConstructor
public enum SessionModeEnum {

    DEFAULT("default", "只通过允许的请求"),
    AUTO("auto", "自动通过所有请求"),
    // TODO 实现计划模式
//    PLAN("plan", "撰写计划"),
    ;

    private final String id;
    private final String description;

    public static boolean isValid(String id) {
        return Arrays.stream(values()).anyMatch(m -> m.id.equals(id));
    }

    @NonNull
    public static Optional<SessionModeEnum> fromId(@NonNull String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }

    public AcpSchema.SessionMode toSessionMode() {
        return new AcpSchema.SessionMode(id, id, description);
    }
}
