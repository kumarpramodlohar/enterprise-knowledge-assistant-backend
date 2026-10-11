package org.enterpriseknowledgeassitance.api;

import java.util.UUID;

/** Returned with 202 Accepted; poll {@code statusUrl} until the document is INDEXED or FAILED. */
public record UploadAcceptedResponse(UUID documentId, DocumentStatus status, String statusUrl) {}
