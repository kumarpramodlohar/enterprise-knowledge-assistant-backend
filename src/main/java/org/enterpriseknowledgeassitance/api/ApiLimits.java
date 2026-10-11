package org.enterpriseknowledgeassitance.api;

/** Size limits for chat filters (bean-validation annotations need compile-time constants). */
public final class ApiLimits {
    public static final int MAX_FILTER_COLLECTIONS = 50;
    public static final int MAX_FILTER_TEXT_LENGTH = 100;
    public static final int MAX_FILTER_TAGS = 20;
    public static final int MAX_FILTER_TAG_LENGTH = 50;

    private ApiLimits() {}
}
