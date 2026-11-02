package com.example.sales.messaging.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesEventJsonTest {

    final JsonMapper json = JsonMapper.builder().build();

    static List<SalesEvent> events() {
        return List.of(OrderPlaced.of(UUID.randomUUID()),
                OrderCancelled.of(UUID.randomUUID(), "changed my mind"),
                ProductGuideChanged.of(42));
    }

    @ParameterizedTest
    @MethodSource("events")
    void roundTripsWithTypeProperty(SalesEvent event) {
        var text = json.writeValueAsString(event);

        assertThat(json.readTree(text).get("type").asString()).isEqualTo(event.getClass().getSimpleName());
        assertThat(json.readTree(text).has("key")).isFalse();
        assertThat(json.readValue(text, SalesEvent.class)).isEqualTo(event);
    }
}
