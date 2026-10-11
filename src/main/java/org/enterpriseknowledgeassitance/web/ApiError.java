package org.enterpriseknowledgeassitance.web;

/** Consistent error envelope. Never carries protected resource content. */
public record ApiError(String code, String message, String correlationId) {}
