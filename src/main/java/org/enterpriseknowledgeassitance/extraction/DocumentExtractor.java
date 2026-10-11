package org.enterpriseknowledgeassitance.extraction;

import java.nio.file.Path;

interface DocumentExtractor {

    /** Extracts text from a file already verified to match this extractor's format. */
    ExtractedDocument extract(Path file, ExtractionLimits limits) throws Exception;
}
