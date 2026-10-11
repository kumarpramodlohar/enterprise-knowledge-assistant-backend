package org.enterpriseknowledgeassitance.api;

import java.util.List;

/** {@code answer} is null when {@code insufficientEvidence} is true. */
public record ChatQueryResponse(
        String answer,
        boolean insufficientEvidence,
        List<Citation> citations,
        String correlationId,
        long timingMs,
        long retrievalMs) {}
