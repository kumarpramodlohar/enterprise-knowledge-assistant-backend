package org.enterpriseknowledgeassitance.web;

/** Stable machine-readable error codes returned in the error envelope (see api-contract.md). */
public final class ErrorCodes {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String REQUEST_REJECTED = "REQUEST_REJECTED";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";
    public static final String UNPROCESSABLE_DOCUMENT = "UNPROCESSABLE_DOCUMENT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String EMBEDDING_PROVIDER_FAILED = "EMBEDDING_PROVIDER_FAILED";
    public static final String EMBEDDING_DIMENSION_MISMATCH = "EMBEDDING_DIMENSION_MISMATCH";

    private ErrorCodes() {}
}
