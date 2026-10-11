package org.enterpriseknowledgeassitance.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable ingestion state machine. Job and document state live in PostgreSQL, so a restart loses
 * nothing: abandoned RUNNING jobs are re-queued and processing is idempotent because
 * {@link ChunkIndexer#index} atomically replaces a document's chunks and vectors.
 * Failure reasons are stable codes only; no document content or provider detail is persisted.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    static final String EMPTY = "EMPTY";

    private static final String CLAIM = """
            UPDATE ingestion_jobs SET status = 'RUNNING', attempts = attempts + 1, updated_at = now()
            WHERE id = (SELECT j.id FROM ingestion_jobs j JOIN documents d ON d.id = j.document_id
                        WHERE j.status = 'QUEUED' AND j.available_at <= now() AND d.deleted_at IS NULL
                        ORDER BY j.created_at FOR UPDATE OF j SKIP LOCKED LIMIT 1)
            RETURNING id, document_id, attempts""";

    private static final String LOAD_SEGMENTS = """
            SELECT page_number, section, text FROM document_extraction_segments
            WHERE document_id = :d ORDER BY seq""";

    private static final String SET_DOCUMENT_STATUS = """
            UPDATE documents SET status = :status, failure_reason = :reason, updated_at = now()
            WHERE id = :d AND deleted_at IS NULL""";

    private static final String SET_JOB = """
            UPDATE ingestion_jobs SET status = :status, last_error = :error, available_at = now() + CAST(:backoff AS interval),
                                      updated_at = now()
            WHERE id = :j""";

    /** Jobs of deleted documents are cancelled instead of processed. */
    private static final String CANCEL_DELETED = """
            UPDATE ingestion_jobs SET status = 'FAILED', last_error = 'DOCUMENT_DELETED', updated_at = now()
            WHERE status = 'QUEUED' AND document_id IN (SELECT id FROM documents WHERE deleted_at IS NOT NULL)""";

    public record ClaimedJob(UUID jobId, UUID documentId, int attempts) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Chunker chunker;
    private final ChunkIndexer indexer;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final Duration staleAfter;

    public IngestionService(JdbcClient jdbc, TransactionTemplate tx, Chunker chunker, ChunkIndexer indexer,
            EkaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.chunker = chunker;
        this.indexer = indexer;
        this.maxRetries = props.ingestion().maxRetries();
        this.retryBackoff = props.ingestion().retryBackoff();
        this.staleAfter = props.ingestion().staleAfter();
    }

    /** Atomically claims the next runnable job (safe across workers/instances) and marks the document PROCESSING. */
    public Optional<ClaimedJob> claimNext() {
        return tx.execute(status -> {
            jdbc.sql(CANCEL_DELETED).update();
            Optional<ClaimedJob> job = jdbc.sql(CLAIM)
                    .query((rs, n) -> new ClaimedJob(rs.getObject("id", UUID.class),
                            rs.getObject("document_id", UUID.class), rs.getInt("attempts")))
                    .optional();
            job.ifPresent(j -> jdbc.sql(SET_DOCUMENT_STATUS).param("status", "PROCESSING")
                    .param("reason", null, java.sql.Types.VARCHAR).param("d", j.documentId()).update());
            return job;
        });
    }

    public void process(ClaimedJob job) {
        try {
            List<ExtractedSegment> segments = jdbc.sql(LOAD_SEGMENTS).param("d", job.documentId())
                    .query((rs, n) -> new ExtractedSegment(rs.getString("text"), (Integer) rs.getObject("page_number"),
                            rs.getString("section"), 0))
                    .list();
            List<Chunk> chunks = chunker.chunk(job.documentId(), new ExtractedDocument(segments));
            if (chunks.isEmpty()) {
                fail(job, EMPTY);
                return;
            }
            indexer.index(job.documentId(), chunks);
            tx.executeWithoutResult(s -> {
                jdbc.sql(SET_DOCUMENT_STATUS).param("status", "INDEXED").param("reason", null, java.sql.Types.VARCHAR)
                        .param("d", job.documentId()).update();
                setJob(job.jobId(), "SUCCEEDED", null, Duration.ZERO);
            });
        } catch (EmbeddingFailedException e) {
            // A dimension mismatch is a configuration error; retrying cannot help.
            handleFailure(job, e.getMessage(), !ErrorCodes.EMBEDDING_DIMENSION_MISMATCH.equals(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("Ingestion job {} failed unexpectedly: {}", job.jobId(), e.getClass().getName());
            handleFailure(job, ErrorCodes.INTERNAL_ERROR, true);
        }
    }

    private void handleFailure(ClaimedJob job, String code, boolean transientFailure) {
        if (transientFailure && job.attempts() <= maxRetries) {
            tx.executeWithoutResult(s -> {
                setJob(job.jobId(), "QUEUED", code, retryBackoff.multipliedBy(job.attempts()));
                jdbc.sql(SET_DOCUMENT_STATUS).param("status", "QUEUED").param("reason", null, java.sql.Types.VARCHAR)
                        .param("d", job.documentId()).update();
            });
            return;
        }
        fail(job, code);
    }

    private void fail(ClaimedJob job, String code) {
        tx.executeWithoutResult(s -> {
            setJob(job.jobId(), "FAILED", code, Duration.ZERO);
            jdbc.sql(SET_DOCUMENT_STATUS).param("status", "FAILED").param("reason", code).param("d", job.documentId())
                    .update();
        });
    }

    private void setJob(UUID jobId, String status, String error, Duration backoff) {
        jdbc.sql(SET_JOB).param("status", status).param("error", error, java.sql.Types.VARCHAR)
                .param("backoff", backoff.toMillis() / 1000.0 + " seconds")
                .param("j", jobId).update();
    }

    /** Re-queues RUNNING jobs abandoned by a crash/restart, or fails them once retries are exhausted. */
    public int recoverStale() {
        Integer recovered = tx.execute(status -> {
            List<ClaimedJob> stale = jdbc.sql("""
                    UPDATE ingestion_jobs SET updated_at = now()
                    WHERE status = 'RUNNING' AND updated_at < now() - CAST(:stale AS interval)
                    RETURNING id, document_id, attempts""")
                    .param("stale", staleAfter.toMillis() / 1000.0 + " seconds")
                    .query((rs, n) -> new ClaimedJob(rs.getObject("id", UUID.class),
                            rs.getObject("document_id", UUID.class), rs.getInt("attempts")))
                    .list();
            stale.forEach(j -> handleFailure(j, ErrorCodes.INTERNAL_ERROR, true));
            return stale.size();
        });
        return recovered == null ? 0 : recovered;
    }
}
