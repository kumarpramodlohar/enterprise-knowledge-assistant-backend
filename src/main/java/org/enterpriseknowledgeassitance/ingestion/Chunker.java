package org.enterpriseknowledgeassitance.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Splits extracted text into overlapping windows. A "token" is a whitespace-delimited word, which is
 * a cheap, provider-independent approximation of model tokens; size and overlap are configurable.
 */
@Component
public class Chunker {

    private final int size;
    private final int overlap;

    @Autowired
    public Chunker(EkaProperties props) {
        this(props.chunking().sizeTokens(), props.chunking().overlapTokens());
    }

    public Chunker(int size, int overlap) {
        if (size < 1 || overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("overlap must be >= 0 and smaller than size");
        }
        this.size = size;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(UUID documentId, ExtractedDocument doc) {
        List<ExtractedSegment> segments = doc.segments();
        int[] starts = new int[1024];
        int[] ends = new int[1024];
        int[] segOf = new int[1024];
        int[] globalStart = new int[1024];
        int n = 0;
        int base = 0;
        for (int s = 0; s < segments.size(); s++) {
            String text = segments.get(s).text();
            int i = 0;
            int len = text.length();
            while (i < len) {
                while (i < len && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (i >= len) {
                    break;
                }
                int st = i;
                while (i < len && !Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (n == starts.length) {
                    starts = java.util.Arrays.copyOf(starts, n * 2);
                    ends = java.util.Arrays.copyOf(ends, n * 2);
                    segOf = java.util.Arrays.copyOf(segOf, n * 2);
                    globalStart = java.util.Arrays.copyOf(globalStart, n * 2);
                }
                starts[n] = st;
                ends[n] = i;
                segOf[n] = s;
                globalStart[n] = base + st;
                n++;
            }
            base += len;
        }

        List<Chunk> chunks = new ArrayList<>();
        int step = size - overlap;
        for (int from = 0; from < n; from += step) {
            int to = Math.min(from + size, n);
            StringBuilder sb = new StringBuilder();
            int runStart = from;
            for (int t = from + 1; t <= to; t++) {
                if (t == to || segOf[t] != segOf[runStart]) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(segments.get(segOf[runStart]).text(), starts[runStart], ends[t - 1]);
                    runStart = t;
                }
            }
            ExtractedSegment first = segments.get(segOf[from]);
            int index = chunks.size();
            String text = sb.toString();
            chunks.add(new Chunk(stableId(documentId, index, text), documentId, index, text,
                    first.pageNumber(), first.section(), globalStart[from]));
            if (to == n) {
                break;
            }
        }
        return chunks;
    }

    static UUID stableId(UUID documentId, int index, String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            String key = documentId + ":" + index + ":" + HexFormat.of().formatHex(hash);
            return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
