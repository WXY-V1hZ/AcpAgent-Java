package com.v1hz.acpagent.constants.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SessionUpdateEnum {
    TOOL_CALL("tool_call"),
    TOOL_UPDATE("tool_update"),
    ;
    final String value;
}
