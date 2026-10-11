package org.enterpriseknowledgeassitance.api;

/** Versioned API route prefixes shared by controllers and response links. */
public final class ApiPaths {
    public static final String BASE = "/api/v1";
    public static final String DOCUMENTS = BASE + "/documents";
    public static final String CHAT_QUERY = BASE + "/chat/query";
    public static final String ME = BASE + "/me";

    private ApiPaths() {}
}
