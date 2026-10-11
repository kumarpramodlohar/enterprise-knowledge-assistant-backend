package org.enterpriseknowledgeassitance.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Deterministic bag-of-words hashing embeddings so tests need no provider. */
public class FakeEmbeddingModel implements EmbeddingModel {

    public volatile int dimensions = 768;
    public volatile boolean fail = false;
    public volatile long delayMillis = 0;
    public final AtomicInteger calls = new AtomicInteger();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
        }
        if (fail) {
            throw new IllegalStateException("provider down: secret-detail");
        }
        List<Embedding> out = new ArrayList<>();
        int i = 0;
        for (String text : request.getInstructions()) {
            out.add(new Embedding(vector(text), i++));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    private float[] vector(String text) {
        float[] v = new float[dimensions];
        for (String w : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!w.isEmpty()) {
                v[Math.floorMod(w.hashCode(), dimensions)] += 1f;
            }
        }
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            v[0] = 1f;
        } else {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
