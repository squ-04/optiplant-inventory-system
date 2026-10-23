package com.optiplant.common.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
