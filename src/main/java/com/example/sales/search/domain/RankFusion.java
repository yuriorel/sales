package com.example.sales.search.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/// Reciprocal Rank Fusion: merges rankings whose scores are not comparable. A product
/// scores `1 / (K + rank)` in every ranking it appears in (rank from 1), summed.
public final class RankFusion {

    static final int K = 60;

    private RankFusion() {
    }

    public static List<SearchHit> merge(List<List<SearchHit>> rankings, int limit) {
        var fused = new LinkedHashMap<Long, SearchHit>();
        for (var ranking : rankings) {
            for (var rank = 0; rank < ranking.size(); rank++) {
                var hit = ranking.get(rank);
                var score = 1.0 / (K + rank + 1);
                fused.merge(hit.productId(), hit.withScore(score), (old, _) -> old.withScore(old.score() + score));
            }
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(limit)
                .toList();
    }
}
