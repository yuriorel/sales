package com.example.sales.messaging.kafka;

import com.example.sales.common.config.SalesProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

/// Topics and error handling for the Kafka adapter.
///
/// A failed message is retried with exponential backoff (0.5 s, 1 s, 2 s) and then
/// sent to `<topic>-dlt` (the spring-kafka 4 default). Invalid event JSON goes there at once.
@Configuration
@ConditionalOnProperty(name = "sales.messaging.provider", havingValue = "kafka")
class KafkaMessagingConfig {

    static final int PARTITIONS = 3;

    @Bean
    NewTopic salesTopic(SalesProperties properties) {
        return TopicBuilder.name(properties.messaging().topic()).partitions(PARTITIONS).replicas(1).build();
    }

    /// Same partition count as the main topic: a dead letter keeps its partition number.
    @Bean
    NewTopic salesDeadLetterTopic(SalesProperties properties) {
        return TopicBuilder.name(properties.messaging().topic() + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var backOff = new ExponentialBackOff(500, 2);
        backOff.setMaxAttempts(3);
        var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
