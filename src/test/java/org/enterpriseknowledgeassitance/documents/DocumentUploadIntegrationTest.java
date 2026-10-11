package org.enterpriseknowledgeassitance.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw"})
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class DocumentUploadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    UUID manage = UUID.randomUUID();
    UUID manage2 = UUID.randomUUID();
    UUID readOnly = UUID.randomUUID();

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collection_grants").update();
        jdbc.sql("DELETE FROM app_users").update();
        jdbc.sql("DELETE FROM collections").update();
        for (UUID c : new UUID[] {manage, manage2, readOnly}) {
            jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, :n)").param("i", c).param("n", "c" + c).update();
        }
        jdbc.sql("INSERT INTO app_users (subject, role) VALUES ('dev', 'MEMBER')").update();
        jdbc.sql("INSERT INTO collection_grants VALUES ('dev', :c, 'MANAGE'), ('dev', :d, 'MANAGE'), ('dev', :r, 'READ')")
                .param("c", manage).param("d", manage2).param("r", readOnly).update();
    }

    private ResultActions upload(UUID collection, String name, byte[] bytes) throws Exception {
        return upload(collection, name, bytes, true);
    }

    private ResultActions upload(UUID collection, String name, byte[] bytes, boolean auth) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .param("collectionId", collection.toString());
        if (auth) {
            req.with(httpBasic("dev", "pw"));
        }
        return mvc.perform(req);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private void assertNothingCreated() {
        assertThat(count("documents")).isZero();
        assertThat(count("ingestion_jobs")).isZero();
        assertThat(count("document_extraction_segments")).isZero();
    }

    private static byte[] txt(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            d.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Refund policy text");
                cs.endText();
            }
            d.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx() throws IOException {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText("Refunds within 30 days");
            d.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void acceptsSupportedFormatsAndQueuesDocumentAndJob() throws Exception {
        upload(manage, "a.txt", txt("plain text")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.statusUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/documents/")));
        upload(manage, "b.md", txt("# Title\nbody")).andExpect(status().isAccepted());
        upload(manage, "c.pdf", pdf()).andExpect(status().isAccepted());
        upload(manage, "d.DOCX", docx()).andExpect(status().isAccepted());

        assertThat(count("documents")).isEqualTo(4);
        assertThat(count("ingestion_jobs")).isEqualTo(4);
        assertThat(jdbc.sql("SELECT count(*) FROM ingestion_jobs WHERE status = 'QUEUED'").query(Long.class).single())
                .isEqualTo(4);
        assertThat(jdbc.sql("SELECT count(*) FROM documents WHERE status = 'QUEUED' AND uploaded_by = 'dev' "
                + "AND length(checksum_sha256) = 64").query(Long.class).single()).isEqualTo(4);
        assertThat(count("document_extraction_segments")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void storesMetadataAndSanitizesFileName() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "../../etc/notes.txt", "text/plain", txt("hello")))
                        .param("collectionId", manage.toString()).param("version", "v1")
                        .param("documentType", "policy").param("tags", "hr", "finance")
                        .with(httpBasic("dev", "pw")))
                .andExpect(status().isAccepted());
        assertThat(jdbc.sql("SELECT file_name FROM documents").query(String.class).single()).isEqualTo("notes.txt");
        assertThat(jdbc.sql("SELECT version || '/' || document_type || '/' || array_to_string(tags, ',') FROM documents")
                .query(String.class).single()).isEqualTo("v1/policy/hr,finance");
    }

    @Test
    void acceptsFileExactlyAtLimitAndRejectsOverLimit() throws Exception {
        byte[] atLimit = new byte[10 * 1024 * 1024];
        byte[] pattern = txt("word ");
        for (int i = 0; i < atLimit.length; i++) {
            atLimit[i] = pattern[i % pattern.length];
        }
        upload(manage, "big.txt", atLimit).andExpect(status().isAccepted());
        long before = count("documents");
        upload(manage, "bigger.txt", new byte[10 * 1024 * 1024 + 1]).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(count("documents")).isEqualTo(before);
    }

    @Test
    void rejectsUnsupportedAndSpoofedFiles() throws Exception {
        upload(manage, "a.exe", txt("x")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_TYPE"));
        upload(manage, "noext", txt("x")).andExpect(status().isUnsupportedMediaType());
        upload(manage, "fake.pdf", txt("TOP-SECRET plain text")).andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string(not(containsString("TOP-SECRET"))));
        upload(manage, "fake.docx", pdf()).andExpect(status().isUnsupportedMediaType());
        assertNothingCreated();
    }

    @Test
    void rejectsEmptyAndCorruptFiles() throws Exception {
        upload(manage, "a.txt", new byte[0]).andExpect(status().isBadRequest());
        upload(manage, "a.txt", txt("   \n ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        upload(manage, "a.pdf", txt("%PDF-1.4 garbage")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_DOCUMENT"));
        assertNothingCreated();
    }

    @Test
    void enforcesAuthenticationAndCollectionPermission() throws Exception {
        upload(manage, "a.txt", txt("x"), false).andExpect(status().isUnauthorized());
        upload(readOnly, "a.txt", txt("x")).andExpect(status().isNotFound());
        upload(UUID.randomUUID(), "a.txt", txt("x")).andExpect(status().isNotFound());
        assertNothingCreated();
    }

    @Test
    void duplicateInSameCollectionReturns409ButOtherCollectionAndDeletedAreAllowed() throws Exception {
        upload(manage, "a.txt", txt("same content")).andExpect(status().isAccepted());
        upload(manage, "renamed.txt", txt("same content")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
        assertThat(count("documents")).isEqualTo(1);

        upload(manage2, "a.txt", txt("same content")).andExpect(status().isAccepted());

        jdbc.sql("UPDATE documents SET deleted_at = now() WHERE collection_id = :c").param("c", manage).update();
        upload(manage, "a.txt", txt("same content")).andExpect(status().isAccepted());
        assertThat(count("documents")).isEqualTo(3);
    }

    @Test
    void rejectsInvalidRequestShape() throws Exception {
        mvc.perform(multipart("/api/v1/documents").param("collectionId", manage.toString())
                        .with(httpBasic("dev", "pw")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", txt("x")))
                        .param("collectionId", "not-a-uuid").with(httpBasic("dev", "pw")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", txt("x")))
                        .param("collectionId", manage.toString()).param("tags", java.util.stream.IntStream.range(0, 21).mapToObj(i -> "t" + i).toArray(String[]::new))
                        .with(httpBasic("dev", "pw")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", txt("x")))
                        .param("collectionId", manage.toString()).param("version", "v".repeat(101))
                        .with(httpBasic("dev", "pw")))
                .andExpect(status().isBadRequest());
        assertNothingCreated();
    }
}
