package com.optiplant.common.exception;

/**
 * Expected business or authorization failure carrying its HTTP status, stable error code and user-facing detail.
 * {@link ApiErrors} turns it into the response.
 */
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
