package com.v1hz.acpagent.constants.enums;

import com.agentclientprotocol.sdk.spec.AcpSchema.ModelInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.NonNull;

import java.util.Arrays;
import java.util.Optional;

@Getter
@AllArgsConstructor
public enum SessionModelEnum {

    LITE("lite", "deepseek-v4-flash", "lite model"),
    PRO("pro", "deepseek-v4-flash", "pro model"),
    MAX("max", "deepseek-v4-pro", "max model");

    private final String id;
    private final String modelName;
    private final String description;

    public static boolean isValid(String id) {
        return Arrays.stream(values()).anyMatch(m -> m.id.equals(id));
    }

    @NonNull
    public static Optional<SessionModelEnum> fromId(@NonNull String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }

    public ModelInfo toModelInfo() {
        return new ModelInfo(id, modelName, description);
    }
}
