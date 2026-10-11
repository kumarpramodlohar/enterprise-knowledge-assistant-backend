package org.enterpriseknowledgeassitance.ingestion;

import java.util.UUID;

/**
 * @param id deterministic: identical document id, position and text always yield the same id
 * @param startOffset character offset of the chunk start in the concatenated extracted text
 */
public record Chunk(UUID id, UUID documentId, int index, String text, Integer pageNumber, String section,
        int startOffset) {}
