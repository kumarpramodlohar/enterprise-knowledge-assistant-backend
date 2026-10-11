package org.enterpriseknowledgeassitance.documents;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Content-free audit trail of lifecycle operations. */
@Component
public class DocumentAudit {

    private static final String INSERT = """
            INSERT INTO document_audit (document_id, collection_id, action, outcome, actor)
            VALUES (:d, :c, :action, :outcome, :actor)""";

    private final JdbcClient jdbc;

    DocumentAudit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID documentId, UUID collectionId, String action, String outcome, String actor) {
        jdbc.sql(INSERT).param("d", documentId).param("c", collectionId).param("action", action)
                .param("outcome", outcome).param("actor", actor).update();
    }
}
