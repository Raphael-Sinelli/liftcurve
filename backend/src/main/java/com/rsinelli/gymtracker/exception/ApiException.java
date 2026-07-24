package com.rsinelli.gymtracker.exception;

import jakarta.ws.rs.core.Response;

public class ApiException extends RuntimeException {

    private final String code;
    private final Response.Status status;

    public ApiException(String code, String message, Response.Status status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public Response.Status getStatus() {
        return status;
    }
}
