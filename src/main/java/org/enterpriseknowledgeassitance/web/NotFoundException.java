package org.enterpriseknowledgeassitance.web;

/** Used for both missing and inaccessible resources so existence is not disclosed (404 policy). */
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super("Resource not found");
    }
}
