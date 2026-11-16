package com.example.sales.search.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankFusionTest {

    @Test
    void productsFoundByBothRankingsWin() {
        var text = List.of(hit(1, 9.0), hit(2, 5.0), hit(3, 1.0));
        var semantic = List.of(hit(3, 0.9), hit(4, 0.8), hit(2, 0.7));

        var fused = RankFusion.merge(List.of(text, semantic), 10);

        // 3: ranks 3 and 1, 2: ranks 2 and 3, 1: rank 1 only, 4: rank 2 only
        assertThat(fused).extracting(SearchHit::productId).containsExactly(3L, 2L, 1L, 4L);
        assertThat(fused.getFirst().score()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fused.getFirst().name()).isEqualTo("Product 3");
    }

    @Test
    void resultIsLimited() {
        var fused = RankFusion.merge(List.of(List.of(hit(1, 1), hit(2, 1), hit(3, 1)), List.of()), 2);

        assertThat(fused).extracting(SearchHit::productId).containsExactly(1L, 2L);
    }

    private static SearchHit hit(long id, double score) {
        return new SearchHit(id, "Product " + id, "Acme", BigDecimal.TEN, "USD", score);
    }
}
