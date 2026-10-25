package org.enterpriseknowledgeassitance.chat.crag;

/** Safe marker for a CRAG provider or response-validation failure. */
public final class CragStageException extends RuntimeException {
    private final String stage;

    public CragStageException(String stage) {
        super("CRAG stage failed");
        this.stage = stage;
    }

    public CragStageException(String stage, Throwable cause) {
        super("CRAG stage failed", cause);
        this.stage = stage;
    }

    public String stage() {
        return stage;
    }
}
