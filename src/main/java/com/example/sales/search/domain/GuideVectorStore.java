package com.example.sales.search.domain;

import java.util.List;

/// Persistence port for embedded guide chunks.
public interface GuideVectorStore {

    /// Replaces all chunks of a product.
    void replace(long productId, List<EmbeddedChunk> chunks);

    /// The chunks closest to `query` by cosine distance.
    ///
    /// @param productId limits the search to one product, or `null` for all products
    List<ChunkMatch> nearestChunks(float[] query, int limit, Long productId);

    /// The products closest to `query`, each ranked by its closest chunk.
    List<SearchHit> nearestProducts(float[] query, int limit);

    /// Products whose guide has not been embedded yet.
    List<Long> productsWithoutChunks();

    record EmbeddedChunk(GuideChunk chunk, float[] embedding) {
    }

    /// @param distance cosine distance, `0` for identical direction
    record ChunkMatch(long productId, String productName, int chunkIndex, String section, String content, double distance) {
    }
}
