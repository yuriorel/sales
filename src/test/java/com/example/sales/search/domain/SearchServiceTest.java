package com.example.sales.search.domain;

import com.example.sales.support.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    final FullTextSearch fullText = mock(FullTextSearch.class);
    final GuideVectorStore vectors = mock(GuideVectorStore.class);
    final SearchService service = new SearchService(fullText, vectors, new FakeEmbeddingModel());

    @Test
    void fullTextModeUsesOnlyTheTextIndex() {
        when(fullText.search("speaker", 5)).thenReturn(List.of(hit(1)));

        assertThat(service.search("speaker", SearchMode.FULLTEXT, 5)).containsExactly(hit(1));
        verifyNoInteractions(vectors);
    }

    @Test
    void semanticModeSearchesWithTheQueryEmbedding() {
        when(vectors.nearestProducts(any(), eq(5))).thenReturn(List.of(hit(2)));

        assertThat(service.search("speaker", SearchMode.SEMANTIC, 5)).containsExactly(hit(2));
        verify(vectors).nearestProducts(eq(FakeEmbeddingModel.vector("speaker")), eq(5));
        verifyNoInteractions(fullText);
    }

    @Test
    void hybridModeFusesBothRankings() {
        when(fullText.search("speaker", 4)).thenReturn(List.of(hit(1), hit(2)));
        when(vectors.nearestProducts(any(), anyInt())).thenReturn(List.of(hit(2), hit(3)));

        var hits = service.search("speaker", SearchMode.HYBRID, 2);

        assertThat(hits).extracting(SearchHit::productId).containsExactly(2L, 1L);
        verify(vectors).nearestProducts(any(), eq(4));
    }

    private static SearchHit hit(long id) {
        return new SearchHit(id, "Product " + id, null, BigDecimal.ONE, "USD", 1.0);
    }
}
