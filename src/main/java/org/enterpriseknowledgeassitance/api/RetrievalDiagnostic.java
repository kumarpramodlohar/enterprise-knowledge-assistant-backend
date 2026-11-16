package org.enterpriseknowledgeassitance.api;

import java.util.UUID;

public record RetrievalDiagnostic(UUID chunkId, int rank, double similarityScore) {}