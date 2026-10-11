package org.enterpriseknowledgeassitance.api;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Optional narrowing only; filters never widen access beyond the caller's permissions. */
public record ChatFilters(
        @Size(max = ApiLimits.MAX_FILTER_COLLECTIONS) List<UUID> collectionIds,
        @Size(max = ApiLimits.MAX_FILTER_TEXT_LENGTH) String version,
        @Size(max = ApiLimits.MAX_FILTER_TEXT_LENGTH) String documentType,
        @Size(max = ApiLimits.MAX_FILTER_TAGS) List<@Size(max = ApiLimits.MAX_FILTER_TAG_LENGTH) String> tags) {}
