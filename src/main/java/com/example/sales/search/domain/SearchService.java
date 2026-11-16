package com.example.sales.search.domain;

import com.example.sales.common.concurrency.Structured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/// Product search in three modes. Hybrid search runs full-text and semantic search
/// in parallel (structured concurrency) and merges them with [RankFusion].
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final FullTextSearch fullText;
    private final GuideVectorStore vectors;
    private final EmbeddingModel embeddings;

    public SearchService(FullTextSearch fullText, GuideVectorStore vectors, EmbeddingModel embeddings) {
        this.fullText = fullText;
        this.vectors = vectors;
        this.embeddings = embeddings;
    }

    public List<SearchHit> search(String query, SearchMode mode, int limit) {
        log.debug("Searching '{}' mode={} limit={}", query, mode, limit);
        var hits = switch (mode) {
            case FULLTEXT -> fullText.search(query, limit);
            case SEMANTIC -> semantic(query, limit);
            case HYBRID -> hybrid(query, limit);
        };
        log.debug("Search '{}' mode={} found {}", query, mode, hits);
        return hits;
    }

    private List<SearchHit> semantic(String query, int limit) {
        return vectors.nearestProducts(embeddings.embed(query), limit);
    }

    /// Each side fetches twice the limit, so products ranked lower on one side can still win on the sum.
    private List<SearchHit> hybrid(String query, int limit) {
        try (var scope = Structured.<List<SearchHit>>open()) {
            var text = scope.fork(() -> fullText.search(query, 2 * limit));
            var semantic = scope.fork(() -> semantic(query, 2 * limit));
            Structured.join(scope);
            log.debug("Hybrid '{}': {} full-text and {} semantic hits", query, text.get().size(), semantic.get().size());
            return RankFusion.merge(List.of(text.get(), semantic.get()), limit);
        }
    }
}
