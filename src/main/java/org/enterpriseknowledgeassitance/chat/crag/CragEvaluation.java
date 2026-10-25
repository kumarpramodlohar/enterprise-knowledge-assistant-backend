package org.enterpriseknowledgeassitance.chat.crag;

import org.enterpriseknowledgeassitance.ingestion.SearchHit;

public record CragEvaluation(SearchHit hit, Verdict verdict) {
    public enum Verdict { CORRECT, AMBIGUOUS, INCORRECT }
}
