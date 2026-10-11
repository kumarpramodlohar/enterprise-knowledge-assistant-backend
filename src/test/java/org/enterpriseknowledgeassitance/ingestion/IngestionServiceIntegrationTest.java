package org.enterpriseknowledgeassitance.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.enterpriseknowledgeassitance.FakeEmbeddingConfig;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=unused",
        "eka.ingestion.max-retries=2", "eka.ingestion.retry-backoff=0s", "eka.ingestion.stale-after=1h",
        "eka.ingestion.provider-timeout=1s"})
@Import({PostgresTestSupport.class, FakeEmbeddingConfig.class})
class IngestionServiceIntegrationTest {

    @Autowired IngestionService service;
    @Autowired FakeEmbeddingModel fake;
    @Autowired JdbcClient jdbc;

    UUID col = UUID.randomUUID();
    UUID doc;

    @BeforeEach
    void seed() {
        fake.fail = false;
        fake.dimensions = 768;
        fake.delayMillis = 0;
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collections").update();
        jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, 'c')").param("i", col).update();
        doc = queuedDocument("Refunds are available within thirty days. ".repeat(300));
    }

    private UUID queuedDocument(String text) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes, checksum_sha256,
                    status, uploaded_by) VALUES (:id, :c, 'a.txt', 'text/plain', 10, :h, 'QUEUED', 'u')""")
                .param("id", id).param("c", col).param("h", id.toString().replace("-", "") + "0".repeat(32)).update();
        jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", id).update();
        if (!text.isEmpty()) {
            jdbc.sql("INSERT INTO document_extraction_segments (document_id, seq, text) VALUES (:d, 0, :t)")
                    .param("d", id).param("t", text).update();
        }
        return id;
    }

    private String docStatus(UUID id) {
        return jdbc.sql("SELECT status FROM documents WHERE id = :d").param("d", id).query(String.class).single();
    }

    private String jobStatus(UUID id) {
        return jdbc.sql("SELECT status FROM ingestion_jobs WHERE document_id = :d ORDER BY created_at DESC LIMIT 1")
                .param("d", id).query(String.class).single();
    }

    private long chunks(UUID id) {
        return jdbc.sql("SELECT count(*) FROM document_chunks WHERE document_id = :d").param("d", id)
                .query(Long.class).single();
    }

    private long vectors() {
        return jdbc.sql("SELECT count(*) FROM vector_store_ollama").query(Long.class).single();
    }

    private void runOnce() {
        var job = service.claimNext();
        assertThat(job).isPresent();
        service.process(job.get());
    }

    @Test
    void processesQueuedJobToIndexed() {
        var job = service.claimNext().orElseThrow();
        assertThat(docStatus(doc)).isEqualTo("PROCESSING");
        assertThat(job.attempts()).isEqualTo(1);
        service.process(job);

        assertThat(docStatus(doc)).isEqualTo("INDEXED");
        assertThat(jobStatus(doc)).isEqualTo("SUCCEEDED");
        assertThat(chunks(doc)).isGreaterThan(1);
        assertThat(vectors()).isEqualTo(chunks(doc));
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    void transientFailureRetriesThenSucceedsWithoutDuplicateChunks() {
        fake.fail = true;
        runOnce();
        assertThat(docStatus(doc)).isEqualTo("QUEUED");
        assertThat(jobStatus(doc)).isEqualTo("QUEUED");
        assertThat(chunks(doc)).isZero();
        assertThat(jdbc.sql("SELECT last_error FROM ingestion_jobs").query(String.class).single())
                .isEqualTo("EMBEDDING_PROVIDER_FAILED");

        fake.fail = false;
        runOnce();
        long expected = chunks(doc);
        assertThat(docStatus(doc)).isEqualTo("INDEXED");

        // Re-running the same document replaces rather than duplicates active chunks.
        service.process(new IngestionService.ClaimedJob(
                jdbc.sql("SELECT id FROM ingestion_jobs").query(UUID.class).single(), doc, 3));
        assertThat(chunks(doc)).isEqualTo(expected);
        assertThat(vectors()).isEqualTo(expected);
    }

    @Test
    void failsTerminallyWhenRetriesAreExhaustedWithSafeReason() {
        fake.fail = true;
        runOnce();
        runOnce();
        assertThat(docStatus(doc)).isEqualTo("QUEUED");
        runOnce();
        assertThat(docStatus(doc)).isEqualTo("FAILED");
        assertThat(jobStatus(doc)).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT failure_reason FROM documents").query(String.class).single())
                .isEqualTo("EMBEDDING_PROVIDER_FAILED");
        assertThat(jdbc.sql("SELECT attempts FROM ingestion_jobs").query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT coalesce(last_error, '') || coalesce(failure_reason, '') FROM ingestion_jobs, "
                + "documents").query(String.class).single()).doesNotContain("secret-detail");
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    void dimensionMismatchFailsImmediately() {
        fake.dimensions = 10;
        runOnce();
        assertThat(docStatus(doc)).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT failure_reason FROM documents").query(String.class).single())
                .isEqualTo("EMBEDDING_DIMENSION_MISMATCH");
        assertThat(chunks(doc)).isZero();
    }

    @Test
    void providerTimeoutIsRetriedAndReported() {
        fake.delayMillis = 3000;
        runOnce();
        assertThat(jobStatus(doc)).isEqualTo("QUEUED");
        assertThat(jdbc.sql("SELECT last_error FROM ingestion_jobs").query(String.class).single())
                .isEqualTo("EMBEDDING_TIMEOUT");
    }

    @Test
    void documentWithoutTextFailsAsEmpty() {
        jdbc.sql("DELETE FROM documents").update();
        UUID empty = queuedDocument("");
        runOnce();
        assertThat(docStatus(empty)).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT failure_reason FROM documents").query(String.class).single()).isEqualTo("EMPTY");
    }

    @Test
    void backoffDelaysRetryAndDeletedDocumentsAreNotClaimed() {
        jdbc.sql("UPDATE ingestion_jobs SET available_at = now() + interval '1 hour'").update();
        assertThat(service.claimNext()).isEmpty();
        jdbc.sql("UPDATE ingestion_jobs SET available_at = now()").update();
        jdbc.sql("UPDATE documents SET status = 'DELETED', deleted_at = now()").update();
        assertThat(service.claimNext()).isEmpty();
        assertThat(jobStatus(doc)).isEqualTo("FAILED");
    }

    @Test
    void recoversAbandonedRunningJobsAfterRestart() {
        var job = service.claimNext().orElseThrow();
        assertThat(service.recoverStale()).isZero();

        jdbc.sql("UPDATE ingestion_jobs SET updated_at = now() - interval '2 hours'").update();
        assertThat(service.recoverStale()).isEqualTo(1);
        assertThat(jobStatus(doc)).isEqualTo("QUEUED");
        assertThat(docStatus(doc)).isEqualTo("QUEUED");

        runOnce();
        assertThat(docStatus(doc)).isEqualTo("INDEXED");
        assertThat(job.attempts()).isEqualTo(1);
    }

    @Test
    void abandonedJobWithNoAttemptsLeftFails() {
        jdbc.sql("UPDATE ingestion_jobs SET status = 'RUNNING', attempts = 3, updated_at = now() - interval '2 hours'")
                .update();
        assertThat(service.recoverStale()).isEqualTo(1);
        assertThat(jobStatus(doc)).isEqualTo("FAILED");
        assertThat(docStatus(doc)).isEqualTo("FAILED");
    }
}
