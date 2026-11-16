package com.example.sales.ai.domain;

import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.search.domain.GuideVectorStore;
import com.example.sales.search.domain.GuideVectorStore.ChunkMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/// Answers product questions with retrieval-augmented generation: the guide chunks
/// closest to the question are the only context the chat model may use.
@Service
public class AssistantService {

    static final int TOP_K = 6;

    static final String NO_CONTEXT = "I could not find anything about this in the product guides.";

    private static final String SYSTEM = """
            You are the product support assistant of an online shop.
            Answer only from the numbered user guide excerpts in the user message.
            If the excerpts do not contain the answer, say that the guides do not cover it; never invent facts.
            Mention the product name, cite excerpts like [2], and keep the answer short and practical.
            """;

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final GuideVectorStore store;
    private final ProductRepository products;
    private final EmbeddingModel embeddings;
    private final ChatClient chat;

    public AssistantService(GuideVectorStore store, ProductRepository products, EmbeddingModel embeddings,
                            ChatClient.Builder chat) {
        this.store = store;
        this.products = products;
        this.embeddings = embeddings;
        this.chat = chat.defaultSystem(SYSTEM).build();
    }

    /// @param productId limits the context to one product's guide, or `null` for all guides
    public Answer ask(String question, Long productId) {
        log.debug("Question '{}' productId={}", question, productId);
        if (productId != null && products.findById(productId).isEmpty()) {
            throw new NotFoundException("Product", productId);
        }
        var chunks = store.nearestChunks(embeddings.embed(question), TOP_K, productId);
        log.debug("Retrieved {} chunks: {}", chunks.size(),
                chunks.stream().map(c -> c.productId() + "#" + c.chunkIndex()).toList());
        if (chunks.isEmpty()) {
            return new Answer(NO_CONTEXT, List.of());
        }
        // the prompt is passed as plain text, not as a template, because guides may contain braces
        var answer = chat.prompt().user(prompt(question, chunks)).call().content();
        log.debug("Answer: {}", answer);
        var sources = chunks.stream()
                .map(c -> new Answer.Source(c.productId(), c.productName(), c.chunkIndex(), c.section(), 1 - c.distance()))
                .toList();
        return new Answer(answer, sources);
    }

    static String prompt(String question, List<ChunkMatch> chunks) {
        var excerpts = IntStream.range(0, chunks.size())
                .mapToObj(i -> "[%d] %s - %s%n%s".formatted(
                        i + 1, chunks.get(i).productName(), chunks.get(i).section(), chunks.get(i).content()))
                .collect(Collectors.joining("\n\n"));
        return """
                User guide excerpts:

                %s

                Question: %s""".formatted(excerpts, question);
    }
}
