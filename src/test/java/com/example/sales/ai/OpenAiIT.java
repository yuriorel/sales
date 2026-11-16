package com.example.sales.ai;

import com.example.sales.ai.domain.AssistantService;
import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryService;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/// The real OpenAI models: embedding dimension, guide indexing and a RAG answer.
/// Skipped when no `OPENAI_API_KEY` is set (environment or `.env`). Uses its own
/// topic and consumer group, like `MessagingIT`.
@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=openai",
        "sales.messaging.topic=sales_online_openai_it",
        "spring.kafka.consumer.group-id=sales-openai-it"})
@ActiveProfiles({"test", "openai"})
@EnabledIf("hasApiKey")
class OpenAiIT {

    @Autowired
    EmbeddingModel embeddings;

    @Autowired
    AssistantService assistant;

    @Autowired
    CategoryService categories;

    @Autowired
    ProductService products;

    @Autowired
    JdbcClient jdbc;

    static boolean hasApiKey() throws IOException {
        var env = Path.of(".env");
        return System.getenv("OPENAI_API_KEY") != null
                || Files.exists(env) && Files.readString(env).contains("OPENAI_API_KEY=");
    }

    @Test
    void embeddingsMatchTheVectorColumn() {
        assertThat(embeddings.embed("wireless speaker")).hasSize(1536);
    }

    @Test
    void assistantAnswersFromAnIndexedGuide() {
        var category = categories.create(new CategoryData(CatalogFixtures.slug(), "OpenAI " + UUID.randomUUID(), null));
        var base = CatalogFixtures.product(CatalogFixtures.sku(), category.id(), "59.00", List.of(), Map.of());
        var guide = """
                # Overview
                The Aurora speaker is a portable Bluetooth speaker.

                # Pairing
                To pair the Aurora speaker, hold the Bluetooth button for 7 seconds until the LED blinks purple.
                Then select "Aurora" in the Bluetooth settings of your phone.
                """;
        var id = products.create(new ProductData(base.sku(), base.categoryId(), "Aurora Speaker", base.brand(),
                base.description(), base.price(), base.currency(), base.rating(), base.tags(), base.imageUrls(),
                base.dimensions(), base.attributes(), guide, List.of("Overview", "Pairing"))).product().id();
        await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.sql(
                "select count(*) from product_guide_chunks where product_id = :id").param("id", id)
                .query(Long.class).single() > 0);

        var answer = assistant.ask("How long do I hold the Bluetooth button to pair it?", id);

        assertThat(answer.answer()).contains("7");
        assertThat(answer.sources()).isNotEmpty().allSatisfy(source -> assertThat(source.productId()).isEqualTo(id));
    }
}
