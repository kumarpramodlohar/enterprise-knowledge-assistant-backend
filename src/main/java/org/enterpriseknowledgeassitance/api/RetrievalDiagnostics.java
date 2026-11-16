package org.enterpriseknowledgeassitance.api;

import java.util.List;

public record RetrievalDiagnostics(List<RetrievalDiagnostic> candidates) {
    public RetrievalDiagnostics {
        candidates = List.copyOf(candidates);
    }
}