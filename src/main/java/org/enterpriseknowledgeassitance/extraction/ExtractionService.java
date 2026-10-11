package org.enterpriseknowledgeassitance.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.springframework.stereotype.Service;

/**
 * Extracts text from PDF, DOCX, TXT and Markdown. Uploaded bytes only ever live in a temporary
 * file that is deleted on success and failure; no original file is stored (DEC-005).
 */
@Service
public class ExtractionService {

    private final ExtractionLimits limits;

    public ExtractionService(EkaProperties props) {
        this.limits = new ExtractionLimits(props.extraction().maxCharacters(), props.extraction().maxPages());
        ZipSecureFile.setMinInflateRatio(props.extraction().zipMinInflateRatio());
        ZipSecureFile.setMaxEntrySize(props.extraction().zipMaxEntrySizeBytes());
        ZipSecureFile.setMaxTextSize(limits.maxCharacters() * 4);
    }

    public ExtractedDocument extract(String fileName, InputStream content) throws ExtractionException {
        String ext = extensionOf(fileName);
        DocumentExtractor extractor = switch (ext) {
            case "pdf" -> new PdfExtractor();
            case "docx" -> new DocxExtractor();
            case "txt" -> new TextExtractor(false);
            case "md" -> new TextExtractor(true);
            default -> throw new ExtractionException(ExtractionException.Reason.UNSUPPORTED_TYPE);
        };
        Path temp = null;
        try {
            temp = Files.createTempFile("eka-upload-", ".tmp");
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            verifySignature(ext, temp);
            ExtractedDocument doc = extractor.extract(temp, limits);
            if (doc.segments().stream().allMatch(s -> s.text().isBlank())) {
                throw new ExtractionException(ExtractionException.Reason.EMPTY);
            }
            return doc;
        } catch (ExtractionException e) {
            throw e;
        } catch (OutOfMemoryError e) {
            throw new ExtractionException(ExtractionException.Reason.TOO_LARGE);
        } catch (Exception e) {
            throw new ExtractionException(ExtractionException.Reason.CORRUPT, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    temp.toFile().deleteOnExit();
                }
            }
        }
    }

    /** Content check so a renamed file is not parsed as another format. */
    private static void verifySignature(String ext, Path file) throws IOException, ExtractionException {
        byte[] head = new byte[5];
        int n;
        try (InputStream in = Files.newInputStream(file)) {
            n = in.readNBytes(head, 0, head.length);
        }
        String prefix = new String(head, 0, n, StandardCharsets.ISO_8859_1);
        boolean ok = switch (ext) {
            case "pdf" -> prefix.startsWith("%PDF-");
            case "docx" -> prefix.startsWith("PK");
            default -> true;
        };
        if (!ok) {
            throw new ExtractionException(ExtractionException.Reason.CONTENT_MISMATCH);
        }
    }

    private static String extensionOf(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
