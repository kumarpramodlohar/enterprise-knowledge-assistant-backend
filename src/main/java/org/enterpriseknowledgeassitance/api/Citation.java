package org.enterpriseknowledgeassitance.api;

import java.util.UUID;

/** Built from trusted application metadata, never from model-generated identifiers. */
public record Citation(
        UUID documentId,
        String fileName,
        UUID chunkId,
        Integer pageNumber,
        String section,
        String snippet,
        SourceType sourceType,
        String title,
        String url) {

    public Citation(UUID documentId, String fileName, UUID chunkId, Integer pageNumber,
            String section, String snippet) {
        this(documentId, fileName, chunkId, pageNumber, section, snippet, SourceType.INTERNAL, null, null);
    }

    public static Citation external(String title, String url, String snippet) {
        return new Citation(null, null, null, null, null, snippet, SourceType.EXTERNAL, title, url);
    }

    public enum SourceType { INTERNAL, EXTERNAL }
}
