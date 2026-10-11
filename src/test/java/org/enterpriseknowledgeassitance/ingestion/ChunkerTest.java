package org.enterpriseknowledgeassitance.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static final UUID DOC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static String words(int n) {
        return IntStream.range(0, n).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    private static ExtractedDocument one(String text) {
        return new ExtractedDocument(List.of(new ExtractedSegment(text, null, null, 0)));
    }

    private static int count(String s) {
        return s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    @Test
    void splitsWithConfiguredSizeAndOverlap() {
        List<Chunk> chunks = new Chunker(10, 3).chunk(DOC, one(words(25)));
        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(c -> count(c.text())).containsExactly(10, 10, 10, 4);
        assertThat(chunks.get(3).text()).startsWith("w21 ");
        assertThat(chunks.get(1).text()).startsWith("w7 ");
        assertThat(chunks.get(0).text()).endsWith("w9");
        assertThat(chunks).extracting(Chunk::index).containsExactly(0, 1, 2, 3);
        assertThat(chunks).allMatch(c -> c.documentId().equals(DOC));
    }

    @Test
    void exactFitProducesSingleChunkAndNoOverlapOnlyTail() {
        assertThat(new Chunker(10, 3).chunk(DOC, one(words(10)))).hasSize(1);
        assertThat(new Chunker(10, 3).chunk(DOC, one(words(11)))).hasSize(2);
    }

    @Test
    void shortAndEmptyText() {
        assertThat(new Chunker(10, 3).chunk(DOC, one("hello"))).extracting(Chunk::text).containsExactly("hello");
        assertThat(new Chunker(10, 3).chunk(DOC, one("  \n\t "))).isEmpty();
        assertThat(new Chunker(10, 3).chunk(DOC, new ExtractedDocument(List.of()))).isEmpty();
    }

    @Test
    void chunksAreNeverEmpty() {
        assertThat(new Chunker(4, 1).chunk(DOC, one(words(100)))).allMatch(c -> !c.text().isBlank());
    }

    @Test
    void preservesPageSectionAndOffsets() {
        String a = "alpha beta gamma ";
        String b = "delta epsilon";
        ExtractedDocument doc = new ExtractedDocument(List.of(
                new ExtractedSegment(a, 1, "Intro", 0), new ExtractedSegment(b, 2, "Details", a.length())));
        List<Chunk> chunks = new Chunker(2, 1).chunk(DOC, doc);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).section()).isEqualTo("Intro");
        assertThat(chunks.get(0).startOffset()).isZero();
        Chunk last = chunks.get(chunks.size() - 1);
        assertThat(last.pageNumber()).isEqualTo(2);
        assertThat(last.section()).isEqualTo("Details");
        assertThat(last.startOffset()).isEqualTo(a.length());
        Chunk crossing = chunks.stream().filter(c -> c.text().contains("gamma") && c.text().contains("delta"))
                .findFirst().orElseThrow();
        assertThat(crossing.text()).isEqualTo("gamma\ndelta");
        assertThat(crossing.pageNumber()).isEqualTo(1);
    }

    @Test
    void idsAreStableAndDistinguishDocumentPositionAndText() {
        List<Chunk> first = new Chunker(10, 3).chunk(DOC, one(words(25)));
        List<Chunk> second = new Chunker(10, 3).chunk(DOC, one(words(25)));
        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(Chunk::id).doesNotHaveDuplicates();
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertThat(new Chunker(10, 3).chunk(other, one(words(25))).get(0).id()).isNotEqualTo(first.get(0).id());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new Chunker(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(5, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesLargeInputWithDefaults() {
        List<Chunk> chunks = new Chunker(800, 100).chunk(DOC, one(words(100_000)));
        assertThat(chunks).hasSize(143);
    }
}
