package com.example.sales.search.domain;

import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.search.domain.GuideVectorStore.EmbeddedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;

/// Chunks a product's user guide, embeds the chunks in batches and replaces the stored ones.
@Service
public class GuideIndexer {

    /// chunks per embedding request
    static final int BATCH_SIZE = 32;

    private static final Logger log = LoggerFactory.getLogger(GuideIndexer.class);

    private final ProductRepository products;
    private final GuideVectorStore store;
    private final EmbeddingModel embeddings;
    private final CacheAside cache;

    public GuideIndexer(ProductRepository products, GuideVectorStore store, EmbeddingModel embeddings, CacheAside cache) {
        this.products = products;
        this.store = store;
        this.embeddings = embeddings;
        this.cache = cache;
    }

    /// A deleted product has nothing to index; its chunks were removed with it.
    public void reindex(long productId) {
        var guide = products.guide(productId);
        if (guide.isEmpty()) {
            log.debug("Product {} no longer exists, nothing to index", productId);
            return;
        }
        var chunks = GuideChunker.chunk(guide.get());
        log.debug("Embedding {} chunks of product {}", chunks.size(), productId);
        var embedded = chunks.stream()
                .gather(Gatherers.windowFixed(BATCH_SIZE))
                .flatMap(batch -> embed(batch).stream())
                .toList();
        store.replace(productId, embedded);
        // the cached product details include the guide summary (embedded chunk count)
        cache.evict(ProductService.cacheKey(productId));
        log.info("Indexed the guide of product {} as {} chunks", productId, embedded.size());
    }

    private List<EmbeddedChunk> embed(List<GuideChunk> batch) {
        var vectors = embeddings.embed(batch.stream().map(GuideChunk::content).toList());
        return IntStream.range(0, batch.size())
                .mapToObj(i -> new EmbeddedChunk(batch.get(i), vectors.get(i)))
                .toList();
    }
}
