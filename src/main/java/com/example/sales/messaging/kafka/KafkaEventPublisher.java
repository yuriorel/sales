package com.example.sales.messaging.kafka;

import com.example.sales.common.config.SalesProperties;
import com.example.sales.common.logging.CorrelationFilter;
import com.example.sales.common.logging.RequestContext;
import com.example.sales.messaging.domain.EventPublisher;
import com.example.sales.messaging.domain.EventPublishingException;
import com.example.sales.messaging.domain.SalesEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/// Publishes events as JSON, keyed by aggregate id, with the correlation id in the
/// `X-Correlation-Id` header.
@Component
@ConditionalOnProperty(name = "sales.messaging.provider", havingValue = "kafka")
class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final String topic;

    KafkaEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper json, SalesProperties properties) {
        this.kafka = kafka;
        this.json = json;
        this.topic = properties.messaging().topic();
    }

    @Override
    public void publish(SalesEvent event) {
        var record = new ProducerRecord<>(topic, event.key(), json.writeValueAsString(event));
        var correlationId = RequestContext.current()
                .map(RequestContext::correlationId)
                .orElseGet(() -> UUID.randomUUID().toString());
        record.headers().add(CorrelationFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        log.debug("Publishing to {} key={} value={}", topic, record.key(), record.value());
        try {
            var metadata = kafka.send(record).join().getRecordMetadata();
            log.debug("Published event {} to {}-{}@{}", event.eventId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (CompletionException e) {
            throw new EventPublishingException(event, e.getCause());
        }
    }
}
