package org.enterpriseknowledgeassitance.extraction;

/** Safe, recordable processing failure. The reason is a stable code and never includes document content. */
public class ExtractionException extends Exception {

    public enum Reason {
        UNSUPPORTED_TYPE, CONTENT_MISMATCH, CORRUPT, EMPTY, TOO_LARGE, ENCRYPTED,
        PDF_PAGE_LIMIT_EXCEEDED, EXTRACTED_CONTENT_TOO_LARGE
    }

    private final Reason reason;

    public ExtractionException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public ExtractionException(Reason reason) {
        this(reason, null);
    }

    public Reason reason() {
        return reason;
    }
}
