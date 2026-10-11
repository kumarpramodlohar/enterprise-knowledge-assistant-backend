package org.enterpriseknowledgeassitance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/** Query parameters for GET /api/v1/documents. Page size is bounded to protect the service. */
public record DocumentListQuery(
        UUID collectionId,
        DocumentStatus status,
        String documentType,
        String tag,
        @Min(0) Integer page,
        @Min(1) @Max(MAX_SIZE) Integer size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
