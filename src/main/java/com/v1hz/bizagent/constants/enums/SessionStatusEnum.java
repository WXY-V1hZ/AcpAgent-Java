package com.v1hz.bizagent.constants.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SessionStatusEnum {

    ACTIVE("ACTIVE"),
    CLOSED("CLOSED");

    private final String value;
}
