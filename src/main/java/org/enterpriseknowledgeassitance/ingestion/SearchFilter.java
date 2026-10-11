package org.enterpriseknowledgeassitance.ingestion;

import java.util.List;

/** Optional narrowing; null fields are ignored. Tags match when any tag overlaps. */
public record SearchFilter(String version, String documentType, List<String> tags) {
    public static final SearchFilter NONE = new SearchFilter(null, null, null);
}
