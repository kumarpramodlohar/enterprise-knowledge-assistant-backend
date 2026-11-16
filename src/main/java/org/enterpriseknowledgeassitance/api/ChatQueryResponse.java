package org.enterpriseknowledgeassitance.api;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code answer} is null when {@code insufficientEvidence} is true. */
public record ChatQueryResponse(
        String answer,
        boolean insufficientEvidence,
        List<Citation> citations,
        String correlationId,
        long timingMs,
                long retrievalMs,
                @JsonInclude(JsonInclude.Include.NON_NULL) RetrievalDiagnostics retrievalDiagnostics) {

        public ChatQueryResponse(String answer, boolean insufficientEvidence, List<Citation> citations,
                        String correlationId, long timingMs, long retrievalMs) {
                this(answer, insufficientEvidence, citations, correlationId, timingMs, retrievalMs, null);
        }
}
