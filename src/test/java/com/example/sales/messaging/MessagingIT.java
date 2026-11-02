package com.example.sales.messaging;

import com.example.sales.common.logging.RequestContext;
import com.example.sales.messaging.domain.EventPublisher;
import com.example.sales.messaging.domain.OrderPlaced;
import com.example.sales.messaging.domain.ProductGuideChanged;
import com.example.sales.order.domain.OrderProcessor;
import com.example.sales.search.domain.GuideIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// Publish -> Kafka -> listener -> dispatcher against the local broker. The handlers
/// are mocks. This class uses its own topic and consumer group, because other cached
/// test contexts also run listeners on the test topic.
@SpringBootTest(properties = {
        "sales.messaging.topic=" + MessagingIT.TOPIC,
        "spring.kafka.consumer.group-id=sales-messaging-it"})
@ActiveProfiles("test")
class MessagingIT {

    static final String TOPIC = "sales_online_it";
    static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    EventPublisher publisher;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ConsumerFactory<String, String> consumers;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    GuideIndexer guides;

    @MockitoBean
    OrderProcessor orders;

    @Test
    void publishedEventIsHandledWithItsCorrelationId() {
        var event = ProductGuideChanged.of(System.nanoTime());
        var seenCorrelationId = new AtomicReference<String>();
        doAnswer(_ -> {
            seenCorrelationId.set(RequestContext.currentCorrelationId());
            return null;
        }).when(guides).reindex(event.productId());

        ScopedValue.where(RequestContext.CURRENT, new RequestContext("corr-it-1", null)).run(() -> publisher.publish(event));

        verify(guides, timeout(WAIT.toMillis())).reindex(event.productId());
        assertThat(seenCorrelationId).hasValue("corr-it-1");
        await().atMost(WAIT).untilAsserted(() -> assertThat(processedType(event.eventId())).containsExactly("ProductGuideChanged"));
    }

    @Test
    void duplicateDeliveryIsHandledOnce() {
        var event = ProductGuideChanged.of(System.nanoTime());

        publisher.publish(event);
        publisher.publish(event);

        verify(guides, timeout(WAIT.toMillis())).reindex(event.productId());
        verify(guides, after(3000).times(1)).reindex(event.productId());
    }

    @Test
    void failingHandlerIsRetriedThenDeadLettered() {
        var event = OrderPlaced.of(UUID.randomUUID());
        doThrow(new IllegalStateException("payment gateway down")).when(orders).process(event.orderId());

        publisher.publish(event);

        // first attempt plus three retries, each rolled back, so the event is never recorded
        verify(orders, timeout(WAIT.toMillis()).times(4)).process(event.orderId());
        assertThat(deadLetterContaining(event.eventId().toString())).isTrue();
        assertThat(processedType(event.eventId())).isEmpty();
    }

    @Test
    void invalidMessageGoesStraightToDeadLetterTopic() {
        var garbage = "not an event " + UUID.randomUUID();

        kafka.send(TOPIC, "key", garbage).join();

        assertThat(deadLetterContaining(garbage)).isTrue();
        verify(guides, after(1000).times(0)).reindex(anyLong());
        verify(orders, times(0)).process(any());
    }

    private List<String> processedType(UUID eventId) {
        return jdbc.sql("select event_type from processed_events where event_id = :id").param("id", eventId)
                .query(String.class).list();
    }

    private boolean deadLetterContaining(String text) {
        try (var consumer = consumers.createConsumer("dlt-check-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(TOPIC + "-dlt"));
            var deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value() != null && record.value().contains(text)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
