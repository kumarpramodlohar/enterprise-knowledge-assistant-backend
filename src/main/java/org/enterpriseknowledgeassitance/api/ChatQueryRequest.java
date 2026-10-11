package org.enterpriseknowledgeassitance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQueryRequest(
        @NotBlank @Size(max = MAX_QUESTION_LENGTH) String question,
        @Valid ChatFilters filters) {

    public static final int MAX_QUESTION_LENGTH = 2000;
}
