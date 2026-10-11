package org.enterpriseknowledgeassitance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=unused"})
@Import(PostgresTestSupport.class)
class MigrationIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateExpectedSchemaAndExtension() {
        Integer ext = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertThat(ext).isEqualTo(1);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("collections", "documents", "ingestion_jobs", "document_chunks",
                "vector_store_ollama", "vector_store_openai");
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isEqualTo(4);
    }

    @Test
    void vectorInsertSearchLinksToChunkMetadata() {
        UUID collection = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        UUID chunk = UUID.randomUUID();
        jdbc.update("INSERT INTO collections (id, name) VALUES (?, ?)", collection, "c-" + collection);
        jdbc.update("""
                INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes,
                    checksum_sha256, status, uploaded_by)
                VALUES (?, ?, 'a.txt', 'text/plain', 10, ?, 'INDEXED', 'tester')
                """, document, collection, "a".repeat(64));
        jdbc.update("INSERT INTO document_chunks (id, document_id, chunk_index, content) VALUES (?, ?, 0, 'hello')",
                chunk, document);
        String vec = "[" + "0.1,".repeat(767) + "0.1]";
        jdbc.update("INSERT INTO vector_store_ollama (id, content, metadata, embedding) "
                + "VALUES (?, 'hello', '{}'::json, ?::vector)", chunk, vec);

        List<String> hits = jdbc.queryForList("""
                SELECT c.content FROM vector_store_ollama v
                JOIN document_chunks c ON c.id = v.id
                ORDER BY v.embedding <=> ?::vector LIMIT 1
                """, String.class, vec);
        assertThat(hits).containsExactly("hello");
    }

    @Test
    void duplicateChecksumInSameCollectionIsRejected() {
        UUID collection = UUID.randomUUID();
        jdbc.update("INSERT INTO collections (id, name) VALUES (?, ?)", collection, "d-" + collection);
        String insert = """
                INSERT INTO documents (collection_id, file_name, content_type, size_bytes,
                    checksum_sha256, status, uploaded_by)
                VALUES (?, 'a.txt', 'text/plain', 10, ?, 'QUEUED', 'tester')
                """;
        jdbc.update(insert, collection, "b".repeat(64));
        assertThatThrownBy(() -> jdbc.update(insert, collection, "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
