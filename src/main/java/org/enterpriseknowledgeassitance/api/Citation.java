package org.enterpriseknowledgeassitance.api;

import java.util.UUID;

/** Built from trusted application metadata, never from model-generated identifiers. */
public record Citation(
        UUID documentId,
        String fileName,
        UUID chunkId,
        Integer pageNumber,
        String section,
        String snippet) {}
