package org.enterpriseknowledgeassitance.extraction;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/** DOCX body paragraphs; Heading styles become section metadata. DOCX has no stable page numbers. */
class DocxExtractor implements DocumentExtractor {

    @Override
    public ExtractedDocument extract(Path file, ExtractionLimits limits) throws Exception {
        try (InputStream in = Files.newInputStream(file); XWPFDocument doc = new XWPFDocument(in)) {
            List<ExtractedSegment> out = new ArrayList<>();
            String section = null;
            long total = 0;
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                String style = p.getStyle();
                if (style != null && style.toLowerCase().startsWith("heading")) {
                    section = text.strip();
                }
                int start = (int) total;
                total += text.length() + 1;
                if (total > limits.maxCharacters()) {
                    throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
                }
                out.add(new ExtractedSegment(text + "\n", null, section, start));
            }
            return new ExtractedDocument(out);
        }
    }
}
