package org.enterpriseknowledgeassitance.web;

import org.springframework.http.HttpStatus;

/** Expected, client-visible failure with a stable error code and a message that is safe to disclose. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
