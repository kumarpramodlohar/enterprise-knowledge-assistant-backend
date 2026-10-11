package org.enterpriseknowledgeassitance.api;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Optional narrowing only; filters never widen access beyond the caller's permissions. */
public record ChatFilters(
        @Size(max = 50) List<UUID> collectionIds,
        @Size(max = 100) String version,
        @Size(max = 100) String documentType,
        @Size(max = 20) List<@Size(max = 50) String> tags) {}
