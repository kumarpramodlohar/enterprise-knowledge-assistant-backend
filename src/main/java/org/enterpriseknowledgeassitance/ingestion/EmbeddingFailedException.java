package org.enterpriseknowledgeassitance.ingestion;

/** Safe failure from the embedding provider or an incompatible embedding; carries no document content. */
public class EmbeddingFailedException extends Exception {

    public EmbeddingFailedException(String code, Throwable cause) {
        super(code, cause);
    }
}
