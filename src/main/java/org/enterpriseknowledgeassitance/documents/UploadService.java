package org.enterpriseknowledgeassitance.documents;

import org.enterpriseknowledgeassitance.web.ErrorCodes;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.enterpriseknowledgeassitance.api.DocumentStatus;
import org.enterpriseknowledgeassitance.api.UploadAcceptedResponse;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.enterpriseknowledgeassitance.extraction.ExtractionException;
import org.enterpriseknowledgeassitance.extraction.ExtractionService;
import org.enterpriseknowledgeassitance.security.AccessService;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and accepts an upload. The original file is never stored (DEC-005): its text is extracted
 * during validation and staged in the database so the ingestion job (T10) is durable.
 */
@Service
public class UploadService {

    private static final String INSERT_DOCUMENT = """
            INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes, checksum_sha256,
                                   version, document_type, tags, status, uploaded_by)
            VALUES (:id, :collectionId, :fileName, :contentType, :size, :checksum,
                    :version, :documentType, string_to_array(:tags, ','), 'QUEUED', :uploadedBy)""";

    private static final String INSERT_SEGMENT = """
            INSERT INTO document_extraction_segments (document_id, seq, page_number, section, text)
            VALUES (:d, :seq, :page, :section, :text)""";

    private static final String FIND_DUPLICATE = """
            SELECT EXISTS (SELECT 1 FROM documents
                           WHERE collection_id = :c AND checksum_sha256 = :h AND deleted_at IS NULL)""";

    /** Bound by component name to {@link #INSERT_DOCUMENT}. */
    private record DocumentRow(UUID id, UUID collectionId, String fileName, String contentType, long size,
            String checksum, String version, String documentType, String tags, String uploadedBy) {}

    private final AccessService access;
    private final ExtractionService extraction;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final EkaProperties props;

    public UploadService(AccessService access, ExtractionService extraction, JdbcClient jdbc, TransactionTemplate tx,
            EkaProperties props) {
        this.access = access;
        this.extraction = extraction;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    public UploadAcceptedResponse accept(String subject, UUID collectionId, MultipartFile file, String version,
            String documentType, List<String> tags) {
        access.requireManage(subject, collectionId);

        List<String> cleanTags = tags == null ? List.of() : tags.stream().map(String::strip).filter(t -> !t.isEmpty()).toList();
        validateMetadata(version, documentType, cleanTags);

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "A non-empty file is required");
        }
        if (file.getSize() > props.upload().maxFileSizeBytes()) {
            throw tooLarge();
        }
        String fileName = safeName(file.getOriginalFilename());
        String ext = extension(fileName);
        if (!props.upload().allowedExtensions().contains(ext)) {
            throw unsupported();
        }

        String checksum = checksum(file);
        if (isDuplicate(collectionId, checksum)) {
            throw duplicate();
        }

        ExtractedDocument extracted = extract(fileName, file);

        UUID documentId = UUID.randomUUID();
        try {
            tx.executeWithoutResult(status -> persist(documentId, collectionId, subject, fileName, file, checksum,
                    version, documentType, cleanTags, extracted));
        } catch (DuplicateKeyException e) {
            throw duplicate();
        }
        return new UploadAcceptedResponse(documentId, DocumentStatus.QUEUED, ApiPaths.DOCUMENTS + "/" + documentId);
    }

    private void persist(UUID id, UUID collectionId, String subject, String fileName, MultipartFile file,
            String checksum, String version, String documentType, List<String> tags, ExtractedDocument extracted) {
        String contentType = file.getContentType() == null ? props.upload().defaultContentType() : file.getContentType();
        jdbc.sql(INSERT_DOCUMENT).paramSource(new DocumentRow(id, collectionId, fileName, contentType, file.getSize(),
                checksum, blankToNull(version), blankToNull(documentType), String.join(",", tags), subject)).update();
        jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", id).update();
        List<ExtractedSegment> segments = extracted.segments();
        for (int i = 0; i < segments.size(); i++) {
            ExtractedSegment s = segments.get(i);
            jdbc.sql(INSERT_SEGMENT).param("d", id).param("seq", i).param("page", s.pageNumber())
                    .param("section", s.section()).param("text", s.text()).update();
        }
    }

    private ExtractedDocument extract(String fileName, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return extraction.extract(fileName, in);
        } catch (ExtractionException e) {
            throw switch (e.reason()) {
                case UNSUPPORTED_TYPE, CONTENT_MISMATCH -> unsupported();
                case TOO_LARGE -> tooLarge();
                case EMPTY -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                        "The file contains no extractable text");
                case CORRUPT, ENCRYPTED -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.UNPROCESSABLE_DOCUMENT,
                        "The file is corrupt or protected and cannot be read (" + e.reason() + ")");
            };
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "The file could not be read");
        }
    }

    private boolean isDuplicate(UUID collectionId, String checksum) {
        return jdbc.sql(FIND_DUPLICATE).param("c", collectionId).param("h", checksum).query(Boolean.class).single();
    }

    private static String checksum(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream din = new DigestInputStream(in, md)) {
                din.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "The file could not be read");
        }
    }

    private void validateMetadata(String version, String documentType, List<String> tags) {
        boolean bad = (version != null && version.length() > props.upload().maxMetadataLength())
                || (documentType != null && documentType.length() > props.upload().maxMetadataLength())
                || tags.size() > props.upload().maxTags()
                || tags.stream().anyMatch(t -> t.length() > props.upload().maxTagLength() || t.contains(",") || t.indexOf('\0') >= 0);
        if (bad) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Document metadata is invalid");
        }
    }

    /** Keeps only the base file name so path components from the client are never used. */
    private String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty() || name.length() > props.upload().maxFileNameLength()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "File name is missing or too long");
        }
        return name;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE, "File exceeds the maximum allowed size");
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_TYPE,
                "Only PDF, DOCX, TXT and Markdown files are supported");
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_DOCUMENT,
                "This document already exists in the collection");
    }
}
