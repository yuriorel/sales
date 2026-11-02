package com.example.sales.messaging.kafka;

import com.example.sales.common.logging.CorrelationFilter;
import com.example.sales.common.logging.RequestContext;
import com.example.sales.messaging.domain.EventDispatcher;
import com.example.sales.messaging.domain.ProductGuideChanged;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class KafkaEventListenerTest {

    final JsonMapper json = JsonMapper.builder().build();
    final EventDispatcher dispatcher = mock(EventDispatcher.class);
    final KafkaEventListener listener = new KafkaEventListener(dispatcher, json);
    final AtomicReference<RequestContext> context = new AtomicReference<>();
    final AtomicReference<String> mdc = new AtomicReference<>();

    KafkaEventListenerTest() {
        doAnswer(_ -> {
            context.set(RequestContext.CURRENT.get());
            mdc.set(MDC.get(CorrelationFilter.MDC_KEY));
            return null;
        }).when(dispatcher).dispatch(any());
    }

    @Test
    void bindsCorrelationIdFromHeader() {
        var record = record(json.writeValueAsString(ProductGuideChanged.of(42)));
        record.headers().add(CorrelationFilter.HEADER, "corr-9".getBytes(StandardCharsets.UTF_8));

        listener.onMessage(record);

        assertThat(context.get()).isEqualTo(new RequestContext("corr-9", null));
        assertThat(mdc.get()).isEqualTo("corr-9");
        assertThat(MDC.get(CorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() {
        listener.onMessage(record(json.writeValueAsString(ProductGuideChanged.of(42))));

        assertThat(context.get().correlationId()).matches("[0-9a-f-]{36}");
    }

    @Test
    void invalidJsonFailsWithoutDispatching() {
        assertThatThrownBy(() -> listener.onMessage(record("not json"))).isInstanceOf(JacksonException.class);
        verifyNoInteractions(dispatcher);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("sales_online_test", 0, 0, "42", value);
    }
}
