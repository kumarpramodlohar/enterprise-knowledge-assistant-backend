package org.enterpriseknowledgeassitance.extraction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

class PdfExtractor implements DocumentExtractor {

    @Override
    public ExtractedDocument extract(Path file, ExtractionLimits limits) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(new RandomAccessReadBufferedFile(file.toFile()))) {
            if (pdf.isEncrypted()) {
                throw new ExtractionException(ExtractionException.Reason.ENCRYPTED);
            }
            int pages = pdf.getNumberOfPages();
            if (pages > limits.maxPages()) {
                throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedSegment> out = new ArrayList<>();
            long total = 0;
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(pdf);
                total += text.length();
                if (total > limits.maxCharacters()) {
                    throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
                }
                if (!text.isBlank()) {
                    out.add(new ExtractedSegment(text, p, null, (int) (total - text.length())));
                }
            }
            return new ExtractedDocument(out);
        }
    }
}
