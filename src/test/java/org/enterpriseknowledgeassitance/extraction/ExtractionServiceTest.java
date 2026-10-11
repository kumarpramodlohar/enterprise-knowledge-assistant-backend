package org.enterpriseknowledgeassitance.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.extraction.ExtractionException.Reason;
import org.junit.jupiter.api.Test;

class ExtractionServiceTest {

    private final ExtractionService service = serviceWith(1_000_000, 5);

    private static ExtractionService serviceWith(long maxChars, int maxPages) {
        return new ExtractionService(new EkaProperties(
                new EkaProperties.Upload(10_485_760, Set.of("pdf", "docx", "txt", "md"), 255, 100, 20, 50, "application/octet-stream"),
                new EkaProperties.Chunking(800, 100), new EkaProperties.Retrieval(5, 4000, 0.7),
                new EkaProperties.Ingestion(2, Duration.ofSeconds(60), 3),
                new EkaProperties.Ai("m", "e", 768, "t", 32), new EkaProperties.Retention(7),
                new EkaProperties.Extraction(maxChars, maxPages, 0.01, 200L * 1024 * 1024)));
    }

    private static InputStream in(byte[] b) {
        return new ByteArrayInputStream(b);
    }

    private static byte[] pdf(int pages, String text) throws IOException {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage();
                d.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text + " page " + i);
                    cs.endText();
                }
            }
            d.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx() throws IOException {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph h = d.createParagraph();
            h.setStyle("Heading1");
            h.createRun().setText("Refund Policy");
            d.createParagraph().createRun().setText("Refunds are issued within 30 days.");
            d.write(out);
            return out.toByteArray();
        }
    }

    private static long tempFiles() throws IOException {
        try (Stream<Path> s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith("eka-upload-")).count();
        }
    }

    @Test
    void extractsPdfWithPageNumbers() throws Exception {
        ExtractedDocument doc = service.extract("a.pdf", in(pdf(2, "Hello")));
        assertThat(doc.segments()).hasSize(2);
        assertThat(doc.segments().get(1).pageNumber()).isEqualTo(2);
        assertThat(doc.segments().get(0).text()).contains("Hello page 1");
    }

    @Test
    void extractsDocxWithHeadingSection() throws Exception {
        ExtractedDocument doc = service.extract("a.docx", in(docx()));
        assertThat(doc.segments()).extracting(ExtractedSegment::text).anyMatch(t -> t.contains("30 days"));
        assertThat(doc.segments().get(1).section()).isEqualTo("Refund Policy");
    }

    @Test
    void extractsMarkdownWithSectionsAndOffsets() throws Exception {
        String md = "# Intro\nHello\n## Details\nMore text\n";
        ExtractedDocument doc = service.extract("a.md", in(md.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.segments()).hasSize(2);
        assertThat(doc.segments().get(1).section()).isEqualTo("Details");
        assertThat(doc.segments().get(1).startOffset()).isEqualTo(md.indexOf("## Details"));
    }

    @Test
    void extractsPlainText() throws Exception {
        ExtractedDocument doc = service.extract("A.TXT", in("héllo".getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.segments().get(0).text()).isEqualTo("héllo");
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertReason("a.exe", "x".getBytes(), Reason.UNSUPPORTED_TYPE);
        assertReason("noext", "x".getBytes(), Reason.UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsEmptyAndBlank() {
        assertReason("a.txt", new byte[0], Reason.EMPTY);
        assertReason("a.txt", "  \n ".getBytes(), Reason.EMPTY);
    }

    @Test
    void rejectsCorruptAndMislabelledContent() throws Exception {
        assertReason("a.pdf", "not a pdf".getBytes(), Reason.CONTENT_MISMATCH);
        assertReason("a.docx", "not a zip".getBytes(), Reason.CONTENT_MISMATCH);
        assertReason("a.docx", pdf(1, "x"), Reason.CONTENT_MISMATCH);
        assertReason("a.pdf", "%PDF-1.4 garbage".getBytes(), Reason.CORRUPT);
        assertReason("a.txt", new byte[] {(byte) 0xC3, 0x28}, Reason.CORRUPT);
        assertReason("a.txt", "a\0b".getBytes(), Reason.CORRUPT);
    }

    @Test
    void enforcesResourceLimits() throws Exception {
        assertReason("a.pdf", pdf(6, "x"), Reason.TOO_LARGE);
        ExtractionService tiny = serviceWith(10, 5);
        assertThatThrownBy(() -> tiny.extract("a.txt", in("this is longer than ten".getBytes())))
                .isInstanceOfSatisfying(ExtractionException.class, e -> assertThat(e.reason()).isEqualTo(Reason.TOO_LARGE));
    }

    @Test
    void temporaryFilesAreRemovedOnSuccessAndFailure() throws Exception {
        long before = tempFiles();
        service.extract("a.txt", in("ok".getBytes()));
        assertThatThrownBy(() -> service.extract("a.pdf", in("bad".getBytes()))).isInstanceOf(ExtractionException.class);
        assertThatThrownBy(() -> service.extract("a.txt", in(new byte[0]))).isInstanceOf(ExtractionException.class);
        assertThat(tempFiles()).isEqualTo(before);
    }

    @Test
    void failureMessagesDoNotContainDocumentContent() {
        assertThatThrownBy(() -> service.extract("a.pdf", in("TOP-SECRET-CONTENT".getBytes())))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("TOP-SECRET"));
    }

    private void assertReason(String name, byte[] bytes, Reason reason) {
        assertThatThrownBy(() -> service.extract(name, in(bytes)))
                .isInstanceOfSatisfying(ExtractionException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
