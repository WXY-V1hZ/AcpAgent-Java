package com.v1hz.acpagent.constants;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.v1hz.acpagent.constants.enums.PermissionOptionEnum;
import com.v1hz.acpagent.constants.enums.SessionModeEnum;
import com.v1hz.acpagent.constants.enums.SessionModelEnum;

import java.util.Arrays;
import java.util.List;

public class AcpConstants {

    // 模式
    public static final String DEFAULT_SESSION_MODE_ID = SessionModeEnum.ASK.getId();
    public static final List<AcpSchema.SessionMode> MODES = Arrays.stream(SessionModeEnum.values())
            .map(SessionModeEnum::toSessionMode)
            .toList();

    // 模型
    public static final String DEFAULT_SESSION_MODEL_ID = SessionModelEnum.LITE.getId();
    public static final List<AcpSchema.ModelInfo> MODELS = Arrays.stream(SessionModelEnum.values())
            .map(SessionModelEnum::toModelInfo)
            .toList();

    public static final List<AcpSchema.PermissionOption> PERMISSION_OPTIONS = Arrays.stream(PermissionOptionEnum.values())
            .map(PermissionOptionEnum::toPermissionOption)
            .toList();
}
