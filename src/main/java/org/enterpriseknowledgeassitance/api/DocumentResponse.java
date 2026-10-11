package org.enterpriseknowledgeassitance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID collectionId,
        String fileName,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String failureReason,
        String version,
        String documentType,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {}
