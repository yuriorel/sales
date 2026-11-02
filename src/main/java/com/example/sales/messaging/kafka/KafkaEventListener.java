package com.example.sales.messaging.kafka;

import com.example.sales.common.logging.CorrelationFilter;
import com.example.sales.common.logging.RequestContext;
import com.example.sales.messaging.domain.EventDispatcher;
import com.example.sales.messaging.domain.SalesEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/// Consumes the sales topic on virtual threads. The correlation id from the message
/// header is bound as [RequestContext#CURRENT] and put in the MDC, so the handler's
/// log lines match the request that published the event.
@Component
@ConditionalOnProperty(name = "sales.messaging.provider", havingValue = "kafka")
class KafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    private final EventDispatcher dispatcher;
    private final JsonMapper json;

    KafkaEventListener(EventDispatcher dispatcher, JsonMapper json) {
        this.dispatcher = dispatcher;
        this.json = json;
    }

    @KafkaListener(topics = "${sales.messaging.topic}")
    void onMessage(ConsumerRecord<String, String> record) {
        var correlationId = Optional.ofNullable(record.headers().lastHeader(CorrelationFilter.HEADER))
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(CorrelationFilter.MDC_KEY, correlationId);
        try {
            ScopedValue.where(RequestContext.CURRENT, new RequestContext(correlationId, null)).run(() -> {
                log.debug("Received {}-{}@{} key={} value={}",
                        record.topic(), record.partition(), record.offset(), record.key(), record.value());
                dispatcher.dispatch(json.readValue(record.value(), SalesEvent.class));
            });
        } finally {
            MDC.remove(CorrelationFilter.MDC_KEY);
        }
    }
}
