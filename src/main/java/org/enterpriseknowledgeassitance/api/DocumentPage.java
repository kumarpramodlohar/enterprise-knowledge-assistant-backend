package org.enterpriseknowledgeassitance.api;

import java.util.List;

public record DocumentPage(List<DocumentResponse> items, int page, int size, long totalItems) {}
