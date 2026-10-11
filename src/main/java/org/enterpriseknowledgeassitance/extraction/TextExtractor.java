package org.enterpriseknowledgeassitance.extraction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Plain text and Markdown. Markdown headings become section metadata. */
class TextExtractor implements DocumentExtractor {

    private final boolean markdown;

    TextExtractor(boolean markdown) {
        this.markdown = markdown;
    }

    @Override
    public ExtractedDocument extract(Path file, ExtractionLimits limits) throws IOException, ExtractionException {
        if (Files.size(file) > limits.maxCharacters() * 4) {
            throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ExtractionException(ExtractionException.Reason.CORRUPT, e);
        }
        if (text.indexOf('\0') >= 0) {
            throw new ExtractionException(ExtractionException.Reason.CORRUPT);
        }
        if (text.length() > limits.maxCharacters()) {
            throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return markdown ? splitByHeading(text) : new ExtractedDocument(List.of(new ExtractedSegment(text, null, null, 0)));
    }

    private static ExtractedDocument splitByHeading(String text) {
        List<ExtractedSegment> out = new ArrayList<>();
        String section = null;
        int segStart = 0;
        int pos = 0;
        for (String line : text.split("(?<=\n)", -1)) {
            if (line.startsWith("#") && line.stripTrailing().matches("#{1,6}\\s+.+")) {
                if (pos > segStart) {
                    out.add(new ExtractedSegment(text.substring(segStart, pos), null, section, segStart));
                }
                section = line.replaceFirst("^#{1,6}\\s+", "").strip();
                segStart = pos;
            }
            pos += line.length();
        }
        if (pos > segStart) {
            out.add(new ExtractedSegment(text.substring(segStart, pos), null, section, segStart));
        }
        return new ExtractedDocument(out);
    }
}
