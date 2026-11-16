package com.example.sales.ai.domain;

import java.util.List;

/// An assistant answer with the guide chunks it was based on.
public record Answer(String answer, List<Source> sources) {

    /// @param similarity cosine similarity between question and chunk, `1` at most
    public record Source(long productId, String productName, int chunkIndex, String section, double similarity) {
    }
}
