package org.enterpriseknowledgeassitance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQueryRequest(
        @NotBlank @Size(max = MAX_QUESTION_LENGTH) String question,
        @Valid ChatFilters filters,
        Boolean allowExternalSearch) {

    public ChatQueryRequest {
        allowExternalSearch = allowExternalSearch == null ? false : allowExternalSearch;
    }

    public ChatQueryRequest(String question, ChatFilters filters) {
        this(question, filters, false);
    }

    public static final int MAX_QUESTION_LENGTH = 2000;
}
