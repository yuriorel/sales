package com.example.sales.messaging.kafka;

import com.example.sales.common.config.SalesProperties;
import com.example.sales.common.config.SalesProperties.Messaging;
import com.example.sales.common.config.SalesProperties.MessagingProvider;
import com.example.sales.common.config.SalesProperties.Persistence;
import com.example.sales.common.config.SalesProperties.PersistenceProvider;
import com.example.sales.common.logging.CorrelationFilter;
import com.example.sales.common.logging.RequestContext;
import com.example.sales.messaging.domain.EventPublishingException;
import com.example.sales.messaging.domain.ProductGuideChanged;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {

    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final JsonMapper json = JsonMapper.builder().build();
    final KafkaEventPublisher publisher = new KafkaEventPublisher(kafka, json, new SalesProperties(
            new Persistence(PersistenceProvider.POSTGRES), new Messaging(MessagingProvider.KAFKA, "topic-x")));

    @Test
    @SuppressWarnings("unchecked")
    void sendsJsonKeyedByAggregateWithCorrelationHeader() {
        var metadata = new RecordMetadata(new TopicPartition("topic-x", 1), 5, 0, 0, 0, 0);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, metadata)));
        var event = ProductGuideChanged.of(42);

        ScopedValue.where(RequestContext.CURRENT, new RequestContext("corr-5", null)).run(() -> publisher.publish(event));

        var captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("topic-x");
        assertThat(record.key()).isEqualTo("42");
        assertThat(json.readValue(record.value(), ProductGuideChanged.class)).isEqualTo(event);
        assertThat(new String(record.headers().lastHeader(CorrelationFilter.HEADER).value(), StandardCharsets.UTF_8))
                .isEqualTo("corr-5");
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokerFailureIsPublishingException() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Topic not present in metadata after 5000 ms")));

        assertThatThrownBy(() -> publisher.publish(ProductGuideChanged.of(42)))
                .isInstanceOf(EventPublishingException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }
}
