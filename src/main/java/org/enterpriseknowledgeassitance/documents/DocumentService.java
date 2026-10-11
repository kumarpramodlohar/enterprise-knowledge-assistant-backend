package org.enterpriseknowledgeassitance.documents;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.api.DocumentListQuery;
import org.enterpriseknowledgeassitance.api.DocumentPage;
import org.enterpriseknowledgeassitance.api.DocumentResponse;
import org.enterpriseknowledgeassitance.api.DocumentStatus;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.security.AccessService;
import org.enterpriseknowledgeassitance.web.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Document listing, status, re-index and soft deletion. Inaccessible and deleted documents are 404. */
@Service
public class DocumentService {

    private static final String COLUMNS = """
            id, collection_id, file_name, content_type, size_bytes, status, failure_reason, version,
            document_type, tags, created_at, updated_at""";

    private static final String VISIBLE = """
            deleted_at IS NULL AND collection_id IN (:cols)
              AND (CAST(:collection AS uuid) IS NULL OR collection_id = :collection)
              AND (CAST(:status AS text) IS NULL OR status = :status)
              AND (CAST(:dtype AS text) IS NULL OR document_type = :dtype)
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY (tags))""";

    private static final String LIST = "SELECT " + COLUMNS + " FROM documents WHERE " + VISIBLE
            + " ORDER BY created_at DESC, id LIMIT :size OFFSET :offset";

    private static final String COUNT = "SELECT count(*) FROM documents WHERE " + VISIBLE;

    private static final String GET = "SELECT " + COLUMNS + " FROM documents WHERE id = :d AND deleted_at IS NULL";

    private static final String REQUEUE = """
            UPDATE documents SET status = 'QUEUED', failure_reason = NULL, updated_at = now()
            WHERE id = :d AND deleted_at IS NULL AND status IN ('INDEXED', 'FAILED')""";

    private static final String SOFT_DELETE = """
            UPDATE documents
            SET status = 'DELETED', deleted_at = now(), deleted_by = :by, updated_at = now(),
                purge_after = now() + make_interval(days => :days)
            WHERE id = :d AND deleted_at IS NULL""";

    private static final String CANCEL_JOBS = """
            UPDATE ingestion_jobs SET status = 'FAILED', last_error = 'DOCUMENT_DELETED', updated_at = now()
            WHERE document_id = :d AND status IN ('QUEUED', 'RUNNING')""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AccessService access;
    private final DocumentAudit audit;
    private final int purgeAfterDays;

    public DocumentService(JdbcClient jdbc, TransactionTemplate tx, AccessService access, DocumentAudit audit,
            EkaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.access = access;
        this.audit = audit;
        this.purgeAfterDays = props.retention().purgeAfterDays();
    }

    public DocumentPage list(String subject, DocumentListQuery q) {
        int page = q.pageOrDefault();
        int size = q.sizeOrDefault();
        List<UUID> readable = access.readableCollectionIds(subject);
        if (readable.isEmpty()) {
            return new DocumentPage(List.of(), page, size, 0);
        }
        var list = jdbc.sql(LIST).param("size", size).param("offset", (long) page * size);
        var count = jdbc.sql(COUNT);
        for (var spec : List.of(list, count)) {
            spec.param("cols", readable).param("collection", q.collectionId(), Types.OTHER)
                    .param("status", q.status() == null ? null : q.status().name(), Types.VARCHAR)
                    .param("dtype", q.documentType(), Types.VARCHAR).param("tag", q.tag(), Types.VARCHAR);
        }
        List<DocumentResponse> items = list.query(DocumentService::map).list();
        long total = count.query(Long.class).single();
        return new DocumentPage(items, page, size, total);
    }

    public DocumentResponse get(String subject, UUID id) {
        access.requireReadableDocument(subject, id);
        return jdbc.sql(GET).param("d", id).query(DocumentService::map).optional().orElseThrow(NotFoundException::new);
    }

    /** Idempotent: documents already queued or processing are returned unchanged. */
    public DocumentResponse reindex(String subject, UUID id) {
        UUID collectionId = access.requireReadableDocument(subject, id);
        access.requireManage(subject, collectionId);
        tx.executeWithoutResult(s -> {
            if (jdbc.sql(REQUEUE).param("d", id).update() == 1) {
                jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", id)
                        .update();
                audit.record(id, collectionId, "REINDEX", "SUCCESS", subject);
            }
        });
        return get(subject, id);
    }

    /** Content becomes inaccessible immediately; chunks, vectors and staged text are removed by the purge. */
    public void delete(String subject, UUID id) {
        UUID collectionId = access.requireReadableDocument(subject, id);
        access.requireManage(subject, collectionId);
        tx.executeWithoutResult(s -> {
            if (jdbc.sql(SOFT_DELETE).param("by", subject).param("days", purgeAfterDays).param("d", id).update() == 0) {
                throw new NotFoundException();
            }
            jdbc.sql(CANCEL_JOBS).param("d", id).update();
            audit.record(id, collectionId, "DELETE", "SUCCESS", subject);
        });
    }

    private static DocumentResponse map(ResultSet rs, int n) throws SQLException {
        String[] tags = (String[]) rs.getArray("tags").getArray();
        return new DocumentResponse(rs.getObject("id", UUID.class), rs.getObject("collection_id", UUID.class),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                DocumentStatus.valueOf(rs.getString("status")), rs.getString("failure_reason"),
                rs.getString("version"), rs.getString("document_type"), Arrays.asList(tags),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
