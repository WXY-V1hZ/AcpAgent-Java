package com.v1hz.acpagent.exception;

import com.v1hz.acpagent.dto.response.ApiResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(Exception e) {
        super(e);
        this.code = ApiResponse.CODE_INTERNAL_ERROR;
    }
}