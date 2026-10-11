package org.enterpriseknowledgeassitance.extraction;

import java.util.List;

public record ExtractedDocument(List<ExtractedSegment> segments) {

    public ExtractedDocument {
        segments = List.copyOf(segments);
    }

    public int totalCharacters() {
        return segments.stream().mapToInt(s -> s.text().length()).sum();
    }
}
