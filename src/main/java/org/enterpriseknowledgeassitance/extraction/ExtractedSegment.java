package org.enterpriseknowledgeassitance.extraction;

/**
 * A piece of extracted text with the source location that is available.
 *
 * @param pageNumber 1-based page, or null when the format has no pages
 * @param section nearest heading, or null
 * @param startOffset character offset of the segment in the concatenated document text
 */
public record ExtractedSegment(String text, Integer pageNumber, String section, int startOffset) {}
