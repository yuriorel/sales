package com.example.sales.search.domain;

import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.search.domain.GuideVectorStore.EmbeddedChunk;
import com.example.sales.support.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuideIndexerTest {

    final ProductRepository products = mock(ProductRepository.class);
    final GuideVectorStore store = mock(GuideVectorStore.class);
    final FakeEmbeddingModel embeddings = spy(new FakeEmbeddingModel());
    final CacheAside cache = mock(CacheAside.class);
    final GuideIndexer indexer = new GuideIndexer(products, store, embeddings, cache);

    @Test
    @SuppressWarnings("unchecked")
    void chunksAreEmbeddedInBatchesAndReplaced() {
        var guide = IntStream.range(0, 800)
                .mapToObj(i -> "Line %d of a long guide with enough words to fill chunks.".formatted(i))
                .collect(Collectors.joining("\n"));
        var expected = GuideChunker.chunk(guide);
        when(products.guide(7)).thenReturn(Optional.of(guide));

        indexer.reindex(7);

        assertThat(expected.size()).isGreaterThan(GuideIndexer.BATCH_SIZE);
        verify(embeddings, times((expected.size() + GuideIndexer.BATCH_SIZE - 1) / GuideIndexer.BATCH_SIZE)).embed(anyList());
        ArgumentCaptor<List<EmbeddedChunk>> stored = ArgumentCaptor.forClass(List.class);
        verify(store).replace(eq(7L), stored.capture());
        assertThat(stored.getValue()).extracting(EmbeddedChunk::chunk).containsExactlyElementsOf(expected);
        assertThat(stored.getValue().getLast().embedding()).isEqualTo(FakeEmbeddingModel.vector(expected.getLast().content()));
        verify(cache).evict(ProductService.cacheKey(7));
    }

    @Test
    void deletedProductIsSkipped() {
        when(products.guide(8)).thenReturn(Optional.empty());

        indexer.reindex(8);

        verifyNoInteractions(store, cache);
    }
}
