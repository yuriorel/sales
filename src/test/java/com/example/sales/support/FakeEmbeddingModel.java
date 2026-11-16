package com.example.sales.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.Locale;
import java.util.stream.IntStream;

/// A deterministic bag-of-words embedding: every word adds 1 to the dimension its hash
/// selects, then the vector is normalised. Texts that share words point in similar
/// directions, so cosine search behaves sensibly without calling a model.
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var inputs = request.getInstructions();
        return new EmbeddingResponse(IntStream.range(0, inputs.size())
                .mapToObj(i -> new Embedding(vector(inputs.get(i)), i))
                .toList());
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vector(String text) {
        var vector = new float[DIMENSIONS];
        // a small constant component keeps an empty text from becoming the zero vector
        vector[0] = 0.01f;
        for (var word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
            }
        }
        var norm = 0.0;
        for (var value : vector) {
            norm += value * value;
        }
        var length = (float) Math.sqrt(norm);
        for (var i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
        return vector;
    }
}
