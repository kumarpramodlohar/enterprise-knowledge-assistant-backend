package org.enterpriseknowledgeassitance.ingestion;

import java.util.UUID;

public record SearchHit(UUID chunkId, UUID documentId, UUID collectionId, String fileName, Integer pageNumber,
        String section, String text, double score) {}
