package com.v1hz.acpagent.constants.enums;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionOptionEnum {
    ALLOW_ONCE("allow_once", "允许一次", AcpSchema.PermissionOptionKind.ALLOW_ONCE),
    ALLOW_ALWAYS("allow_always", "总是允许", AcpSchema.PermissionOptionKind.ALLOW_ALWAYS),
    REJECT_ONCE("reject_once", "拒绝", AcpSchema.PermissionOptionKind.REJECT_ONCE),
    REJECT_ALWAYS("reject_always", "总是拒绝", AcpSchema.PermissionOptionKind.REJECT_ALWAYS),
    ;

    private final String id;
    private final String name;
    private final AcpSchema.PermissionOptionKind kind;

    public AcpSchema.PermissionOption toPermissionOption() {
        return new AcpSchema.PermissionOption(id, name, kind);
    }
}
