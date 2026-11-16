package com.example.sales.ai.domain;

import com.example.sales.catalog.domain.Product;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.search.domain.GuideVectorStore;
import com.example.sales.search.domain.GuideVectorStore.ChunkMatch;
import com.example.sales.support.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    final GuideVectorStore store = mock(GuideVectorStore.class);
    final ProductRepository products = mock(ProductRepository.class);
    final AtomicReference<Prompt> sent = new AtomicReference<>();
    final ChatModel chatModel = prompt -> {
        sent.set(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("Hold the button for 5 seconds [1]."))));
    };
    final AssistantService assistant = new AssistantService(store, products, new FakeEmbeddingModel(),
            ChatClient.builder(chatModel));

    final ChunkMatch reset = new ChunkMatch(7, "Speaker", 3, "Troubleshooting",
            "To reset, hold the {power} button for 5 seconds.", 0.25);

    @Test
    void answersFromRetrievedChunksAndListsSources() {
        when(store.nearestChunks(any(), eq(AssistantService.TOP_K), eq(null))).thenReturn(List.of(reset));

        var answer = assistant.ask("How do I reset it?", null);

        assertThat(answer.answer()).isEqualTo("Hold the button for 5 seconds [1].");
        assertThat(answer.sources()).singleElement().satisfies(source -> {
            assertThat(source.productId()).isEqualTo(7);
            assertThat(source.section()).isEqualTo("Troubleshooting");
            assertThat(source.similarity()).isCloseTo(0.75, within(1e-9));
        });
        // braces in guide text reach the model unchanged: the prompt is not rendered as a template
        assertThat(sent.get().getUserMessage().getText())
                .contains("[1] Speaker - Troubleshooting", "hold the {power} button", "Question: How do I reset it?");
        assertThat(sent.get().getSystemMessage().getText()).contains("Answer only from the numbered user guide excerpts");
    }

    @Test
    void questionAboutOneProductSearchesOnlyItsGuide() {
        when(products.findById(7)).thenReturn(Optional.of(mock(Product.class)));
        when(store.nearestChunks(any(), eq(AssistantService.TOP_K), eq(7L))).thenReturn(List.of(reset));

        assertThat(assistant.ask("How do I reset it?", 7L).sources()).hasSize(1);
    }

    @Test
    void unknownProductIsNotFound() {
        when(products.findById(8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assistant.ask("Anything?", 8L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withoutContextTheModelIsNotAsked() {
        when(store.nearestChunks(any(), eq(AssistantService.TOP_K), eq(null))).thenReturn(List.of());

        var answer = assistant.ask("How do I reset it?", null);

        assertThat(answer.answer()).isEqualTo(AssistantService.NO_CONTEXT);
        assertThat(answer.sources()).isEmpty();
        assertThat(sent).hasNullValue();
    }
}
