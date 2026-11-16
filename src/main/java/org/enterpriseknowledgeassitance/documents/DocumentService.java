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
import org.enterpriseknowledgeassitance.security.IdentityAssertion;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.enterpriseknowledgeassitance.web.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
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
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY (tags))
              AND (CAST(:search AS text) IS NULL OR file_name ILIKE :search ESCAPE '!')""";

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
            WHERE id = :d AND deleted_at IS NULL AND status NOT IN ('QUEUED', 'PROCESSING')""";

    private static final String STATUS = "SELECT status FROM documents WHERE id = :d AND deleted_at IS NULL";

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
        return list(subject, null, q);
    }

    public DocumentPage list(IdentityAssertion identity, DocumentListQuery q) {
        return list(identity.publicSubject(), identity, q);
    }

    private DocumentPage list(String subject, IdentityAssertion identity, DocumentListQuery q) {
        int page = q.pageOrDefault();
        int size = q.sizeOrDefault();
        List<UUID> readable = identity == null ? access.readableCollectionIds(subject)
                : access.readableCollectionIds(identity);
        if (readable.isEmpty()) {
            return new DocumentPage(List.of(), page, size, 0);
        }
        var list = jdbc.sql(LIST).param("size", size).param("offset", (long) page * size);
        var count = jdbc.sql(COUNT);
        String search = searchPattern(q.search());
        for (var spec : List.of(list, count)) {
            spec.param("cols", readable).param("collection", q.collectionId(), Types.OTHER)
                    .param("status", q.status() == null ? null : q.status().name(), Types.VARCHAR)
                    .param("dtype", q.documentType(), Types.VARCHAR).param("tag", q.tag(), Types.VARCHAR)
                    .param("search", search, Types.VARCHAR);
        }
        List<DocumentResponse> items = list.query(DocumentService::map).list();
        long total = count.query(Long.class).single();
        return new DocumentPage(items, page, size, total);
    }

    public DocumentResponse get(String subject, UUID id) {
        return get(subject, null, id);
    }

    public DocumentResponse get(IdentityAssertion identity, UUID id) {
        return get(identity.publicSubject(), identity, id);
    }

    private DocumentResponse get(String subject, IdentityAssertion identity, UUID id) {
        if (identity == null) {
            access.requireReadableDocument(subject, id);
        } else {
            access.requireReadableDocument(identity, id);
        }
        return jdbc.sql(GET).param("d", id).query(DocumentService::map).optional().orElseThrow(NotFoundException::new);
    }

    /** Re-index is available only after the current ingestion has finished. */
    public DocumentResponse reindex(String subject, UUID id) {
        return reindex(subject, null, id);
    }

    public DocumentResponse reindex(IdentityAssertion identity, UUID id) {
        return reindex(identity.publicSubject(), identity, id);
    }

    private DocumentResponse reindex(String subject, IdentityAssertion identity, UUID id) {
        UUID collectionId = identity == null ? access.requireReadableDocument(subject, id)
                : access.requireReadableDocument(identity, id);
        if (identity == null) {
            access.requireManage(subject, collectionId);
        } else {
            access.requireManage(identity, collectionId);
        }
        tx.executeWithoutResult(s -> {
            if (jdbc.sql(REQUEUE).param("d", id).update() != 1) {
                requireNotBusyOrNotFound(id);
            }
            jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", id)
                    .update();
            audit.record(id, collectionId, "REINDEX", "SUCCESS", subject);
        });
        return get(subject, identity, id);
    }

    /** Content becomes inaccessible immediately; chunks, vectors and staged text are removed by the purge. */
    public void delete(String subject, UUID id) {
        delete(subject, null, id);
    }

    public void delete(IdentityAssertion identity, UUID id) {
        delete(identity.publicSubject(), identity, id);
    }

    private void delete(String subject, IdentityAssertion identity, UUID id) {
        UUID collectionId = identity == null ? access.requireReadableDocument(subject, id)
                : access.requireReadableDocument(identity, id);
        if (identity == null) {
            access.requireManage(subject, collectionId);
        } else {
            access.requireManage(identity, collectionId);
        }
        tx.executeWithoutResult(s -> {
            if (jdbc.sql(SOFT_DELETE).param("by", subject).param("days", purgeAfterDays).param("d", id).update() == 0) {
                requireNotBusyOrNotFound(id);
            }
            jdbc.sql(CANCEL_JOBS).param("d", id).update();
            audit.record(id, collectionId, "DELETE", "SUCCESS", subject);
        });
    }

    private void requireNotBusyOrNotFound(UUID id) {
        String status = jdbc.sql(STATUS).param("d", id).query(String.class).optional()
                .orElseThrow(NotFoundException::new);
        if ("QUEUED".equals(status) || "PROCESSING".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.DOCUMENT_BUSY,
                    "Document ingestion is still in progress");
        }
        throw new NotFoundException();
    }

    private static String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
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
