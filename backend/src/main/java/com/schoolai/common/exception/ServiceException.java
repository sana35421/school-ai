package com.schoolai.common.exception;

import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {
    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }
}