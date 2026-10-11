package org.enterpriseknowledgeassitance.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.enterpriseknowledgeassitance.FakeEmbeddingConfig;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=unused",
        "eka.ingestion.worker-enabled=true", "eka.ingestion.poll-interval=200ms"})
@Import({PostgresTestSupport.class, FakeEmbeddingConfig.class})
class IngestionWorkerIntegrationTest {

    @Autowired JdbcClient jdbc;

    @Test
    void backgroundWorkerPicksUpQueuedJobsWithoutAnyRequest() throws Exception {
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collections").update();
        UUID col = UUID.randomUUID();
        jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, 'w')").param("i", col).update();
        UUID[] docs = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        for (UUID id : docs) {
            jdbc.sql("""
                    INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes, checksum_sha256,
                        status, uploaded_by) VALUES (:id, :c, 'a.txt', 'text/plain', 10, :h, 'QUEUED', 'u')""")
                    .param("id", id).param("c", col).param("h", id.toString().replace("-", "") + "0".repeat(32))
                    .update();
            jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", id).update();
            jdbc.sql("INSERT INTO document_extraction_segments (document_id, seq, text) VALUES (:d, 0, 'hello world')")
                    .param("d", id).update();
        }
        long deadline = System.currentTimeMillis() + 20_000;
        long indexed = 0;
        while (System.currentTimeMillis() < deadline && indexed < docs.length) {
            Thread.sleep(200);
            indexed = jdbc.sql("SELECT count(*) FROM documents WHERE status = 'INDEXED'").query(Long.class).single();
        }
        assertThat(indexed).isEqualTo(docs.length);
    }
}
